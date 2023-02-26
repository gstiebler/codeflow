package codeflow.graph

class Variable(id: Int, label: String) : GraphNode(id, label) {

    override fun toString() = "Variable GraphNode: $label ($id)"

}