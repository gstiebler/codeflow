package codeflow.graph

class ObjVariable {
    var lastNode: GraphNode

    constructor(lastNode: GraphNode) {
        this.lastNode = lastNode
    }

    override fun toString(): String {
        return "ObjVariable($lastNode)"
    }
}
