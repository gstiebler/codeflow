package codeflow.java.ids

import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import javax.lang.model.element.Name

class JNodeId(
    private val name: Name,
    private val memPos: MemPos?,
    private val stack: List<String>,
    private val posId: Long
) : GraphNodeId(stack, posId) {
    override fun getIntId(): Long {
        var hash: Long = 31
        hash = hash * 17 + memPos.hashCode()
        hash = hash * 17 + name.hashCode()
        return hash
    }

    override fun toString() = "'$name', '$memPos', $posId, $stack"
}