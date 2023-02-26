package codeflow.graph

class Literal(id: Int, label: String) : GraphNode(id, label) {

    override fun print() = println("Literal GraphNode: $label ($id)")

}