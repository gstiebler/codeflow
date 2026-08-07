package codeflow.graph

import com.sun.source.tree.Tree
import mu.KotlinLogging

/**
 * Represents a memory position. Multiple variables can point to the same memory position.
 *
 * [label] is only what the memory position is called in a log line or an error, so it is any tree
 * at all: a `catch` parameter is bound by a declaration rather than by an expression, and has no
 * expression to name it with.
 */
class MemPos(private val label: Tree) {

    // nodes for primitive variables inside this instance
    // In x.memberX = 5; a node is created for memberX, and receives 5
    // x is a MemPos
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
        return "MemPos($id, '$label')"
    }

    fun addNode(node: GraphNode) {
        logger.debug { "addNode: $node" }
        referencedNodes[node.id] = node
    }

    fun getNode(id: GraphNodeId): GraphNode? {
        return referencedNodes[id];
    }
}
