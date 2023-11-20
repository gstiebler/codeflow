package codeflow.graph

import codeflow.java.processors.AstBlockProcessor

class PosStack {
    private val stack = ArrayList<String>()

    fun push(pos: AstBlockProcessor.Position): PosStack {
        val newStack = PosStack()
        newStack.stack.add("${pos.path}:${pos.pos}")
        return newStack
    }

    override fun hashCode(): Int {
        return stack.hashCode()
    }

    override fun toString() = stack.joinToString(separator = " -> ")
    override fun equals(other: Any?): Boolean {
        other as PosStack
        return stack == other.stack
    }
}