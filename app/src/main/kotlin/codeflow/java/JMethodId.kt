package codeflow.java

import codeflow.graph.MethodId
import javax.lang.model.element.Name

class JMethodId(private val name: Name) : MethodId() {
    override fun getIntId() = name.hashCode()
    override fun toString() = name.toString()
}
