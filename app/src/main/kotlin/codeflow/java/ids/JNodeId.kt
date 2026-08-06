package codeflow.java.ids

import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import codeflow.graph.PosStack
import javax.lang.model.element.Name

class JNodeId(
    stack: PosStack,
    private val name: Name,
    private val memPos: MemPos?
) : GraphNodeId(stack, name.toString()) {

    /**
     * The position is deliberately absent: a read of `x` has to find the `x` declared somewhere
     * else, and the two are at different positions. What distinguishes one occurrence from the
     * next on the diagram is the node's serial, not this.
     *
     * The name is compared as text rather than by javac's `Name`, which is interned per
     * compilation context and so happens to compare by identity today. MemPos has no equals of
     * its own, so two instances are two objects - which is what is wanted, since two objects are
     * two places for a field to live.
     */
    override fun key(): List<Any?> = listOf(name.toString(), memPos)

    override fun toString() = "JNodeId=(name: '$name', memPos: '$memPos')"
}
