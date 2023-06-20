package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree

class Method(val name: MethodTree, val posId: Long, val ctx: ProcessorContext) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(posId, RandomGraphNodeId(), "return"))
}