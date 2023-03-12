package codeflow

import codeflow.graph.Graph
import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphNode


class MermaidExporter(private val graph: Graph) {
    fun graphToMermaid(writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        for (node in graph.getNodesSortedByExtId()) {
            for (toNode in node.edgesIterator()) {
                writer("    ${graph.getNodeExtId(node)}[${node.label}] --> ${graph.getNodeExtId(toNode)}[${toNode.label}]")
            }
        }
        writer("```")
    }
    private fun getNodeStr(node: GraphNode) = "#${graph.getNodeExtId(node)}[${node.label}]:::${node.getType()}"

    private fun getClasses() = listOf(
        "  classDef LITERAL fill:#00FF0030",
        "  classDef VARIABLE fill:#80808030",
        "  classDef BIN_OP fill:#80808080",
        "  classDef FUNC_PARAM fill:#8080FF80",
        "  classDef RETURN fill:#FF808080"
    )

    fun methodsToMermaid(methods: List<GraphBuilderMethod>, writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        for (method in methods) {
            writer("  subgraph ${method.method.name}")
            for (node in method.graph.getNodesSortedByExtId()) {
                for (toNode in node.edgesIterator()) {
                    writer("    ${getNodeStr(node)} --> ${getNodeStr(toNode)}")
                }
            }
            writer("  end")
            getClasses().forEach { writer(it) }
        }
        writer("```")
    }
}

