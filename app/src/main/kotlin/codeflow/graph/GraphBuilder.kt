package codeflow.graph


class GraphBuilder() {

    val graph = Graph()

    fun addLiteral(id: Int, label: String): GraphNode {
        val newNode = Literal(id, label)
        graph.addNode(newNode)
        return newNode
    }

    fun addVariable(id: Int, label: String): GraphNode {
        val newNode = Variable(id, label)
        graph.addNode(newNode)
        return newNode
    }

    fun addInitializer(sourceVar: GraphNode, init: GraphNode) {
        init.addEdge(sourceVar)
    }

    fun addBinOp(id: Int, label: String, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = GraphNode.BinOp(id, label)
        graph.addNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

}