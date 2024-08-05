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
class Variable(lastNode: GraphNode) {

    private val ifCondition: IfCondition = IfCondition()

    val latestNode: GraphNode?
        get() = ifCondition.directNode

    init {
        ifCondition.directNode = lastNode
    }

    fun setLatestNode(latestNode: GraphNode, ifNode: GraphNode?, ifSide: Boolean) {
        if (ifNode == null) {
            ifCondition.directNode = latestNode
            return
        }
        ifCondition.directNode = null
        ifCondition.ifNode = ifNode
        if (ifSide) {
            ifCondition.trueNode = IfCondition()
            ifCondition.trueNode!!.directNode = latestNode
        } else {
            ifCondition.falseNode = IfCondition()
            ifCondition.falseNode!!.directNode = latestNode
        }
    }
}