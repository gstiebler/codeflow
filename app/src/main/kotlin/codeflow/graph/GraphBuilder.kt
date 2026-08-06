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
    // Class whose constructor this block is running, for blocks created by AstMemPosProcessor.
    // Used to resolve `super(...)` / `this(...)` delegation. "noneClass" for plain method calls.
    val className: String,
    private val ctx: ProcessorContext
) {
    private val logger = KotlinLogging.logger {}

    // Declared above returnNode and parameterNodes, which create nodes and so call nextSerial()
    // while this block is still being constructed. Kotlin runs property initialisers top to
    // bottom, so moving this down leaves the counter at 0 for those first few nodes.
    private var serialCounter = 0

    val graph: Graph = Graph(this)
    private val nodeIdToVariable = HashMap<GraphNodeId, Variable>()

    /**
     * Identity for everything drawn, handed out in creation order.
     *
     * The root block owns the counter and every descendant asks it, so a serial is unique across
     * the whole exported document rather than just within one method - the subgraphs all live in
     * one Mermaid flowchart and share an id namespace. It is per-run, not per-JVM, so a snapshot
     * does not depend on what else the test suite happened to build first.
     *
     * What this replaces was a hash of the node's label and call stack. Two unrelated nodes whose
     * hashes agreed were rendered under one id, which draws them as a single box and puts every
     * edge of both on it. assertNoSelfEdges caught that only when the merged pair referenced each
     * other; otherwise the diagram simply claimed a value flowed somewhere it never did.
     */
    fun nextSerial(): Int = parent?.nextSerial() ?: serialCounter++

    /** The subgraph's own id, from the same counter and for the same reason. */
    val serial = nextSerial()

    val calledMethods = ArrayList<GraphBuilderBlock>()
    var returnNode = createReturnNode(stack)

    // should it be here? or on a MethodBlock class?
    val parameterNodes = method.name.parameters.map {
        val baseNode = GraphNode.Base(JNodeId(stack.push(ctx, it), it.name, memPos))
        graph.createGraphNode(NodeType.FUNC_PARAM, baseNode)
    }

    init {
        setLastNode(returnNode)
        parameterNodes.forEach { setLastNode(it) }
    }

    private fun setLastNode(node: GraphNode) {
        logger.debug { "setLastNode: $node" }
        val previousVariable = nodeIdToVariable[node.id]
        if (previousVariable == null) {
            nodeIdToVariable[node.id] = Variable(node)
        } else {
            previousVariable.lastNode = node
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

    fun addLiteral(base: GraphNode.Base): GraphNode {
        val newNode = graph.createGraphNode(NodeType.LITERAL, base)
        setLastNode(newNode)
        return newNode
    }

    /*
    In x.memberX = 5;, x is the owner of memberX
     */
    fun addPrimitiveVariable(base: GraphNode.Base, owner: MemPos?): GraphNode {
        val newNode = graph.createGraphNode(NodeType.VARIABLE, base)
        setLastNode(newNode)
        owner?.addNode(newNode)
        return newNode
    }

    fun addObjectVariable(base: GraphNode.Base, owner: MemPos?): GraphNode {
        val newNode = graph.createGraphNode(NodeType.OBJ_VARIABLE, base)
        setLastNode(newNode)
        owner?.addNode(newNode)
        return newNode
    }

    fun addBinOp(base: GraphNode.Base, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setLastNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

    fun addUnaryOp(base: GraphNode.Base, operand: GraphNode): GraphNode {
        val unaryOpNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setLastNode(unaryOpNode)
        operand.addEdge(unaryOpNode)
        return unaryOpNode
    }

    /**
     * The node standing for something outside the analysed sources. A call has no body to inline,
     * so it is opaque: its inputs flow in and whatever it produces flows out. A value such as an
     * enum constant has no inputs at all.
     */
    fun addExternal(base: GraphNode.Base, inputs: List<GraphNode>): GraphNode {
        val externalNode = graph.createGraphNode(NodeType.EXTERNAL, base)
        setLastNode(externalNode)
        inputs.forEach { it.addEdge(externalNode) }
        return externalNode
    }

    /**
     * The node standing for a value picked from several alternatives, by `?:` or by a `switch`
     * used as an expression. Every branch can produce the value and the selector decides which,
     * so the selector and all the branches flow into it.
     */
    fun addSelection(base: GraphNode.Base, inputs: List<GraphNode>): GraphNode {
        val selectionNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setLastNode(selectionNode)
        inputs.forEach { it.addEdge(selectionNode) }
        return selectionNode
    }

    /** `condition ? ifTrue : ifFalse`, the two-branch case of [addSelection]. */
    fun addTernaryOp(base: GraphNode.Base, condition: GraphNode, ifTrue: GraphNode, ifFalse: GraphNode) =
        addSelection(base, listOf(condition, ifTrue, ifFalse))

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