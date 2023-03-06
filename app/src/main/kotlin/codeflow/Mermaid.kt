package codeflow

import codeflow.graph.Graph


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
