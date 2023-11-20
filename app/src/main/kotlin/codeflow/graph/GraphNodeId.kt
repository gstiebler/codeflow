package codeflow.graph

open class GraphNodeId(private val stack: PosStack, private val posId: Long, val label: String) {

    open fun getIntId() = getExtId()

    fun getExtId(): Long {
        var hash: Long = 31
        hash = hash * 17 + posId
        hash = hash * 17 + label.hashCode()
        hash = hash * 17 + stack.hashCode()
        return hash
    }
    override fun hashCode(): Int = getIntId().toInt()
    override fun equals(other: Any?): Boolean {
        if (other is GraphNodeId) {
            return other.getIntId() == getIntId()
        }
        return false
    }

    override fun toString() = "GraphNodeId=(posId: '$posId', '$label', $stack, ${getExtId()})"
}