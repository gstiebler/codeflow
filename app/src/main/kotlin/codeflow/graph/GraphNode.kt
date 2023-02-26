package codeflow.graph

open class GraphNode(val id: Int, val label: String) {
    private val edges = ArrayList<GraphNode>()

    fun addEdge(node: GraphNode) = edges.add(node)
    open fun print() = println("GraphNode: $label ($id)")
}