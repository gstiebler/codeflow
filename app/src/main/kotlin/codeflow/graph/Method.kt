package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import java.nio.file.Path

class Method(val name: String) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(Path.of(""), RandomGraphNodeId(), "return"))
}