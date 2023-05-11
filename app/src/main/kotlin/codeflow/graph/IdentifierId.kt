package codeflow.graph

abstract class IdentifierId {
    abstract fun getIntId(): Int
    override fun hashCode(): Int = getIntId()
    override fun equals(other: Any?): Boolean {
        if (other is IdentifierId) {
            return other.getIntId() == getIntId()
        }
        return false
    }
}