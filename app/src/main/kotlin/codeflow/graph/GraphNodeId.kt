package codeflow.graph

abstract class GraphNodeId {

    abstract fun getIntId(): Int
    override fun hashCode(): Int = getIntId()
}