package codeflow.graph

/**
 * Represents a variable in the code.
 * There are multiple variables that can point to the same Java variable.
 * It happens when the same variable is called with different call stacks.
 */
class Variable {
    var lastNode: GraphNode

    constructor(lastNode: GraphNode) {
        this.lastNode = lastNode
    }

    override fun toString(): String {
        return "Variable($lastNode)"
    }
}