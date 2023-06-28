package codeflow.graph

class Graph() {
    // Node Id -> Node
    private val nodes = HashMap<GraphNodeId, GraphNode>()

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
    }

    fun getNodesSortedByExtId() = nodes.values.sortedBy { it.id.getExtId() }

    private fun getFormattedNodes(): String {
        return nodes.keys.joinToString(separator = "\n") { it.toString() }
    }
    fun getNode(id: GraphNodeId): GraphNode {
        return nodes[id] ?: throw GraphException("Identifier '${id}' not found in graph: \n${getFormattedNodes()}")
    }
}