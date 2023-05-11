package codeflow.graph

class MemPos {
    companion object {
        var counter = 0
    }

    val id = counter++

    override fun toString(): String {
        return "MemPos($id)"
    }
}
