package codeflow.graph

import codeflow.java.ids.JNodeId
import codeflow.java.ids.RandomGraphNodeId
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree

class Method(val name: MethodTree, val posId: Long, val ctx: ProcessorContext) {

    val parameterNodes = name.parameters.map {
        GraphNode.FuncParam(GraphNode.Base(ctx.getPosId(it), JNodeId(it.name, null), it.name.toString()))
    }
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(posId, RandomGraphNodeId(), "return"))
}