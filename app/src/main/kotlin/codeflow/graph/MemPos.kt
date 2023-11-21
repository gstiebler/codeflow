package codeflow.graph

import com.sun.source.tree.ExpressionTree
import mu.KotlinLogging

/**
 * Represents a memory position. Multiple variables can point to the same memory position.
 */
class MemPos(val expr: ExpressionTree) {

    private val referencedNodes = HashMap<GraphNodeId, GraphNode>()

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

    fun addNode(node: GraphNode) {
        logger.debug { "addNode: $node" }
        referencedNodes[node.id] = node
    }

    fun getNode(id: GraphNodeId): GraphNode? {
        return referencedNodes[id];
    }
}
