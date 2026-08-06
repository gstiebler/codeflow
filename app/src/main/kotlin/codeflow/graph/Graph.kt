package codeflow.graph

import mu.KotlinLogging

class Graph(private val parentGBB: GraphBuilderBlock) {
    private val logger = KotlinLogging.logger {}
    private val nodes = ArrayList<GraphNode>()

    fun createGraphNode(type: NodeType, base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.createNode(type, base, parentGBB.nextSerial())
        nodes.add(newNode)
        return newNode;
    }

    /**
     * Creation order, which is also serial order.
     *
     * This used to sort by a hash of the node's label and call stack, so the order a method's
     * nodes appeared in was arbitrary and moved whenever a label changed. Creation order follows
     * the source.
     */
    fun getNodes(): List<GraphNode> = nodes

    override fun toString(): String {
        return "Graph(nodes=${nodes.joinToString { "\n    $it" }})"
    }
}
