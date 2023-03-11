package codeflow.graph

class Graph() {
    // Node Id -> Node
    private val nodes = HashMap<Int, GraphNode>()
    // Node -> External Id
    private val nodesList = HashMap<GraphNode, Int>()

    companion object {
        var counter = 0
    }

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
        nodesList[node] = counter++
    }

    fun getNodeExtId(node: GraphNode) = nodesList[node]

    fun getNodesSortedByExtId() = nodesList.keys.sortedBy { nodesList[it] }

    fun getNode(id: Int) = nodes[id]

    fun merge(other: Graph) {
        for (node in other.nodes.values) {
            nodes[node.id] = node
        }
        for (node in other.nodesList.keys) {
            nodesList[node] = other.nodesList[node]!!
        }
    }
}