package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import codeflow.java.processors.ProcessorContext
import java.nio.file.Path

class Method(val name: String, ctx: ProcessorContext) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(ctx, RandomGraphNodeId(), "return"))
}