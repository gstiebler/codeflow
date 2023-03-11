package codeflow.graph

import java.nio.file.Path

class MethodCall(val p: Path, val methodCode: Int, val parameterNodes: List<GraphNode>) {
    val returnNode = GraphNode.MethodReturn(GraphNode.Base(p, 0, "return"))
}


class GraphBuilder() {
    private val methods = HashMap<Int, GraphBuilderMethod>()

    fun getMethods() = methods.values.toList()

    fun addMethod(name: String, parameterNodes: List<GraphNode>, hashCode: Int): GraphBuilderMethod {
        val newMethod = GraphBuilderMethod(Method(name, parameterNodes))
        methods[hashCode] = newMethod
        return newMethod
    }

    fun bindMethodCalls() {
        methods.forEach { (methodCode, method) ->
            method.methodCalls.forEach { methodCall ->
                val calledMethod = methods[methodCall.methodCode] ?: throw Exception("Method not found")
                methodCall.parameterNodes.forEachIndexed { index, parameterNode ->
                    parameterNode.addEdge(calledMethod.method.parameterNodes[index])
                }
                calledMethod.method.returnNode.addEdge(methodCall.returnNode)
            }
        }
    }
}

class GraphBuilderMethod(val method: Method) {

    val methodCalls = ArrayList<MethodCall>()

    val graph = Graph()

    fun addLiteral(base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.Literal(base)
        graph.addNode(newNode)
        return newNode
    }

    fun addVariable(base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.Variable(base)
        graph.addNode(newNode)
        return newNode
    }

    fun addInitializer(sourceVar: GraphNode, init: GraphNode) {
        init.addEdge(sourceVar)
    }

    fun addBinOp(base: GraphNode.Base, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = GraphNode.BinOp(base)
        graph.addNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

    fun addAssignment(sourceVar: GraphNode, expression: GraphNode) {
        expression.addEdge(sourceVar)
    }

    fun callMethod(p: Path, methodCode: Int, parameterNodes: List<GraphNode>): GraphNode? {
        val methodCall = MethodCall(p, methodCode, parameterNodes)
        methodCalls.add(methodCall)
        return methodCall.returnNode
    }

}