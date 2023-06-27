package codeflow.java.ids

import codeflow.graph.GraphNodeId
import javax.lang.model.element.Name

class RandomGraphNodeId() : GraphNodeId() {
    private val id = (Math.random() * 1000000).toLong()
    override fun getIntId() = id
    override fun getExtId() = id

    override fun toString() = id.toString()
}