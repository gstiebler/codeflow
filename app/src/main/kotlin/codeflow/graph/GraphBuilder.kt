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
    // the objects the method could be running on - see Frame.owner
    private val memPos: Set<MemPos>,
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

    /**
     * Which objects this invocation returned, so the caller can go on tracking them.
     *
     * Every `return` in the body contributes, because every one of them is a way the call can come
     * back. It used to be the first that named an object, which is the arm-picking a set exists to
     * stop: a method choosing between two instances handed the caller one of them, and the fields
     * of the other were unreachable from the call site with nothing on the diagram to say so.
     */
    var returnedMemPos: Set<MemPos> = emptySet()
        private set

    // should it be here? or on a MethodBlock class?
    // The parameter's declaration comes off the method's own element rather than being looked up
    // per tree, and is what a read of the parameter inside the body resolves to.
    val parameterNodes = method.name.parameters.mapIndexed { index, parameter ->
        val id = JNodeId(stack.push(ctx, parameter), parameter.name.toString(), method.element.parameters[index], memPos)
        graph.createGraphNode(NodeType.FUNC_PARAM, GraphNode.Base(id, method.ctx.location(parameter)))
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

    /**
     * A return is not a variable and has no declaration to be keyed by, so it uses the plain
     * label-and-stack key. The stack is this block's, which is unique to this invocation, and
     * nothing looks the node up: whatever returns a value reaches it through [addReturnNode].
     *
     * It stands for the method as a whole rather than for any one `return`, of which there may be
     * several, so the declaration is what it points at.
     */
    private fun createReturnNode(stack: PosStack): GraphNode {
        val nodeBase = GraphNode.Base(
            GraphNodeId(stack, method.name.name.toString()), method.ctx.location(method.name)
        )
        return graph.createGraphNode(NodeType.RETURN, nodeBase)
    }

    /**
     * Where the method this block draws was declared, as `path:line:column`.
     *
     * From the method's own context, not the caller's [ctx]: a method inlined at a call site in
     * another file would otherwise be given a line number read off the caller's line map, which is
     * a real position naming the wrong file - the kind of wrong that reads as fact.
     */
    fun getSource(): String = method.ctx.location(method.name)

    override fun toString() = method.name.name.toString()

    fun connectParameters(methodArguments: List<GraphNode>) {
        methodArguments.forEachIndexed { index, callingParameter ->
            callingParameter.addEdge(parameterFor(index, methodArguments.size))
        }
    }

    /**
     * The parameter an argument is bound to.
     *
     * Everything past the last declared parameter goes into it, which is what varargs means:
     * `f(a, b, c)` on `f(A a, B... rest)` puts `b` and `c` in the array `rest` names. Indexing
     * straight into the list walked off the end instead, and an IndexOutOfBoundsException names no
     * source at all - `String.format(fmt, x, y)` is enough to reach it.
     *
     * Any other count mismatch is the analysis having gone wrong, not the language, so it says so.
     */
    private fun parameterFor(index: Int, argumentCount: Int): GraphNode {
        parameterNodes.getOrNull(index)?.let { return it }
        if (!method.element.isVarArgs || parameterNodes.isEmpty()) {
            throw GraphException(
                "$argumentCount arguments for ${parameterNodes.size} parameters of '${method.displayName()}'"
            )
        }
        return parameterNodes.last()
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
    fun addPrimitiveVariable(base: GraphNode.Base, owner: Set<MemPos>): GraphNode {
        val newNode = graph.createGraphNode(NodeType.VARIABLE, base)
        setLastNode(newNode)
        owner.forEach { it.addNode(newNode) }
        return newNode
    }

    fun addObjectVariable(base: GraphNode.Base, owner: Set<MemPos>): GraphNode {
        val newNode = graph.createGraphNode(NodeType.OBJ_VARIABLE, base)
        setLastNode(newNode)
        owner.forEach { it.addNode(newNode) }
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
     * The node standing for a construct codeflow does not model.
     *
     * Shaped like [addExternal] - what it was built from flows in, its value flows out - because
     * that is the honest reading of "something here I cannot see inside", and it is the reading
     * that keeps a value traceable across the gap instead of ending the analysis there. It is a
     * separate type from EXTERNAL for the reason on [GraphNode.Unmodelled].
     */
    fun addUnmodelled(base: GraphNode.Base, inputs: List<GraphNode>): GraphNode {
        val node = graph.createGraphNode(NodeType.UNMODELLED, base)
        setLastNode(node)
        inputs.forEach { it.addEdge(node) }
        return node
    }

    /**
     * The node standing for a value made out of several others: one picked from alternatives by
     * `?:` or by a `switch` used as an expression, or an array built from its size and elements.
     * All of them flow in, since all of them go into deciding what the value is.
     */
    fun addSelection(base: GraphNode.Base, inputs: List<Pair<GraphNode, EdgeKind>>): GraphNode {
        val selectionNode = graph.createGraphNode(NodeType.BIN_OP, base)
        setLastNode(selectionNode)
        inputs.forEach { (node, kind) -> node.addEdge(selectionNode, kind) }
        return selectionNode
    }

    /**
     * A variable that is one of several values, drawn as a variable: all of them flow into one box.
     *
     * Two things arrive here. A phi is the control-flow case - the paths of an `if`, the arms of a
     * `switch`, a loop's entry and its back edge - and a field read through a name pointing at more
     * than one object is the alias case, where the same field on each possible object is a value
     * the read can produce. Both are "one of these, and nothing here says which".
     *
     * Where a value *did* say which, it is among the inputs and the box is captioned with the
     * construct rather than the variable - see [codeflow.ir.Gate]. That covers an `if` and a
     * `switch` statement; a loop, a `try` and the alias case have no such value, and there the
     * caller passes only the paths.
     *
     * Still not a [addSelection], although a gated join and a selection now say much the same
     * thing. A selection is an expression the source wrote, and its node stands for the value that
     * expression produced; this is a variable at a place in the program, and it exists whether or
     * not anything was written there.
     */
    fun addJoin(base: GraphNode.Base, inputs: List<Pair<GraphNode, EdgeKind>>, isPrimitive: Boolean): GraphNode {
        val type = if (isPrimitive) NodeType.VARIABLE else NodeType.OBJ_VARIABLE
        val phiNode = graph.createGraphNode(type, base)
        setLastNode(phiNode)
        inputs.forEach { (node, kind) -> node.addEdge(phiNode, kind) }
        return phiNode
    }

    fun addAssignment(lhsNode: GraphNode, rhsNode: GraphNode) {
        rhsNode.addEdge(lhsNode)
    }

    fun addReturnNode(newReturnNode: GraphNode, returned: Set<MemPos> = emptySet()) {
        newReturnNode.addEdge(returnNode)
        returnedMemPos = returnedMemPos + returned
    }

    fun getMethodName(): String = method.displayName()

}