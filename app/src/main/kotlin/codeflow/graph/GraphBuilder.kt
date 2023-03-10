package codeflow.graph

import java.nio.file.Path


class GraphBuilder() {

    val graph = Graph()
    val methods = HashMap<Int, Method>()

    fun addLiteral(base: GraphNode.Base): GraphNode {
        val newNode = Literal(base)
        graph.addNode(newNode)
        return newNode
    }

    fun addVariable(base: GraphNode.Base): GraphNode {
        val newNode = Variable(base)
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

    fun addMethod(name: String, parameterNodes: List<GraphNode>, hashCode: Int) {
        methods[hashCode] = Method(name, parameterNodes)
    }

    fun callMethod(p: Path, methodCode: Int, parameterNodes: List<GraphNode>): GraphNode {
        val method = methods[methodCode] ?: throw Exception("Method not found")
        for (i in 0 until method.parameterNodes.size) {
            parameterNodes[i].addEdge(method.parameterNodes[i])
        }
        return GraphNode.MethodReturn(GraphNode.Base(p, , method.name))
    }

}