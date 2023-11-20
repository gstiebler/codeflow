package codeflow.graph

import com.sun.source.tree.ExpressionTree
import mu.KotlinLogging

/**
 * Represents a memory position. Multiple variables can point to the same memory position.
 */
class MemPos(val expr: ExpressionTree, private val graphBuilder: GraphBuilderBlock) {

    private val logger = KotlinLogging.logger {}
    companion object {
        var counter = 0
    }

    private val id = counter++

    init {
         logger.debug { "MemPos created: $this" }
    }

    override fun toString(): String {
        return "MemPos($id, '$expr')"
    }

    fun getNode(id: GraphNodeId): GraphNode? {
        return graphBuilder.graph.getNode(id);
    }
}
