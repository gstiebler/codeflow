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
    override fun getIntId(): Long {
        var hash: Long = 31
        hash = hash * 17 + memPos.hashCode()
        hash = hash * 17 + name.hashCode()
        return hash
    }

    override fun toString() = "JNodeId=(name: '$name', memPos: '$memPos')"
}