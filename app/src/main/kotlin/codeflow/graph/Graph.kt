package codeflow.graph

class Graph() {
    private val nodes = HashMap<Int, GraphNode>()

    fun addNode(node: GraphNode) = nodes.put(node.id, node)
    fun getNode(id: Int) = nodes[id]
    fun nodesIterator() = nodes.iterator()

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