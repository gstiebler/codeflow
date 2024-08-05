package codeflow.graph

class IfCondition() {
    var directNode: GraphNode? = null
    var trueNode: IfCondition? = null
    var falseNode: IfCondition? = null
    var ifNode: GraphNode? = null
}

/**
 * Represents a variable in the code.
 * There are multiple variables that can point to the same Java variable.
 * It happens when the same variable is called with different call stacks.
 */
class Variable() {

    private val ifCondition: IfCondition = IfCondition()

    var lastNode: GraphNode?
        get() = ifCondition.directNode
        set(value) {
            ifCondition.directNode = value
        }

    constructor(lastNode: GraphNode) : this() {
        ifCondition.directNode = lastNode
    }
}