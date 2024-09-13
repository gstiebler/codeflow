package codeflow.graph

import mu.KotlinLogging

enum class NodeType {
    BASE, LITERAL, VARIABLE, OBJ_VARIABLE, BIN_OP, FUNC_PARAM, RETURN, MEM_SPACE
}

abstract class GraphNode(private val base: Base) {
    private val logger = KotlinLogging.logger {}
    private val edges = ArrayList<GraphNode>()

    val id: GraphNodeId
        get() = base.id

    val label: String
        get() = base.label

    open fun getType() = NodeType.BASE

    class Base(val id: GraphNodeId) {
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

    override fun equals(other: Any?): Boolean {
        if (other is GraphNode) {
            return id == other.id
        }
        return false
    }

    override fun hashCode() = id.hashCode()

    override fun toString(): String {
        return "(label: '$label', id: $id, type: ${getType()})"
    }

    companion object {
        fun createNode(type: NodeType, base: Base): GraphNode {
            return when (type) {
                NodeType.LITERAL -> Literal(base)
                NodeType.VARIABLE -> Variable(base)
                NodeType.OBJ_VARIABLE -> ObjVariable(base)
                NodeType.BIN_OP -> BinOp(base)
                NodeType.FUNC_PARAM -> FuncParam(base)
                NodeType.MEM_SPACE -> MemSpace(base)
                NodeType.RETURN -> MethodReturn(base)
                NodeType.BASE -> TODO()
            }

        }
    }

    class Literal(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.LITERAL
    }
    class Variable(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.VARIABLE
    }
    class ObjVariable(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.OBJ_VARIABLE
    }
    class BinOp(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.BIN_OP
    }
    class FuncParam(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.FUNC_PARAM
    }
    class MemSpace(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.MEM_SPACE
    }
    class Assignment(base: Base) : GraphNode(base)
    class MethodReturn(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.RETURN
    }
}