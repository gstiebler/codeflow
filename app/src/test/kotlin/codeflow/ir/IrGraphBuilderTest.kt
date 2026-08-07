package codeflow.ir

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import codeflow.java.AstReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The graph built from the IR, against the graph built from the trees.
 *
 * A port has one question worth asking, and it is not "is this right" - the tree walker's answers
 * are what the whole suite already certifies. It is "is this the same". So each fixture is built
 * both ways and the two are compared *structurally*: the multiset of `label:TYPE` nodes and of
 * `label:TYPE -> label:TYPE` edges, with ids and serials stripped. Ids move whenever anything is
 * created in a different order, and a diff of them says nothing about whether the graph changed.
 *
 * Where the two deliberately differ, the difference is asserted here rather than hidden: a port
 * that quietly draws a different graph is the failure this project exists to avoid, so a change has
 * to be written down as an expectation before it counts as intended.
 */
class IrGraphBuilderTest {

    private val testResourcesPath = Path.of(System.getProperty("user.dir"))
        .resolve("src").resolve("test").resolve("resources")

    /**
     * The graph reduced to what a reader would compare: what is drawn, and what points at what.
     *
     * Sorted, because creation order is exactly what a port is allowed to change - the same graph
     * built by walking instructions rather than trees reaches the same nodes in a different
     * sequence, and serial numbers follow that sequence.
     */
    private fun shape(root: GraphBuilderBlock): List<String> {
        val lines = ArrayList<String>()
        fun describe(node: GraphNode) = "${node.label}:${node.getType()}"
        fun walk(block: GraphBuilderBlock) {
            block.graph.getNodes().forEach { node ->
                lines.add("node ${describe(node)}")
                node.edgesIterator().forEach { lines.add("edge ${describe(node)} -> ${describe(it)}") }
            }
            block.calledMethods.forEach { walk(it) }
        }
        walk(root)
        return lines.sorted()
    }

    private fun sources(fixture: String): List<Path> =
        Files.walk(testResourcesPath.resolve(fixture)).filter { it.toString().endsWith(".java") }.toList()

    private fun fromTrees(fixture: String): List<String> =
        shape(AstReader(testResourcesPath).process(sources(fixture)))

    private fun fromIr(fixture: String): List<String> =
        shape(AstReader(testResourcesPath).processFromIr(sources(fixture)))

    private fun assertSameGraph(fixture: String) =
        assertEquals(fromTrees(fixture), fromIr(fixture), "$fixture is drawn differently from the IR")

    /**
     * Locals, literals and operators - the part of a method that needs nothing but the instruction
     * list.
     *
     * `base` is five assignments and one `+`, so every value in it is produced and consumed inside
     * one block: no call to inline, no object to track, no field to find. If the instruction list
     * really is a dataflow graph then this is the case where saying so is a matter of walking it in
     * order and drawing what each instruction consumes.
     */
    @Test
    fun localsAndOperatorsDrawTheSameGraphFromTheIr() = assertSameGraph("base")

    /** And an operator whose value nothing hands back - `!flag` must not come out as `flag`. */
    @Test
    fun unaryOperatorsDrawTheSameGraphFromTheIr() = assertSameGraph("unary")

    /** And a value chosen from alternatives, where all three parts reach the result. */
    @Test
    fun aConditionalDrawsTheSameGraphFromTheIr() = assertSameGraph("ternary")

    /**
     * A call whose body is in the sources, inlined at the call site.
     *
     * `funcCall` is the shape the whole design turns on: one method reached from two places is two
     * sets of boxes, and the locals inside each are different variables. From the IR that is one
     * lowered body read twice, which is the thing the split was for.
     */
    @Test
    fun aCallInlinesTheSameWayFromTheIr() = assertSameGraph("funcCall")

    /** A call with no body to inline, which is opaque: receiver and arguments in, result out. */
    @Test
    fun anExternalCallDrawsTheSameGraphFromTheIr() = assertSameGraph("externalCall")

    /** Fields, found through the object they live on rather than through the block they are in. */
    @Test
    fun fieldsDrawTheSameGraphFromTheIr() = assertSameGraph("implicitThis")

    /** `new X(...)`, which creates an object and produces it as a value. */
    @Test
    fun constructionDrawsTheSameGraphFromTheIr() = assertSameGraph("newObject")

    /**
     * Every fixture in the suite, both ways, in one assertion.
     *
     * The tests above name the shapes the port turns on, which is what makes a failure in one of them
     * readable. This is the one that decides whether the port is finished: the corpus is what the
     * rest of the suite certifies, and a construct handled by the tree walker and forgotten here
     * would otherwise be found by a golden file moving, which says only that something changed.
     *
     * A fixture that fails is compared too, and by its message. Several exist to fail - a local with
     * no reaching definition, a name a switch label never bound - and a port that turned one of those
     * into a graph would have removed the loud failure that is the whole point of them.
     */
    @Test
    fun everyFixtureDrawsTheSameGraphFromTheIr() {
        val differences = (fixtures() - INTENDED_DIFFERENCES.keys).mapNotNull { fixture ->
            val fromTrees = outcome { fromTrees(fixture) }
            val fromIr = outcome { fromIr(fixture) }
            if (fromTrees == fromIr) null else "$fixture:\n  trees: $fromTrees\n  ir:    $fromIr"
        }
        assertEquals(emptyList(), differences, "${differences.size} fixtures are drawn differently")
    }

    /**
     * Every fixture excluded above still differs, so an exclusion cannot outlive its reason.
     *
     * A list of names allowed to differ is a hole in the assertion that matters most here, and one
     * that widens quietly: the day a fixture stops differing, its entry goes on excusing whatever
     * happens to it next. Each is asserted individually below; this only keeps the list honest.
     */
    @Test
    fun everyIntendedDifferenceIsStillADifference() {
        val agreeing = INTENDED_DIFFERENCES.keys.filter {
            outcome { fromTrees(it) } == outcome { fromIr(it) }
        }
        assertEquals(emptyList(), agreeing, "these no longer differ and should not be excluded")
    }

    /**
     * A lambda's `int` parameter is a primitive, and is drawn as one.
     *
     * The tree walker had no answer to give: the parameter of a lambda is bound by a visitor that
     * predates the question, so every one came out an object variable whatever its type. The IR
     * carries javac's answer about the declared type on the binding itself, so `value -> value *
     * base` on an `IntUnaryOperator` says `int`, which is what the reader is looking at.
     *
     * The shape of the graph is untouched - the same edges reach the same places, and only what the
     * box says about itself changes.
     */
    @Test
    fun aPrimitiveLambdaParameterIsDrawnAsAPrimitiveFromTheIr() {
        assertEquals(
            listOf(
                "ir only: edge value:VARIABLE -> *:BIN_OP",
                "ir only: node value:VARIABLE",
                "trees only: edge value:OBJ_VARIABLE -> *:BIN_OP",
                "trees only: node value:OBJ_VARIABLE"
            ),
            differences("lambda")
        )
        assertEquals(
            listOf(
                "ir only: edge value:VARIABLE -> +:BIN_OP",
                "ir only: edge value:VARIABLE -> +:BIN_OP",
                "ir only: node value:VARIABLE",
                "trees only: edge value:OBJ_VARIABLE -> +:BIN_OP",
                "trees only: edge value:OBJ_VARIABLE -> +:BIN_OP",
                "trees only: node value:OBJ_VARIABLE"
            ),
            differences("statementLambda")
        )
    }

    /**
     * A class declared inside a method body is a declaration, and running it where it stands is a
     * flow the program does not have.
     *
     * The tree walker scanned into `class Doubler { int twice(int n) { return n * 2; } }` as if it
     * were a statement, so `main` gained a multiply on a parameter nobody had passed and a `return`
     * that was not its own - and then failed, because the parameter it had just read has no value in
     * `main`. The failure is what made it visible; the flow is what made it worth fixing.
     *
     * What the IR draws instead is the call, opaque: `AstProcessor` records methods declared in a
     * class, not in a method body, so there is no body registered to inline. That is the same
     * reading as any method from outside the corpus, and it is a narrower gap than the one it
     * replaces - the diagram no longer claims `main` multiplies anything.
     */
    @Test
    fun aLocalClassDeclarationRunsNothingWhereItStands() {
        val message = assertFailsWith<GraphException> { fromTrees("localClass") }.message!!
        assertTrue("localClass/App.java:15" in message, "the tree walker failed elsewhere: $message")

        val ir = fromIr("localClass")
        assertTrue("edge seed:VARIABLE -> twice:EXTERNAL" in ir, "the argument does not reach the call: $ir")
        assertTrue("edge twice:EXTERNAL -> result:VARIABLE" in ir, "the result does not come back: $ir")
        assertTrue(ir.none { "*:BIN_OP" in it }, "the declared method's body runs in main: $ir")
        assertTrue(ir.none { "node n:" in it }, "the declared method's parameter is read in main: $ir")
    }

    /**
     * `case String text ->` binds `text`, on a `switch` used as a statement as well as one used as
     * an expression.
     *
     * This is the failure the `unboundLocal` fixture was built around: the label bound nothing, so
     * the read several lines later found no node and blamed a line that was not at fault. The
     * lowering binds the label itself, so the name has a value where the source says it does, and
     * what it matched flows into it.
     *
     * The loud failure it used to demonstrate is not gone, only unreachable from this fixture - see
     * [aReadWithNoReachingDefinitionStillFails], which asks the builder directly.
     */
    @Test
    fun aPatternOnASwitchStatementBindsItsNameFromTheIr() {
        val message = assertFailsWith<GraphException> { fromTrees("unboundLocal") }.message!!
        assertTrue("unboundLocal/App.java:15" in message, "the tree walker failed elsewhere: $message")

        val ir = fromIr("unboundLocal")
        assertTrue("edge value:OBJ_VARIABLE -> text:OBJ_VARIABLE" in ir, "what it matched is lost: $ir")
        assertTrue("edge text:OBJ_VARIABLE -> println:EXTERNAL" in ir, "the bound name is unused: $ir")
    }

    /**
     * A read with nothing reaching it still fails, and still says which line.
     *
     * `unboundLocal` used to be where this was asserted, and the lowering fixed the gap underneath
     * it - which is what a gap being fixed looks like, and is also how a guard quietly stops
     * guarding. The failure is not a gap in the analysis here but a gap in the *program*: `int
     * total;` read before anything writes it. Attribution accepts input that does not compile, so
     * codeflow meets this, and drawing a value arriving from nowhere is the one thing it must not do.
     *
     * Both paths refuse, at the same line, for the same reason; only the wording differs, which is
     * why the fixture is excluded from the corpus comparison and asserted here instead.
     */
    @Test
    fun aReadWithNoReachingDefinitionStillFails() {
        val message = assertFailsWith<GraphException> { fromIr("unwrittenLocal") }.message!!
        assertTrue("total" in message, "the failure does not name the local: $message")
        assertTrue("unwrittenLocal/App.java:19" in message, "the failure does not say which line: $message")

        val fromTrees = assertFailsWith<GraphException> { fromTrees("unwrittenLocal") }.message!!
        assertTrue("unwrittenLocal/App.java:19" in fromTrees, "the tree walker refused elsewhere: $fromTrees")
    }

    /** The fixtures the port deliberately draws differently, each asserted by name above. */
    private val INTENDED_DIFFERENCES = mapOf(
        "lambda" to "a primitive lambda parameter is drawn as a primitive",
        "statementLambda" to "a primitive lambda parameter is drawn as a primitive",
        "localClass" to "a local class declaration no longer runs in the enclosing method",
        "unboundLocal" to "a pattern on a switch statement binds its name",
        "unwrittenLocal" to "both refuse at the same line, in different words"
    )

    /**
     * What one graph has that the other does not, as a multiset: an edge drawn twice and an edge
     * drawn once are different graphs, and set arithmetic would call them equal.
     */
    private fun differences(fixture: String): List<String> {
        val trees = fromTrees(fixture)
        val ir = fromIr(fixture)
        val treesOnly = trees.toMutableList().also { remaining -> ir.forEach { remaining.remove(it) } }
        val irOnly = ir.toMutableList().also { remaining -> trees.forEach { remaining.remove(it) } }
        return (treesOnly.map { "trees only: $it" } + irOnly.map { "ir only: $it" }).sorted()
    }

    /** What a build did, whether it produced a graph or refused to - both are answers to compare. */
    private fun outcome(build: () -> List<String>): Any =
        try {
            build()
        } catch (e: Exception) {
            "${e::class.simpleName}: ${e.message}"
        }

    private fun fixtures(): List<String> = Files.list(testResourcesPath)
        .filter { Files.isDirectory(it) && Files.walk(it).anyMatch { f -> f.toString().endsWith(".java") } }
        .map { it.fileName.toString() }
        .sorted()
        .toList()
}
