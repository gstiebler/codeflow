package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.Tree

/**
 * It's used to differentiate between nodes for primitive types created in different calls to the method,
 * from different places.
 */
class PosStack {
    private val stack = ArrayList<String>()

    fun push(pos: Position): PosStack {
        val newStack = PosStack()
        newStack.stack.addAll(stack)
        newStack.stack.add("${pos.path}:${pos.pos}")
        return newStack
    }

    fun push(ctx: ProcessorContext, tree: Tree): PosStack {
        val invocationPos = ctx.getPosId(tree)
        val pos = Position(invocationPos, ctx.path)
        val newStack = PosStack()
        newStack.stack.addAll(stack)
        newStack.stack.add("${pos.path}:${pos.pos}")
        return newStack
    }

    override fun hashCode(): Int {
        return stack.hashCode()
    }

    override fun toString() = "PosStack(${stack.joinToString(separator = "\n")}"

    override fun equals(other: Any?): Boolean {
        other as PosStack
        return stack == other.stack
    }
}