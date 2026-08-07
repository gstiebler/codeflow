package codeflow.ir

import codeflow.java.AstReader
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lowering, asserted on directly, with nothing rendered.
 *
 * This is the point of having an IR at all. Every existing test of what codeflow understands has to
 * go through a Mermaid document or a JSON payload, so a question about *the analysis* is answered by
 * reading a diagram - and the golden files, which are most of the suite, certify only that the
 * diagram has not changed. Here the subject is the instruction list itself: what the method means,
 * in the order Java evaluates it, before anything has decided how to draw it.
 */
class LoweringTest {

    private val testResourcesPath = Path.of(System.getProperty("user.dir"))
        .resolve("src").resolve("test").resolve("resources")

    /** The instruction list of one method, as text, so an assertion reads like the source does. */
    private fun lower(testDir: String, testFiles: List<String>, method: String): List<String> {
        val testDirPath = testResourcesPath.resolve(testDir)
        val paths = testFiles.map { testDirPath.resolve(it) }
        val analysis = AstReader(testResourcesPath).analyse(paths)
        val target = analysis.method(method)
        return Lowering(analysis.symbols).lower(target).render()
    }

    /**
     * `int bonus = base * 2; return base + bonus;` - the smallest method that has an order at all.
     *
     * Each instruction names the values it consumes by the position of the instruction that produced
     * them, so the list is a dataflow graph already: `binOp * 0 1` says the multiply takes what
     * instruction 0 and instruction 1 produced. That is the property the graph builder needs and the
     * only one it needs, which is what lets it stop looking at javac trees.
     */
    @Test
    fun aMethodBodyLowersToInstructionsInTheOrderJavaEvaluatesThem() {
        assertEquals(
            listOf(
                "0: read base",
                "1: const 2",
                "2: binOp * 0 1",
                "3: write bonus <- 2",
                "4: read base",
                "5: read bonus",
                "6: binOp + 4 5",
                "7: return 6"
            ),
            lower("noMain", listOf("Report.java"), "Report#total")
        )
    }

    /**
     * A declaration and an assignment are one instruction, and a declaration with no initializer is
     * none at all.
     *
     * `int d;` writes nothing, so there is nothing to lower; the write appears at `d = b`, where the
     * value arrives. Both spellings produce the same `write`, which is what makes them the same
     * thing to everything downstream.
     */
    @Test
    fun aDeclarationAndAnAssignmentBothLowerToAWrite() {
        assertEquals(
            listOf(
                "0: const 5",
                "1: write a <- 0",
                "2: read a",
                "3: write b <- 2",
                "4: read b",
                "5: const 8",
                "6: binOp + 4 5",
                "7: write c <- 6",
                "8: read b",
                "9: write d <- 8",
                "10: read d",
                "11: write e <- 10"
            ),
            lower("base", listOf("App.java"), "App#main")
        )
    }

    /**
     * `-x` and `!flag` are operations, not the operand handed back.
     *
     * This is the mistake the `MODELLED_EXPRESSIONS` gate exists to prevent, in its original form: a
     * scanner that walks the children and returns one of their results lowers `!flag` to the read of
     * `flag`, so the negation is not in the instruction list at all and no reader of it could tell.
     */
    @Test
    fun aUnaryOperatorIsItsOwnInstruction() {
        assertEquals(
            listOf(
                "0: const 7",
                "1: write value <- 0",
                "2: const true",
                "3: write flag <- 2",
                "4: read value",
                "5: unOp neg 4",
                "6: write negated <- 5",
                "7: read flag",
                "8: unOp not 7",
                "9: write inverted <- 8",
                "10: const 0",
                "11: write counter <- 10",
                "12: read counter",
                "13: unOp postInc 12",
                "14: read counter",
                "15: write afterIncrement <- 14"
            ),
            lower("unary", listOf("App.java"), "App#main")
        )
    }

    /**
     * All three parts of `c ? a : b` reach the result, including the condition.
     *
     * The dropped branch is the bug that cost a real debugging session: left to the default walk the
     * expression comes back as whichever branch was reached first, so the diagram claims the code
     * can only produce one of its two values and loses the guard entirely.
     */
    @Test
    fun aConditionalKeepsItsConditionAndBothBranches() {
        assertEquals(
            listOf(
                "0: const 0",
                "1: write divisor <- 0",
                "2: const 100",
                "3: write value <- 2",
                "4: const 7",
                "5: write fallback <- 4",
                "6: read divisor",
                "7: const 0",
                "8: binOp == 6 7",
                "9: read fallback",
                "10: read value",
                "11: read divisor",
                "12: binOp div 10 11",
                "13: select ternary 8 9 12",
                "14: write guarded <- 13"
            ),
            lower("ternary", listOf("App.java"), "App#main")
        )
    }

    /**
     * A name that resolves to a field is a field read, whether or not `this.` was written.
     *
     * This is the decision the lowering exists to make once. `value` and `this.value` are the same
     * access spelled two ways, and only javac can say so - the walk that built the graph asked the
     * same question in two places, `visitIdentifier` and `visitMemberSelect`, and each had to
     * remember to consult the object the method runs on.
     *
     * Neither spelling produces a value for `this`. The object is not a value the code computed, it
     * is the one the method was entered on, so it is the *receiver* of the access rather than an
     * input to it - which is why `readField this.value` names it in place of a `Val`.
     */
    @Test
    fun aFieldReadIsOneInstructionWhicheverWayItIsWritten() {
        assertEquals(
            listOf(
                "0: readField this.value",
                "1: readField this.step",
                "2: binOp + 0 1",
                "3: return 2"
            ),
            lower("implicitThis", listOf("App.java"), "Counter#advance")
        )
    }

    /** And a write to a field is the same access in the other direction. */
    @Test
    fun aFieldWriteNamesTheObjectItLandsOn() {
        assertEquals(
            listOf(
                "0: read initial",
                "1: writeField this.value <- 0",
                "2: const 3",
                "3: writeField this.step <- 2"
            ),
            lower("implicitThis", listOf("App.java"), "Counter#<init>")
        )
    }

    /**
     * A call names the object it runs on and the values passed to it, and nothing else.
     *
     * What it does *not* do is inline the callee. That is the split the IR is for: a body is
     * lowered once, and which call site it is being read from, which object it is running on and
     * how many boxes it draws are the builder's questions. `AstBlockProcessor` answered them all at
     * once, which is why asking "what object did this call return?" had to be memoised to stop the
     * question from inlining the callee a second time.
     */
    @Test
    fun aCallNamesItsReceiverAndArgumentsWithoutInliningTheCallee() {
        assertEquals(
            listOf(
                "0: const 10",
                "1: new Counter 0",
                "2: write counter <- 1",
                "3: read counter",
                "4: call advance on 3",
                "5: write result <- 4"
            ),
            lower("implicitThis", listOf("App.java"), "App#main")
        )
    }

    /**
     * `new X(...)` is one instruction that both creates the object and produces it as a value.
     *
     * Those used to be separate: the object came from a scanner run as a side effect of an
     * assignment working out what the left-hand side now pointed at, and nothing produced a value
     * at all - so a `new` in an argument position failed outright, and in an assignment position
     * the failure was swallowed and the object appeared to come from nowhere.
     *
     * A class from outside the sources - `StringBuilder` - is the same instruction. Whether there
     * is a constructor body to draw is the builder's question, and it has the element to ask it.
     */
    @Test
    fun constructionIsOneInstruction() {
        assertEquals(
            listOf(
                "0: const 4",
                "1: write seed <- 0",
                "2: read seed",
                "3: new Box 2",
                "4: write box <- 3",
                "5: const \"text\"",
                "6: new StringBuilder 5",
                "7: write sb <- 6",
                "8: read box",
                "9: readField 8.held",
                "10: write read <- 9"
            ),
            lower("newObject", listOf("App.java"), "App#main")
        )
    }
}
