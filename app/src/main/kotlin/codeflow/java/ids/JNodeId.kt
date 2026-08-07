package codeflow.java.ids

import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import codeflow.graph.PosStack
import javax.lang.model.element.Element

class JNodeId(
    stack: PosStack,
    private val name: String,
    private val element: Element?,
    private val memPos: MemPos?
) : GraphNodeId(stack, name) {

    /**
     * Which variable this is: the declaration it resolves to, in the object it lives in.
     *
     * Both halves are needed. The declaration alone is per *class*, and one field declaration
     * lives at a different address in every instance, so `a.total` and `b.total` would be one
     * entry. The MemPos alone is per *object*, and every local in every method of that object
     * shares it.
     *
     * It used to be the variable's name in place of its declaration, and a name is not scoped to
     * anything: two methods on one object each declaring a local `a` produced the same key, and
     * because getVariable walks the block-parent chain, a callee's `a` resolved to its caller's.
     *
     * The position is deliberately absent: a read of `x` has to find the `x` declared somewhere
     * else, and the two are at different positions. What distinguishes one occurrence from the
     * next on the diagram is the node's serial, not this.
     *
     * Falling back to the name is for what javac could not resolve, where there is nothing better
     * to key on. An Element never equals a String, so the two kinds of key cannot be confused.
     */
    override fun key(): List<Any?> = listOf(element ?: name, memPos)

    override fun toString() = "JNodeId=(name: '$name', memPos: '$memPos')"
}
