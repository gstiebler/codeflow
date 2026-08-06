package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JIdentifierId
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstBlockProcessor(
    private val globalCtx: GlobalContext,
    private val parent: AstBlockProcessor?,
    val graphBuilderBlock: GraphBuilderBlock,
    private val pos: Position,
    private val owner: MemPos? // instance of the class that contains the method, null for static methods
) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    init {
        logger.debug { "AstBlockProcessor created: $owner" }
    }

    override fun toString() = graphBuilderBlock.method.name.name.toString()

    private fun getStack(): PosStack {
        val parentStack = parent?.getStack() ?: PosStack()
        return parentStack.push(pos)
    }

    override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), ctx)
        val lhsIsPrimitive = globalCtx.isPrimitive(JIdentifierId(lhsName))

        val lhsParentExpr = lhs.accept(AstParentExprProcessor(), ctx)
        val lhsMemPos = if (lhsParentExpr == null) {
            owner
        } else {
            getMemPos(lhsParentExpr, ctx)
        }
        val lhsId = JNodeId(getStack(), lhsName, lhsMemPos)
        if (lhsIsPrimitive) {
            // val lhsId = JNodeId(lhsName, memPos)
            assignPrimitive(lhsMemPos, lhsId, rhs, ctx)
        } else {
            assignMemPos(lhsMemPos, lhsId, rhs, ctx)
        }
        return null
    }

    override fun visitVariable(node: VariableTree, ctx: ProcessorContext): GraphNode? {
        val typeKind = node.type.kind

        val isPrimitive = typeKind == Tree.Kind.PRIMITIVE_TYPE
        globalCtx.registerIsPrimitive(JIdentifierId(node.name), isPrimitive)
        val name = node.name

        if (node.initializer != null) {
            val variableNodeId = JNodeId(getStack().push(ctx, node), name, owner)
            if (isPrimitive) {
                return assignPrimitive(owner, variableNodeId, node.initializer, ctx)
            } else {
                assignMemPos(owner, variableNodeId, node.initializer, ctx)
            }
        }

        return null
    }

    private fun assignPrimitive(owner: MemPos?, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext): GraphNode {
        val lhsNode = graphBuilderBlock.addPrimitiveVariable(GraphNode.Base(lhsId), owner)
        val rhsNode = rhs.accept(this, ctx)
        graphBuilderBlock.addAssignment(lhsNode, rhsNode)
        return lhsNode
    }

    /**
     * Assigns the mem pos of the rhs to the lhs.
     */
    private fun assignMemPos(owner: MemPos?, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val lhsNode = graphBuilderBlock.addObjectVariable(GraphNode.Base(lhsId), owner)
        val rhsMemPos = getMemPos(rhs, ctx) ?: throw GraphException("Mem pos of $rhs is null")
        val rhsNode = try {
            rhs.accept(this, ctx)
        } catch(e: Exception) {
            null
        }
        if (rhsNode != null) {
            graphBuilderBlock.addAssignment(lhsNode, rhsNode)
        }
        globalCtx.addMemPos(lhsId, rhsMemPos)
    }

    /**
     * Returns the mem pos of the given expression.
     */
    private fun getMemPos(node: Tree?, ctx: ProcessorContext): MemPos? {
        return node?.accept(AstMemPosProcessor(globalCtx, graphBuilderBlock, this, getStack(), owner), ctx)
    }

    private fun getLastNodeOfVariable(id: GraphNodeId): GraphNode {
        val variable = graphBuilderBlock.getVariable(id)
        return variable?.lastNode ?:
            throw GraphException("Identifier '${id}' not found in graph: ${graphBuilderBlock.graph}")
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): GraphNode {
        // before the dot
        val expression = node.expression
        // after the dot
        val identifier = node.identifier
        // memory position of the class instance
        val exprMemPos = getMemPos(expression, ctx)
        val nodeId = JNodeId(getStack().push(ctx, node), identifier, exprMemPos)
        return exprMemPos?.getNode(nodeId) ?: getLastNodeOfVariable(nodeId)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: ProcessorContext): GraphNode? {
        return super.visitMemberReference(node, p)
    }

    /**
     * A bare identifier can be a local variable or a field read with an implicit `this`.
     * Fields live on the owning instance's MemPos, so it has to be consulted the same way
     * visitMemberSelect consults it for the explicit `this.field` form. Without this, a field
     * written in one method (typically the constructor) and read in another blows up, because
     * the block parent chain searched by getLastNodeOfVariable does not span sibling methods.
     */
    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): GraphNode {
        val nId = JNodeId(getStack().push(ctx, node), node.name, owner)
        return owner?.getNode(nId) ?: getLastNodeOfVariable(nId)
    }

    override fun visitLiteral(node: LiteralTree, ctx: ProcessorContext): GraphNode {
        val nodeId = GraphNodeId(getStack().push(ctx, node), node.toString())
        val gNode = GraphNode.Base(nodeId)
        val newNode = graphBuilderBlock.addLiteral(gNode)
        super.visitLiteral(node, ctx)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, ctx: ProcessorContext): GraphNode {
        val rightNode = node.leftOperand.accept(this, ctx)
        val leftNode = node.rightOperand.accept(this, ctx)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            "DIVIDE" -> "div"
            "EQUAL_TO" -> "=="
            "LESS_THAN" -> "<"
            else -> "UNKNOWN"
        }
        val jId = GraphNodeId(getStack().push(ctx, node), label)
        return graphBuilderBlock.addBinOp(GraphNode.Base(jId), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode {
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        val methodName = methodIdentifier.methodName.toString()
        // `super(...)` and `this(...)` are parsed as invocations of a method literally named
        // "super"/"this", which no lookup by name can ever resolve.
        if (methodIdentifier.expression == null && (methodName == "super" || methodName == "this")) {
            return invokeConstructorDelegation(methodName, node, ctx)
        }
        val method = globalCtx.getMethod(JMethodId(methodIdentifier.methodName))
        val methodArguments = node.arguments.map { it.accept(this, ctx) }

        val invocationPos = ctx.getPosId(node)
        val localPos = Position(invocationPos, ctx.path)
        val exprMemPos = getMemPos(methodIdentifier.expression, ctx)

        val graphBlock = GraphBuilderBlock(graphBuilderBlock, method, getStack().push(localPos), exprMemPos, "noneClass", ctx)
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, exprMemPos)
        blockProcessor.invokeMethod(methodArguments)
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    /**
     * Runs the constructor that a `super(...)` or `this(...)` call delegates to.
     *
     * Unlike `new X(...)`, no instance is created: the delegate initialises the object already
     * under construction, so it runs against the current [owner] MemPos. That is what lets an
     * inherited field assigned in the superclass constructor be read from the subclass.
     */
    private fun invokeConstructorDelegation(
        keyword: String,
        node: MethodInvocationTree,
        ctx: ProcessorContext
    ): GraphNode {
        val currentClass = graphBuilderBlock.className
        val targetClass = if (keyword == "super") globalCtx.getSuperclass(currentClass) else currentClass
        if (targetClass == null) {
            throw GraphException("Superclass of '$currentClass' not found, needed for '$node'")
        }

        val argumentTypes = resolveArgumentTypeNames(node.arguments, globalCtx, getStack(), owner, ctx)
        val constructor = globalCtx.constructors.get(targetClass, argumentTypes)
            ?: throw GraphException("Constructor '$targetClass(${argumentTypes.joinToString(", ")})' not found")

        val localPos = Position(ctx.getPosId(node), ctx.path)
        val graphBlock = GraphBuilderBlock(
            graphBuilderBlock, Method(constructor, ctx), getStack().push(localPos), owner, targetClass, ctx
        )
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, owner)
        val argumentNodes = node.arguments.map { it.accept(this, ctx) }
        blockProcessor.invokeMethod(argumentNodes)
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    fun invokeMethod(methodArguments: List<GraphNode>) {
        val method = graphBuilderBlock.method
        val methodName =  method.name
        methodName.receiverParameter?.accept(this, method.ctx)
        methodName.body.accept(this, method.ctx)
        graphBuilderBlock.connectParameters(methodArguments)
    }

    override fun visitReturn(node: ReturnTree, p: ProcessorContext): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilderBlock.addReturnNode(newNode)
        return newNode
    }

    override fun visitExpressionStatement(node: ExpressionStatementTree, ctx: ProcessorContext): GraphNode? {
        return super.visitExpressionStatement(node, ctx)
    }

    override fun visitBlock(node: BlockTree, ctx: ProcessorContext): GraphNode? {
        val statements = node.statements
        statements.forEach() {
            logger.debug { "Processing statement: '$it'" }
            it.accept(this, ctx)
        }
        return null
    }
}