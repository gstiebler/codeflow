package codeflow.graph

import com.sun.source.tree.ExpressionTree

class MemPos(val expr: ExpressionTree, private val graphBuilder: GraphBuilderBlock) {

    companion object {
        var counter = 0
    }

    val id = counter++

    override fun toString(): String {
        return "MemPos($id, '$expr')"
    }

    fun getNode(id: GraphNodeId): GraphNode? {
        return graphBuilder.graph.getNode(id);
    }
}
