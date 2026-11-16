package codeflow

import codeflow.graph.EdgeKind
import codeflow.graph.GraphNode
import codeflow.graph.NodeType
import java.nio.file.Files
import java.nio.file.Path

/** What more than one suite sweeps, said once so the sweeps cannot drift apart. */
object TestFixtures {

    /** The fixture tree, or `CODEFLOW_CORPUS` when a run is pointed at another one. */
    val resources: Path = System.getenv("CODEFLOW_CORPUS")?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.dir")).resolve("src").resolve("test").resolve("resources")

    /**
     * Every fixture directory, which is more than the goldens cover: `codemap` and `ls` have no test
     * naming them at all.
     *
     * One exclusion, and it is a gap in the *program* rather than in codeflow: `unwrittenLocal` reads
     * a local nothing has written, which javac rejects and attribution accepts, and refusing to draw
     * it is what the fixture is for. Its own assertions are
     * [codeflow.ir.LoweringTest.aLocalWithNoReachingDefinitionFailsWhereItIsRead] and
     * [codeflow.ir.IrGraphBuilderTest.aReadWithNoReachingDefinitionStillFails].
     */
    fun fixtures(root: Path = resources): List<String> = Files.list(root)
        .filter { Files.isDirectory(it) && Files.walk(it).anyMatch { f -> f.toString().endsWith(".java") } }
        .map { it.fileName.toString() }
        .filter { it != "unwrittenLocal" }
        .sorted()
        .toList()

    /**
     * Whether a value at [from] reaches [to] - the walk `AppTest.reaches` does over a Mermaid
     * document's ids, done over the drawn nodes themselves.
     *
     * Data only: an [EdgeKind.ORDER] edge says a boundary call ran before another, not that anything
     * flowed into it, and crossing it would make every argument of a boundary call reach every later
     * one. [ordered] is the walk that follows it. Nor an [EdgeKind.EVENT] edge: the entry point
     * starting a node is not a value reaching it, and every node would reach through the event.
     *
     * An edge into a callee's block is an edge to one of its nodes, so nested blocks need nothing
     * of their own. Nodes are compared by serial, which is which box, never by label.
     */
    fun reaches(from: GraphNode, to: GraphNode): Boolean =
        walk(from, to) { _, edge -> edge.kind != EdgeKind.ORDER && edge.kind != EdgeKind.EVENT }

    /**
     * Whether boundary call [from] runs before [to]: along the order chain, which leaves a call by
     * its [EdgeKind.ORDER] edge only - its other edges are its value - and leaves a token or the
     * join of one by every edge but [EdgeKind.SHARED], which says "if another root ran first".
     */
    fun ordered(from: GraphNode, to: GraphNode): Boolean =
        walk(from, to) { node, edge ->
            if (node.getType() == NodeType.EXTERNAL) edge.kind == EdgeKind.ORDER else edge.kind != EdgeKind.SHARED
        }

    private fun walk(from: GraphNode, to: GraphNode, follows: (GraphNode, GraphNode.Edge) -> Boolean): Boolean {
        val seen = HashSet<GraphNode>()
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!seen.add(node)) continue
            if (node == to && node != from) return true
            node.edgesIterator().forEach { if (follows(node, it)) queue.addLast(it.target) }
        }
        return false
    }
}
