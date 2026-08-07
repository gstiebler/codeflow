package codeflow.ir

import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.java.Symbols
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import javax.lang.model.element.ElementKind

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
 * `AstBlockProcessor` hard to reason about was doing all of those at once, which is why four
 * satellite scanners existed to re-walk the same tree asking a different question, and why one of
 * them had to call back into the builder behind a memo so that *asking* did not *inline*.
 *
 * The value each expression produces is returned as a [Val], which is an index into the list being
 * built. So an instruction never refers to a tree, only to earlier instructions.
 */
class Lowering(private val symbols: Symbols) {

    fun lower(method: Method): MethodBody {
        val body = Body(symbols, method.ctx)
        method.name.body?.accept(body, method.ctx)
        return MethodBody(method, body.instructions)
    }

    private class Body(
        private val symbols: Symbols,
        private val ctx: ProcessorContext
    ) : TreeScanner<Val, ProcessorContext>() {

        val instructions = ArrayList<Insn>()

        private fun emit(insn: Insn): Val {
            instructions.add(insn)
            return Val(instructions.size - 1)
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
                return emit(ReadField(Receiver.Enclosing, node.name.toString(), element, ctx.location(node)))
            }
            return emit(ReadLocal(node.name.toString(), element, ctx.location(node)))
        }

        override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): Val =
            emit(
                ReadField(
                    receiverOf(node.expression, ctx),
                    node.identifier.toString(),
                    symbols.element(node),
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
            return emit(WriteLocal(node.name.toString(), symbols.element(node), value, ctx.location(node)))
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
            if (element?.kind?.isField == true) {
                return emit(WriteField(receiver, lastName(target), element, value, ctx.location(target)))
            }
            return emit(WriteLocal(lastName(target), element, value, ctx.location(target)))
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

        override fun visitReturn(node: ReturnTree, ctx: ProcessorContext): Val =
            emit(Return(node.expression?.let { evaluate(it, ctx) }, ctx.location(node)))

        override fun visitBlock(node: BlockTree, ctx: ProcessorContext): Val? {
            node.statements.forEach { scan(it, ctx) }
            return null
        }
    }
}

/**
 * Label shown on the operator, mapped here because some symbols are Mermaid syntax.
 *
 * `/` opens a parallelogram node, `|` delimits an edge label and `&` separates nodes, so the raw
 * symbol corrupts the document rather than just looking odd. §8 of `docs/if-written-again.md` wants
 * this decision moved into the renderers, with the instruction carrying a semantic operator
 * instead; until then it lives with the lowering rather than being duplicated per exporter.
 */
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
