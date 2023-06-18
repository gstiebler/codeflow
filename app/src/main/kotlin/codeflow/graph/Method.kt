package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import com.sun.source.tree.MethodTree

class Method(val name: MethodTree, posId: Long) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(posId, RandomGraphNodeId(), "return"))
}