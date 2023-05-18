package codeflow

import codeflow.graph.Graph
import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode


class MermaidExporter(private val graph: Graph) {
    fun graphToMermaid(writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        for (node in graph.getNodesSortedByExtId()) {
            for (toNode in node.edgesIterator()) {
                writer("    ${node.extId}[${node.label}] --> ${toNode.extId}[${toNode.label}]")
            }
        }
        writer("```")
    }
    private fun getNodeStr(node: GraphNode) = "${node.extId}[${node.label}]:::${node.getType()}"

    private fun getClasses() = listOf(
        "classDef LITERAL fill:#00FF0030",
        "classDef VARIABLE fill:#80808030",
        "classDef BIN_OP fill:#80808080",
        "classDef FUNC_PARAM fill:#8080FF30",
        "classDef RETURN fill:#FF808080"
    )

    private fun genSpaces(n: Int) = " ".repeat(n)

    fun methodsToMermaid(methods: List<GraphBuilderBlock>, writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        for (method in methods) {
            writer(genSpaces(2) + "subgraph ${method.method.name}")
            for (node in method.graph.getNodesSortedByExtId()) {
                for (toNode in node.edgesIterator()) {
                    writer(genSpaces(4) + "${getNodeStr(node)} --> ${getNodeStr(toNode)}")
                }
            }
            writer(genSpaces(2) + "end")
        }
        getClasses().forEach { writer(genSpaces(2) + it) }
        writer("```")
    }
}

