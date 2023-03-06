package codeflow.graph

class Graph() {
    private val nodes = HashMap<Int, GraphNode>()
    private val nodesList = HashMap<GraphNode, Int>()
    private var counter = 0

    fun addNode(node: GraphNode) {
        nodes[node.id] = node
        nodesList[node] = counter++
    }

    fun getNodeExtId(node: GraphNode) = nodesList[node]

    fun getNodesSortedByExtId() = nodesList.keys.sortedBy { nodesList[it] }

    fun getNode(id: Int) = nodes[id]

    fun print() {
        for (entry in nodes.entries.iterator()) {
            entry.value.print()
        }
    }

    fun compare(other: Graph): Boolean {
        for (entry in nodes.entries) {
            val otherNode = other.getNode(entry.key)
            if (entry.value != otherNode) {
                return false
            }
        }
        return true
    }
}