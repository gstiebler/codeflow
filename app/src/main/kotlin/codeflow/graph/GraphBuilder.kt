package codeflow.graph

import codeflow.java.ids.JNodeId
import codeflow.java.processors.ProcessorContext
import mu.KotlinLogging

class GraphBuilderBlock(
    // val globalCtx: GlobalContext,
    private val parent: GraphBuilderBlock?,
    // should it be here? or on a MethodBlock class?
    val method: Method,
    stack: PosStack,
    // instance of the class that contains the method
    private val memPos: MemPos?,
    private val className: String,
    private val ctx: ProcessorContext
) {
    private val logger = KotlinLogging.logger {}
    val graph: Graph = Graph(this)
    private val nodeIdToLastNodeOfVariable = HashMap<GraphNodeId, GraphNode>()
    val localId = stack.hashCode() * 37 + 4308977
    val calledMethods = ArrayList<GraphBuilderBlock>()
    var returnNode = createReturnNode(stack)

    // should it be here? or on a MethodBlock class?
    val parameterNodes = method.name.parameters.map {
        val baseNode = GraphNode.Base(JNodeId(stack.push(ctx, it), it.name, memPos))
        graph.createGraphNode(NodeType.FUNC_PARAM, baseNode)
    }

    init {
        setVarAssignmentNode(returnNode)
        parameterNodes.forEach { setVarAssignmentNode(it) }
    }

    private fun setVarAssignmentNode(node: GraphNode) {
        logger.debug { "setVarAssignmentNode: $node" }
        nodeIdToLastNodeOfVariable[node.id] = node
    }

    fun getLastNodeOfVariable(id: GraphNodeId): GraphNode? {
        return nodeIdToLastNodeOfVariable[id] ?: parent?.getLastNodeOfVariable(id)
    }

    private fun createReturnNode(stack: PosStack): GraphNode {
        val nodeBase = GraphNode.Base(JNodeId(stack, method.name.name, memPos))
        return graph.createGraphNode(NodeType.RETURN, nodeBase)
    }

    override fun toString() = method.name.name.toString()

    fun connectParameters(methodArguments: List<GraphNode>) {
        methodArguments.forEachIndexed { index, callingParameter ->
            val methodParameter = parameterNodes[index]
            callingParameter.addEdge(methodParameter)
        }
    }

    fun addCalledMethod(graphBlock: GraphBuilderBlock) {
        calledMethods.add(graphBlock)
    }

    fun addLiteral(base: GraphNode.Base): GraphNode {
        val newNode = graph.createGraphNode(NodeType.LITERAL, base)
        setVarAssignmentNode(newNode)
        return newNode
    }

    fun addVariable(base: GraphNode.Base, memPos: MemPos?): GraphNode {
        val newNode = graph.createGraphNode(NodeType.VARIABLE, base)
        setVarAssignmentNode(newNode)
        memPos?.addNode(newNode)
        return newNode
    }

    fun addBinOp(base: GraphNode.Base, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setVarAssignmentNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

    fun addAssignment(lhsNode: GraphNode, rhsNode: GraphNode) {
        rhsNode.addEdge(lhsNode)
    }

    fun addReturnNode(newReturnNode: GraphNode) {
        newReturnNode.addEdge(returnNode)
    }

    fun getMethodName(): String {
        val methodName = method.name.name.toString()
        return if (methodName == "<init>") "$className.constructor" else methodName
    }

}