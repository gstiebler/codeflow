package codeflow.graph

import codeflow.java.ids.JNodeId
import codeflow.java.ids.RandomGraphNodeId
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree
import mu.KotlinLogging


class GraphBuilder() {
    private val methods = HashMap<MethodId, Method>()
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()
    private val logger = KotlinLogging.logger {}

    fun addMethod(methodTree: MethodTree, hashCode: MethodId, posId: Long, ctx: ProcessorContext) {
        methods[hashCode] = Method(methodTree, posId, ctx)
    }

    fun getMethod(hashCode: MethodId): Method {
        return methods[hashCode] ?: throw GraphException("Method not found")
    }

    fun getMainMethod(): Method {
        val method = methods.firstNotNullOf {
            if (it.value.name.name.toString() == "main") it.value else null
        }
        return method
    }

    fun registerIsPrimitive(id: IdentifierId, isPrimitive: Boolean) {
        isPrimitiveMap[id] = isPrimitive
    }

    fun isPrimitive(id: IdentifierId): Boolean {
        // return the value, or throw an exception if it's not found
        return isPrimitiveMap[id] ?: throw GraphException("Variable not found")
    }

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found: $nodeId")
    }

    fun createMemPos(label: String): MemPos {
        val newMemPos = MemPos(label)
        logger.debug { "createMemPos: $label, $newMemPos" }
        return newMemPos
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}

class GraphBuilderBlock(val parent: GraphBuilder, val method: Method, private val memPos: MemPos?) {

    val graph = Graph()
    val calledMethods = ArrayList<GraphBuilderBlock>()
    var returnNode = GraphNode.MethodReturn(GraphNode.Base(method.posId, RandomGraphNodeId(), "return"))
    val parameterNodes = method.name.parameters.map {
        GraphNode.FuncParam(GraphNode.Base(method.ctx.getPosId(it), JNodeId(it.name, memPos), it.name.toString()))
    }

    init {
        graph.addNode(returnNode)
        parameterNodes.forEach { graph.addNode(it) }
    }

    override fun toString() = method.name.name.toString()

    fun addCalledMethod(graphBlock: GraphBuilderBlock) {
        calledMethods.add(graphBlock)
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

    fun setReturnNode(newReturnNode: GraphNode) {
        graph.addNode(newReturnNode)
        newReturnNode.addEdge(returnNode)
    }

}