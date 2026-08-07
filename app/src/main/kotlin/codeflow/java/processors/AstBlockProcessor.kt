package codeflow.java.processors

import codeflow.graph.*
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import codeflow.java.ids.JNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import java.util.IdentityHashMap

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

    /**
     * What each `new X(...)` and each call in this invocation produced.
     *
     * Keyed by identity, and held per processor rather than globally, because a processor is one
     * invocation of one method: the same expression reached through two call sites is two objects,
     * and the same expression asked about twice within one invocation is one.
     *
     * Both the value and the object are asked for separately - an assignment wants the node to draw
     * an edge from and the memory position to record what the variable now points at - so without
     * the cache the callee's body would be inlined twice, drawing every box in it twice over.
     */
    private val evaluated = IdentityHashMap<ExpressionTree, Evaluation>()

    /** [node] is null for a `super(...)` reaching outside the sources, which produces no value. */
    private class Evaluation(val memPos: MemPos?, val node: GraphNode?)

    /** The node for `this` in this invocation, created on the first mention. See [thisValue]. */
    private var thisNode: GraphNode? = null

    /**
     * Where a `return` sends its value, when that is not the method's own return node.
     *
     * Only a lambda with a statement body sets this. Its body is walked in the enclosing method's
     * block, so without the redirect its `return` would be wired to *that* method's return node -
     * an edge claiming the method returns a value it does not. See [visitLambdaExpression].
     */
    private var returnTarget: GraphNode? = null

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
        val lhsId = JNodeId(getStack(), lhsName, globalCtx.symbols.element(lhs), lhsMemPos)
        if (lhsIsPrimitive) {
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
            val variableNodeId = JNodeId(getStack().push(ctx, node), name, globalCtx.symbols.element(node), owner)
            if (isPrimitive) {
                return assignPrimitive(owner, variableNodeId, node.initializer, ctx)
            } else {
                assignMemPos(owner, variableNodeId, node.initializer, ctx)
            }
        }

        return null
    }

    /**
     * `for (LoanCharge charge : charges)`, which binds the variable to each element in turn.
     *
     * The elements come out of the thing being iterated, so that is an edge, and the variable is a
     * declaration like any other - without one, every read of it in the body found no node and took
     * the run down, which is what an unmodelled *statement* looks like: the gate in [scan] only
     * covers expressions, so nothing named the construct and the failure surfaced a few lines later
     * as a name that had gone missing.
     *
     * The element gets a memory position of its own rather than the collection's. It is not the
     * collection, and handing it that identity would file its fields under the container's, so a
     * method inlined on the element would read them off the wrong object.
     */
    override fun visitEnhancedForLoop(node: EnhancedForLoopTree, ctx: ProcessorContext): GraphNode? {
        val elements = evaluate(node.expression, ctx)
        val variable = node.variable
        val id = JNodeId(getStack().push(ctx, variable), variable.name, globalCtx.symbols.element(variable), owner)
        val base = GraphNode.Base(id)
        val loopNode = if (globalCtx.symbols.isPrimitive(variable)) {
            graphBuilderBlock.addPrimitiveVariable(base, owner)
        } else {
            globalCtx.addMemPos(id, globalCtx.createMemPos(node.expression))
            graphBuilderBlock.addObjectVariable(base, owner)
        }
        graphBuilderBlock.addAssignment(loopNode, elements)
        scan(node.statement, ctx)
        return null
    }

    /**
     * `catch (NumberFormatException failure)`, which binds a name the handler goes on to read.
     *
     * Nothing flows in. Which `throw` reached this handler is control flow, and none is modelled,
     * so a value with no source is the honest drawing - the same answer a lambda parameter gets.
     * What it cannot be is absent: the gate in [scan] covers expressions only, so an unmodelled
     * statement names nothing and surfaces further down as a read of a name with no node, blaming
     * a line that is not the one at fault. That is how this was found, twice.
     */
    override fun visitCatch(node: CatchTree, ctx: ProcessorContext): GraphNode? {
        val parameter = node.parameter
        val id = JNodeId(getStack().push(ctx, parameter), parameter.name, globalCtx.symbols.element(parameter), owner)
        globalCtx.addMemPos(id, globalCtx.createMemPos(parameter))
        graphBuilderBlock.addObjectVariable(GraphNode.Base(id), owner)
        scan(node.block, ctx)
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
     *
     * The right-hand side is evaluated like any other, and a failure to evaluate it is a failure.
     * This used to catch everything and carry on with no edge, which turned every gap on this path
     * - `new X(...)`, then any unmodelled construct - into a variable drawn with nothing flowing
     * into it. That is not a smaller version of the failure, it is the wrong graph the gate in
     * [scan] exists to prevent, and object assignment is most of real Java.
     */
    private fun assignMemPos(owner: MemPos?, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val lhsNode = graphBuilderBlock.addObjectVariable(GraphNode.Base(lhsId), owner)
        // An object we know nothing about: it came from outside the analysed sources, or from a
        // call whose returns we do not follow. It still gets a memory position of its own, so that
        // fields set on it and calls made on it have somewhere to hang.
        val rhsMemPos = getMemPos(rhs, ctx) ?: globalCtx.createMemPos(rhs)
        graphBuilderBlock.addAssignment(lhsNode, evaluate(rhs, ctx))
        globalCtx.addMemPos(lhsId, rhsMemPos)
    }

    /**
     * Returns the mem pos of the given expression.
     */
    private fun getMemPos(node: Tree?, ctx: ProcessorContext): MemPos? {
        return node?.accept(AstMemPosProcessor(globalCtx, graphBuilderBlock, this, getStack(), owner), ctx)
    }

    private fun getLastNodeOfVariable(id: GraphNodeId): GraphNode? = graphBuilderBlock.getVariable(id)?.lastNode

    /**
     * The node for a name we are tracking the object of but have found no value for.
     *
     * For a field that is not a gap in the analysis, it is the program: a field nothing has assigned
     * yet holds its default, and reading one is ordinary Java - a builder left half-filled, then
     * `this.x = builder.x`, is the usual way to get there. So it becomes a value with nothing
     * flowing into it, which is what the diagram should say, rather than taking the run down.
     *
     * An enum constant has no assignment anywhere to look for, because the declaration is the value.
     * Written out, `ChargeCalculationType.FLAT` already came back as an opaque node with nothing
     * flowing in; a bare `FLAT` inside the enum is the same value written shorter, so it is the same
     * node rather than a failure.
     *
     * A local or a parameter cannot be read before it is written, so not finding one of those means
     * the analysis lost it somewhere, and that still fails loudly with a file and a line.
     */
    private fun unassigned(id: JNodeId, tree: Tree, owner: MemPos?, ctx: ProcessorContext): GraphNode {
        val kind = globalCtx.symbols.element(tree)?.kind
        if (kind == ElementKind.ENUM_CONSTANT) {
            return graphBuilderBlock.addExternal(GraphNode.Base(id), emptyList())
        }
        if (kind != ElementKind.FIELD) {
            throw GraphException(
                "Identifier '$id' at ${ctx.location(tree)} not found in graph: ${graphBuilderBlock.graph}"
            )
        }
        val base = GraphNode.Base(id)
        return if (globalCtx.symbols.isPrimitive(tree)) {
            graphBuilderBlock.addPrimitiveVariable(base, owner)
        } else {
            graphBuilderBlock.addObjectVariable(base, owner)
        }
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): GraphNode {
        // before the dot
        val expression = node.expression
        // after the dot
        val identifier = node.identifier
        // memory position of the class instance
        val exprMemPos = getMemPos(expression, ctx)
        val nodeId = JNodeId(getStack().push(ctx, node), identifier, globalCtx.symbols.element(node), exprMemPos)
        exprMemPos?.getNode(nodeId)?.let { return it }
        // With a memory position we are tracking the object, so the field either has a value here
        // or has not been assigned yet - see [unassigned]. Without one the receiver is something
        // from outside the analysed sources, such as the enum in
        // `LoanCapitalizedIncomeStrategy.EQUAL_AMORTIZATION`, and the value it selects is opaque
        // rather than missing.
        if (exprMemPos != null) {
            return getLastNodeOfVariable(nodeId) ?: unassigned(nodeId, node, exprMemPos, ctx)
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
        if (node.name.contentEquals("this")) return thisValue(node, ctx)
        val nId = JNodeId(getStack().push(ctx, node), node.name, globalCtx.symbols.element(node), owner)
        return owner?.getNode(nId)
            ?: graphBuilderBlock.getVariable(nId)?.lastNode
            ?: unassigned(nId, node, owner, ctx)
    }

    /**
     * `this`, the object the method is running on.
     *
     * It is a value - `return this`, `new Wrapper(this)` - and had no node, so a bare `this` was a
     * name that resolved to nothing and took the whole method down. It is also an object, so it
     * carries [owner] as its memory position and what the callee reads off it is this object's
     * own fields.
     *
     * Held per processor because a processor is one invocation: `this` is one object however many
     * times the method mentions it. It cannot be keyed like a variable instead, because a method
     * reached through a receiver we could not track has no owner, and every such `this` in the run
     * would key alike and be drawn as one object.
     */
    private fun thisValue(node: IdentifierTree, ctx: ProcessorContext): GraphNode = thisNode ?: run {
        val instance = owner ?: globalCtx.createMemPos(node)
        val id = JNodeId(getStack().push(ctx, node), node.name, globalCtx.symbols.element(node), instance)
        val created = graphBuilderBlock.addObjectVariable(GraphNode.Base(id), instance)
        globalCtx.addMemPos(id, instance)
        thisNode = created
        created
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
     * `array[index]`, which reads a value out of the array, with the index deciding which.
     *
     * Both flow in, for the same reason both operands of `a + b` do. The label is a word because
     * `[` and `]` delimit a node in Mermaid, so `n5[[]]` is a different shape rather than a label.
     */
    override fun visitArrayAccess(node: ArrayAccessTree, ctx: ProcessorContext): GraphNode {
        val arrayNode = evaluate(node.expression, ctx)
        val indexNode = evaluate(node.index, ctx)
        val jId = GraphNodeId(getStack().push(ctx, node), "index")
        return graphBuilderBlock.addBinOp(GraphNode.Base(jId), arrayNode, indexNode)
    }

    /**
     * `new byte[16]` and `new int[] { seed, 9 }`, a value built out of what is written inside it.
     *
     * The elements are what the array holds, so they flow in. So does each dimension: it is not
     * held in the array, but it is what decides how much of it there is, and dropping it draws a
     * value that came from nowhere - the same wrongness as any other dropped operand. Nothing here
     * tracks which element ended up at which index, so a read through [visitArrayAccess] reaches
     * all of them; that is coarse rather than wrong, and it is what the index node says.
     *
     * The two forms are exclusive in the language - `new int[2] { .. }` does not compile - so this
     * never sees both, but taking whichever is there needs no case split.
     *
     * The label is a word because `[` and `]` delimit a node in Mermaid.
     */
    override fun visitNewArray(node: NewArrayTree, ctx: ProcessorContext): GraphNode {
        val dimensions = node.dimensions.orEmpty().map { evaluate(it, ctx) }
        val elements = node.initializers.orEmpty().map { evaluate(it, ctx) }
        val jId = GraphNodeId(getStack().push(ctx, node), "array")
        return graphBuilderBlock.addSelection(GraphNode.Base(jId), dimensions + elements)
    }

    /**
     * `value instanceof String`, and `value instanceof String text`, which also binds `text`.
     *
     * The test is a value derived from the thing tested, so it gets a node with that flowing in.
     * The binding is not decoration: it is a declaration, and every later read of the name resolves
     * to it, so leaving it out does not lose an edge but takes the whole method down. It names the
     * same object, so it shares the memory position rather than being given one of its own -
     * otherwise a field read through the pattern variable is looked for in the wrong place.
     */
    override fun visitInstanceOf(node: InstanceOfTree, ctx: ProcessorContext): GraphNode {
        val testedNode = evaluate(node.expression, ctx)
        bindPattern(node.pattern, node.expression, testedNode, ctx)
        val jId = GraphNodeId(getStack().push(ctx, node), "instanceof")
        return graphBuilderBlock.addUnaryOp(GraphNode.Base(jId), testedNode)
    }

    /**
     * `k -> new MathContext(...)`, a function value.
     *
     * The body is walked in the enclosing block rather than being given one of its own, because a
     * lambda is not a call: nothing here invokes it, so there is no call site to nest it under. Its
     * value is derived from the body, which is what flows into the node standing for the function.
     *
     * The parameters are filled in by whoever calls the lambda, which is not visible from here, so
     * they are values with nothing flowing into them - the same honest answer as a field nobody has
     * assigned. They have to exist even when nothing reads them, since a read that finds no node is
     * a failure rather than a missing edge.
     *
     * A statement body returns through [returnTarget] rather than the enclosing method's return
     * node, which is a different method entirely. A body that returns nothing leaves the function
     * value with no inputs, which is what a `Consumer` is.
     */
    override fun visitLambdaExpression(node: LambdaExpressionTree, ctx: ProcessorContext): GraphNode {
        node.parameters.forEach { parameter ->
            val id = JNodeId(getStack().push(ctx, parameter), parameter.name, globalCtx.symbols.element(parameter), owner)
            graphBuilderBlock.addObjectVariable(GraphNode.Base(id), owner)
        }
        val base = GraphNode.Base(GraphNodeId(getStack().push(ctx, node), "lambda"))
        val body = node.body
        if (body is ExpressionTree) {
            return graphBuilderBlock.addUnaryOp(base, evaluate(body, ctx))
        }
        val lambdaNode = graphBuilderBlock.addSelection(base, emptyList())
        val enclosing = returnTarget
        returnTarget = lambdaNode
        try {
            scan(body, ctx)
        } finally {
            returnTarget = enclosing
        }
        return lambdaNode
    }

    /**
     * `DisbursementData::disbursementDate`, a function value naming a method.
     *
     * Nothing here calls it, so there is no call site to inline the method under and no arguments to
     * bind to its parameters - what runs inside it is settled by whoever invokes it later. It is
     * opaque for the same reason a call outside the sources is.
     *
     * A qualifier that is a value, as in `charges::add`, is captured by the function and flows in.
     * One that is a type name is not a value at all and contributes no node, which is the same
     * distinction [invokeExternalMethod] draws for the receiver of `Math.abs(x)`.
     */
    override fun visitMemberReference(node: MemberReferenceTree, ctx: ProcessorContext): GraphNode {
        val qualifier = node.qualifierExpression
        val captured = if (isValue(qualifier)) evaluate(qualifier, ctx) else null
        val jId = GraphNodeId(getStack().push(ctx, node), node.name.toString())
        return graphBuilderBlock.addExternal(GraphNode.Base(jId), listOfNotNull(captured))
    }

    /** Whether an expression is something the graph holds a value for, rather than a type name. */
    private fun isValue(tree: ExpressionTree): Boolean {
        if (tree is IdentifierTree && tree.name.contentEquals("this")) return true
        val kind = globalCtx.symbols.element(tree)?.kind ?: return false
        return kind.isField || kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.PARAMETER
    }

    /**
     * Whether an expression names a type rather than producing a value - the `Math` of `Math.abs`.
     *
     * Asked instead of [isValue] because the receiver of a call is any expression at all:
     * `of(x).getAmount()` and `charges[0].getAmount()` are values that resolve to no variable, so a
     * test for one would drop the edge carrying them into the call.
     *
     * This is the question a catch-everything used to answer. Evaluating the receiver and keeping
     * whatever came back meant a `GraphException` from an unmodelled receiver was discarded along
     * with the type names, which is the one construct in the language that must never be silent.
     */
    private fun isTypeName(tree: ExpressionTree): Boolean {
        val kind = globalCtx.symbols.element(tree)?.kind ?: return false
        return kind.isClass || kind.isInterface || kind == ElementKind.PACKAGE
    }

    private fun bindPattern(
        pattern: PatternTree?,
        tested: ExpressionTree,
        testedNode: GraphNode,
        ctx: ProcessorContext
    ) {
        if (pattern == null) return
        // A record deconstruction pattern binds a name per component, which is not modelled. Left
        // alone it would bind nothing and the components would be read as names that resolve to
        // nothing, several lines away from the pattern that was supposed to declare them.
        val variable = (pattern as? BindingPatternTree)?.variable
            ?: throw GraphException("Unsupported pattern at ${ctx.location(pattern)}: '$pattern'")
        val boundId = JNodeId(getStack().push(ctx, variable), variable.name, globalCtx.symbols.element(variable), owner)
        val boundNode = graphBuilderBlock.addObjectVariable(GraphNode.Base(boundId), owner)
        graphBuilderBlock.addAssignment(boundNode, testedNode)
        getMemPos(tested, ctx)?.let { globalCtx.addMemPos(boundId, it) }
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
        val lhsId = JNodeId(getStack().push(ctx, node), lhsName, globalCtx.symbols.element(node.variable), owner)
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

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode? =
        invoke(node, ctx).node

    /**
     * The object a call returned, so that a method inlined on the result reads the right fields.
     *
     * The result used to be given a memory position of its own, holding nothing, and that is the
     * silently-wrong graph rather than a coarser one: `Money.of(...)` then `.getAmount()` inlines
     * the getter against an object with no fields, so the getter's own `return amount` resolves to
     * nothing at all. A factory followed by a getter is most of a real codebase.
     *
     * A call whose body is not in the sources still has no position to give, and that is the honest
     * answer - the caller makes one up for it, as before.
     */
    fun invocationMemPos(node: MethodInvocationTree, ctx: ProcessorContext): MemPos? = invoke(node, ctx).memPos

    private fun invoke(node: MethodInvocationTree, ctx: ProcessorContext): Evaluation =
        evaluated.getOrPut(node) { invokeUncached(node, ctx) }

    private fun invokeUncached(node: MethodInvocationTree, ctx: ProcessorContext): Evaluation {
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        // `super(...)` and `this(...)` are parsed as invocations of a method literally named
        // "super"/"this", so no lookup by name could ever resolve them. Attribution resolves both
        // to the constructor they delegate to, and the kind is what says which case this is.
        val target = globalCtx.symbols.element(node)
        if (target?.kind == ElementKind.CONSTRUCTOR) {
            return Evaluation(null, invokeConstructorDelegation(target, node, ctx))
        }
        val method = globalCtx.findMethod(globalCtx.symbols.element(node, ElementKind.METHOD))
            ?: return Evaluation(null, invokeExternalMethod(methodIdentifier, node, ctx))
        val methodArguments = node.arguments.map { evaluate(it, ctx) }

        val invocationPos = ctx.getPosId(node)
        val localPos = Position(invocationPos, ctx.path)
        val exprMemPos = getMemPos(methodIdentifier.expression, ctx)

        val graphBlock = GraphBuilderBlock(graphBuilderBlock, method, getStack().push(localPos), exprMemPos, ctx)
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, exprMemPos)
        blockProcessor.invokeMethod(methodArguments, argumentMemPositions(node.arguments, ctx))
        graphBuilderBlock.addCalledMethod(graphBlock)
        return Evaluation(graphBlock.returnedMemPos, graphBlock.returnNode)
    }

    /**
     * `new X(...)`, which both creates an object and produces it as a value.
     *
     * The two halves used to be split: the memory position was made by [AstMemPosProcessor] as a
     * side effect of an assignment working out what the left-hand side now points at, and nothing
     * produced a node at all. In an argument position that failed outright, and in an assignment
     * position [assignMemPos] swallowed the failure, so the object appeared to come from nowhere.
     *
     * Both halves come from here now, and the result is remembered per expression because both
     * halves are asked for separately: one `new` in the source is one object, however many times
     * it is looked at.
     */
    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): GraphNode =
        construct(node, ctx).node!!

    /** The memory position of the object `new X(...)` creates, constructing it if it has not been. */
    fun constructedMemPos(node: NewClassTree, ctx: ProcessorContext): MemPos = construct(node, ctx).memPos!!

    private fun construct(node: NewClassTree, ctx: ProcessorContext): Evaluation =
        evaluated.getOrPut(node) {
            val createdMemPos = globalCtx.createMemPos(node.identifier)
            // The arguments belong to the caller, so they are resolved here rather than inside the
            // constructor's own block: resolving them there makes an argument that happens to share
            // a name with a parameter resolve to that parameter, which connects the parameter to
            // itself and drops the edge from the value actually passed in.
            val argumentNodes = node.arguments.map { evaluate(it, ctx) }
            // Which overload `new X(...)` selects is javac's answer to give. Comparing the argument
            // types as uppercased strings, which is what this did, guessed - and said so in its own
            // TODO.
            val constructor = globalCtx.findMethod(globalCtx.symbols.element(node, ElementKind.CONSTRUCTOR))
            Evaluation(createdMemPos, constructorNode(constructor, createdMemPos, argumentNodes, node, ctx))
        }

    /**
     * The node standing for the object.
     *
     * A constructor in the analysed sources has a body to inline, and its return node is what the
     * expression produces, exactly as for any other call. A class from outside - or one whose only
     * constructor is the one attribution inserted, which is not source anybody wrote - has no body,
     * so the object is opaque and the arguments flow into it.
     */
    private fun constructorNode(
        constructor: Method?,
        createdMemPos: MemPos,
        argumentNodes: List<GraphNode>,
        node: NewClassTree,
        ctx: ProcessorContext
    ): GraphNode {
        if (constructor == null) {
            logger.debug { "No constructor found: $node" }
            val typeName = node.identifier.accept(AstLastNameProcessor(), ctx)?.toString()
                ?: node.identifier.toString()
            val jId = GraphNodeId(getStack().push(ctx, node), typeName)
            return graphBuilderBlock.addExternal(GraphNode.Base(jId), argumentNodes)
        }
        val localPos = Position(ctx.getPosId(node), ctx.path)
        val graphBlock = GraphBuilderBlock(
            graphBuilderBlock, constructor, getStack().push(localPos), createdMemPos, ctx
        )
        AstBlockProcessor(globalCtx, this, graphBlock, localPos, createdMemPos)
            .invokeMethod(argumentNodes, argumentMemPositions(node.arguments, ctx))
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
        val receiver = methodIdentifier.expression?.takeUnless { isTypeName(it) }
        val receiverNode = receiver?.let { evaluate(it, ctx) }
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
        blockProcessor.invokeMethod(argumentNodes, argumentMemPositions(node.arguments, ctx))
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    /**
     * Runs the method's body with the caller's arguments bound to its parameters.
     *
     * An argument that is an object is bound twice over: its node, so the value is traceable, and
     * its memory position, so it is the *same object* inside. Only the nodes used to be connected,
     * which left the parameter naming an object nothing was known about, so a field read off it
     * fell to the opaque path - an EXTERNAL node with no edge back to the field really holding the
     * value. That draws a complete, readable graph asserting a flow that does not exist.
     *
     * The positions are bound before the body is walked, because the body is what asks for them.
     */
    fun invokeMethod(methodArguments: List<GraphNode>, argumentMemPositions: List<MemPos?> = emptyList()) {
        val method = graphBuilderBlock.method
        val methodName =  method.name
        graphBuilderBlock.parameterNodes.zip(argumentMemPositions).forEach { (parameter, memPos) ->
            memPos?.let { globalCtx.addMemPos(parameter.id, it) }
        }
        methodName.receiverParameter?.accept(this, method.ctx)
        methodName.body.accept(this, method.ctx)
        graphBuilderBlock.connectParameters(methodArguments)
    }

    /** The memory position of each argument, so [invokeMethod] can bind them to the parameters. */
    private fun argumentMemPositions(arguments: List<ExpressionTree>, ctx: ProcessorContext) =
        arguments.map { getMemPos(it, ctx) }

    /**
     * The returned value, and the object it is - see [GraphBuilderBlock.returnedMemPos].
     *
     * The memory position is asked for after the value, so that a `return new X(...)` has already
     * built the object by the time it is looked up rather than building it a second time.
     */
    override fun visitReturn(node: ReturnTree, ctx: ProcessorContext): GraphNode? {
        // `return;` leaves a void method early and carries no value, so there is nothing to wire to
        // the return node. Every return was assumed to have one, which made an early exit - the
        // ordinary guard clause at the top of a method - a NullPointerException naming no source at
        // all, which is worse to be handed than a wrong graph.
        val expression = node.expression ?: return null
        val value = evaluate(expression, ctx)
        val lambda = returnTarget
        if (lambda != null) {
            graphBuilderBlock.addAssignment(lambda, value)
        } else {
            graphBuilderBlock.addReturnNode(value, getMemPos(expression, ctx))
        }
        return value
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
            Tree.Kind.SWITCH_EXPRESSION, Tree.Kind.NEW_CLASS,
            Tree.Kind.ARRAY_ACCESS, Tree.Kind.INSTANCE_OF, Tree.Kind.LAMBDA_EXPRESSION,
            Tree.Kind.MEMBER_REFERENCE, Tree.Kind.NEW_ARRAY
        )
    }
}