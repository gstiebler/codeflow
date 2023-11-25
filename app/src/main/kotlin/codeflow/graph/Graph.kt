package codeflow.graph

import mu.KotlinLogging

class Graph(private val parentGBB: GraphBuilderBlock) {
    private val logger = KotlinLogging.logger {}
    private val nodes = ArrayList<GraphNode>()

    fun createGraphNode(type: NodeType, base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.createNode(type, base)
        nodes.add(newNode)
        return newNode;
    }

    fun getNodesSortedByExtId() = nodes.sortedBy { it.id.getExtId() }

    override fun toString(): String {
        return "Graph(nodes=${nodes.joinToString { "\n    $it" }})"
    }
}