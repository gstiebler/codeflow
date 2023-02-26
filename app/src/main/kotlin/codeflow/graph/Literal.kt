package codeflow.graph

class Literal(id: Int, label: String) : GraphNode(id, label) {

    override fun toString() = "Literal GraphNode: $label ($id)"

}