package codeflow.ir

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import codeflow.java.AstReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The graph, asserted on as a set of typed boxes and typed edges.
 *
 * What every other test of the graph reads is a rendered document, where a node's *type* survives
 * only as the shape of a Mermaid bracket. These are the cases where the type is the claim being
 * made - whether a value is a primitive, whether a call was inlined or left opaque - so they are
 * asserted where it is still a type.
 *
 * Each of these was a difference the port was measured against: while both builders existed every
 * fixture was built both ways and compared as `label:TYPE` multisets, and the corpus agreed
 * everywhere except here. The comparison went when the tree walker did; what is left is the
 * behaviour it found.
 */
class IrGraphBuilderTest {

    private val testResourcesPath = Path.of(System.getProperty("user.dir"))
        .resolve("src").resolve("test").resolve("resources")

    /** The graph reduced to what is drawn and what points at what, with ids and serials stripped. */
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

    private fun graph(fixture: String): List<String> =
        shape(AstReader(testResourcesPath).process(sources(fixture)))

    /**
     * A lambda's `int` parameter is a primitive, and is drawn as one.
     *
     * The tree walker had no answer to give: the parameter of a lambda was bound by a visitor that
     * predated the question, so every one came out an object variable whatever its type. The
     * lowering carries javac's answer about the declared type on the binding itself, so `value ->
     * value * base` on an `IntUnaryOperator` says `int`, which is what the reader is looking at.
     */
    @Test
    fun aPrimitiveLambdaParameterIsDrawnAsAPrimitive() {
        val expression = graph("lambda")
        assertTrue("node value:VARIABLE" in expression, "the lambda parameter is not a primitive: $expression")
        assertTrue("edge value:VARIABLE -> *:BIN_OP" in expression, "it does not reach the body: $expression")

        val statement = graph("statementLambda")
        assertTrue("node value:VARIABLE" in statement, "the lambda parameter is not a primitive: $statement")
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
     * What is drawn instead is the call, opaque: `AstProcessor` records methods declared in a class,
     * not in a method body, so there is no body registered to inline. That is the same reading as
     * any method from outside the corpus, and a narrower gap than the one it replaces - the diagram
     * no longer claims `main` multiplies anything.
     */
    @Test
    fun aLocalClassDeclarationRunsNothingWhereItStands() {
        val graph = graph("localClass")
        assertTrue("edge seed:VARIABLE -> twice:EXTERNAL" in graph, "the argument does not reach the call: $graph")
        assertTrue("edge twice:EXTERNAL -> result:VARIABLE" in graph, "the result does not come back: $graph")
        assertTrue(graph.none { "*:BIN_OP" in it }, "the declared method's body runs in main: $graph")
        assertTrue(graph.none { "node n:" in it }, "the declared method's parameter is read in main: $graph")
    }

    /**
     * `case String text ->` binds `text`, on a `switch` used as a statement as well as one used as
     * an expression.
     *
     * The tree walker bound nothing here, so the read on the same line found no node and failed
     * blaming a line that was not at fault - which is how the `unboundLocal` fixture got its name.
     * The label binds the value the selector produced, so what it matched flows into it.
     */
    @Test
    fun aPatternOnASwitchStatementBindsItsName() {
        val graph = graph("unboundLocal")
        assertTrue("edge value:OBJ_VARIABLE -> text:OBJ_VARIABLE" in graph, "what it matched is lost: $graph")
        assertTrue("edge text:OBJ_VARIABLE -> println:EXTERNAL" in graph, "the bound name is unused: $graph")
    }

    /**
     * A read with nothing reaching it still fails, and still says which line.
     *
     * `unboundLocal` used to be where this was asserted, and the lowering fixed the gap underneath
     * it - which is what a gap being fixed looks like, and is also how a guard quietly stops
     * guarding. The failure here is not a gap in the analysis but a gap in the *program*: `int
     * total;` read before anything writes it. Attribution accepts input that does not compile, so
     * codeflow meets this, and drawing a value arriving from nowhere is the one thing it must not do.
     */
    @Test
    fun aReadWithNoReachingDefinitionStillFails() {
        val message = assertFailsWith<GraphException> { graph("unwrittenLocal") }.message!!
        assertTrue("total" in message, "the failure does not name the local: $message")
        assertTrue("unwrittenLocal/App.java:19" in message, "the failure does not say which line: $message")
    }
}
