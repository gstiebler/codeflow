package codeflow.graph

abstract class GraphNodeId {

    abstract fun getIntId(): Long
    abstract fun getExtId(): Long
    override fun hashCode(): Int = getIntId().toInt()
    override fun equals(other: Any?): Boolean {
        if (other is GraphNodeId) {
            return other.getIntId() == getIntId()
        }
        return false
    }

}