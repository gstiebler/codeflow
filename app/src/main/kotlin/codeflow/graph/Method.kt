package codeflow.graph

import java.nio.file.Path

class Method(val name: String) {
    val parameterNodes = ArrayList<GraphNode.FuncParam>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(Path.of(""), 0, "return"))
}