package codeflow

import codeflow.graph.Graph
import codeflow.graph.GraphBuilderMethod


fun graphToMermaid(graph: Graph, writer: (String) -> Unit) {
    writer("```mermaid")
    writer("flowchart TD")
    for (node in graph.getNodesSortedByExtId()) {
        for (toNode in node.edgesIterator()) {
            writer("    ${graph.getNodeExtId(node)}[${node.label}] --> ${graph.getNodeExtId(toNode)}[${toNode.label}]")
        }
    }
    writer("```")
}

fun methodsToMermaid(methods: List<GraphBuilderMethod>, writer: (String) -> Unit) {
    writer("```mermaid")
    writer("flowchart TD")
    for (method in methods) {
        writer("  subgraph ${method.method.name}")
        for (node in method.graph.getNodesSortedByExtId()) {
            for (toNode in node.edgesIterator()) {
                writer("    ${method.graph.getNodeExtId(node)}[${node.label}] --> ${method.graph.getNodeExtId(toNode)}[${toNode.label}]")
            }
        }
        writer("  end")
    }
    writer("```")
}
