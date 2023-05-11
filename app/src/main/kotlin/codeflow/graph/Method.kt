package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import codeflow.java.processors.ProcessorContext
import java.nio.file.Path

class Method(val name: String, posId: Long) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(posId, RandomGraphNodeId(), "return"))
}