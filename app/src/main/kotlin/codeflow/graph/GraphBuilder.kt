package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import com.sun.source.tree.MethodTree
import javax.lang.model.element.Name

class MethodCall(posId: Long, val methodCode: MethodId, val parameterNodes: List<GraphNode>) {
    val returnNode = GraphNode.MethodReturn(GraphNode.Base(posId, RandomGraphNodeId(), "return"))
}


class GraphBuilder() {
    private val methods = HashMap<MethodId, GraphBuilderBlock>()
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()

    fun getMethods() = methods.values.toList()

    fun addMethod(name: MethodTree, hashCode: MethodId, posId: Long) {
        val newMethod = GraphBuilderBlock(this, Method(name, posId))
        methods[hashCode] = newMethod
    }

    fun bindMethodCalls(): Graph {
        val mergedGraph = Graph()
        for (method in methods.values) {
            mergedGraph.merge(method.graph)
        }
        return mergedGraph
    }

    fun getMethod(hashCode: MethodId): GraphBuilderBlock {
        return methods[hashCode] ?: throw GraphException("Method not found")
    }

    fun registerIsPrimitive(id: IdentifierId, isPrimitive: Boolean) {
        isPrimitiveMap[id] = isPrimitive
    }

    fun isPrimitive(id: IdentifierId): Boolean {
        // return the value, or throw an exception if it's not found
        return isPrimitiveMap[id] ?: throw GraphException("Variable not found")
    }

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found")
    }

    fun createMemPos(label: String): MemPos {
        println("createMemPos: $label")
        return MemPos()
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        idToMemPos[nodeId] = rhsMemPos
    }
}

class GraphBuilderBlock(val parent: GraphBuilder, val method: Method) {

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

    fun addBinOp(base: GraphNode.Base, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = GraphNode.BinOp(base)
        graph.addNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

    fun addAssignment(lhsNode: GraphNode, rhsNode: GraphNode) {
        rhsNode.addEdge(lhsNode)
    }

    fun setReturnNode(returnNode: GraphNode) {
        graph.addNode(returnNode)
        returnNode.addEdge(method.returnNode)
    }

}