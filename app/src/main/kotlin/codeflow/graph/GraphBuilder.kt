package codeflow.graph

import java.nio.file.Path

class MethodCall(p: Path, val methodCode: Int, val parameterNodes: List<GraphNode>) {
    val returnNode = GraphNode.MethodReturn(GraphNode.Base(p, 0, "return"))
}


class GraphBuilder() {
    private val methods = HashMap<Int, GraphBuilderMethod>()

    fun getMethods() = methods.values.toList()

    fun addMethod(name: String, hashCode: Int): GraphBuilderMethod {
        val newMethod = GraphBuilderMethod(Method(name))
        methods[hashCode] = newMethod
        return newMethod
    }

    fun bindMethodCalls(): Graph {
        val mergedGraph = Graph()
        for (method in methods.values) {
            mergedGraph.merge(method.graph)
        }
        methods.forEach { (methodCode, method) ->
            method.methodCalls.forEach { methodCall ->
                val calledMethod = methods[methodCall.methodCode] ?: throw Exception("Method not found")
                methodCall.parameterNodes.forEachIndexed { index, parameterNode ->
                    val toNode = calledMethod.method.parameterNodes[index]
                    parameterNode.addEdge(toNode)
                }
                calledMethod.method.returnNode.addEdge(methodCall.returnNode)
            }
        }
        return mergedGraph
    }
}

class GraphBuilderMethod(val method: Method) {

    val methodCalls = ArrayList<MethodCall>()
    val graph = Graph()

    init {
        graph.addNode(method.returnNode)
    }

    fun addParameter(base: GraphNode.Base) {
        val paramNode = GraphNode.FuncParam(base)
        graph.addNode(paramNode)
        method.parameterNodes.add(paramNode)
    }

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

    fun callMethod(p: Path, methodCode: Int, parameterNodes: List<GraphNode>): GraphNode {
        val methodCall = MethodCall(p, methodCode, parameterNodes)
        methodCalls.add(methodCall)
        graph.addNode(methodCall.returnNode)
        return methodCall.returnNode
    }

    fun setReturnNode(returnNode: GraphNode) {
        graph.addNode(returnNode)
        returnNode.addEdge(method.returnNode)
    }

}