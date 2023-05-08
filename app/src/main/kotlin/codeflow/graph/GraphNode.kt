package codeflow.graph

import mu.KotlinLogging
import java.nio.file.Path

enum class NodeType {
    BASE, LITERAL, VARIABLE, BIN_OP, FUNC_PARAM, RETURN, MEM_SPACE
}

abstract class GraphNode(private val base: Base) {
    private val logger = KotlinLogging.logger {}
    private val edges = ArrayList<GraphNode>()

    val id: Int
        get() = base.hashCode

    val label: String
        get() = base.label

    open fun getType() = NodeType.BASE

    data class Base(val path: Path, val hashCode: Int, val label: String)

    fun edgesIterator() = edges.iterator()

    fun addEdge(node: GraphNode) = edges.add(node)
    fun print() {
        logger.info { this }
        if (edges.size > 0) {
            logger.info { "Edges:" }
            for (edge in edges) {
                logger.info { "  To $edge" }
            }
        }
    }

    class Literal(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.LITERAL
    }
    class Variable(base: Base) : GraphNode(base) {
        override fun getType() = NodeType.VARIABLE
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