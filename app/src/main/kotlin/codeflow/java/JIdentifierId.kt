package codeflow.java

import codeflow.graph.IdentifierId
import codeflow.graph.MethodId
import javax.lang.model.element.Name

class JIdentifierId(private val name: Name) : IdentifierId() {
    override fun getIntId() = name.hashCode()
    override fun toString() = name.toString()
}
