package codeflow.graph

import mu.KotlinLogging

enum class NodeType {
    BASE, LITERAL, VARIABLE, OBJ_VARIABLE, BIN_OP, FUNC_PARAM, RETURN, MEM_SPACE, EXTERNAL, UNMODELLED
}

/**
 * @param serial what makes this node itself, handed out by [Graph.createGraphNode] when the node
 *   is created. It is a constructor parameter rather than something set afterwards so that a node
 *   cannot exist without one, and it does not live on [Base] because Base is built at the call
 *   sites that draw a node, before the graph has seen it.
 */
abstract class GraphNode(private val base: Base, val serial: Int) {
    private val logger = KotlinLogging.logger {}
    private val edges = ArrayList<GraphNode>()

    val id: GraphNodeId
        get() = base.id

    val label: String
        get() = base.label

    /** See [Base.source]. */
    val source: String
        get() = base.source

    open fun getType() = NodeType.BASE

    /**
     * @param source where this node was written, as `path:line:column`.
     *
     * Required rather than optional, and on Base rather than set afterwards, so that a node cannot
     * exist without one. A position on most nodes is worse than none on any: a viewer offering to
     * navigate would silently do nothing on whichever kinds were missed, and which kinds those are
     * is invisible from the outside. The compiler is what enforces it - a new call site that has no
     * position to give has to say so at the call site rather than at run time.
     *
     * It comes from the [codeflow.java.processors.ProcessorContext] of the *compilation unit the
     * tree belongs to*, which is not always the one being walked: a callee is inlined with the
     * caller's context in hand, and asking that one for a line number in another file gives a
     * number from the wrong line map.
     */
    class Base(val id: GraphNodeId, val source: String) {
        val label: String
            get() = id.label
    }

    fun edgesIterator() = edges.iterator()

    fun addEdge(node: GraphNode) {
        logger.debug { "addEdge:\n\t $this ->\n\t $node" }
        edges.add(node)
    }

    fun print() {
        logger.info { this }
        if (edges.size > 0) {
            logger.info { "Edges:" }
            for (edge in edges) {
                logger.info { "  To $edge" }
            }
        }
    }

    /**
     * Two nodes are the same node when they were created as the same node. Comparing the lookup
     * key instead would make every occurrence of a variable equal to every other, since that is
     * exactly what the key is for.
     */
    override fun equals(other: Any?): Boolean = other is GraphNode && other.serial == serial

    override fun hashCode() = serial

    override fun toString(): String {
        return "(serial: $serial, label: '$label', id: $id, type: ${getType()})"
    }

    companion object {
        fun createNode(type: NodeType, base: Base, serial: Int): GraphNode {
            return when (type) {
                NodeType.LITERAL -> Literal(base, serial)
                NodeType.VARIABLE -> Variable(base, serial)
                NodeType.OBJ_VARIABLE -> ObjVariable(base, serial)
                NodeType.BIN_OP -> BinOp(base, serial)
                NodeType.FUNC_PARAM -> FuncParam(base, serial)
                NodeType.MEM_SPACE -> MemSpace(base, serial)
                NodeType.RETURN -> MethodReturn(base, serial)
                NodeType.EXTERNAL -> External(base, serial)
                NodeType.UNMODELLED -> Unmodelled(base, serial)
                NodeType.BASE -> throw GraphException("BASE is the abstract case and has no node")
            }

        }
    }

    class Literal(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.LITERAL
    }
    class Variable(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.VARIABLE
    }
    class ObjVariable(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.OBJ_VARIABLE
    }
    class BinOp(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.BIN_OP
    }
    class FuncParam(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.FUNC_PARAM
    }
    class MemSpace(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.MEM_SPACE
    }
    /** Something from outside the analysed sources: a call with no body to inline, or a value
     *  such as an enum constant, that we can only treat as opaque. */
    class External(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.EXTERNAL
    }
    /**
     * A construct codeflow does not model, drawn rather than being an error that ends the run.
     *
     * Its own type, and not [External], because the two say different things and the difference is
     * the reader's to act on: EXTERNAL is a limit of the *sources* - a call into `java.util` whose
     * body is not there to read - while this is a limit of *codeflow*, and what it hides is code
     * sitting in the corpus, readable, that the diagram is not showing. Sharing a type would make a
     * gap in the tool indistinguishable from a gap in the input.
     */
    class Unmodelled(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.UNMODELLED
    }
    class MethodReturn(base: Base, serial: Int) : GraphNode(base, serial) {
        override fun getType() = NodeType.RETURN
    }
}