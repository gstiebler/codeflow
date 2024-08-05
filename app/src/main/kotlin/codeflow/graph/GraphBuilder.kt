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
    private val ctx: ProcessorContext,
    ifNode: GraphNode?,
    ifSide: Boolean
) {
    private val logger = KotlinLogging.logger {}
    val graph: Graph = Graph(this)
    private val nodeIdToVariable = HashMap<GraphNodeId, Variable>()
    val localId = stack.hashCode() * 37 + 4308977
    val calledMethods = ArrayList<GraphBuilderBlock>()
    var returnNode = createReturnNode(stack)

    // should it be here? or on a MethodBlock class?
    val parameterNodes = method.name.parameters.map {
        val baseNode = GraphNode.Base(JNodeId(stack.push(ctx, it), it.name, memPos))
        graph.createGraphNode(NodeType.FUNC_PARAM, baseNode)
    }

    init {
        setVarAssignmentNode(returnNode, ifNode, ifSide)
        parameterNodes.forEach { setVarAssignmentNode(it, ifNode, ifSide) }
    }

    private fun setVarAssignmentNode(node: GraphNode, ifNode: GraphNode?, ifSide: Boolean) {
        logger.debug { "setVarAssignmentNode: $node" }
        val previousVariable = nodeIdToVariable[node.id]
        if (previousVariable == null) {
            nodeIdToVariable[node.id] = Variable(node)
        } else {
            previousVariable.setLatestNode(node, ifNode, ifSide)
        }
    }

    fun getVariable(id: GraphNodeId): Variable? {
        return nodeIdToVariable[id] ?: parent?.getVariable(id)
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

    fun addLiteral(base: GraphNode.Base, ifNode: GraphNode?, ifSide: Boolean): GraphNode {
        val newNode = graph.createGraphNode(NodeType.LITERAL, base)
        setVarAssignmentNode(newNode, ifNode, ifSide)
        return newNode
    }

    fun addVariable(base: GraphNode.Base, memPos: MemPos?, ifNode: GraphNode?, ifSide: Boolean): GraphNode {
        val newNode = graph.createGraphNode(NodeType.VARIABLE, base)
        setVarAssignmentNode(newNode, ifNode, ifSide)
        memPos?.addNode(newNode)
        return newNode
    }

    fun addBinOp(
        base: GraphNode.Base,
        leftNode: GraphNode,
        rightNode: GraphNode,
        ifNode: GraphNode?,
        ifSide: Boolean
    ): GraphNode {
        val binOpNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setVarAssignmentNode(binOpNode, ifNode, ifSide)
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

    fun addIf(base: GraphNode.Base, conditionNode: GraphNode): GraphNode {
        val ifNode = graph.createGraphNode(NodeType.IF, base)
        conditionNode.addEdge(ifNode)
        return ifNode
    }

}