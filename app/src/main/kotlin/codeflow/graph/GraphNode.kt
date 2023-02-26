package codeflow.graph

abstract class GraphNode(val id: Int, val label: String) {
    private val edges = ArrayList<GraphNode>()

    fun addEdge(node: GraphNode) = edges.add(node)
    fun print() {
        println(this)
        if (edges.size > 0) {
            println("Edges:")
            for (edge in edges) {
                println("  To $edge")
            }
        }
    }
}