package codeflow.graph

import mu.KotlinLogging

class Graph(private val parentGBB: GraphBuilderBlock) {
    private val logger = KotlinLogging.logger {}
    private val nodes = HashMap<GraphNodeId, GraphNode>()

    /**
     * A node is created when an assignment is made.
     */
    fun addNode(node: GraphNode) {
        logger.debug { "addNode: $node" }
        nodes[node.id] = node
    }

    fun getNodesSortedByExtId() = nodes.values.sortedBy { it.id.getExtId() }

    private fun getFormattedNodes(): String {
        return nodes.keys.joinToString(separator = "\n") { it.toString() }
    }

    fun getNode(id: GraphNodeId): GraphNode? {
        return nodes[id] ?: parentGBB.parent?.graph?.getNode(id)
    }

    override fun toString(): String {
        return "Graph(nodes=${nodes.values.joinToString { "\n    $it" }})"
    }
}