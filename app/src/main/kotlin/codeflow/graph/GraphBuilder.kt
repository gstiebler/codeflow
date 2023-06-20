package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree


class GraphBuilder() {
    private val methods = HashMap<MethodId, Method>()
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()

    fun getMethods() = methods.values.toList()

    fun addMethod(methodTree: MethodTree, hashCode: MethodId, posId: Long, ctx: ProcessorContext) {
        methods[hashCode] = Method(methodTree, posId, ctx)
    }

    fun getMethod(hashCode: MethodId): Method {
        return methods[hashCode] ?: throw GraphException("Method not found")
    }

    fun getMainMethod(): GraphBuilderBlock {
        val method = methods.firstNotNullOf {
            if (it.value.name.name.toString() == "main") it.value else null
        }
        return GraphBuilderBlock(this, method)
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

    val graph = Graph()

    val calledMethods = ArrayList<GraphBuilderBlock>()

    init {
        graph.addNode(method.returnNode)
    }

    fun addCalledMethod(graphBlock: GraphBuilderBlock) {
        calledMethods.add(graphBlock)
        graph.merge(graphBlock.graph)
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