package codeflow.ir

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import codeflow.graph.Method
import codeflow.graph.PosStack
import codeflow.java.ids.JNodeId
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier

/**
 * The graph, built by walking instructions rather than javac trees.
 *
 * Everything needing a tree has already happened by the time an instruction exists - which
 * declaration a name resolved to, which overload a call selected, whether a value is a primitive -
 * so what is left here is the part the tree walker this replaced did alongside all of that:
 * which box, which object, and how many times each is drawn.
 *
 * A body is lowered once and instantiated per call site. That is the split the IR exists for, and
 * it is why the four satellite scanners are not needed: a second question about the same call site
 * is a second look at the same instruction list, not a second walk of the same trees.
 */
class IrGraphBuilder(val globalCtx: GlobalContext) {

    private val lowering = Lowering(globalCtx.symbols)

    /** Lowered once per method, however many call sites reach it. */
    private val bodies = HashMap<Element, MethodBody>()
    private val initializers = HashMap<Element, List<Insn>>()
    private val enumConstants = HashMap<Element, List<Insn>?>()

    fun bodyOf(method: Method): MethodBody = bodies.getOrPut(method.element) { lowering.lower(method) }

    /** What [classElement] assigns outside any method body, or nothing for a class from outside. */
    fun initializersOf(classElement: Element?): List<Insn> {
        val declaringClass = globalCtx.findClass(classElement) ?: return emptyList()
        return initializers.getOrPut(classElement!!) { lowering.lowerInitializers(declaringClass) }
    }

    /** `SMALL(3)`, or null for an enum outside the sources or a constant with no constructor. */
    fun enumConstantOf(element: Element): List<Insn>? {
        val declaringClass = globalCtx.findClass(element.enclosingElement) ?: return null
        return enumConstants.getOrPut(element) { lowering.lowerEnumConstant(declaringClass, element) }
    }

    fun build(root: Method): GraphBuilderBlock {
        val block = GraphBuilderBlock(null, root, PosStack(), null, root.ctx)
        Frame(this, block, PosStack(), null, null).invoke(emptyList(), emptyList())
        return block
    }
}

/**
 * One invocation of one method: the block being drawn into, the object it runs on, and the values
 * its instructions have produced so far.
 *
 * A frame per call site rather than per method, because that is what inlining means here - the same
 * instruction list read twice draws two sets of boxes, and the locals in each are different
 * variables.
 */
class Frame(
    private val builder: IrGraphBuilder,
    val block: GraphBuilderBlock,
    private val stack: PosStack,
    private val owner: MemPos?,
    private val parent: Frame?
) {

    private val globalCtx = builder.globalCtx

    /** The node for `this` in this invocation, created on the first mention. See [thisValue]. */
    private var thisValue: Value? = null

    /**
     * What one instruction produced: the node to draw an edge from, and the object that value *is*.
     *
     * The two used to be asked for separately, which is why a call had to be inlined behind a memo -
     * "what did this produce?" and "which object is it?" each ran the whole callee. Here one pass
     * answers both.
     */
    class Value(val node: GraphNode?, val memPos: MemPos?)

    /** The values of one instruction list, indexed by [Val] - see [Insn]. */
    private inner class Run {
        val values = ArrayList<Value?>()

        /** Edges waiting on a value from further down the list - see [Phi.addPath]. */
        val backEdges = ArrayList<Pair<GraphNode, Val>>()

        /** How far the run has got, which is what makes a [Val] a back reference or not. */
        val reached get() = values.size

        fun node(v: Val): GraphNode = values[v.index]?.node
            ?: throw GraphException("Instruction ${v.index} produced no value in ${block.getMethodName()}")

        fun memPos(v: Val): MemPos? = values[v.index]?.memPos

        fun connectBackEdges() = backEdges.forEach { (phi, value) -> node(value).addEdge(phi) }
    }

    /**
     * Runs the method's body with the caller's arguments bound to its parameters.
     *
     * An argument that is an object is bound twice over: its node, so the value is traceable, and
     * its memory position, so it is the *same object* inside. The positions go on before the body
     * runs, because the body is what asks for them.
     */
    fun invoke(arguments: List<GraphNode>, argumentMemPositions: List<MemPos?>) {
        block.parameterNodes.zip(argumentMemPositions).forEach { (parameter, memPos) ->
            memPos?.let { globalCtx.addMemPos(parameter.id, it) }
        }
        val method = block.method
        if (method.element.kind == ElementKind.CONSTRUCTOR && !delegatesToSameClass(method)) {
            execute(builder.initializersOf(method.element.enclosingElement))
        }
        execute(builder.bodyOf(method).instructions)
        block.connectParameters(arguments)
    }

    /**
     * Whether the constructor starts with `this(...)`, which is the one case where the initializers
     * do not run here: the constructor delegated to runs them, and running them at both ends of the
     * chain draws every initializer twice.
     *
     * `super(...)` is not this case - the superclass constructor runs the superclass's initializers,
     * which are a different set of fields. Which one it is comes from the class the delegate's
     * target belongs to rather than from the word written, since attribution resolves both spellings
     * to the constructor they reach.
     */
    private fun delegatesToSameClass(method: Method): Boolean =
        builder.bodyOf(method).instructions
            .any { it is Delegate && it.target.enclosingElement == method.element.enclosingElement }

    private fun execute(instructions: List<Insn>): Run {
        val run = Run()
        instructions.forEach { run.values.add(draw(it, run)) }
        run.connectBackEdges()
        return run
    }

    private fun draw(insn: Insn, run: Run): Value? = when (insn) {
        is Const -> Value(block.addLiteral(base(labelId(insn.text, insn), insn)), null)

        is Param -> block.parameterNodes[insn.index].let { Value(it, globalCtx.findMemPos(it.id)) }

        is WriteLocal -> write(insn.name, insn.element, insn.isPrimitive, owner, run.memPos(insn.value), insn, run.node(insn.value))

        is ReadField -> readField(insn, run)

        is WriteField -> {
            val holder = holderOf(insn.receiver, insn.element, insn, run)
            write(insn.name, insn.element, insn.isPrimitive, holder, run.memPos(insn.value), insn, run.node(insn.value))
        }

        is ThisRef -> thisValue(insn)

        is Phi -> phi(insn, run)

        is Bind -> bind(insn, run)

        is BinOp -> Value(
            block.addBinOp(base(labelId(insn.label, insn), insn), run.node(insn.left), run.node(insn.right)),
            null
        )

        is UnOp -> Value(block.addUnaryOp(base(labelId(insn.label, insn), insn), run.node(insn.operand)), null)

        is Select -> Value(
            block.addSelection(base(labelId(insn.label, insn), insn), insn.inputs.map { run.node(it) }),
            null
        )

        is Call -> call(insn, run)

        is Delegate -> delegate(insn, run)

        is New -> construct(insn.typeName, insn.constructor, insn.args.map { run.node(it) },
            insn.args.map { run.memPos(it) }, null, insn)

        is Opaque -> Value(
            block.addExternal(base(labelId(insn.label, insn), insn), insn.inputs.map { run.node(it) }),
            null
        )

        is Unmodelled -> {
            globalCtx.recordUnmodelled("${insn.kind} at ${insn.source}")
            Value(
                block.addUnmodelled(base(labelId(insn.kind, insn), insn), insn.inputs.map { run.node(it) }),
                null
            )
        }

        is Return -> {
            insn.value?.let { block.addReturnNode(run.node(it), run.memPos(it)) }
            null
        }
    }

    /**
     * The object an access happens on.
     *
     * A static field is held by its class whichever way it is written, so that is asked first: the
     * class in front of the dot is a type name and has no object of its own to offer, and an
     * unqualified static inside its own class has no `this` to fall back on when the method is
     * static too.
     */
    private fun holderOf(receiver: Receiver, element: Element?, insn: Insn, run: Run): MemPos? {
        globalCtx.staticHolder(element, insn.source)?.let { return it }
        return when (receiver) {
            Receiver.Enclosing -> owner
            Receiver.TypeName -> null
            is Receiver.Value -> run.memPos(receiver.value)
        }
    }

    /**
     * A field read, which finds the object first and the field on it second.
     *
     * With no object the receiver is something from outside the analysed sources - the `System` of
     * `System.out`, or an enum we do not have - and the value it selects is opaque rather than
     * missing. That is only true of a receiver somebody *wrote*: an unqualified name with no
     * enclosing object is this method's own field, which has a default, and it takes [unassigned].
     */
    private fun readField(insn: ReadField, run: Run): Value {
        enumConstant(insn)?.let { return it }
        val holder = holderOf(insn.receiver, insn.element, insn, run)
        val written = insn.receiver != Receiver.Enclosing
        return read(insn.name, insn.element, insn.isPrimitive, holder, written, insn)
    }

    private fun read(
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        holder: MemPos?,
        written: Boolean,
        insn: Insn
    ): Value {
        val id = JNodeId(stack, name, element, holder)
        val node = holder?.getNode(id)
            ?: block.getVariable(id)?.lastNode
            ?: if (holder == null && written) {
                block.addExternal(base(id, insn), emptyList())
            } else {
                unassigned(id, name, element, isPrimitive, holder, insn)
            }
        return Value(node, globalCtx.findMemPos(id))
    }

    /**
     * A write, which is a new box with the value flowing into it.
     *
     * A new one every time: `y = 1; y = y + 1` is two boxes, and only the lookup key is shared. An
     * object write also records what the name now points at, so a later read through it finds the
     * right fields - and gets a position of its own when the value has none, so that a field set on
     * an object from outside still has somewhere to hang.
     */
    private fun write(
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        holder: MemPos?,
        valueMemPos: MemPos?,
        insn: Insn,
        valueNode: GraphNode
    ): Value {
        val id = JNodeId(stack, name, element, holder)
        val node = if (isPrimitive) {
            block.addPrimitiveVariable(base(id, insn), holder)
        } else {
            block.addObjectVariable(base(id, insn), holder)
        }
        block.addAssignment(node, valueNode)
        if (isPrimitive) return Value(node, null)
        val memPos = valueMemPos ?: globalCtx.createMemPos(insn.source)
        globalCtx.addMemPos(id, memPos)
        return Value(node, memPos)
    }

    /**
     * A name bound by something other than a declaration - see [Bind].
     *
     * Which object it stands for is the instruction's to say, not this one's: a pattern names what
     * it matched, a loop element is not the collection it came out of, and a lambda's parameter is
     * filled in by a caller who is not here.
     */
    private fun bind(insn: Bind, run: Run): Value {
        val id = JNodeId(stack, insn.name, insn.element, owner)
        val node = if (insn.isPrimitive) {
            block.addPrimitiveVariable(base(id, insn), owner)
        } else {
            block.addObjectVariable(base(id, insn), owner)
        }
        insn.value?.let { block.addAssignment(node, run.node(it)) }
        val memPos = when (insn.identity) {
            Identity.OfValue -> insn.value?.let { run.memPos(it) }
            Identity.Fresh -> globalCtx.createMemPos(insn.source).takeUnless { insn.isPrimitive }
            Identity.Unknown -> null
        }
        memPos?.let { globalCtx.addMemPos(id, it) }
        return Value(node, memPos)
    }

    /**
     * A variable where two paths meet, as one box taking the value from each - see [Phi].
     *
     * Keyed by occurrence rather than by declaration, because it is not a place anything looks up:
     * a use below the join was resolved to this instruction while the method was being lowered, so
     * what the box has to be is the merge itself, at the line of the `if` that caused it.
     *
     * Which object it is is the first path that names one, on the same terms as a method with two
     * returns: control flow says which, nothing here knows which way it went, and an object is
     * better tracked through one of its two possibilities than through neither.
     */
    private fun phi(insn: Phi, run: Run): Value {
        val (arrived, pending) = insn.inputs.partition { it.index < run.reached }
        val id = labelId(insn.name, insn)
        val node = block.addPhi(base(id, insn), arrived.map { run.node(it) }, insn.isPrimitive)
        pending.forEach { run.backEdges.add(node to it) }
        return Value(node, arrived.firstNotNullOfOrNull { run.memPos(it) })
    }

    /**
     * `this`, the object the method is running on - a value in its own right, as in `return this`.
     *
     * Held per frame because a frame is one invocation: `this` is one object however many times the
     * method mentions it. It cannot be keyed like a variable instead, because a method reached
     * through a receiver nobody could track has no owner, and every such `this` in the run would key
     * alike and be drawn as one object.
     */
    private fun thisValue(insn: ThisRef): Value = thisValue ?: run {
        val instance = owner ?: globalCtx.createMemPos(insn.source)
        val id = JNodeId(stack, "this", null, instance)
        val node = block.addObjectVariable(base(id, insn), instance)
        globalCtx.addMemPos(id, instance)
        Value(node, instance).also { thisValue = it }
    }

    /**
     * `Size.SMALL`, when the declaration `SMALL(3)` runs a constructor these sources contain.
     *
     * A bare constant is opaque - the declaration is the value, and [unassigned] draws that. With a
     * constructor there is a story: arguments go in and fields come out, and skipping it leaves
     * `SMALL.units()` inlined against an object with no fields, returning a value from nowhere.
     *
     * The object is the constant's and is held for the whole run, since one constant is one
     * instance. The constructor is inlined per mention like any other call, writing the same values
     * to the same position each time.
     */
    private fun enumConstant(insn: ReadField): Value? {
        val element = insn.element ?: return null
        if (element.kind != ElementKind.ENUM_CONSTANT) return null
        val instructions = builder.enumConstantOf(element) ?: return null
        val creation = instructions.lastOrNull() as? New ?: return null
        globalCtx.findMethod(creation.constructor) ?: return null
        val run = execute(instructions.dropLast(1))
        return construct(
            creation.typeName,
            creation.constructor,
            creation.args.map { run.node(it) },
            creation.args.map { run.memPos(it) },
            globalCtx.enumConstantMemPos(element, creation.source),
            creation
        )
    }

    /**
     * A call, inlined when there is a body to inline and opaque when there is not.
     *
     * Opaque means the arguments and the receiver flow in and the result flows out, which keeps a
     * value traceable across the call rather than ending the analysis there - what matters for real
     * code, where almost every method eventually reaches the standard library.
     */
    private fun call(insn: Call, run: Run): Value {
        val method = globalCtx.findMethod(insn.target)
        if (method == null || isBeingInlined(method)) {
            val receiverNode = (insn.receiver as? Receiver.Value)?.let { run.node(it.value) }
            val inputs = listOfNotNull(receiverNode) + insn.args.map { run.node(it) }
            return Value(block.addExternal(base(labelId(insn.name, insn), insn), inputs), null)
        }
        val receiverMemPos = when (insn.receiver) {
            // A static method runs on no object at all, so it has nothing to inherit.
            Receiver.Enclosing -> owner.takeIf { Modifier.STATIC !in method.element.modifiers }
            Receiver.TypeName -> null
            is Receiver.Value -> run.memPos(insn.receiver.value)
        }
        val child = enter(method, receiverMemPos, insn)
        child.invoke(insn.args.map { run.node(it) }, insn.args.map { run.memPos(it) })
        return Value(child.block.returnNode, child.block.returnedMemPos)
    }

    /**
     * Whether this method is already open further up the chain of call sites being inlined.
     *
     * Inlining is per call site with no depth limit, so a method that reaches itself - directly, or
     * around a cycle of any length - has nothing to stop it, and `fact(n - 1)` took the whole run
     * down with a StackOverflowError and no output at all. Compared by the declaration javac
     * resolved, since two invocations of one method are two lookups of the same one.
     */
    private fun isBeingInlined(method: Method): Boolean =
        block.method.element == method.element || parent?.isBeingInlined(method) == true

    /**
     * `super(...)` or `this(...)`, which initialises the object already being built.
     *
     * Unlike a `new` no instance is created: the delegate runs against this frame's own object,
     * which is what lets an inherited field assigned in the superclass constructor be read from the
     * subclass. A constructor outside the analysed sources has no body to inline and, since the
     * delegation is a statement, no value for anything to read, so it contributes nothing.
     */
    private fun delegate(insn: Delegate, run: Run): Value? {
        val constructor = globalCtx.findMethod(insn.target) ?: return null
        val child = enter(constructor, owner, insn)
        child.invoke(insn.args.map { run.node(it) }, insn.args.map { run.memPos(it) })
        return Value(child.block.returnNode, null)
    }

    /**
     * `new X(...)`, which both creates an object and produces it as a value.
     *
     * A constructor in the analysed sources has a body to inline, and its return node is what the
     * expression produces, exactly as for any other call. A class from outside - or one whose only
     * constructor is the one attribution inserted, which is not source anybody wrote - has no body,
     * so the object is opaque and the arguments flow into it.
     *
     * Such a class can still declare field initializers, and those are code somebody wrote: they run
     * on every `new`, and skipping them leaves every field of the class reading as one nothing has
     * assigned. They are drawn in this block, at the `new` that runs them, because there is no
     * constructor here to nest them in - and they run on the object being built rather than on this
     * method's own, which is why they need a frame of their own.
     */
    private fun construct(
        typeName: String,
        constructorElement: Element?,
        arguments: List<GraphNode>,
        argumentMemPositions: List<MemPos?>,
        into: MemPos?,
        insn: Insn
    ): Value {
        val created = into ?: globalCtx.createMemPos(insn.source)
        val constructor = globalCtx.findMethod(constructorElement)
        if (constructor == null) {
            Frame(builder, block, stack.push(insn.source), created, this)
                .execute(builder.initializersOf(constructorElement?.enclosingElement))
            return Value(block.addExternal(base(labelId(typeName, insn), insn), arguments), created)
        }
        val child = enter(constructor, created, insn)
        child.invoke(arguments, argumentMemPositions)
        return Value(child.block.returnNode, created)
    }

    /** The frame a call site opens: a block nested in this one, on the object the callee runs on. */
    private fun enter(method: Method, memPos: MemPos?, insn: Insn): Frame {
        val childStack = stack.push(insn.source)
        val childBlock = GraphBuilderBlock(block, method, childStack, memPos, method.ctx)
        block.addCalledMethod(childBlock)
        return Frame(builder, childBlock, childStack, memPos, this)
    }

    /**
     * The node for a name being tracked that has no value yet.
     *
     * For a field that is not a gap in the analysis, it is the program: a field nothing has assigned
     * holds its default, and reading one is ordinary Java. So does an enum constant, whose
     * declaration is its value.
     *
     * Only fields reach here now - a use of a local names the instruction that defined it, so the
     * lowering is where that failure is raised and where the position in it comes from. What is left
     * is a name javac resolved to something that is not a field and not a constant, which is the
     * analysis having lost it, and that still fails loudly with a file and a line.
     */
    private fun unassigned(
        id: JNodeId,
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        holder: MemPos?,
        insn: Insn
    ): GraphNode {
        if (element?.kind == ElementKind.ENUM_CONSTANT) return block.addExternal(base(id, insn), emptyList())
        if (element?.kind != ElementKind.FIELD) {
            throw GraphException("'$name' at ${insn.source} has no value in ${block.getMethodName()}")
        }
        return if (isPrimitive) {
            block.addPrimitiveVariable(base(id, insn), holder)
        } else {
            block.addObjectVariable(base(id, insn), holder)
        }
    }

    /** A node that stands for an occurrence rather than for a variable: an operator, a literal. */
    private fun labelId(label: String, insn: Insn) = GraphNodeId(stack.push(insn.source), label)

    private fun base(id: GraphNodeId, insn: Insn) = GraphNode.Base(id, insn.source)
}
