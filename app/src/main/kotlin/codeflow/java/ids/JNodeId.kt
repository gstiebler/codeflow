package codeflow.java.ids

import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import javax.lang.model.element.Name

class JNodeId(private val name: Name, private val memPos: MemPos?, private val stack: List<String>) : GraphNodeId() {
    override fun getIntId(): Int {
        var hash = 31
        hash = hash * 17 + memPos.hashCode()
        hash = hash * 17 + name.hashCode()
        // hash = hash * 17 + stack.hashCode()
        return hash
    }

    override fun toString() = "'$name', '$memPos', '$stack'"
}