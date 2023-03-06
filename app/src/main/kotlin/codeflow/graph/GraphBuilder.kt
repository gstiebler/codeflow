package codeflow.graph


class GraphBuilder() {

    val graph = Graph()

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

}