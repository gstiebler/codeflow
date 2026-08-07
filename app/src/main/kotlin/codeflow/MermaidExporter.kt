package codeflow

import codeflow.graph.EdgeKind
import codeflow.graph.Graph
import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode
import mu.KotlinLogging


class MermaidExporter() {
    private val logger = KotlinLogging.logger {}
    // The `n` prefix keeps a node id from ever being read as a subgraph id, which is prefixed `b`,
    // and makes both greppable in a snapshot.
    private fun getNodeStr(node: GraphNode) = "n${node.serial}[${node.label}]:::${node.getType()}"

    private fun getClasses() = listOf(
        "classDef LITERAL fill:#00FF0030",
        "classDef VARIABLE fill:#80808030",
        "classDef BIN_OP fill:#80808080",
        "classDef FUNC_PARAM fill:#8080FF30",
        "classDef RETURN fill:#FF808080",
        "classDef EXTERNAL fill:#FFA50040",
        // Dashed, because it is the one node type that is not a claim about the code: it marks
        // where codeflow stopped, and it should not sit on the diagram looking like the rest.
        "classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2"
    )

    /**
     * The stroke each marked edge is drawn in, keyed by kind. [EdgeKind.FLOW] is absent on purpose:
     * an edge with no entry here gets no `linkStyle` line, which is what keeps the colouring to the
     * few edges that mean something beyond "this value goes there".
     */
    private val strokes = mapOf(
        EdgeKind.TRUE to "#2e7d32",
        EdgeKind.FALSE to "#c62828",
        EdgeKind.CONDITION to "#6a6a6a"
    )

    private fun genSpaces(n: Int) = " ".repeat(n)

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        val links = Links()
        processMethod(mainMethod, 2, writer, links)
        links.styles.forEach { writer(genSpaces(2) + it) }
        getClasses().forEach { writer(genSpaces(2) + it) }
        writer("```")
    }

    /**
     * The running link count and the styles collected against it.
     *
     * Mermaid numbers links across the whole flowchart in the order they are declared and
     * `linkStyle` addresses them by that number, so a style cannot be written beside the edge it
     * applies to: the index is only settled once every nested block has been walked.
     */
    private class Links {
        var count = 0
        val styles = ArrayList<String>()
    }

    private fun processMethod(
        method: GraphBuilderBlock,
        depth: Int,
        writer: (String) -> Unit,
        links: Links
    ) {
        val nodes = method.graph.getNodes()
        writer(genSpaces(depth) + "subgraph b${method.serial}[\"${method.getMethodName()}\"]")
        logger.debug { "processMethod: ${method.getMethodName()}" }
        logger.debug { "Graph: ${method.graph}" }
        for (node in nodes) {
            writer(genSpaces(depth + 2) + getNodeStr(node))
        }
        for (node in nodes) {
            for (edge in node.edgesIterator()) {
                val arrow = edge.kind.label?.let { "-->|$it|" } ?: "-->"
                strokes[edge.kind]?.let {
                    links.styles.add("linkStyle ${links.count} stroke:$it,color:$it")
                }
                links.count++
                writer(genSpaces(depth + 2) + "${getNodeStr(node)} $arrow ${getNodeStr(edge.target)}")
            }
        }
        for (calledMethod in method.calledMethods) {
            processMethod(calledMethod, depth + 2, writer, links)
        }
        writer(genSpaces(depth) + "end")
    }
}

