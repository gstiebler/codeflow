package codeflow.graph

class Graph() {
    // Node Id -> Node
    private val nodes = HashMap<GraphNodeId, GraphNode>()

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
    }

    fun getNodesSortedByExtId() = nodes.values.sortedBy { it.extId }
    fun getNode(id: GraphNodeId) = nodes[id] ?: throw GraphException("Identifier '${id}' not found in graph")

    fun merge(other: Graph) {
        for (node in other.nodes.values) {
            nodes[node.id] = node
        }
    }
}