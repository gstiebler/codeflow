package codeflow

import codeflow.graph.Graph


fun graphToMermaid(graph: Graph, writer: (String) -> Unit) {
    writer("```mermaid")
    writer("flowchart TD")
    for (nodeEntry in graph.nodesIterator()) {
        val node = nodeEntry.value
        for (toNode in node.edgesIterator()) {
            writer("    ${node.id}[${node.label}] --> ${toNode.id}[${toNode.label}]")
        }
    }
    writer("```")
}
