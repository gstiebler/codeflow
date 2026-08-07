package codeflow.ir

import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.java.Symbols
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier

/**
 * One method's body, as instructions.
 *
 * Per method and context-free: nothing here knows which call site it is being read from, which
 * object it is running on, or how many times it will be drawn. That separation is the whole point -
 * inlining, object identity and node ids are the *builder's* job, so the same body lowered once can
 * be instantiated at every call site.
 */
class MethodBody(val method: Method, val instructions: List<Insn>) {
    fun render(): List<String> = instructions.mapIndexed { index, insn -> "$index: ${insn.render()}" }
}

/**
 * javac trees in, instructions out.
 *
 * The walk resolves names and nothing else: no `MemPos`, no `PosStack`, no ids, no edges. What made
 * the tree walker this replaced hard to reason about was doing all of those at once, which is why
 * four satellite scanners existed to re-walk the same tree asking a different question, and why one
 * of them had to call back into the builder behind a memo so that *asking* did not *inline*.
 *
 * The value each expression produces is returned as a [Val], which is an index into the list being
 * built. So an instruction never refers to a tree, only to earlier instructions.
 */
class Lowering(private val symbols: Symbols) {

    fun lower(method: Method): MethodBody {
        val body = Body(symbols, method.ctx)
        body.declareParameters(method)
        method.name.body?.accept(body, method.ctx)
        return MethodBody(method, body.instructions)
    }

    /**
     * What a class assigns outside any method body: `int n = 5;` and `{ blocked = 7; }`.
     *
     * A body of its own, because it is not one: these run on every `new`, ahead of the constructor,
     * and a class declaring no constructor still runs them. Skipping them is not a missing edge but
     * a missing *value* - the constructor's `n = n + 1` reads an `n` nothing has assigned, and most
     * fields have no other writer.
     *
     * Static members are left out. An enum constant is a static field, and so is a field of an
     * interface, so both are left out here too.
     */
    fun lowerInitializers(declaringClass: GlobalContext.SourceClass): List<Insn> {
        val body = Body(symbols, declaringClass.ctx)
        declaringClass.tree.members.forEach { member ->
            val runs = when (member) {
                is VariableTree -> member.initializer != null && !isStatic(member)
                is BlockTree -> !member.isStatic
                else -> false
            }
            if (runs) body.scan(member, declaringClass.ctx)
        }
        return body.instructions
    }

    /**
     * `SMALL(3)`, the declaration of one enum constant, whose last instruction is the `new` it is.
     *
     * The constant's declaration *is* its value, and with a constructor there is a story to tell:
     * arguments in, fields out. Lowered from the enum's own class tree, since it is a member rather
     * than anything a method body contains, and in the enum's own context - an enum in another file
     * has its positions there.
     */
    fun lowerEnumConstant(declaringClass: GlobalContext.SourceClass, element: Element): List<Insn>? {
        val declaration = declaringClass.tree.members
            .filterIsInstance<VariableTree>()
            .firstOrNull { symbols.element(it) == element } ?: return null
        val initializer = declaration.initializer as? NewClassTree ?: return null
        val body = Body(symbols, declaringClass.ctx)
        body.scan(initializer, declaringClass.ctx)
        return body.instructions
    }

    /** Asked of the declaration javac resolved, since `static` is implicit on an interface field. */
    private fun isStatic(member: VariableTree): Boolean {
        val element = symbols.element(member) ?: return Modifier.STATIC in member.modifiers.flags
        return Modifier.STATIC in element.modifiers
    }

    private class Body(
        private val symbols: Symbols,
        private val ctx: ProcessorContext
    ) : TreeScanner<Val, ProcessorContext>() {

        val instructions = ArrayList<Insn>()

        /** Where a `return` goes when it belongs to a lambda rather than to the method. */
        private val lambdaReturns = ArrayDeque<MutableList<Val>>()

        /**
         * The value each local currently holds, which is what a use of it resolves to.
         *
         * Keyed by the declaration javac resolved, so two `x`es in disjoint scopes are two entries
         * without anything here comparing names or tracking scopes. A name javac could not resolve
         * falls back to the name itself, which is the same compromise `JNodeId` makes and for the
         * same reason: on input that does not compile there is nothing better to key by.
         */
        private val definitions = HashMap<Any, Val>()

        private fun emit(insn: Insn): Val {
            instructions.add(insn)
            return Val(instructions.size - 1)
        }

        private fun key(element: Element?, name: String): Any = element ?: name

        /** Records what a local holds from here on. Every write and every binding goes through it. */
        private fun define(element: Element?, name: String, value: Val): Val {
            definitions[key(element, name)] = value
            return value
        }

        /**
         * The parameters, as definitions, ahead of anything the body does.
         *
         * From the method's own element rather than looked up per declaration tree, because that is
         * what a use inside the body resolves to and what `GraphBuilderBlock` binds arguments to.
         */
        fun declareParameters(method: Method) {
            method.name.parameters.forEachIndexed { index, parameter ->
                val element = method.element.parameters.getOrNull(index)
                val name = parameter.name.toString()
                define(element, name, emit(Param(name, element, index, ctx.location(parameter))))
            }
        }

        /**
         * The value an expression produces.
         *
         * Everything wanting a value comes through here rather than calling `accept`, so an
         * expression that produces nothing is a loud failure at the place that needed one, instead
         * of a null quietly standing in for a value further down.
         */
        private fun evaluate(tree: ExpressionTree, ctx: ProcessorContext): Val =
            scan(tree, ctx) ?: throw GraphException("'$tree' at ${ctx.location(tree)} produced no value")

        override fun visitLiteral(node: LiteralTree, ctx: ProcessorContext): Val =
            emit(Const(node.toString(), ctx.location(node)))

        /**
         * A bare name is a local, a parameter, or a field read with the `this.` left off.
         *
         * Which it is comes from javac, asked once here, rather than from two visitors each
         * remembering to consult the object the method runs on - which is how a field written in a
         * constructor and read in another method came to resolve to nothing at all.
         */
        override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): Val {
            if (node.name.contentEquals("this")) return emit(ThisRef(ctx.location(node)))
            val element = symbols.element(node)
            if (element?.kind?.isField == true) {
                return emit(
                    ReadField(
                        Receiver.Enclosing,
                        node.name.toString(),
                        element,
                        symbols.isPrimitive(node),
                        ctx.location(node)
                    )
                )
            }
            return use(element, node.name.toString(), ctx.location(node))
        }

        /**
         * The definition reaching a use of a local, which is the whole of what SSA buys.
         *
         * Not finding one is the failure that has to stay hard, and it is raised here rather than
         * several inferences later while a graph is being drawn: a local cannot be read before it is
         * written, so either the program never wrote it or the lowering lost it, and drawing a value
         * arriving from nowhere is indistinguishable from a real one. A *field* never reaches here -
         * it holds its default, which is ordinary Java, and is lowered as a field read instead.
         */
        private fun use(element: Element?, name: String, source: String): Val =
            definitions[key(element, name)]
                ?: throw GraphException("'$name' at $source has no value reaching it")

        /**
         * A name introduced by something other than a declaration or an assignment - see [Bind].
         *
         * A definition like any other, which is what makes a later use of it resolve. Missing that
         * is how the enhanced `for`, the `catch` parameter and a `case` pattern label were each
         * found: not as a wrong edge, but as a use several lines below with nothing to reach.
         */
        private fun bind(variable: VariableTree, value: Val?, identity: Identity, ctx: ProcessorContext): Val {
            val element = symbols.element(variable)
            val name = variable.name.toString()
            val insn = Bind(name, element, symbols.isPrimitive(variable), value, identity, ctx.location(variable))
            return define(element, name, emit(insn))
        }

        override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): Val =
            emit(
                ReadField(
                    receiverOf(node.expression, ctx),
                    node.identifier.toString(),
                    symbols.element(node),
                    symbols.isPrimitive(node),
                    ctx.location(node)
                )
            )

        /**
         * The object an access or a call is written against.
         *
         * `this` and `super` both name the object the enclosing method is running on, so neither
         * produces a value: `super.m()` runs on the same instance `this.m()` would. A type name
         * produces no value either, but for the opposite reason - there is no object at all.
         */
        private fun receiverOf(expression: ExpressionTree?, ctx: ProcessorContext): Receiver {
            if (expression == null) return Receiver.Enclosing
            if (expression is IdentifierTree &&
                (expression.name.contentEquals("this") || expression.name.contentEquals("super"))
            ) {
                return Receiver.Enclosing
            }
            if (isTypeName(expression)) return Receiver.TypeName
            return Receiver.Value(evaluate(expression, ctx))
        }

        /**
         * Whether an expression names a type rather than producing a value - the `Math` of
         * `Math.abs`.
         *
         * Asked of what javac resolved, not of the shape of the tree: `of(x).getAmount()` and
         * `charges[0].getAmount()` are receivers that name no variable at all, so a test for one
         * would drop the edge carrying them into the call.
         */
        private fun isTypeName(tree: ExpressionTree): Boolean {
            val kind = symbols.element(tree)?.kind ?: return false
            return kind.isClass || kind.isInterface || kind == ElementKind.PACKAGE
        }

        override fun visitVariable(node: VariableTree, ctx: ProcessorContext): Val? {
            val initializer = node.initializer ?: return null
            val value = evaluate(initializer, ctx)
            val element = symbols.element(node)
            val name = node.name.toString()
            return define(
                element,
                name,
                emit(WriteLocal(name, element, symbols.isPrimitive(node), value, ctx.location(node)))
            )
        }

        /**
         * `d = b`, which is the declaration form with the declaration elsewhere.
         *
         * The right-hand side is evaluated before the write exists, which is Java's own order and
         * the only one that reads `x = x + 1` correctly: emitting the target first would make the
         * `x` inside the expression find the value about to be written rather than the old one.
         */
        override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): Val {
            val target = node.variable
            // The receiver before the value, which is the order Java evaluates them in: the object
            // `y.x` names is settled before whatever is about to be stored in it is worked out.
            val receiver = if (target is MemberSelectTree) receiverOf(target.expression, ctx) else Receiver.Enclosing
            val value = evaluate(node.expression, ctx)
            val element = symbols.element(target)
            val primitive = symbols.isPrimitive(target)
            if (element?.kind?.isField == true) {
                return emit(WriteField(receiver, lastName(target), element, primitive, value, ctx.location(target)))
            }
            val name = lastName(target)
            return define(element, name, emit(WriteLocal(name, element, primitive, value, ctx.location(target))))
        }

        override fun visitBinary(node: BinaryTree, ctx: ProcessorContext): Val {
            val left = evaluate(node.leftOperand, ctx)
            val right = evaluate(node.rightOperand, ctx)
            return emit(BinOp(binaryOperatorLabel(node), left, right, ctx.location(node)))
        }

        override fun visitUnary(node: UnaryTree, ctx: ProcessorContext): Val {
            val operand = evaluate(node.expression, ctx)
            return emit(UnOp(unaryOperatorLabel(node), operand, ctx.location(node)))
        }

        /**
         * The condition is an input like the branches are. It decides which value comes out, so a
         * reader who cannot see it cannot tell why either branch would be taken.
         *
         * `?:` is not the label: `:` runs into Mermaid's `:::` class syntax.
         */
        override fun visitConditionalExpression(node: ConditionalExpressionTree, ctx: ProcessorContext): Val {
            val condition = evaluate(node.condition, ctx)
            val ifTrue = evaluate(node.trueExpression, ctx)
            val ifFalse = evaluate(node.falseExpression, ctx)
            return emit(Select("ternary", listOf(condition, ifTrue, ifFalse), ctx.location(node)))
        }

        /**
         * The receiver, then the arguments, which is the order Java evaluates them in.
         *
         * `super(...)` and `this(...)` are parsed as calls to a method literally named "super" or
         * "this", so no lookup by name could resolve them; attribution resolves both to the
         * constructor they delegate to, and the kind is what says which case this is. One of those
         * is not in the source at all - a constructor that starts with neither gains a `super()` -
         * and it always delegates outside the analysed sources, so requiring the target to be
         * declared here drops the inserted one without having to detect it.
         */
        override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): Val? {
            val target = symbols.element(node)
            if (target?.kind == ElementKind.CONSTRUCTOR) {
                if (!symbols.isDeclaredInSources(target)) return null
                return emit(Delegate(target, node.arguments.map { evaluate(it, ctx) }, ctx.location(node)))
            }
            val select = node.methodSelect
            val receiver = if (select is MemberSelectTree) receiverOf(select.expression, ctx) else Receiver.Enclosing
            val args = node.arguments.map { evaluate(it, ctx) }
            return emit(
                Call(
                    lastName(select),
                    symbols.element(node, ElementKind.METHOD),
                    receiver,
                    args,
                    ctx.location(node)
                )
            )
        }

        override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): Val {
            val args = node.arguments.map { evaluate(it, ctx) }
            return emit(
                New(
                    lastName(node.identifier),
                    symbols.element(node, ElementKind.CONSTRUCTOR),
                    args,
                    ctx.location(node)
                )
            )
        }

        override fun visitReturn(node: ReturnTree, ctx: ProcessorContext): Val? =
            returnValue(node.expression?.let { evaluate(it, ctx) }, node, ctx)

        /**
         * `class Doubler { ... }` written inside a method body, which runs nothing where it stands.
         *
         * Its methods run when something calls them, and that call site reaches the declaration the
         * way every other one does - javac resolved it, and the body was recorded when the
         * compilation unit was walked. Descending into it here instead lowers every method it
         * declares into the enclosing one, so the caller gains an operation it does not perform on a
         * parameter nobody has passed. A statement produces no value and can still fabricate a flow.
         */
        override fun visitClass(node: ClassTree, ctx: ProcessorContext): Val? = null

        override fun visitBlock(node: BlockTree, ctx: ProcessorContext): Val? {
            node.statements.forEach { scan(it, ctx) }
            return null
        }

        /**
         * `y += 1`, which is `y = y + 1`: the variable is read, combined, and written back.
         *
         * Both halves matter. Walking the children and taking the first yields the read of `y` and
         * drops the operation and the right-hand side, so the new value appears to arrive from
         * nowhere.
         */
        override fun visitCompoundAssignment(node: CompoundAssignmentTree, ctx: ProcessorContext): Val {
            val target = node.variable
            val current = evaluate(target, ctx)
            val rhs = evaluate(node.expression, ctx)
            val combined = emit(BinOp(compoundAssignmentLabel(node), current, rhs, ctx.location(node)))
            val element = symbols.element(target)
            if (element?.kind?.isField == true) {
                val receiver =
                    if (target is MemberSelectTree) receiverOf(target.expression, ctx) else Receiver.Enclosing
                return emit(WriteField(receiver, lastName(target), element, true, combined, ctx.location(target)))
            }
            val name = lastName(target)
            return define(element, name, emit(WriteLocal(name, element, true, combined, ctx.location(target))))
        }

        /**
         * `array[index]`, which takes a value out of the array with the index deciding which.
         *
         * Both flow in, for the same reason both operands of `a + b` do. The label is a word because
         * `[` and `]` delimit a node in Mermaid, so the symbol would change the shape rather than
         * the text.
         */
        override fun visitArrayAccess(node: ArrayAccessTree, ctx: ProcessorContext): Val {
            val array = evaluate(node.expression, ctx)
            val index = evaluate(node.index, ctx)
            return emit(BinOp("index", array, index, ctx.location(node)))
        }

        /**
         * `new byte[16]` and `new int[] { seed, 9 }`, a value built out of what is written inside.
         *
         * The elements are what the array holds, so they flow in; so does each dimension, which is
         * not held in it but decides how much of it there is. Nothing here tracks which element
         * ended up at which index, which is what the `index` node above says.
         */
        override fun visitNewArray(node: NewArrayTree, ctx: ProcessorContext): Val {
            val dimensions = node.dimensions.orEmpty().map { evaluate(it, ctx) }
            val elements = node.initializers.orEmpty().map { evaluate(it, ctx) }
            return emit(Select("array", dimensions + elements, ctx.location(node)))
        }

        /**
         * `value instanceof String`, and `value instanceof String text`, which also binds `text`.
         *
         * The binding is not decoration: every later read of the name resolves to it, so leaving it
         * out does not lose an edge, it takes the whole method down.
         */
        override fun visitInstanceOf(node: InstanceOfTree, ctx: ProcessorContext): Val {
            val tested = evaluate(node.expression, ctx)
            bindPattern(node.pattern, tested, ctx)
            return emit(UnOp("instanceof", tested, ctx.location(node)))
        }

        /**
         * A pattern binds a name to the value it was tested against.
         *
         * A record deconstruction pattern binds one name per component and is not modelled. Left
         * alone it binds nothing, and the components are read further down as names that resolve to
         * nothing - so it is named here, at the pattern's own line, which is the whole point of the
         * gate reaching past expressions.
         */
        private fun bindPattern(pattern: PatternTree?, value: Val, ctx: ProcessorContext) {
            if (pattern == null) return
            val variable = (pattern as? BindingPatternTree)?.variable
            if (variable == null) {
                unmodelled(pattern, listOf(value), ctx)
                return
            }
            bind(variable, value, Identity.OfValue, ctx)
        }

        /**
         * `k -> new MathContext(...)`, a function value.
         *
         * The body is lowered here rather than in a block of its own, because a lambda is not a
         * call: nothing invokes it, so there is no call site to nest it under. Its parameters are
         * filled in by whoever does invoke it, which is not visible from here, so they bind with
         * nothing flowing in.
         *
         * A statement body's `return` belongs to the lambda, not to the enclosing method. Sending
         * it to the method's own return would claim the method returns a value it does not.
         */
        override fun visitLambdaExpression(node: LambdaExpressionTree, ctx: ProcessorContext): Val {
            node.parameters.forEach { bind(it, null, Identity.Unknown, ctx) }
            val body = node.body
            if (body is ExpressionTree) {
                return emit(Select("lambda", listOf(evaluate(body, ctx)), ctx.location(node)))
            }
            val returns = ArrayList<Val>()
            lambdaReturns.addLast(returns)
            try {
                scan(body, ctx)
            } finally {
                lambdaReturns.removeLast()
            }
            // A body that returns nothing leaves the function value with no inputs, which is what a
            // Consumer is.
            return emit(Select("lambda", returns, ctx.location(node)))
        }

        /**
         * `DisbursementData::disbursementDate`, a function value naming a method.
         *
         * Nothing here calls it, so there is no call site to nest the method under and no arguments
         * to bind - what runs inside is settled by whoever invokes it later. A qualifier that is a
         * value, as in `charges::add`, is captured by the function and flows in; one that is a type
         * name is not a value at all.
         */
        override fun visitMemberReference(node: MemberReferenceTree, ctx: ProcessorContext): Val {
            val captured = receiverOf(node.qualifierExpression, ctx)
            return emit(Opaque(node.name.toString(), captured.inputs, ctx.location(node)))
        }

        /**
         * `switch` used as an expression, which is `?:` with more than two branches.
         *
         * Only the `case X -> expression` form produces a value here. A branch that yields out of a
         * block would need the `yield` traced out of it, and guessing instead would produce a value
         * arriving from nowhere.
         */
        override fun visitSwitchExpression(node: SwitchExpressionTree, ctx: ProcessorContext): Val {
            val selector = evaluate(node.expression, ctx)
            val branches = node.cases.map { case ->
                val body = case.body
                if (body is ExpressionTree) evaluate(body, ctx) else unmodelled(case, listOf(selector), ctx)
            }
            return emit(Select("switch", listOf(selector) + branches, ctx.location(node)))
        }

        /**
         * `switch` used as a statement, which produces no value but reads one to decide where to go.
         *
         * That read was invisible: the default walk reaches the selector, gives it a node and draws
         * no edge out of it, so the value deciding the entire branch appears unused - and nothing
         * failed, because nothing declared a name. Each constant label is compared against the
         * selector, which is what the code does; a pattern label binds instead.
         *
         * Every arm is lowered, whichever one runs. Control flow is not modelled yet, so `break` and
         * fall-through make no difference here - §1 is where that changes.
         */
        override fun visitSwitch(node: SwitchTree, ctx: ProcessorContext): Val? {
            val selector = evaluate(node.expression, ctx)
            node.cases.forEach { case ->
                case.labels.forEach { label -> caseLabel(label, selector, ctx) }
                scan(case.guard, ctx)
                // The colon form carries statements and the arrow form a body, and each is null for
                // the other, so both have to be asked.
                case.body?.let { scan(it, ctx) } ?: case.statements?.forEach { scan(it, ctx) }
            }
            return null
        }

        /** `case 1:` compares against the selector; `case String text ->` binds; `default` neither. */
        private fun caseLabel(label: CaseLabelTree, selector: Val, ctx: ProcessorContext) {
            when (label) {
                is ConstantCaseLabelTree -> {
                    val constant = evaluate(label.constantExpression, ctx)
                    emit(BinOp("==", selector, constant, ctx.location(label)))
                }

                is PatternCaseLabelTree -> bindPattern(label.pattern, selector, ctx)
                is DefaultCaseLabelTree -> Unit
                else -> unmodelled(label, listOf(selector), ctx)
            }
        }

        /**
         * `for (LoanCharge charge : charges)`, which binds the variable to each element in turn.
         *
         * The elements come out of the thing being iterated, so that is what flows in. Without the
         * binding every read of the variable in the body found nothing and took the run down - which
         * is what an unmodelled *statement* looked like before the gate reached them.
         */
        override fun visitEnhancedForLoop(node: EnhancedForLoopTree, ctx: ProcessorContext): Val? {
            val elements = evaluate(node.expression, ctx)
            bind(node.variable, elements, Identity.Fresh, ctx)
            scan(node.statement, ctx)
            return null
        }

        /**
         * `catch (NumberFormatException failure)`, which binds a name the handler goes on to read.
         *
         * Nothing flows in: which `throw` reached this handler is control flow, and none is modelled
         * yet, so a value with no source is the honest answer.
         */
        override fun visitCatch(node: CatchTree, ctx: ProcessorContext): Val? {
            bind(node.parameter, null, Identity.Fresh, ctx)
            scan(node.block, ctx)
            return null
        }

        /** A `return` inside a lambda belongs to the lambda - see [visitLambdaExpression]. */
        private fun returnValue(value: Val?, node: ReturnTree, ctx: ProcessorContext): Val? {
            val lambda = lambdaReturns.lastOrNull()
            if (lambda != null) {
                value?.let { lambda.add(it) }
                return value
            }
            return emit(Return(value, ctx.location(node)))
        }

        /**
         * The one place every tree passes through, and where a construct with no visitor is caught.
         *
         * TreeScanner's default for something it is not told about is to scan the children and
         * return one of their results. For an expression that is a fabricated value: `!flag` comes
         * back as `flag`, so the operator is not in the instruction list and nothing downstream can
         * tell. Two real bugs came from exactly that.
         *
         * Unlike the gate this replaces, it is not restricted to expressions. A *statement* nobody
         * modelled declares nothing, so the failure used to surface further down as a read of a name
         * with no value, blaming a line that was not at fault - the enhanced `for` and the `catch`
         * parameter were both found that way, and a `case` pattern label was still doing it when
         * this was written.
         *
         * All three now have visitors, and [MODELLED_STATEMENTS] names every statement kind Java
         * has, so the second branch catches nothing today and no fixture trips it. It is here so
         * that stays a fact rather than an assumption: the list is what is *known* to be safe to
         * walk through, and a kind added to the language, or removed from the set, is a gap that
         * says so rather than a silent walk into something nobody looked at.
         */
        override fun scan(node: Tree?, ctx: ProcessorContext): Val? {
            if (node == null) return null
            if (node.kind in TYPE_KINDS) return null
            if (node is ExpressionTree && node.kind !in MODELLED_EXPRESSIONS) return unmodelled(node, operands(node, ctx), ctx)
            if (node is StatementTree && node.kind !in MODELLED_STATEMENTS) return unmodelled(node, operands(node, ctx), ctx)
            return super.scan(node, ctx)
        }

        /** Records the gap and keeps its operands, so nothing it was built from is lost. */
        private fun unmodelled(node: Tree, inputs: List<Val>, ctx: ProcessorContext): Val =
            emit(Unmodelled(node.kind.toString(), inputs, ctx.location(node)))

        /**
         * The values a construct with no visitor is built out of, so they still reach what it
         * produces.
         *
         * Applied with `accept` rather than `scan`, so the construct's own children are seen rather
         * than the construct itself coming straight back here. A child that is an expression is
         * evaluated; anything else is descended into, since a value can sit under a child that is
         * not one. A type name is not a value and contributes nothing.
         */
        private fun operands(node: Tree, ctx: ProcessorContext): List<Val> {
            val collector = Operands(this)
            node.accept(collector, ctx)
            return collector.values
        }

        private class Operands(private val outer: Body) : TreeScanner<Unit, ProcessorContext>() {
            val values = ArrayList<Val>()

            override fun scan(tree: Tree?, ctx: ProcessorContext) {
                if (tree == null || tree.kind in TYPE_KINDS) return
                if (tree !is ExpressionTree) return super.scan(tree, ctx)
                if (outer.isTypeName(tree)) return
                values.add(outer.evaluate(tree, ctx))
            }
        }
    }

    companion object {
        /** Expression kinds with a visitor above, or - for PARENTHESIZED - genuinely transparent. */
        private val MODELLED_EXPRESSIONS = setOf(
            Tree.Kind.PLUS, Tree.Kind.MINUS, Tree.Kind.MULTIPLY, Tree.Kind.DIVIDE, Tree.Kind.REMAINDER,
            Tree.Kind.EQUAL_TO, Tree.Kind.NOT_EQUAL_TO, Tree.Kind.LESS_THAN, Tree.Kind.GREATER_THAN,
            Tree.Kind.LESS_THAN_EQUAL, Tree.Kind.GREATER_THAN_EQUAL,
            Tree.Kind.CONDITIONAL_AND, Tree.Kind.CONDITIONAL_OR,
            Tree.Kind.AND, Tree.Kind.OR, Tree.Kind.XOR,
            Tree.Kind.LEFT_SHIFT, Tree.Kind.RIGHT_SHIFT, Tree.Kind.UNSIGNED_RIGHT_SHIFT,
            Tree.Kind.UNARY_MINUS, Tree.Kind.UNARY_PLUS,
            Tree.Kind.LOGICAL_COMPLEMENT, Tree.Kind.BITWISE_COMPLEMENT,
            Tree.Kind.PREFIX_INCREMENT, Tree.Kind.PREFIX_DECREMENT,
            Tree.Kind.POSTFIX_INCREMENT, Tree.Kind.POSTFIX_DECREMENT,
            Tree.Kind.PLUS_ASSIGNMENT, Tree.Kind.MINUS_ASSIGNMENT, Tree.Kind.MULTIPLY_ASSIGNMENT,
            Tree.Kind.DIVIDE_ASSIGNMENT, Tree.Kind.REMAINDER_ASSIGNMENT,
            Tree.Kind.AND_ASSIGNMENT, Tree.Kind.OR_ASSIGNMENT, Tree.Kind.XOR_ASSIGNMENT,
            Tree.Kind.LEFT_SHIFT_ASSIGNMENT, Tree.Kind.RIGHT_SHIFT_ASSIGNMENT,
            Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
            Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL, Tree.Kind.FLOAT_LITERAL,
            Tree.Kind.DOUBLE_LITERAL, Tree.Kind.BOOLEAN_LITERAL, Tree.Kind.CHAR_LITERAL,
            Tree.Kind.STRING_LITERAL, Tree.Kind.NULL_LITERAL,
            Tree.Kind.IDENTIFIER, Tree.Kind.MEMBER_SELECT, Tree.Kind.METHOD_INVOCATION,
            Tree.Kind.ASSIGNMENT, Tree.Kind.CONDITIONAL_EXPRESSION, Tree.Kind.PARENTHESIZED,
            Tree.Kind.SWITCH_EXPRESSION, Tree.Kind.NEW_CLASS,
            Tree.Kind.ARRAY_ACCESS, Tree.Kind.INSTANCE_OF, Tree.Kind.LAMBDA_EXPRESSION,
            Tree.Kind.MEMBER_REFERENCE, Tree.Kind.NEW_ARRAY
        )

        /**
         * Statement kinds either handled above or whose children are the whole of their dataflow.
         *
         * A statement produces no value, so scanning through one is not the fabricated edge that
         * doing the same to an expression is. What makes a statement dangerous is *binding a name*,
         * *reading a value* that nothing then notices, or *containing code that does not run here* -
         * so those have visitors, and this set is what is left. Control flow is deliberately in it
         * and deliberately approximate: an `if` lowers to both arms in sequence, which §1 replaces
         * with a join.
         */
        private val MODELLED_STATEMENTS = setOf(
            Tree.Kind.BLOCK, Tree.Kind.EXPRESSION_STATEMENT, Tree.Kind.VARIABLE, Tree.Kind.RETURN,
            Tree.Kind.IF, Tree.Kind.WHILE_LOOP, Tree.Kind.DO_WHILE_LOOP, Tree.Kind.FOR_LOOP,
            Tree.Kind.ENHANCED_FOR_LOOP, Tree.Kind.SWITCH, Tree.Kind.TRY, Tree.Kind.THROW,
            Tree.Kind.SYNCHRONIZED, Tree.Kind.LABELED_STATEMENT, Tree.Kind.BREAK,
            Tree.Kind.CONTINUE, Tree.Kind.EMPTY_STATEMENT, Tree.Kind.ASSERT, Tree.Kind.YIELD,
            Tree.Kind.CLASS, Tree.Kind.INTERFACE, Tree.Kind.ENUM, Tree.Kind.RECORD,
            Tree.Kind.ANNOTATION_TYPE
        )

        /**
         * Kinds that spell a type rather than produce a value: the `int` of `(int) x`.
         *
         * javac makes several of these subclasses of its expression type, so `is ExpressionTree`
         * says yes and they arrive at the gate looking like values. Emitted as one, `int` becomes an
         * instruction that nothing in the program corresponds to - the fabricated value the gate
         * exists to prevent, arriving through the gate itself.
         */
        private val TYPE_KINDS = setOf(
            Tree.Kind.PRIMITIVE_TYPE, Tree.Kind.ARRAY_TYPE, Tree.Kind.PARAMETERIZED_TYPE,
            Tree.Kind.ANNOTATED_TYPE, Tree.Kind.UNION_TYPE, Tree.Kind.INTERSECTION_TYPE,
            Tree.Kind.EXTENDS_WILDCARD, Tree.Kind.SUPER_WILDCARD, Tree.Kind.UNBOUNDED_WILDCARD
        )
    }
}

/**
 * The name a write goes under, for an lhs that is more than a bare identifier.
 *
 * `counter.count = 3` writes `count`, not `counter.count`: the object it lives on is a separate
 * question, answered by the receiver rather than by the name.
 */
fun lastName(tree: Tree): String = when (tree) {
    is IdentifierTree -> tree.name.toString()
    is MemberSelectTree -> tree.identifier.toString()
    is ArrayAccessTree -> lastName(tree.expression)
    else -> tree.toString()
}

fun unaryOperatorLabel(node: UnaryTree): String = when (node.kind) {
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

/**
 * Label shown on the operator, mapped here because some symbols are Mermaid syntax.
 *
 * `/` opens a parallelogram node, `|` delimits an edge label and `&` separates nodes, so the raw
 * symbol corrupts the document rather than just looking odd. §8 of `docs/if-written-again.md` wants
 * this decision moved into the renderers, with the instruction carrying a semantic operator
 * instead; until then it lives with the lowering rather than being duplicated per exporter.
 */
fun binaryOperatorLabel(node: BinaryTree): String = when (node.kind) {
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

fun compoundAssignmentLabel(node: CompoundAssignmentTree): String = when (node.kind) {
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
