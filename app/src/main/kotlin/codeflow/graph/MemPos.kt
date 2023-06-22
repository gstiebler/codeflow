package codeflow.graph

class MemPos(private val label: String) {
    companion object {
        var counter = 0
    }

    val id = counter++

    override fun toString(): String {
        return "MemPos($id, '$label')"
    }
}
