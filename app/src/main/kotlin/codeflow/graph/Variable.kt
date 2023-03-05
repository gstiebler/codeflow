package codeflow.graph

class Variable(base: Base) : GraphNode(base) {

    override fun toString() = "Variable GraphNode: $label ($id)"

}