package codeflow.graph

class GraphBuilder() {

    val graph = Graph()

    fun addLiteral(id: Int, label: String) {
        graph.addNode(Literal(id, label))
    }

}