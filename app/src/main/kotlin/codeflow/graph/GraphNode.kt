package codeflow.graph

import java.nio.file.Path

abstract class GraphNode(private val base: Base) {
    private val edges = ArrayList<GraphNode>()

    val id: Int
        get() = base.hashCode

    val label: String
        get() = base.label

    data class Base(val path: Path, val hashCode: Int, val label: String)

    fun edgesIterator() = edges.iterator()

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

    class BinOp(base: Base) : GraphNode(base) {}
}