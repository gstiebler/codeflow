package codeflow.graph

class UnmingledEdge(
    val sourceId: Int,
    val destId: Int
)

class GraphBuilder() {

    private val unmingledEdges = ArrayList<UnmingledEdge>()

    val graph = Graph()

    fun addLiteral(id: Int, label: String) {
        graph.addNode(Literal(id, label))
    }

    fun addVariable(id: Int, label: String) {
        graph.addNode(Variable(id, label))
    }

    fun addInitializer(sourceVar: Int, init: Int) {
        println("Initializer sourceVar: $sourceVar, Init: $init")
        unmingledEdges.add(UnmingledEdge(init, sourceVar))
    }

    fun mingleEdges() {
        for (unmingledEdge in unmingledEdges) {
            val source = graph.getNode(unmingledEdge.sourceId)
            val dest = graph.getNode(unmingledEdge.destId)
            if (source == null || dest == null) {
                println("Error: $unmingledEdge not found, source: $source, dest $dest")
                continue
            }
            source.addEdge(dest)
        }
    }

}