package codeflow

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
        "classDef EXTERNAL fill:#FFA50040"
    )

    private fun genSpaces(n: Int) = " ".repeat(n)

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        processMethod(mainMethod, 2, writer)
        getClasses().forEach { writer(genSpaces(2) + it) }
        writer("```")
    }

    private fun processMethod(method: GraphBuilderBlock, depth: Int, writer: (String) -> Unit) {
        val nodes = method.graph.getNodes()
        writer(genSpaces(depth) + "subgraph b${method.serial}[\"${method.getMethodName()}\"]")
        logger.debug { "processMethod: ${method.getMethodName()}" }
        logger.debug { "Graph: ${method.graph}" }
        for (node in nodes) {
            writer(genSpaces(depth + 2) + getNodeStr(node))
        }
        for (node in nodes) {
            for (toNode in node.edgesIterator()) {
                writer(genSpaces(depth + 2) + "${getNodeStr(node)} --> ${getNodeStr(toNode)}")
            }
        }
        for (calledMethod in method.calledMethods) {
            processMethod(calledMethod, depth + 2, writer)
        }
        writer(genSpaces(depth) + "end")
    }
}

