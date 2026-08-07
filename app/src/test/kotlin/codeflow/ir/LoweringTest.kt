package codeflow.ir

import codeflow.graph.GraphException
import codeflow.java.AstReader
import codeflow.java.MethodSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    /** Every method of one fixture, lowered, so a whole directory can be swept. */
    private fun lowerAll(testDir: String): List<MethodBody> {
        val testDirPath = testResourcesPath.resolve(testDir)
        val paths = Files.walk(testDirPath).filter { it.toString().endsWith(".java") }.toList()
        val analysis = AstReader(testResourcesPath).analyse(paths)
        val lowering = Lowering(analysis.symbols)
        return analysis.methods().map { lowering.lower(it) }.also { write(testDirPath, it) }
    }

    /**
     * The instruction list of every method of a fixture, written next to the source it came from.
     *
     * Not an assertion and not a golden file - `ir.txt` is gitignored, and nothing reads it back.
     * It is there to be looked at: the IR is what the lowering actually decided, and the only other
     * way to see it is to add a test that spells the whole list out. Reading `App.java`, `ir.txt`
     * and `truth.md` side by side is source, meaning and diagram for the same fixture, which is
     * what makes it possible to say which of the three a surprise came from.
     *
     * Written on every run rather than only when missing, for the opposite reason to
     * [codeflow.AppTest.codeflow]'s snapshots: a golden that rewrote itself would certify nothing,
     * and this certifies nothing by design - a stale one would be worse than none.
     */
    private fun write(testDirPath: Path, bodies: List<MethodBody>) {
        val text = bodies.joinToString("\n\n") { body ->
            (listOf(MethodSpec.of(body.method)) + body.render()).joinToString("\n")
        }
        Files.writeString(testDirPath.resolve("ir.txt"), text + "\n")
    }

    private fun fixtures(): List<String> = Files.list(testResourcesPath)
        .filter { Files.isDirectory(it) && Files.walk(it).anyMatch { f -> f.toString().endsWith(".java") } }
        .map { it.fileName.toString() }
        .filter { it !in DOES_NOT_LOWER }
        .sorted()
        .toList()

    private companion object {
        /**
         * Fixtures the sweep leaves out, because refusing to lower them is what they are for.
         *
         * One entry, and it is a gap in the *program* rather than in codeflow: `unwrittenLocal`
         * reads a local nothing has written, which javac rejects and attribution accepts. Its own
         * test is [aLocalWithNoReachingDefinitionFailsWhereItIsRead].
         */
        val DOES_NOT_LOWER = setOf("unwrittenLocal")
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
                "0: param base",
                "1: const 2",
                "2: binOp * 0 1",
                "3: write bonus <- 2",
                "4: binOp + 0 3",
                "5: return 4"
            ),
            lower("noMain", listOf("Report.java"), "Report#total")
        )
    }

    /**
     * A use names the definition that reaches it, so a read is not an instruction.
     *
     * `binOp + 0 3` says the addition takes the parameter and the multiply, which is the question a
     * dataflow graph is built to answer, asked and answered inside the method. What it replaced was
     * a `read base` instruction that named only the *name*, leaving "which of the writes to `base`
     * is this one" to be worked out later by whoever drew the graph - from a single mutable slot per
     * variable, which is the thing that has no answer once there are two paths to a use.
     *
     * The parameters are definitions too, which is what makes that total: every use in the body
     * resolves to an instruction, including the ones the caller supplies.
     */
    @Test
    fun aParameterIsADefinitionAndAUseNamesIt() {
        assertEquals(
            listOf(
                "0: param a",
                "1: param b",
                "2: binOp + 0 1",
                "3: write c <- 2",
                "4: return 3"
            ),
            lower("funcCall", listOf("App.java"), "App#methodA")
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
                "0: param args",
                "1: const 5",
                "2: write a <- 1",
                "3: write b <- 2",
                "4: const 8",
                "5: binOp + 3 4",
                "6: write c <- 5",
                "7: write d <- 3",
                "8: write e <- 7"
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
                "0: param args",
                "1: const 7",
                "2: write value <- 1",
                "3: const true",
                "4: write flag <- 3",
                "5: unOp neg 2",
                "6: write negated <- 5",
                "7: unOp not 4",
                "8: write inverted <- 7",
                "9: const 0",
                "10: write counter <- 9",
                "11: unOp postInc 10",
                "12: write afterIncrement <- 11"
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
                "0: param args",
                "1: const 0",
                "2: write divisor <- 1",
                "3: const 100",
                "4: write value <- 3",
                "5: const 7",
                "6: write fallback <- 5",
                "7: const 0",
                "8: binOp == 2 7",
                "9: binOp div 4 2",
                "10: select ternary 8 6 9",
                "11: write guarded <- 10"
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
                "0: param initial",
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
                "0: param args",
                "1: const 10",
                "2: new Counter 1",
                "3: write counter <- 2",
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
                "0: param args",
                "1: const 4",
                "2: write seed <- 1",
                "3: new Box 2",
                "4: write box <- 3",
                "5: const \"text\"",
                "6: new StringBuilder 5",
                "7: write sb <- 6",
                "8: readField 4.held",
                "9: write read <- 8"
            ),
            lower("newObject", listOf("App.java"), "App#main")
        )
    }

    /**
     * A class declared inside a method body contributes nothing at the point it is declared.
     *
     * `class Doubler { int twice(int n) { return n * 2; } }` runs nothing: `twice` runs when
     * something calls it, and that call site reaches the declaration the way every other one does.
     * Left to the default walk the declaration is descended into and every method it declares is
     * lowered into the enclosing method - so `main` gains a multiply it does not perform, attached
     * to a parameter no caller has filled in. A statement that produces no value can still put a
     * flow on the diagram that the program does not have.
     */
    @Test
    fun aClassDeclaredInsideAMethodDoesNotLowerItsBodiesIntoThatMethod() {
        assertEquals(
            listOf(
                "0: param args",
                "1: const 3",
                "2: write seed <- 1",
                "3: new Doubler",
                "4: call twice on 3 2",
                "5: write result <- 4",
                "6: readField static.out",
                "7: call println on 6 5"
            ),
            lower("localClass", listOf("App.java"), "App#main")
        )
    }

    /**
     * Every method of every fixture lowers, and no instruction consumes a value that does not exist
     * yet.
     *
     * A sweep rather than a list, because the fixtures are the corpus of constructs codeflow claims
     * to understand and a lowering that quietly drops one of them is exactly what this whole
     * approach is meant to make impossible. The forward-reference check is what makes the list a
     * dataflow graph rather than a transcript: `binOp * 0 1` is only meaningful if 0 and 1 have
     * already been produced.
     *
     * A loop's header is the one exception, and is named as one: the value arriving back at it is
     * produced by the body below it. See [Phi.addPath].
     */
    @Test
    fun everyFixtureLowersAndEveryValueIsProducedBeforeItIsUsed() {
        val swept = fixtures().flatMap { fixture ->
            lowerAll(fixture).map { fixture to it }
        }
        assertTrue(swept.size > 50, "the sweep found almost nothing to lower: ${swept.size}")
        swept.forEach { (fixture, body) ->
            body.instructions.forEachIndexed { index, insn ->
                if (insn is Phi) return@forEachIndexed
                insn.inputs.forEach { input ->
                    assertTrue(
                        input.index < index,
                        "$fixture ${body.method.name.name}: instruction $index (${insn.render()}) " +
                                "consumes $input, which is not produced before it"
                    )
                }
            }
        }
    }

    /**
     * A use after an `if` names both the value the branch wrote and the one it did not.
     *
     * `if (b == 7) { b = 13; } else { a = 17; }` leaves each of `a` and `b` with two values that can
     * reach the lines below it, depending on which way the branch went. The walk had one slot per
     * variable and the branch that ran last won: `c = b` was drawn taking only the 13 and `d = a`
     * only the 17, so the diagram asserted that `c` cannot be 5 - a flow the program has, missing,
     * with the two boxes it needed sitting right there on the page.
     *
     * A phi is the join written down. It takes the value from each path and is what a use below the
     * `if` resolves to, which is what makes a use resolve to *one* instruction even where control
     * flow means several values arrive.
     */
    @Test
    fun aUseAfterABranchNamesTheValueFromEachPath() {
        assertEquals(
            listOf(
                "0: param args",
                "1: const 5",
                "2: write a <- 1",
                "3: write b <- 2",
                "4: const 7",
                "5: binOp == 3 4",
                "6: const 13",
                "7: write b <- 6",
                "8: const 17",
                "9: write a <- 8",
                "10: phi a 2 9",
                "11: phi b 7 3",
                "12: write c <- 11",
                "13: write d <- 10"
            ),
            lower("if1", listOf("App.java"), "App#main")
        )
    }

    /**
     * A loop's header holds what the body left behind, which is produced after it.
     *
     * `phi y 4 11` at instruction 7 names instruction 11, which is the `y = 7` inside the body -
     * the only place the instruction list points forwards, and the reason a [Val] is an index
     * rather than a reference to an instruction. The header has to exist before the body is
     * lowered, since every use inside the body resolves to it; what the body leaves behind is only
     * known once the body has been walked.
     *
     * `i` gets one too, from the update: the condition tests the incremented counter on every
     * iteration but the first, and `i++` used to produce a box with nothing leaving it.
     */
    @Test
    fun aLoopHeaderTakesTheValueTheBodyLeavesBehind() {
        assertEquals(
            listOf(
                "0: param args",
                "1: const 5",
                "2: write x <- 1",
                "3: const 0",
                "4: write y <- 3",
                "5: const 0",
                "6: write i <- 5",
                "7: phi y 4 11",
                "8: phi i 6 12",
                "9: binOp < 8 2",
                "10: const 7",
                "11: write y <- 10",
                "12: unOp postInc 8",
                "13: write z <- 7",
                "14: const 1",
                "15: binOp += 7 14",
                "16: write y <- 15"
            ),
            lower("forLoop", listOf("App.java"), "App#main")
        )
    }

    /**
     * A local read with nothing reaching it fails here, at the line that reads it.
     *
     * This is the one failure that stays hard, and it is now the lowering's to raise: a local cannot
     * be read before it is written, so finding no definition means either the program does not
     * compile or the analysis has lost something. Either way drawing a value arriving from nowhere
     * would be indistinguishable from a real one - which is the silent wrongness everything else
     * here is arranged to prevent, with the loud failure taken out.
     *
     * It moved because the definitions moved. The graph builder used to notice, several steps later
     * and with only a name to blame; a use resolves to its definition while the tree is still in
     * hand, so the position in the message is the read itself.
     */
    @Test
    fun aLocalWithNoReachingDefinitionFailsWhereItIsRead() {
        val failure = assertFailsWith<GraphException> {
            lower("unwrittenLocal", listOf("App.java"), "App#main")
        }
        assertEquals("'total' at unwrittenLocal/App.java:19:28 has no value reaching it", failure.message)
    }

    /**
     * A construct codeflow does not model becomes an instruction saying so, with its operands still
     * flowing in.
     *
     * The alternative is the one thing that must never happen: a scanner that walks the children and
     * hands back one of their results turns `(int) 3L` into the literal, and nothing downstream can
     * tell that a cast was there. Drawn, that is a diagram which reads fine and is wrong.
     */
    @Test
    fun anUnmodelledExpressionIsAnInstructionRatherThanOneOfItsChildren() {
        assertEquals(
            listOf(
                "0: param args",
                "1: readField static.out",
                "2: const \"x\"",
                "3: call println on 1 2",
                "4: const 3L",
                "5: unmodelled TYPE_CAST 4",
                "6: write count <- 5"
            ),
            lower("unsupported", listOf("App.java"), "App#main")
        )
    }

    /**
     * And the gate reaches what is not an expression, which is the hole it could not cover before.
     *
     * `case String text ->` binds a name. The gate in `AstBlockProcessor.scan` sees expressions
     * only, so a construct that binds one and is not modelled says nothing at all, and the failure
     * surfaces further down as a read of a name with no value - blaming a line that is not the one
     * at fault. The enhanced `for` and the `catch` parameter were both found that way.
     *
     * A binding pattern is modelled here, so this fixture's name now resolves; what is named instead
     * is any pattern that is *not* one, at the label's own line. The bind takes the selector's own
     * value rather than a fresh read of it, because that is what the pattern matched against - the
     * name and the thing being switched on are the same value, and the diagram should say so.
     */
    @Test
    fun aPatternLabelBindsItsNameInsteadOfFailingSeveralLinesLater() {
        assertEquals(
            listOf(
                "0: param args",
                "1: write value <- 0",
                "2: bind text <- 1",
                "3: readField static.out",
                "4: call println on 3 2",
                "5: readField static.out",
                "6: const \"none\"",
                "7: call println on 5 6"
            ),
            lower("unboundLocal", listOf("App.java"), "App#main")
        )
    }
}
