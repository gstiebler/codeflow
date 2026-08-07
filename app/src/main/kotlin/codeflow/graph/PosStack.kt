package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.Tree

/**
 * It's used to differentiate between nodes for primitive types created in different calls to the method,
 * from different places.
 */
class PosStack {
    private val stack = ArrayList<String>()

    /**
     * One frame, named by wherever it came from.
     *
     * A string rather than a [Position] because the entries are only ever compared: the IR names a
     * place as `path:line:col` and a call site as `path:offset`, and both are unique to the place
     * they name, which is all this has to be.
     */
    fun push(source: String): PosStack {
        val newStack = PosStack()
        newStack.stack.addAll(stack)
        newStack.stack.add(source)
        return newStack
    }

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