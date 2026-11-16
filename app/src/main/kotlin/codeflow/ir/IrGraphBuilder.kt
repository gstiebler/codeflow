package codeflow.ir

import codeflow.graph.EdgeKind
import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import codeflow.graph.Method
import codeflow.graph.NodeType
import codeflow.graph.PosStack
import codeflow.java.ids.JNodeId
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
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
 *
 * One thing is held across instructions rather than named by them: what each field holds. A local
 * is joined by [Lowering] with a [Phi]; a field is heap state resolved per call site, so the
 * builder keeps a journal of field writes while a [FieldFork] is open and merges them at the
 * [FieldJoin] - see [journal]. Without it the last write in the flat list wins and an `if`'s other
 * arm is drawn as a dead end. Known gaps, on `docs/decisions/joins.md`: no back edge for a field
 * in a loop body, `?:`/`&&`/`||` arms are not forked, and a write through a receiver with several
 * holders is a strong update on each.
 */
class IrGraphBuilder(val globalCtx: GlobalContext) {

    private val lowering = Lowering(globalCtx.symbols, globalCtx::isBoundary)

    /** Which objects a name can point at; no nodes in it. */
    val heap = Heap(globalCtx.symbols)

    /**
     * The nodes each object's fields currently hold, by the name they are looked up under.
     * In `x.memberX = 5;` a node is created for memberX on every object `x` could be.
     */
    private val fields = HashMap<Int, HashMap<GraphNodeId, GraphNode>>()

    /** Files [node] as a field of each of [owners], and hands it back. */
    fun record(node: GraphNode, owners: Set<MemPos>): GraphNode {
        owners.forEach { fields.getOrPut(it.id) { HashMap() }[node.id] = node }
        return node
    }

    fun fieldNode(owner: MemPos, id: GraphNodeId): GraphNode? = fields[owner.id]?.get(id)

    /**
     * Field writes since each open [FieldFork] began, with what each key held before - see
     * [FieldFork].
     *
     * Only *fields* are journaled: a local or a [Bind] also goes through [record], but [Lowering]
     * already joins those with a [Phi], and undoing or merging them here would draw the join twice.
     * One list for the whole builder, because a callee runs nested inside the call that reached it,
     * so its forks open and close inside the caller's and nest correctly with nothing more.
     */
    private val journal = ArrayList<Pair<FieldKey, FieldState?>>()
    private val openForks = ArrayList<OpenFork>()

    /** Whether a field write has to be journaled, which is only while some fork is open. */
    val forking: Boolean get() = openForks.isNotEmpty()

    /**
     * What a field holds right now, everything a read of it would consult: the box, and the objects
     * the value could be. Null when nothing has written (or read) it.
     */
    fun fieldState(key: FieldKey): FieldState? {
        val node = if (key.owner != null) fields[key.owner]?.get(key.id) else key.block!!.getVariable(key.id)?.lastNode
        return node?.let { FieldState(it, if (key.isPrimitive) emptySet() else heap.objectsOf(key.id)) }
    }

    /** Puts a field back to [state], or to holding nothing when it is null. Not journaled. */
    private fun restore(key: FieldKey, state: FieldState?) {
        if (key.owner != null) {
            if (state == null) fields[key.owner]?.remove(key.id) else fields.getOrPut(key.owner) { HashMap() }[key.id] = state.node
        } else {
            key.block!!.rebindVariable(key.id, state?.node)
        }
        if (!key.isPrimitive) heap.setObjects(key.id, state?.objects ?: emptySet())
    }

    /**
     * Sets a field to [state] as a write would, so that it is undone with the path that made it.
     *
     * [before] is what the field held, for the caller that drew a box registering itself as the
     * field's last node on the way: asking again would find that box, and undoing would put it back.
     */
    fun setFieldState(key: FieldKey, state: FieldState, before: FieldState? = fieldState(key)) {
        if (before != null && before.node === state.node && before.objects == state.objects) return
        if (forking) journal.add(key to before)
        restore(key, state)
    }

    /** Notes a field write just made, with what the field held before it. */
    fun journalWrite(key: FieldKey, before: FieldState?) {
        journal.add(key to before)
    }

    fun openFork(): OpenFork = OpenFork(journal.size).also { openForks.add(it) }

    /**
     * One path of [fork] has ended: set aside what its writes left, and put the fields back.
     *
     * [block] is the block the fork is being run in. A field with no holder is looked up through the
     * block chain, and a write from a callee went to the callee's block - which nothing outside it
     * can read - so those are undone like the rest and then dropped rather than merged.
     */
    fun endArm(fork: OpenFork, block: GraphBuilderBlock) {
        val left = LinkedHashMap<FieldKey, FieldState>()
        for (index in fork.mark until journal.size) {
            val key = journal[index].first
            if (key in left || (key.block != null && key.block !== block)) continue
            fieldState(key)?.let { left[key] = it }
        }
        for (index in journal.size - 1 downTo fork.mark) {
            val (key, before) = journal[index]
            restore(key, before)
            journal.removeAt(index)
        }
        fork.arms.add(left)
    }

    /**
     * Every field any of [arms] wrote, with each distinct box that can arrive for it.
     *
     * Read before the fork is closed and the result applied after, so that what is written back is
     * journaled into the fork enclosing this one, if there is one. A path that did not write a field
     * arrives with what the fork held; so does the fork itself, when [withEntry]. A field nothing
     * held yet arrives only from the paths that wrote it.
     */
    fun mergeFields(fork: OpenFork, arms: List<Int>, withEntry: Boolean, armNames: List<String>): List<FieldMerge> {
        val keys = LinkedHashSet<FieldKey>()
        arms.forEach { keys.addAll(fork.arms[it].keys) }
        return keys.map { key ->
            val entry = fieldState(key)
            val arrived = ArrayList<Pair<FieldState, String?>>()
            arms.forEachIndexed { position, arm ->
                (fork.arms[arm][key] ?: entry)?.let { arrived.add(it to armNames.getOrNull(position)) }
            }
            if (withEntry && entry != null) arrived.add(entry to null)
            val byBox = arrived.groupBy { it.first.node }
            FieldMerge(key, byBox.values.map { paths ->
                // The same rule as for locals: a value that came down exactly one named path is that
                // arm's, and one that came down several is neither.
                FieldMerge.Reaching(paths.first().first, paths.map { it.second }.singleOrNull())
            })
        }
    }

    fun closeFork(fork: OpenFork) {
        check(openForks.lastOrNull() === fork) { "fields forked and joined out of order" }
        openForks.removeAt(openForks.size - 1)
    }

    /** Every static field's current node, as if nothing had been written before - see [forgetStatics]. */
    private fun staticWrites(): Map<GraphNodeId, GraphNode> =
        heap.staticHolders().flatMap { fields[it.id]?.entries ?: emptySet() }.associate { it.key to it.value }

    /** Static fields back to holding nothing, so that the next entry point is built as if it ran alone. */
    private fun forgetStatics() = heap.staticHolders().forEach { fields[it.id]?.clear() }

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

    /** The reads of a static field, in the entry point being built, that found nothing written. */
    val unwrittenStaticReads = ArrayList<Pair<GraphNodeId, GraphNode>>()

    /**
     * One root per entry point, each built as if it ran alone and then tied together.
     *
     * Entry points can run in any order, so none is built on top of another's writes: static
     * fields are emptied between them. What each leaves in a static field at its end, and what each
     * reads of one before writing it, are recorded, and afterwards every read gets an edge from
     * every *other* entry point's write of that field - [EdgeKind.SHARED]. Which was named first
     * decides nothing.
     */
    fun build(roots: List<Method>): List<GraphBuilderBlock> {
        class Built(val block: GraphBuilderBlock, val writes: Map<GraphNodeId, GraphNode>, val reads: List<Pair<GraphNodeId, GraphNode>>)

        var nextSerial = 0
        val built = roots.map { root ->
            forgetStatics()
            unwrittenStaticReads.clear()
            val block = build(root, nextSerial)
            nextSerial = block.serialsUsed()
            val reads = unwrittenStaticReads.toList()
            // A read that found nothing leaves its own box in the field; it is not a write.
            val readNodes = reads.map { it.second }.toSet()
            Built(block, staticWrites().filterValues { it !in readNodes }, reads)
        }
        for (reader in built) for (writer in built) {
            if (reader === writer) continue
            for ((id, read) in reader.reads) writer.writes[id]?.addEdge(read, EdgeKind.SHARED)
        }
        return built.map { it.block }
    }

    fun build(root: Method, firstSerial: Int = 0): GraphBuilderBlock {
        journal.clear()
        openForks.clear()
        val block = GraphBuilderBlock(
            null, root, PosStack(), emptySet(), root.ctx, root.ctx.location(root.name), globalCtx.isBoundary(root), firstSerial
        )
        Frame(this, block, PosStack(), emptySet(), null).invoke(emptyList(), emptyList())
        startFromEvent(block)
        return block
    }

    /**
     * The root's event feeds every node drawn under it - its block and every block inlined below -
     * that nothing else feeds: [EdgeKind.EVENT]. Decided by incoming edges, never by label or type,
     * and before [build] adds the [EdgeKind.SHARED] edges, since "nothing feeds it" is judged within
     * this entry point - the other one may not have run.
     */
    private fun startFromEvent(root: GraphBuilderBlock) {
        val event = root.event!!
        fun blocksUnder(block: GraphBuilderBlock): List<GraphBuilderBlock> = listOf(block) + block.calledMethods.flatMap { blocksUnder(it) }
        val nodes = blocksUnder(root).flatMap { it.graph.getNodes() }
        val fed = nodes.flatMap { node -> node.edgesIterator().asSequence().map { it.target }.toList() }.toSet()
        nodes.filter { it != event && it !in fed }.forEach { event.addEdge(it, EdgeKind.EVENT) }
    }

    /**
     * Every instruction this run drew from, whichever list it came out of.
     *
     * The three maps above are memoised on being *asked for*, so between them they are already the
     * record of what the drawing read - not what the corpus contains. That is the distinction a
     * check for boxes drawn from nowhere needs: a method nothing reaches is not in here, and neither
     * are the initializers of a class nothing constructs, so a node at one of their positions is a
     * node from a list this run never ran.
     */
    fun drawnInstructions(): List<Insn> = bodies.values.flatMap { it.instructions } +
            initializers.values.flatten() +
            enumConstants.values.filterNotNull().flatten()
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
    /**
     * The objects this invocation could be running on - `this`, as a set rather than one object.
     *
     * A call made through a name that a join left pointing at either of two instances runs on
     * either, and the method is still inlined once: which body runs was settled statically from the
     * call's target, not from the receiver, so a set here multiplies the objects a field access
     * reaches and nothing else.
     */
    private val owner: Set<MemPos>,
    private val parent: Frame?
) {

    private val globalCtx = builder.globalCtx

    /**
     * What this run's boundary-order token is captioned with: the name of the root the whole chain
     * of frames was opened from, not of the method this one runs - an inlined callee's token is
     * still that entry point's chain. See [boundaryOrderCaption].
     */
    private val orderCaption: String by lazy {
        parent?.orderCaption ?: boundaryOrderCaption(block.method.displayName())
    }

    /** The node for `this` in this invocation, created on the first mention. See [thisValue]. */
    private var thisValue: Value? = null

    /**
     * What one instruction produced: the node to draw an edge from, and the objects that value
     * could *be*.
     *
     * The two used to be asked for separately, which is why a call had to be inlined behind a memo -
     * "what did this produce?" and "which object is it?" each ran the whole callee. Here one pass
     * answers both.
     *
     * A set, because "which object" has no single answer after a branch: `if (c) p = i1; else p =
     * i2;` makes `p` either. Empty for a primitive, and for an object nothing here can track.
     */
    class Value(val node: GraphNode?, val objects: Set<MemPos> = emptySet())

    /** The values of one instruction list, indexed by [Val] - see [Insn]. */
    private inner class Run {
        val values = ArrayList<Value?>()

        /** The forks this list has opened and not yet closed, by the [Val] of their [FieldFork]. */
        val forks = HashMap<Int, OpenFork>()

        /** Edges waiting on a value from further down the list - see [Phi.addPath]. */
        val backEdges = ArrayList<Pair<GraphNode, Val>>()

        /**
         * The [Val]s that are a [ReadBoundaryOrder]: what a call taking one of them takes is the
         * order token, and that edge is [EdgeKind.ORDER] - decided by the instruction that produced
         * the input, not by what its box is called.
         */
        val orderReads = HashSet<Int>()

        /** How far the run has got, which is what makes a [Val] a back reference or not. */
        val reached get() = values.size

        fun node(v: Val): GraphNode = values[v.index]?.node
            ?: throw GraphException("Instruction ${v.index} produced no value in ${block.getMethodName()}")

        fun objects(v: Val): Set<MemPos> = values[v.index]?.objects ?: emptySet()

        fun fork(v: Val): OpenFork = forks[v.index]
            ?: throw GraphException("Instruction ${v.index} is no open fork in ${block.getMethodName()}")

        fun connectBackEdges() = backEdges.forEach { (phi, value) -> node(value).addEdge(phi) }
    }

    /**
     * Runs the method's body with the caller's arguments bound to its parameters.
     *
     * An argument that is an object is bound twice over: its node, so the value is traceable, and
     * its memory position, so it is the *same object* inside. The positions go on before the body
     * runs, because the body is what asks for them.
     */
    fun invoke(arguments: List<GraphNode>, argumentMemPositions: List<Set<MemPos>>) {
        block.parameterNodes.zip(argumentMemPositions).forEach { (parameter, objects) ->
            if (objects.isNotEmpty()) builder.heap.setObjects(parameter.id, objects)
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
        is Const -> Value(block.addLiteral(base(labelId(insn.text, insn), insn)))

        is Param -> block.parameterNodes[insn.index].let { Value(it, builder.heap.objectsOf(it.id)) }

        is WriteLocal -> write(insn.name, insn.element, insn.isPrimitive, owner, run.objects(insn.value), insn, run.node(insn.value))

        is ReadField -> readField(insn, run)

        is WriteField -> {
            val holders = holderOf(insn.receiver, insn.element, insn, run)
            write(
                insn.name, insn.element, insn.isPrimitive, holders, run.objects(insn.value), insn,
                run.node(insn.value), journal = true
            )
        }

        // Drawn before it is added, so the index it is about to take is how far the run has got.
        is ReadBoundaryOrder -> readBoundaryOrder(insn).also { run.orderReads.add(run.reached) }

        // Journaled like a static field's write, so an arm's calls are undone with the arm and the
        // join draws the order each arm left. The call leaving the token is order, not data.
        is WriteBoundaryOrder -> write(
            BOUNDARY_ORDER, null, true, setOf(builder.heap.boundaryOrderHolder()), emptySet(), insn,
            run.node(insn.value), journal = true, kind = EdgeKind.ORDER,
            draw = { block.addBoundaryOrder(base(it.id, insn, orderCaption)) }
        )

        is FieldFork -> {
            run.forks[run.reached] = builder.openFork()
            null
        }

        is FieldArm -> {
            builder.endArm(run.fork(insn.fork), block)
            null
        }

        is FieldJoin -> {
            fieldJoin(insn, run)
            null
        }

        is ThisRef -> thisValue(insn)

        is Phi -> phi(insn, run)

        is Bind -> bind(insn, run)

        is BinOp -> Value(
            block.addBinOp(base(labelId(insn.label, insn), insn), run.node(insn.left), run.node(insn.right))
        )

        is UnOp -> Value(block.addUnaryOp(base(labelId(insn.label, insn), insn), run.node(insn.operand)))

        // The objects come from the alternatives only, not from every input: a ternary can be
        // either arm and never its condition, and an array is not the elements it holds.
        is Select -> Value(
            block.addSelection(
                base(labelId(insn.label, insn), insn),
                insn.inputs.map { run.node(it) to edgeKind(it, insn.condition, insn.arms) }
            ),
            insn.alternatives.flatMapTo(HashSet()) { run.objects(it) }
        )

        is Call -> call(insn, run)

        is Delegate -> delegate(insn, run)

        is New -> construct(insn.typeName, insn.constructor, insn.args.map { run.node(it) },
            insn.args.map { run.objects(it) }, null, insn)

        is Opaque -> Value(
            block.addExternal(base(labelId(insn.label, insn), insn), insn.inputs.map { run.node(it) })
        )

        is Unmodelled -> {
            globalCtx.recordUnmodelled("${insn.kind} at ${insn.source}")
            Value(block.addUnmodelled(base(labelId(insn.kind, insn), insn), insn.inputs.map { run.node(it) }))
        }

        is Return -> {
            insn.value?.let { block.addReturnNode(run.node(it), run.objects(it)) }
            null
        }
    }

    /**
     * The objects an access happens on, which is every object the receiver could be.
     *
     * A static field is held by its class whichever way it is written, so that is asked first: the
     * class in front of the dot is a type name and has no object of its own to offer, and an
     * unqualified static inside its own class has no `this` to fall back on when the method is
     * static too.
     *
     * Empty means no object at all - a type name, or a receiver nothing here can track - which is a
     * different fact from "one object", and the two are read apart below.
     */
    private fun holderOf(receiver: Receiver, element: Element?, insn: Insn, run: Run): Set<MemPos> {
        builder.heap.staticHolder(element, insn.source)?.let { return setOf(it) }
        return when (receiver) {
            Receiver.Enclosing, Receiver.Super -> owner
            Receiver.TypeName -> emptySet()
            is Receiver.Value -> run.objects(receiver.value)
        }
    }

    /**
     * A field read, which finds the objects first and the field on each of them second.
     *
     * With no object the receiver is something from outside the analysed sources - the `System` of
     * `System.out`, or an enum we do not have - and the value it selects is opaque rather than
     * missing. That is only true of a receiver somebody *wrote*: an unqualified name with no
     * enclosing object is this method's own field, which has a default, and it takes [unassigned].
     */
    private fun readField(insn: ReadField, run: Run): Value {
        enumConstant(insn)?.let { return it }
        val holders = holderOf(insn.receiver, insn.element, insn, run)
        // `super.f` and `this.f` are both a receiver somebody wrote, and neither is the unqualified
        // name that falls back to this method's own field.
        val written = insn.receiver != Receiver.Enclosing
        return read(insn.name, insn.element, insn.isPrimitive, holders, written, insn)
    }

    /**
     * A read of a name, on however many objects it could be happening on.
     *
     * One holder is the ordinary case and produces no box of its own: the read *is* the node the
     * write left behind, which is what makes a value traceable from where it was set to where it is
     * used. Several holders is one of several fields, and that needs a box saying so - the same
     * join a phi draws, for a reason that is about aliasing rather than about control flow.
     */
    private fun read(
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        holders: Set<MemPos>,
        written: Boolean,
        insn: Insn
    ): Value {
        val values = holders.ifEmpty { setOf(null) }
            .map { readFrom(it, name, element, isPrimitive, written, insn) }
        values.singleOrNull()?.let { return it }
        return Value(
            // Plain flow throughout: nothing on the page chose which object the name pointed at.
            block.addJoin(
                base(labelId(name, insn), insn),
                values.mapNotNull { it.node }.map { it to EdgeKind.FLOW },
                isPrimitive
            ),
            values.flatMapTo(HashSet()) { it.objects }
        )
    }

    private fun readFrom(
        holder: MemPos?,
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        written: Boolean,
        insn: Insn
    ): Value {
        val id = JNodeId(stack, name, element, setOfNotNull(holder))
        // An object's field is the object's: the block chain is only for a field with no holder.
        // Asking it for the other kind too would find the write a branch had undone, since a write
        // registers itself in its block whether or not the builder still holds it.
        val node = (if (holder != null) builder.fieldNode(holder, id) else block.getVariable(id)?.lastNode)
            ?: if (holder == null && written) {
                block.addExternal(base(id, insn), emptyList())
            } else {
                unassigned(id, name, element, isPrimitive, setOfNotNull(holder), insn).also {
                    if (holder != null && holder === builder.heap.staticHolder(element, insn.source)) {
                        builder.unwrittenStaticReads.add(id to it)
                    }
                }
            }
        return Value(node, builder.heap.objectsOf(id))
    }

    /**
     * The boundary-order token as it stands - see [ReadBoundaryOrder] - read the way [readFrom] reads
     * a static field: what the last boundary call left, or, before any, a box with nothing flowing in
     * that is the start of the chain and is recorded as an unwritten static read, so that several
     * roots tie it to each other's last call as they tie a static.
     *
     * Keyed with no declaration, which is the name, on a holder nothing else has: [unassigned] is
     * not asked, since there is no field for it to check.
     */
    private fun readBoundaryOrder(insn: ReadBoundaryOrder): Value {
        val holder = builder.heap.boundaryOrderHolder()
        val id = JNodeId(stack, BOUNDARY_ORDER, null, setOf(holder))
        val node = builder.fieldNode(holder, id)
            ?: builder.record(block.addBoundaryOrder(base(id, insn, orderCaption)), setOf(holder))
                .also { builder.unwrittenStaticReads.add(id to it) }
        return Value(node)
    }

    /**
     * A write, which is a new box with the value flowing into it.
     *
     * A new one every time: `y = 1; y = y + 1` is two boxes, and only the lookup key is shared. An
     * object write also records what the name now points at, so a later read through it finds the
     * right fields - and gets a position of its own when the value has none, so that a field set on
     * an object from outside still has somewhere to hang.
     *
     * Through a receiver that could be either of two objects it is a box on each, both taking the
     * same value: there is one write in the source and two fields it could land in, and drawing one
     * of them would be choosing which. The value of the expression is the first, since `(a.f = v)`
     * is `v` whichever object `a` turned out to be.
     *
     * [draw] makes the box; only the boundary-order token draws one other than a variable's.
     */
    private fun write(
        name: String,
        element: Element?,
        isPrimitive: Boolean,
        holders: Set<MemPos>,
        valueObjects: Set<MemPos>,
        insn: Insn,
        valueNode: GraphNode,
        journal: Boolean = false,
        kind: EdgeKind = EdgeKind.FLOW,
        draw: (GraphNode.Base) -> GraphNode =
            { if (isPrimitive) block.addPrimitiveVariable(it) else block.addObjectVariable(it) }
    ): Value {
        val written = if (isPrimitive) emptySet()
        else valueObjects.ifEmpty { setOf(builder.heap.allocate(insn.source)) }
        val nodes = holders.ifEmpty { setOf(null) }.map { holder ->
            val owning = setOfNotNull(holder)
            val id = JNodeId(stack, name, element, owning)
            val key = if (journal && builder.forking) FieldKey(holder?.id, id, block.takeIf { holder == null }, isPrimitive) else null
            val before = key?.let { builder.fieldState(it) }
            val node = builder.record(draw(base(id, insn)), owning)
            block.addAssignment(node, valueNode, kind)
            if (!isPrimitive) builder.heap.setObjects(id, written)
            key?.let { builder.journalWrite(it, before) }
            node
        }
        return Value(nodes.first(), written)
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
            builder.record(block.addPrimitiveVariable(base(id, insn)), owner)
        } else {
            builder.record(block.addObjectVariable(base(id, insn)), owner)
        }
        insn.value?.let { block.addAssignment(node, run.node(it)) }
        val objects = when (insn.identity) {
            Identity.OfValue -> insn.value?.let { run.objects(it) } ?: emptySet()
            Identity.Fresh -> if (insn.isPrimitive) emptySet() else setOf(builder.heap.allocate(insn.source))
            Identity.Unknown -> emptySet()
        }
        if (objects.isNotEmpty()) builder.heap.setObjects(id, objects)
        return Value(node, objects)
    }

    /**
     * A variable where two paths meet, as one box taking the value from each - see [Phi].
     *
     * Keyed by occurrence rather than by declaration, because it is not a place anything looks up:
     * a use below the join was resolved to this instruction while the method was being lowered, so
     * what the box has to be is the merge itself, at the line of the `if` that caused it.
     *
     * A *gated* join is two boxes, not one. The choosing takes the paths and the gate - which is
     * what says why either value would be taken, see [Gate] - and what comes out of it is the
     * variable, which is what a use below the branch reads. That is the shape a `?:` has already:
     * `int g = c ? a : b` draws the `ternary` feeding `g`, and drawing the same choice written as a
     * statement any other way makes two pictures of one program. Collapsing them into a single box
     * captioned `if` also left the variable's name off the diagram entirely at the one point the
     * reader most needs it - the join is the only place `a` means something neither assignment to
     * `a` says on its own.
     *
     * The gate is never among the objects the join can be, only among its inputs.
     *
     * Which object it is is every object any path could have left it holding, which is the whole
     * point of the set: it used to be the first path that named one, and a field read below the
     * join then found the fields of one arm and none of the other's, with nothing on the page
     * showing an arm had been chosen.
     *
     * A path still waiting on a back edge cannot contribute - its value has not been drawn yet -
     * so an object created inside a loop and assigned to a variable declared before it is not among
     * these. That is the remaining edge of the alias model, and it is a *missing* possibility
     * rather than an invented one.
     */
    private fun phi(insn: Phi, run: Run): Value {
        val (arrived, pending) = insn.paths.partition { it.index < run.reached }
        val paths = arrived.map { run.node(it) to edgeKind(it, null, insn.gate?.arms ?: emptyMap()) }
        val chosen = insn.gate?.let { gate ->
            block.addSelection(
                // Keyed by the construct *and* the variable, since one `if` decides several and the
                // key is `(position, label)` - two joins at one `if` keyed `if` would be one box.
                base(labelId("${gate.label} ${insn.name}", insn), insn, gate.label),
                paths + (run.node(gate.value) to EdgeKind.CONDITION)
            )
        }
        val node = block.addJoin(
            base(labelId(insn.name, insn), insn),
            chosen?.let { listOf(it to EdgeKind.FLOW) } ?: paths,
            insn.isPrimitive
        )
        // The choosing takes the paths when there is one, so a back edge has to land where the rest
        // of the paths did or the loop's own contribution arrives at the wrong box.
        pending.forEach { run.backEdges.add((chosen ?: node) to it) }
        return Value(node, arrived.flatMapTo(HashSet()) { run.objects(it) })
    }

    /**
     * The fields where paths meet: each one the paths disagree about is a box taking the value from
     * each, and one they agree about is not drawn - see [FieldJoin].
     *
     * Drawn like [phi], gated or not, and for the same reasons: the choosing is one box taking the
     * paths and the gate, captioned with the construct and keyed by it *and* the field - one `if`
     * decides several, and the key is `(position, label)` - and what comes out of it is the field,
     * which is what a read below the join arrives at. The field's own box is keyed by its
     * declaration and holder like any write, so that `record` files it where a read looks.
     *
     * Nothing here is a back edge: a read inside a loop body does not see the body's later write.
     */
    private fun fieldJoin(insn: FieldJoin, run: Run) {
        val fork = run.fork(insn.fork)
        val merges = builder.mergeFields(fork, insn.arms, insn.withEntry, insn.armNames)
        // Closed before the result goes back, so that it is journaled into the fork around this one
        // when there is one, and into nothing when there is not.
        if (insn.closes) builder.closeFork(fork)
        for (merge in merges) {
            val key = merge.key
            val before = builder.fieldState(key)
            val single = merge.reaching.singleOrNull()
            if (single != null) {
                builder.setFieldState(key, single.state, before)
                continue
            }
            val gate = insn.gate
            val paths = merge.reaching.map { it.state.node to (if (gate == null) EdgeKind.FLOW else armKind(it.arm)) }
            val chosen = gate?.let {
                block.addSelection(
                    base(labelId("${it.label} ${key.id.label}", insn), insn, it.label),
                    paths + (run.node(it.value) to EdgeKind.CONDITION)
                )
            }
            // The token's join is the token, told apart by its holder like every other box of it.
            val isOrder = key.owner == builder.heap.boundaryOrderHolder().id
            val node = if (isOrder) {
                block.addJoin(
                    base(key.id, insn, orderCaption), chosen?.let { listOf(it to EdgeKind.FLOW) } ?: paths,
                    key.isPrimitive, NodeType.BOUNDARIES
                )
            } else {
                block.addJoin(base(key.id, insn), chosen?.let { listOf(it to EdgeKind.FLOW) } ?: paths, key.isPrimitive)
            }
            val objects = if (key.isPrimitive) emptySet() else merge.reaching.flatMapTo(HashSet()) { it.state.objects }
            builder.setFieldState(key, FieldState(node, objects), before)
        }
    }

    private fun armKind(arm: String?) = when (arm) {
        "true" -> EdgeKind.TRUE
        "false" -> EdgeKind.FALSE
        else -> EdgeKind.FLOW
    }

    /**
     * What an input's arrow means: the test, one named arm of a choice, or plain flow.
     *
     * The condition is matched structurally and the arms by name, which is the split the IR makes -
     * see [Gate]. A value with no name is [EdgeKind.FLOW] and is most of them: an array's elements
     * arrive at the array, a loop's back edge arrives at the header, and neither is a side of
     * anything.
     */
    private fun edgeKind(input: Val, condition: Val?, arms: Map<Val, String>) = when {
        input == condition -> EdgeKind.CONDITION
        arms[input] == "true" -> EdgeKind.TRUE
        arms[input] == "false" -> EdgeKind.FALSE
        else -> EdgeKind.FLOW
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
        val instance = owner.ifEmpty { setOf(builder.heap.allocate(insn.source)) }
        val id = JNodeId(stack, "this", null, instance)
        val node = builder.record(block.addObjectVariable(base(id, insn)), instance)
        builder.heap.setObjects(id, instance)
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
            creation.args.map { run.objects(it) },
            builder.heap.enumConstantMemPos(element, creation.source),
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
        val receiverMemPos = receiverObjects(insn, run)
        val method = resolve(insn, receiverMemPos)
        if (method == null || isBeingInlined(method)) {
            val receiverNode = (insn.receiver as? Receiver.Value)?.let { run.node(it.value) }
            // The order token a boundary call takes is not an argument any value flows through.
            val (order, data) = insn.args.partition { it.index in run.orderReads }
            val inputs = listOfNotNull(receiverNode) + data.map { run.node(it) }
            val external = block.addExternal(base(labelId(insn.name, insn), insn, lead = insn.lead), inputs)
            order.forEach { run.node(it).addEdge(external, EdgeKind.ORDER) }
            return Value(external)
        }
        val child = enter(method, receiverMemPos, insn)
        child.invoke(insn.args.map { run.node(it) }, insn.args.map { run.objects(it) })
        return Value(child.block.returnNode, child.block.returnedMemPos)
    }

    /** The objects the callee runs on, which is also what decides which body that is. */
    private fun receiverObjects(insn: Call, run: Run): Set<MemPos> = when (insn.receiver) {
        // A static method runs on no object at all, so it has nothing to inherit.
        Receiver.Enclosing, Receiver.Super ->
            if (Modifier.STATIC in (insn.target?.modifiers ?: emptySet())) emptySet() else owner
        Receiver.TypeName -> emptySet()
        is Receiver.Value -> run.objects(insn.receiver.value)
    }

    /** The rule is shared with the summarizer; see [Dispatch.resolve]. */
    private fun resolve(insn: Call, objects: Set<MemPos>): Method? =
        Dispatch.resolve(globalCtx, insn, objects.mapNotNull { it.type }.distinct())

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
        child.invoke(insn.args.map { run.node(it) }, insn.args.map { run.objects(it) })
        return Value(child.block.returnNode)
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
        argumentMemPositions: List<Set<MemPos>>,
        into: MemPos?,
        insn: Insn
    ): Value {
        // The class comes from the constructor's own declaration, which is present even when the
        // class declares no constructor - attribution inserts one, and its enclosing element is
        // still the class. `Plain` in the fieldInitializer fixture is the case that proves it.
        val created = setOf(
            into ?: builder.heap.allocate(insn.source, constructorElement?.enclosingElement)
        )
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

    /** The frame a call site opens: a block nested in this one, on the objects the callee runs on. */
    private fun enter(method: Method, memPos: Set<MemPos>, insn: Insn): Frame {
        val childStack = stack.push(insn.source)
        val childBlock = GraphBuilderBlock(
            block, method, childStack, memPos, method.ctx, insn.source, globalCtx.isBoundary(method)
        )
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
        holder: Set<MemPos>,
        insn: Insn
    ): GraphNode {
        if (element?.kind == ElementKind.ENUM_CONSTANT) return block.addExternal(base(id, insn), emptyList())
        if (element?.kind != ElementKind.FIELD) {
            throw GraphException("'$name' at ${insn.source} has no value in ${block.getMethodName()}")
        }
        return if (isPrimitive) {
            builder.record(block.addPrimitiveVariable(base(id, insn)), holder)
        } else {
            builder.record(block.addObjectVariable(base(id, insn)), holder)
        }
    }

    /** A node that stands for an occurrence rather than for a variable: an operator, a literal. */
    private fun labelId(label: String, insn: Insn) = GraphNodeId(stack.push(insn.source), label)

    private fun base(id: GraphNodeId, insn: Insn, caption: String? = null, lead: String? = null) =
        GraphNode.Base(id, insn.source, caption, lead)
}
