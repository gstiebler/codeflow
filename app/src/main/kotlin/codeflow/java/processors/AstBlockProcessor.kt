package codeflow.java.processors

import codeflow.graph.*
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
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

    /**
     * Evaluates an expression to the node carrying its value.
     *
     * Everything that needs a value goes through here rather than calling `accept` directly, so
     * that [scan]'s check for unmodelled expressions cannot be bypassed.
     */
    fun evaluate(tree: ExpressionTree, ctx: ProcessorContext): GraphNode =
        scan(tree, ctx) ?: throw GraphException("'$tree' at ${ctx.location(tree)} produced no value")

    override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), ctx)
        // An lhs javac could not resolve is not primitive as far as this can tell, so it takes the
        // object path: that tracks a memory position and tolerates an rhs it cannot evaluate,
        // where the primitive path would drop the assignment.
        val lhsIsPrimitive = globalCtx.symbols.isPrimitive(lhs)

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
        val isPrimitive = globalCtx.symbols.isPrimitive(node)
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
        val rhsNode = evaluate(rhs, ctx)
        graphBuilderBlock.addAssignment(lhsNode, rhsNode)
        return lhsNode
    }

    /**
     * Assigns the mem pos of the rhs to the lhs.
     */
    private fun assignMemPos(owner: MemPos?, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val lhsNode = graphBuilderBlock.addObjectVariable(GraphNode.Base(lhsId), owner)
        // An object we know nothing about: it came from outside the analysed sources, or from a
        // call whose returns we do not follow. It still gets a memory position of its own, so that
        // fields set on it and calls made on it have somewhere to hang.
        val rhsMemPos = getMemPos(rhs, ctx) ?: globalCtx.createMemPos(rhs)
        val rhsNode = try {
            evaluate(rhs, ctx)
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
        exprMemPos?.getNode(nodeId)?.let { return it }
        // With a memory position we are tracking the object, so failing to find the field is a
        // real problem and should be reported. Without one the receiver is something from outside
        // the analysed sources, such as the enum in `LoanCapitalizedIncomeStrategy.EQUAL_AMORTIZATION`,
        // and the value it selects is opaque rather than missing.
        if (exprMemPos != null) {
            return getLastNodeOfVariable(nodeId)
        }
        return graphBuilderBlock.getVariable(nodeId)?.lastNode
            ?: graphBuilderBlock.addExternal(GraphNode.Base(nodeId), emptyList())
    }

    /**
     * The single point every tree passes through, and where an unmodelled expression is rejected.
     *
     * TreeScanner's default for a node it is not told about is to scan the children and return
     * one of their results. For a statement that is fine, because a statement produces no value.
     * For an expression it is a fabricated edge: `!flag` comes back as the node for `flag` and
     * `-x` as the node for `x`, so the operator disappears from the graph and the diagram reads
     * as if the code did something it does not. The dropped branch of `cond ? a : b` was the
     * same mistake, and cost a real debugging session before it was noticed.
     *
     * So expressions have to be modelled explicitly, and anything missing says so on the spot,
     * with a file and line, rather than being found later by someone who trusted the diagram.
     */
    override fun scan(node: Tree?, ctx: ProcessorContext): GraphNode? {
        if (node is ExpressionTree && node.kind !in MODELLED_EXPRESSIONS) {
            throw GraphException("Unsupported expression '${node.kind}' at ${ctx.location(node)}: '$node'")
        }
        return super.scan(node, ctx)
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
        val rightNode = evaluate(node.leftOperand, ctx)
        val leftNode = evaluate(node.rightOperand, ctx)
        val jId = GraphNodeId(getStack().push(ctx, node), binaryOperatorLabel(node))
        return graphBuilderBlock.addBinOp(GraphNode.Base(jId), leftNode, rightNode)
    }

    /**
     * `condition ? ifTrue : ifFalse`.
     *
     * Left to TreeScanner's default handling this returns whichever branch it scanned first and
     * drops the rest, so the graph claims the expression can only produce one of its two values
     * and loses the guard entirely. `?:` is not used as the label because `:` would run into
     * Mermaid's `:::` class syntax.
     */
    override fun visitConditionalExpression(node: ConditionalExpressionTree, ctx: ProcessorContext): GraphNode {
        val conditionNode = evaluate(node.condition, ctx)
        val trueNode = evaluate(node.trueExpression, ctx)
        val falseNode = evaluate(node.falseExpression, ctx)
        val jId = GraphNodeId(getStack().push(ctx, node), "ternary")
        return graphBuilderBlock.addTernaryOp(GraphNode.Base(jId), conditionNode, trueNode, falseNode)
    }

    /**
     * `-x`, `!flag`, `i++`.
     *
     * The operator gets a node of its own with the operand flowing into it. Without one the
     * operand's node is returned directly, so `!flag` is indistinguishable from `flag` and the
     * graph shows a value being used where its negation is.
     *
     * The write-back half of `i++` is not modelled. Loop iteration is not modelled either, so a
     * value that depends on how far a counter advanced is already outside what the graph claims.
     */
    override fun visitUnary(node: UnaryTree, ctx: ProcessorContext): GraphNode {
        val operandNode = evaluate(node.expression, ctx)
        val jId = GraphNodeId(getStack().push(ctx, node), unaryOperatorLabel(node))
        return graphBuilderBlock.addUnaryOp(GraphNode.Base(jId), operandNode)
    }

    /**
     * `y += 1`, which is `y = y + 1`: the variable is read, combined, and written back.
     *
     * Both halves matter. Scanning the children and returning the first, as the default does,
     * yields the node for `y` and drops the operation and the right-hand side entirely, so the
     * new value appears to arrive from nowhere.
     */
    override fun visitCompoundAssignment(node: CompoundAssignmentTree, ctx: ProcessorContext): GraphNode {
        val currentNode = evaluate(node.variable, ctx)
        val rhsNode = evaluate(node.expression, ctx)
        val opId = GraphNodeId(getStack().push(ctx, node), compoundAssignmentLabel(node))
        val opNode = graphBuilderBlock.addBinOp(GraphNode.Base(opId), currentNode, rhsNode)

        val lhsName = node.variable.accept(AstLastNameProcessor(), ctx)
        val lhsId = JNodeId(getStack().push(ctx, node), lhsName, owner)
        val lhsNode = graphBuilderBlock.addPrimitiveVariable(GraphNode.Base(lhsId), owner)
        graphBuilderBlock.addAssignment(lhsNode, opNode)
        return lhsNode
    }

    /**
     * `switch` used as an expression, which is `?:` with more than two branches: the selector
     * picks one of the case values and that becomes the value of the whole thing.
     *
     * Only the `case X -> expression` form is modelled. A branch that yields from a block would
     * need the `yield` traced out of it, and guessing instead would produce a switch node whose
     * value arrives from nowhere, which is the failure this whole check exists to prevent.
     */
    override fun visitSwitchExpression(node: SwitchExpressionTree, ctx: ProcessorContext): GraphNode {
        val selectorNode = evaluate(node.expression, ctx)
        val branchNodes = node.cases.map { case ->
            val body = case.body
            if (body !is ExpressionTree) {
                throw GraphException(
                    "Unsupported switch branch at ${ctx.location(case)}: " +
                            "only 'case X -> expression' is modelled, got '$case'"
                )
            }
            evaluate(body, ctx)
        }
        val jId = GraphNodeId(getStack().push(ctx, node), "switch")
        return graphBuilderBlock.addSelection(GraphNode.Base(jId), listOf(selectorNode) + branchNodes)
    }

    private fun unaryOperatorLabel(node: UnaryTree) = when (node.kind) {
        Tree.Kind.UNARY_MINUS -> "neg"
        Tree.Kind.UNARY_PLUS -> "unaryPlus"
        Tree.Kind.LOGICAL_COMPLEMENT -> "not"
        Tree.Kind.BITWISE_COMPLEMENT -> "bitNot"
        Tree.Kind.PREFIX_INCREMENT -> "preInc"
        Tree.Kind.PREFIX_DECREMENT -> "preDec"
        Tree.Kind.POSTFIX_INCREMENT -> "postInc"
        Tree.Kind.POSTFIX_DECREMENT -> "postDec"
        else -> throw GraphException("Unsupported unary operator '${node.kind}'")
    }

    private fun compoundAssignmentLabel(node: CompoundAssignmentTree) = when (node.kind) {
        Tree.Kind.PLUS_ASSIGNMENT -> "+="
        Tree.Kind.MINUS_ASSIGNMENT -> "-="
        Tree.Kind.MULTIPLY_ASSIGNMENT -> "*="
        Tree.Kind.DIVIDE_ASSIGNMENT -> "divEq"
        Tree.Kind.REMAINDER_ASSIGNMENT -> "%="
        Tree.Kind.AND_ASSIGNMENT -> "bitAndEq"
        Tree.Kind.OR_ASSIGNMENT -> "bitOrEq"
        Tree.Kind.XOR_ASSIGNMENT -> "xorEq"
        Tree.Kind.LEFT_SHIFT_ASSIGNMENT -> "shlEq"
        Tree.Kind.RIGHT_SHIFT_ASSIGNMENT -> "shrEq"
        Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT -> "ushrEq"
        else -> throw GraphException("Unsupported compound assignment '${node.kind}'")
    }

    /**
     * Label shown on the operator's node.
     *
     * Operators whose symbol is also Mermaid syntax get a name instead: `/` opens a parallelogram
     * node, `|` delimits an edge label and `&` separates nodes, so the symbols would corrupt the
     * diagram rather than just look odd.
     */
    private fun binaryOperatorLabel(node: BinaryTree) = when (node.kind) {
        Tree.Kind.PLUS -> "+"
        Tree.Kind.MINUS -> "-"
        Tree.Kind.MULTIPLY -> "*"
        Tree.Kind.DIVIDE -> "div"
        Tree.Kind.REMAINDER -> "%"
        Tree.Kind.EQUAL_TO -> "=="
        Tree.Kind.NOT_EQUAL_TO -> "!="
        Tree.Kind.LESS_THAN -> "<"
        Tree.Kind.GREATER_THAN -> ">"
        Tree.Kind.LESS_THAN_EQUAL -> "<="
        Tree.Kind.GREATER_THAN_EQUAL -> ">="
        Tree.Kind.CONDITIONAL_AND -> "and"
        Tree.Kind.CONDITIONAL_OR -> "or"
        Tree.Kind.AND -> "bitAnd"
        Tree.Kind.OR -> "bitOr"
        Tree.Kind.XOR -> "xor"
        Tree.Kind.LEFT_SHIFT -> "shl"
        Tree.Kind.RIGHT_SHIFT -> "shr"
        Tree.Kind.UNSIGNED_RIGHT_SHIFT -> "ushr"
        else -> throw GraphException("Unsupported binary operator '${node.kind}' in '$node'")
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode? {
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        // `super(...)` and `this(...)` are parsed as invocations of a method literally named
        // "super"/"this", so no lookup by name could ever resolve them. Attribution resolves both
        // to the constructor they delegate to, and the kind is what says which case this is.
        val target = globalCtx.symbols.element(node)
        if (target?.kind == ElementKind.CONSTRUCTOR) {
            return invokeConstructorDelegation(target, node, ctx)
        }
        val method = globalCtx.findMethod(globalCtx.symbols.element(node, ElementKind.METHOD))
            ?: return invokeExternalMethod(methodIdentifier, node, ctx)
        val methodArguments = node.arguments.map { evaluate(it, ctx) }

        val invocationPos = ctx.getPosId(node)
        val localPos = Position(invocationPos, ctx.path)
        val exprMemPos = getMemPos(methodIdentifier.expression, ctx)

        val graphBlock = GraphBuilderBlock(graphBuilderBlock, method, getStack().push(localPos), exprMemPos, ctx)
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, exprMemPos)
        blockProcessor.invokeMethod(methodArguments)
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    /**
     * Represents a call to a method outside the analysed sources as a single node.
     *
     * There is no body to inline, so the call is opaque: the arguments and the receiver flow into
     * it and its result flows out. That keeps a value traceable across the call instead of ending
     * the analysis, which is what matters for real code, where almost every method eventually
     * reaches the standard library.
     */
    private fun invokeExternalMethod(
        methodIdentifier: MethodRefs,
        node: MethodInvocationTree,
        ctx: ProcessorContext
    ): GraphNode {
        val argumentNodes = node.arguments.map { evaluate(it, ctx) }
        // The receiver is a value flowing into the call only when it is something we track. For a
        // static call it is a type name, as in `Math.abs`, which is not a node in the graph.
        val receiverNode = methodIdentifier.expression?.let { runCatching { evaluate(it, ctx) }.getOrNull() }
        val jId = GraphNodeId(getStack().push(ctx, node), methodIdentifier.methodName.toString())
        return graphBuilderBlock.addExternal(GraphNode.Base(jId), listOfNotNull(receiverNode) + argumentNodes)
    }

    /**
     * Runs the constructor that a `super(...)` or `this(...)` call delegates to.
     *
     * Unlike `new X(...)`, no instance is created: the delegate initialises the object already
     * under construction, so it runs against the current [owner] MemPos. That is what lets an
     * inherited field assigned in the superclass constructor be read from the subclass.
     */
    private fun invokeConstructorDelegation(
        target: Element,
        node: MethodInvocationTree,
        ctx: ProcessorContext
    ): GraphNode? {
        // A constructor outside the analysed sources, which for a class that extends nothing is
        // java.lang.Object's. There is no body to inline and, since the delegation is a statement,
        // no value for anything to read, so it contributes nothing rather than an opaque node.
        val constructor = globalCtx.findMethod(target) ?: return null

        val localPos = Position(ctx.getPosId(node), ctx.path)
        val graphBlock = GraphBuilderBlock(
            graphBuilderBlock, constructor, getStack().push(localPos), owner, ctx
        )
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, owner)
        val argumentNodes = node.arguments.map { evaluate(it, ctx) }
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

    override fun visitBlock(node: BlockTree, ctx: ProcessorContext): GraphNode? {
        val statements = node.statements
        statements.forEach() {
            logger.debug { "Processing statement: '$it'" }
            scan(it, ctx)
        }
        return null
    }

    companion object {
        /** `a + b`, `a == b`, and the rest handled by [visitBinary]. */
        private val BINARY_KINDS = setOf(
            Tree.Kind.PLUS, Tree.Kind.MINUS, Tree.Kind.MULTIPLY, Tree.Kind.DIVIDE, Tree.Kind.REMAINDER,
            Tree.Kind.EQUAL_TO, Tree.Kind.NOT_EQUAL_TO, Tree.Kind.LESS_THAN, Tree.Kind.GREATER_THAN,
            Tree.Kind.LESS_THAN_EQUAL, Tree.Kind.GREATER_THAN_EQUAL,
            Tree.Kind.CONDITIONAL_AND, Tree.Kind.CONDITIONAL_OR,
            Tree.Kind.AND, Tree.Kind.OR, Tree.Kind.XOR,
            Tree.Kind.LEFT_SHIFT, Tree.Kind.RIGHT_SHIFT, Tree.Kind.UNSIGNED_RIGHT_SHIFT
        )

        /** `-x`, `!flag`, `i++`, and the rest handled by [visitUnary]. */
        private val UNARY_KINDS = setOf(
            Tree.Kind.UNARY_MINUS, Tree.Kind.UNARY_PLUS,
            Tree.Kind.LOGICAL_COMPLEMENT, Tree.Kind.BITWISE_COMPLEMENT,
            Tree.Kind.PREFIX_INCREMENT, Tree.Kind.PREFIX_DECREMENT,
            Tree.Kind.POSTFIX_INCREMENT, Tree.Kind.POSTFIX_DECREMENT
        )

        /** `y += 1`, `y *= 2`, and the rest handled by [visitCompoundAssignment]. */
        private val COMPOUND_ASSIGNMENT_KINDS = setOf(
            Tree.Kind.PLUS_ASSIGNMENT, Tree.Kind.MINUS_ASSIGNMENT, Tree.Kind.MULTIPLY_ASSIGNMENT,
            Tree.Kind.DIVIDE_ASSIGNMENT, Tree.Kind.REMAINDER_ASSIGNMENT,
            Tree.Kind.AND_ASSIGNMENT, Tree.Kind.OR_ASSIGNMENT, Tree.Kind.XOR_ASSIGNMENT,
            Tree.Kind.LEFT_SHIFT_ASSIGNMENT, Tree.Kind.RIGHT_SHIFT_ASSIGNMENT,
            Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT
        )

        private val LITERAL_KINDS = setOf(
            Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL, Tree.Kind.FLOAT_LITERAL,
            Tree.Kind.DOUBLE_LITERAL, Tree.Kind.BOOLEAN_LITERAL, Tree.Kind.CHAR_LITERAL,
            Tree.Kind.STRING_LITERAL, Tree.Kind.NULL_LITERAL
        )

        /**
         * Every expression kind that produces a node meaning what the code means.
         *
         * PARENTHESIZED is here without a visitor of its own because it really is transparent:
         * the value of `(x)` is the value of `x`, so scanning through to the child is right.
         */
        private val MODELLED_EXPRESSIONS = BINARY_KINDS + UNARY_KINDS + COMPOUND_ASSIGNMENT_KINDS +
                LITERAL_KINDS + setOf(
            Tree.Kind.IDENTIFIER, Tree.Kind.MEMBER_SELECT, Tree.Kind.METHOD_INVOCATION,
            Tree.Kind.ASSIGNMENT, Tree.Kind.CONDITIONAL_EXPRESSION, Tree.Kind.PARENTHESIZED,
            Tree.Kind.SWITCH_EXPRESSION
        )
    }
}