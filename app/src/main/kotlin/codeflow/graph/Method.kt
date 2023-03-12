package codeflow.graph

import java.nio.file.Path

class Method(val name: String) {
    lateinit var parameterNodes: List<GraphNode>
    val returnNode: GraphNode.MethodReturn = GraphNode.MethodReturn(GraphNode.Base(Path.of(""), 0, "return"))
}