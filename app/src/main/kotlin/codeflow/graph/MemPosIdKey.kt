package codeflow.graph

class MemPosIdKey(private val memPos: MemPos?, private val id: GraphNodeId) {
    override fun equals(other: Any?): Boolean {
        if (other is MemPosIdKey) {
            return memPos == other.memPos && id == other.id
        }
        return false
    }

    override fun hashCode(): Int {
        var hash = 31
        hash = hash * 17 + memPos.hashCode()
        hash = hash * 17 + id.hashCode()
        return hash
    }

    override fun toString() = "MemPosIdKey(memPos=$memPos, id=$id)"
}
