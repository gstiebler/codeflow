package codeflow.graph

abstract class GraphNodeId {

    abstract fun getIntId(): Int
    override fun hashCode(): Int = getIntId()
    override fun equals(other: Any?): Boolean {
        if (other is GraphNodeId) {
            return other.getIntId() == getIntId()
        }
        return false
    }

}