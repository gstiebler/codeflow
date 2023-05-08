package codeflow.java

import codeflow.graph.GraphNodeId
import javax.lang.model.element.Name

class JavaGraphNodeId(private val name: Name) : GraphNodeId() {
    override fun getIntId() = name.hashCode()

    override fun toString() = name.toString()
}