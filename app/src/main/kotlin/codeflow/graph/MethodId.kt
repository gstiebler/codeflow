package codeflow.graph

abstract class MethodId {
    abstract fun getIntId(): Int
    override fun hashCode(): Int = getIntId()
    override fun equals(other: Any?): Boolean {
        if (other is MethodId) {
            return other.getIntId() == getIntId()
        }
        return false
    }
}