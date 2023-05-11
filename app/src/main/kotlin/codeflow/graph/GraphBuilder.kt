package codeflow.graph

import codeflow.java.ids.RandomGraphNodeId
import java.nio.file.Path

class MethodCall(p: Path, val methodCode: MethodId, val parameterNodes: List<GraphNode>) {
    val returnNode = GraphNode.MethodReturn(GraphNode.Base(p, RandomGraphNodeId(), "return"))
}


class GraphBuilder() {
    private val methods = HashMap<MethodId, GraphBuilderMethod>()
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()

    init {
        GraphNode.counter = 0
    }

    fun getMethods() = methods.values.toList()

    fun addMethod(name: String, hashCode: MethodId): GraphBuilderMethod {
        val newMethod = GraphBuilderMethod(this, Method(name))
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
                val calledMethod = methods[methodCall.methodCode] ?: throw GraphException("Method not found")
                methodCall.parameterNodes.forEachIndexed { index, parameterNode ->
                    val toNode = calledMethod.method.parameterNodes[index]
                    parameterNode.addEdge(toNode)
                }
                calledMethod.method.returnNode.addEdge(methodCall.returnNode)
            }
        }
        return mergedGraph
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

class GraphBuilderMethod(val parent: GraphBuilder, val method: Method) {

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

    fun callMethod(p: Path, methodCode: MethodId, parameterNodes: List<GraphNode>): GraphNode {
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