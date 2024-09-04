package codeflow.graph

import mu.KotlinLogging

class IfCondition() {
    var directNode: GraphNode? = null
    var trueNode: IfCondition? = null
    var falseNode: IfCondition? = null
    var conditionNode: GraphNode? = null

    override fun toString(): String {
        return "IfCondition(directNode=$directNode, trueNode=$trueNode, falseNode=$falseNode, conditionNode=$conditionNode)"
    }
}

data class GetTopMatchingIfConditionReturn(val ifCondition: IfCondition, val ifStack: List<IfItem>)
private fun getTopMatchingIfCondition(
    ifCondition: IfCondition,
    ifStack: List<IfItem>
): GetTopMatchingIfConditionReturn {
    if (ifStack.isEmpty()) {
        return GetTopMatchingIfConditionReturn(ifCondition, emptyList())
    }
    val currentIf = ifStack[0]
    if (currentIf.conditionNode != ifCondition.conditionNode) {
        return GetTopMatchingIfConditionReturn(ifCondition, ifStack)
    }
    if (currentIf.ifSide) {
        return getTopMatchingIfCondition(ifCondition.trueNode!!, ifStack.drop(1))
    }
    return getTopMatchingIfCondition(ifCondition.falseNode!!, ifStack.drop(1))
}

/**
 * Represents a variable in the code.
 * There are multiple variables that can point to the same Java variable.
 * It happens when the same variable is called with different call stacks.
 */
class Variable(lastNode: GraphNode) {
    private val logger = KotlinLogging.logger {}
    // binary tree
    private val ifCondition: IfCondition = IfCondition()

    init {
        ifCondition.directNode = lastNode
    }

    fun setLatestNode(latestNode: GraphNode, ifStack: List<IfItem>) {
        val topMatchingIfCondition = getTopMatchingIfCondition(ifCondition, ifStack)
        val currentIf = ifStack[0]
        topMatchingIfCondition.ifCondition.conditionNode = currentIf.conditionNode
        topMatchingIfCondition.ifCondition.trueNode = IfCondition()
        topMatchingIfCondition.ifCondition.falseNode = IfCondition()
        logger.debug { "setLatestNode: $latestNode, $ifCondition" }
        if (currentIf.ifSide) {
            topMatchingIfCondition.ifCondition.trueNode!!.directNode = latestNode
            topMatchingIfCondition.ifCondition.falseNode!!.directNode =
                topMatchingIfCondition.ifCondition.falseNode!!.directNode ?: topMatchingIfCondition.ifCondition.directNode
        } else {
            topMatchingIfCondition.ifCondition.falseNode!!.directNode = latestNode
            topMatchingIfCondition.ifCondition.trueNode!!.directNode =
                topMatchingIfCondition.ifCondition.trueNode!!.directNode ?: topMatchingIfCondition.ifCondition.directNode
        }
        topMatchingIfCondition.ifCondition.directNode = null
    }

    fun getLatestNode(graphBuilderBlock: GraphBuilderBlock, stack: PosStack): GraphNode {
        return if (ifCondition.directNode != null) {
            ifCondition.directNode!!
        } else {
            val trueNode = ifCondition.trueNode!!.directNode
            val falseNode = ifCondition.falseNode!!.directNode

            val nodeId = GraphNodeId(stack, "if")
            val ifNode = graphBuilderBlock.addIf(GraphNode.Base(nodeId), ifCondition.conditionNode!!)
            trueNode!!.addEdge(ifNode)
            falseNode!!.addEdge(ifNode)
            val varId = GraphNodeId(stack, trueNode.label)
            val variable = graphBuilderBlock.addVariable(GraphNode.Base(varId), null, emptyList())
            ifNode.addEdge(variable)
            variable
        }
    }
}