package codeflow.graph

import mu.KotlinLogging
import java.nio.file.Path

enum class NodeType {
    BASE, LITERAL, VARIABLE, BIN_OP, FUNC_PARAM, RETURN, MEM_SPACE
}

abstract class GraphNode(private val base: Base) {
    private val logger = KotlinLogging.logger {}
    private val edges = ArrayList<GraphNode>()

    val id: GraphNodeId
        get() = base.id

    val label: String
        get() = base.label

    open fun getType() = NodeType.BASE

    data class Base(val path: Path, val id: GraphNodeId, val label: String)

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

    override fun equals(other: Any?): Boolean {
        if (other is GraphNode) {
            return id == other.id
        }
        return false
    }

    override fun hashCode() = id.hashCode()

    override fun toString(): String {
        return "GraphNode(label=$label, type=${getType()})"
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