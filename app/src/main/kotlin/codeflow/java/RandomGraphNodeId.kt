package codeflow.java

import codeflow.graph.GraphNodeId
import javax.lang.model.element.Name

class RandomGraphNodeId() : GraphNodeId() {
    private val id = (Math.random() * 1000000).toInt()
    override fun getIntId() = id
    override fun toString() = id.toString()
}