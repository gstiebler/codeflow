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
}