package codeflow.graph

import mu.KotlinLogging
import javax.lang.model.element.Element

/**
 * Represents a memory position. Multiple variables can point to the same memory position.
 *
 * [label] is only what the memory position is called in a log line or an error, so it is whatever
 * names the object best where it is created - a `path:line:col`, or the expression that made it.
 *
 * [type] is the class the object was constructed as, which is what decides *which* implementation a
 * call on it runs. Null where nothing said - a loop element, a caught exception, an object from
 * outside the analysed sources - and a null type dispatches to nothing, which leaves the call
 * exactly where it was before.
 */
class MemPos(private val label: String, val type: Element? = null) {

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
