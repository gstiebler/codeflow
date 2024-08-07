package codeflow.graph

data class IfItem(
    val conditionNode: GraphNode,
    val ifSide: Boolean,
)
