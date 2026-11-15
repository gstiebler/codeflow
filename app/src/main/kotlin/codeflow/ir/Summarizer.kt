package codeflow.ir

import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.ir.AbsVal.Companion.EMPTY
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement

/**
 * Every source method read into a [Summary], by walking the same instructions the builder draws.
 *
 * A second interpreter over one IR, which is only worth having if it agrees with the first: its
 * rules are the builder's, read off `IrGraphBuilder`, and whatever edge the builder draws is a flow
 * here. Where the builder has a known gap (`docs/decisions/joins.md`: no back edge for a field in a
 * loop body, `?:`/`&&`/`||` not forked, a strong update through several holders) this has the same
 * one, so the two disagree only where a summary is meant to differ - which the parity test Task 6
 * adds will record.
 *
 * Interpreting a method is a pure function of the method and what it is given, so it can be run
 * again. A call to a source method instantiates the callee's summary at the call site
 * ([Interpretation.instantiate]); a call to anything else is opaque ([Interpretation.opaqueCall]),
 * an [ExtResult] and a sink, as the builder's EXTERNAL is.
 */
class Summarizer(
    private val globalCtx: GlobalContext,
    private val bodyOf: (Method) -> MethodBody,
    private val initializersOf: (Element?) -> List<Insn>,
    private val budget: Int = DEFAULT_BUDGET
) {
    init {
        require(budget >= 1) { "a budget of $budget runs computes nothing" }
    }

    /** Keyed by `(method, receiver class)`, or by [Initializers] - see [solve]. */
    private val summaries = HashMap<Any, Summary>()
    /** How each key is computed and where it is declared, so a callee that changed can have it run again. */
    private val tasks = HashMap<Any, Task>()
    /** For each key, every key a run of which read it - those that must run again when it changes. */
    private val dependents = HashMap<Any, LinkedHashSet<Any>>()
    private val runs = HashMap<Any, Int>()
    private val worklist = LinkedHashSet<Any>()
    /** The keys being run right now, innermost last: the one a [solve] is reading on behalf of. */
    private val running = ArrayList<Any>()

    /** Methods whose lowering threw: a call to one is opaque, and inherits the `body` gap that says why. */
    private val unlowerable = HashSet<Element>()
    private val overridden = HashMap<Element, Boolean>()

    /**
     * What [method] does when it runs on an object of class [receiverType].
     *
     * The class matters only to a virtual call on `this`, which it decides - a template method
     * summarised for `I` runs `I.step`. Null means nobody said: such a call resolves statically, as
     * the builder does for an untyped receiver, and carries a `dispatch` gap where the sources
     * override it.
     */
    fun summary(method: Method, receiverType: Element? = null): Summary =
        solve(method.element to receiverType) {
            Task(method.ctx.location(method.name), label(method.element)) { summarise(method, receiverType) }
        }

    /** A class's field initializers, summarised as a body of their own for a `new` with no constructor to run them. */
    private data class Initializers(val cls: Element)

    /** What [solve] runs for a key, and the declaration a `fixpoint` gap names when it will not settle. */
    private class Task(val at: String, val label: String, val compute: () -> Summary)

    /**
     * Every summary is computed here, as a fixpoint: a key's run reads the summaries of what it
     * calls as they stand, and is run again whenever one of them changes.
     *
     * So recursion needs no cut. A key asked for while it is running - a cycle - contributes what it
     * has so far, nothing the first time, and the reader is queued to run again once it has more.
     * The outermost call drains the queue, and every key is then the same as one more run would
     * make it. Every key starts from nothing and is run again until it stops changing, so where
     * runs only add - most code - the answer is the least one, whichever key was asked first. A key
     * that has finished is never queued again, since everything it read had finished first.
     *
     * Each key runs at most [budget] times; then it keeps its last value and a `fixpoint` gap at its
     * declaration says it had not settled. That bound, not the analysis converging, is what makes it
     * stop: a strong update makes a caller's summary able to shrink when its callee's grows, and
     * such a system need not settle at all.
     *
     * A run that throws leaves nothing half-solved behind: every key this query added is forgotten,
     * since each was read against values that will now never be finished.
     */
    private fun solve(key: Any, task: () -> Task): Summary {
        val reader = running.lastOrNull()
        if (reader != null) {
            // The reader is recorded after the read: what a first run hands back is what it read,
            // and only a later change is news to it.
            val value = summaries[key] ?: run(key.also { tasks[it] = task() })
            dependents.getOrPut(key, ::LinkedHashSet).add(reader)
            return value
        }
        summaries[key]?.let { return it }
        tasks[key] = task()
        val known = HashSet(summaries.keys)
        var solved = false
        try {
            run(key)
            while (worklist.isNotEmpty()) run(worklist.first().also { worklist.remove(it) })
            solved = true
        } finally {
            if (!solved) {
                listOf(summaries, tasks, runs, dependents).forEach { it.keys.retainAll(known) }
                dependents.values.forEach { it.retainAll(known) }
                worklist.clear()
            }
        }
        return summaries.getValue(key)
    }

    /** One run of [key], queueing its readers when it changed - or, out of budget, its gap. */
    private fun run(key: Any): Summary {
        val task = tasks.getValue(key)
        val before = summaries.getOrPut(key) { NOTHING }
        val count = (runs[key] ?: 0) + 1
        val after = if (count > budget) {
            before.copy(gaps = before.gaps + Gap("fixpoint", task.at, "${task.label} still changing after $budget runs"))
        } else {
            runs[key] = count
            running.add(key)
            try {
                task.compute()
            } finally {
                running.removeAt(running.size - 1)
            }
        }
        if (after != before) {
            summaries[key] = after
            dependents[key]?.let { worklist.addAll(it) }
        }
        return after
    }

    /**
     * The lists a method runs, then the run. A constructor runs its class's initializers first,
     * unless it delegates to `this(...)`, which runs them - the builder's `invoke`.
     *
     * Lowering is the one thing caught: a method it cannot read is a `body` gap with no flows, not
     * a run that takes the whole summarizer down with it.
     */
    private fun summarise(method: Method, receiverType: Element?): Summary {
        val element = method.element
        val lists = try {
            val body = bodyOf(method).instructions
            val delegates = body.any { it is Delegate && it.target.enclosingElement == element.enclosingElement }
            if (element.kind == ElementKind.CONSTRUCTOR && !delegates) listOf(initializersOf(element.enclosingElement), body)
            else listOf(body)
        } catch (e: GraphException) {
            unlowerable.add(element)
            return unlowered(method.ctx.location(method.name), e)
        }
        val thisObjs: Set<Obj> = if (Modifier.STATIC in element.modifiers) emptySet() else setOf(PathObj(This))
        return Interpretation(thisObjs, element.parameters, receiverType).run(lists)
    }

    /** Null for a class outside the sources, which has none. */
    private fun initializers(cls: Element?): Summary? {
        val declaring = globalCtx.findClass(cls) ?: return null
        val at = declaring.ctx.location(declaring.tree)
        return solve(Initializers(cls!!)) {
            Task(at, "${cls.simpleName} initializers") {
                val lists = try {
                    listOf(initializersOf(cls))
                } catch (e: GraphException) {
                    return@Task unlowered(at, e)
                }
                Interpretation(setOf(PathObj(This)), emptyList(), cls).run(lists)
            }
        }
    }

    private fun unlowered(at: String, e: GraphException) =
        Summary(EMPTY, emptyMap(), emptyMap(), setOf(Gap("body", at, e.message ?: "lowering failed")))

    private fun overriddenInSources(declared: ExecutableElement): Boolean =
        overridden.getOrPut(declared) { Dispatch.overriddenInSources(globalCtx, declared) }

    private fun label(element: Element) = "${element.enclosingElement.simpleName}#${element.simpleName}"

    /** Every method in the sources, in [GlobalContext.sourceMethods] order. */
    fun summariseAll(): Map<Method, Summary> =
        globalCtx.sourceMethods().associateWithTo(LinkedHashMap()) { summary(it) }

    /**
     * The builder's box, as far as a join can tell: a write makes a new one, and a join with one value
     * arriving keeps the one it found.
     *
     * Compared by identity, deliberately. The builder merges a field by *box*, not by value, and puts
     * the gate on the merge only when more than one box arrives - so `if (c) x = a; else x = a;` is
     * still gated by `c`, and an arm that wrote nothing is still a second way to arrive.
     */
    private class Cell(val value: AbsVal)

    /**
     * The builder's [OpenFork]: where its journal starts, and what each finished arm left. [index]
     * is the fork's position in the run (see [Interpretation.base]), so a join can tell an object
     * from before the fork from one made inside it.
     */
    private class Fork(val mark: Int, val index: Int) {
        val arms = ArrayList<Map<Pair<Obj, Element>, Cell>>()
    }

    /** What an [Interpretation] held when an instruction list began, for each pass to start from. */
    private class State(
        val heap: Map<Pair<Obj, Element>, Cell>,
        val written: Map<Pair<Obj, Element>, String>,
        val ret: AbsVal,
        val sinks: Map<String, AbsVal>,
        val gaps: Set<Gap>
    )

    /**
     * One run of one method: its abstract heap, and what it has returned, written and called.
     *
     * [thisObjs] is empty for a static method; [receiverType] is the class `this` is known to be,
     * which only a virtual call on it asks.
     */
    private inner class Interpretation(
        private val thisObjs: Set<Obj>,
        parameters: List<VariableElement>,
        private val receiverType: Element?
    ) {
        private val params: List<AbsVal> = parameters.mapIndexed { i, parameter ->
            val objs: Set<Obj> = if (parameter.asType().kind.isPrimitive) emptySet() else setOf(PathObj(ParamRoot(i)))
            AbsVal(setOf(ParamAtom(i)), objs)
        }

        /** What every field holds right now. A key with no entry has been neither written nor read. */
        private var heap = LinkedHashMap<Pair<Obj, Element>, Cell>()

        /** Field writes since the oldest open fork began, with what each key held before - see [FieldFork]. */
        private val journal = ArrayList<Pair<Pair<Obj, Element>, Cell?>>()
        private val openForks = ArrayList<Fork>()

        /** Every field a write touched, with where it was first written: the heap also holds fields only read. */
        private var written = LinkedHashMap<Pair<Obj, Element>, String>()
        /** The position each [StandInObj] was made at - the same on every pass. */
        private val standInsMadeAt = HashMap<Obj, Int>()

        /**
         * Where the current list starts in the run, so a position is an index into every list run so
         * far: a constructor's initializers come before its body, and a stand-in one of them made is
         * older than every fork in the body. An index into one list alone would compare the two.
         */
        private var base = 0
        private var ret = EMPTY
        private var sinks = LinkedHashMap<String, AbsVal>()
        private var gaps = LinkedHashSet<Gap>()

        fun run(lists: List<List<Insn>>): Summary {
            lists.forEach { execute(it) }
            return Summary(ret, effects(), sinks, gaps)
        }

        /**
         * The final value of every field written on an object the caller can reach: a [PathObj], or
         * an object made here that the result or such a field leads to. The rest died with the method.
         *
         * What died is said where it was a flow lost rather than a dead store: a write on a
         * [StandInObj] was to an object from outside, and an object the caller reaches only past
         * the depth cap cannot be spelled.
         */
        private fun effects(): Map<Pair<Obj, Element>, AbsVal> {
            val final = written.keys.mapNotNull { key -> heap[key]?.let { key to it.value } }
            val kept = LinkedHashSet<Obj>()
            val work = ArrayList<Obj>()
            fun keep(objs: Set<Obj>) = objs.forEach { if (it !is PathObj && kept.add(it)) work.add(it) }
            keep(ret.objs)
            final.forEach { (key, value) -> if (key.first is PathObj) keep(value.objs) }
            while (work.isNotEmpty()) {
                val obj = work.removeAt(work.size - 1)
                final.forEach { (key, value) -> if (key.first == obj) keep(value.objs) }
            }
            val effects = final.filter { (key, _) -> key.first is PathObj || key.first in kept }.toMap(LinkedHashMap())
            val tooDeep = names(effects).tooDeep
            for ((key, at) in written) {
                if (key.first is StandInObj && key !in effects) {
                    gaps.add(Gap("unmodelled", at, "${qualifiedField(key.second)} written on an object with no tracked origin"))
                }
                val cut = tooDeep[key.first]
                if (cut != null && key in effects) gaps.add(Gap("depth", at, spell(cut, key.second)))
            }
            return effects
        }

        /**
         * One instruction list, run until its back edges stop changing.
         *
         * A loop's header [Phi] names a value from further down the list, which the builder joins
         * once the list is drawn. Here that value is the previous pass's, so the list is re-run from
         * the state it started in until a pass changes nothing.
         *
         * At most [budget] passes: values need not only grow - a strong update, or a callee's summary
         * that shrank - so nothing else promises it stops. A list still changing on the last pass
         * keeps that pass, and a `fixpoint` gap at its first loop says the result is not settled.
         */
        private fun execute(instructions: List<Insn>) {
            check(openForks.isEmpty()) { "an instruction list started inside a fork" }
            val header = instructions.withIndex().firstOrNull { (index, insn) ->
                insn is Phi && insn.paths.any { it.index >= index }
            }?.value
            val start = State(LinkedHashMap(heap), LinkedHashMap(written), ret, LinkedHashMap(sinks), LinkedHashSet(gaps))
            val values = Array(instructions.size) { EMPTY }
            val forks = HashMap<Int, Fork>()
            for (pass in 1..budget) {
                heap = LinkedHashMap(start.heap)
                written = LinkedHashMap(start.written)
                ret = start.ret
                sinks = LinkedHashMap(start.sinks)
                gaps = LinkedHashSet(start.gaps)
                val previous = values.copyOf()
                instructions.forEachIndexed { index, insn -> values[index] = eval(insn, index, values, forks) }
                if (header == null || values.contentEquals(previous)) break
                if (pass == budget) gaps.add(Gap("fixpoint", header.source, "loop still changing after $budget passes"))
            }
            base += instructions.size
        }

        private fun eval(insn: Insn, index: Int, values: Array<AbsVal>, forks: HashMap<Int, Fork>): AbsVal {
            fun v(value: Val) = values[value.index]
            fun flows(inputs: List<Val>) = AbsVal(inputs.flatMapTo(LinkedHashSet()) { v(it).flows })
            return when (insn) {
                is Const -> EMPTY
                is Param -> params[insn.index]
                is WriteLocal -> {
                    val stored = stored(v(insn.value), insn.isPrimitive, insn, index)
                    // A field initializer is lowered as the declaration it is, but what it declares
                    // is a field of the object being built - the builder writes it onto `owner`.
                    val element = insn.element
                    if (element?.kind == ElementKind.FIELD) {
                        holders(Receiver.Enclosing, element, values).forEach { write(it to element, stored, insn.source) }
                    }
                    stored
                }
                is ReadField -> readField(insn, values)
                is WriteField -> {
                    val stored = stored(v(insn.value), insn.isPrimitive, insn, index)
                    val element = insn.element
                    val holders = element?.let { holders(insn.receiver, it, values) } ?: emptySet()
                    if (holders.isEmpty()) untracked(insn, insn.name, element)
                    holders.forEach { write(it to element!!, stored, insn.source) }
                    stored
                }
                is FieldFork -> EMPTY.also { forks[index] = Fork(journal.size, base + index).also { openForks.add(it) } }
                is FieldArm -> EMPTY.also { endArm(forks.getValue(insn.fork.index)) }
                is FieldJoin -> EMPTY.also { fieldJoin(insn, forks.getValue(insn.fork.index), values) }
                is ThisRef -> AbsVal(objs = thisObjs.ifEmpty { error("`this` in a static method at ${insn.source}") })
                // A path from further down is a back edge: the builder joins its node, so its flows
                // arrive, but its objects do not - the phi's objects are the paths that had arrived.
                is Phi -> {
                    val arrived = insn.paths.filter { it.index < index }
                    AbsVal(
                        insn.paths.flatMapTo(LinkedHashSet()) { v(it).flows } +
                            (insn.gate?.let { v(it.value).flows } ?: emptySet()),
                        arrived.flatMapTo(LinkedHashSet()) { v(it).objs }
                    )
                }
                is Bind -> AbsVal(
                    insn.value?.let { v(it).flows } ?: emptySet(),
                    when (insn.identity) {
                        Identity.OfValue -> insn.value?.let { v(it).objs } ?: emptySet()
                        Identity.Fresh -> if (insn.isPrimitive) emptySet() else setOf(standIn(insn, index))
                        Identity.Unknown -> emptySet()
                    }
                )
                is BinOp -> flows(insn.inputs)
                is UnOp -> flows(insn.inputs)
                // The objects come from the alternatives only: a ternary is never its condition.
                is Select -> AbsVal(flows(insn.inputs).flows, insn.alternatives.flatMapTo(LinkedHashSet()) { v(it).objs })
                is Opaque -> flows(insn.inputs)
                is Unmodelled -> flows(insn.inputs).also { gaps.add(Gap("unmodelled", insn.source, insn.kind)) }
                is Return -> EMPTY.also { insn.value?.let { ret += v(it) } }
                is Call -> call(insn, index, values)
                is New -> construct(insn, index, values)
                is Delegate -> delegate(insn, index, values)
            }
        }

        /**
         * A call, which runs the body the receiver's class picks - [Dispatch], the builder's rule.
         *
         * `this` is the class this run was asked for, an allocation the class it was made as, and
         * anything else says nothing about its class. Where the sources override the method and the
         * receiver is a parameter's or a field's object, a stand-in, or nothing tracked at all,
         * nothing here can say which body runs: the call is opaque and a `dispatch` gap says why,
         * rather than taking the declared body as the answer. `this` with no class known resolves
         * statically, as the builder's untyped receiver does, and carries the gap too.
         */
        private fun call(insn: Call, index: Int, values: Array<AbsVal>): AbsVal {
            val opaque = { opaqueCall(insn.name, insn.receiver.inputs + insn.args, insn, values) }
            val declared = insn.target as? ExecutableElement ?: return opaque()
            val receiver: Set<Obj> = when (insn.receiver) {
                Receiver.Enclosing, Receiver.Super -> if (Modifier.STATIC in declared.modifiers) emptySet() else thisObjs
                Receiver.TypeName -> emptySet()
                is Receiver.Value -> values[insn.receiver.value.index].objs
            }
            val types = receiver.mapNotNull { typeOf(it) }.distinct()
            val virtual = Dispatch.isVirtual(insn.receiver, declared)
            if (virtual && overriddenInSources(declared)) {
                val undecided = receiver.filter { typeOf(it) == null }
                if (undecided.isNotEmpty() || receiver.isEmpty()) {
                    val on = if (receiver.isEmpty()) "an untracked receiver"
                    else undecided.joinToString { if (it is PathObj) spell(it) else "an object with no tracked origin" }
                    gaps.add(Gap("dispatch", insn.source, "${label(declared)} on $on"))
                    if (receiver.isEmpty() || undecided.any { it != PathObj(This) }) return opaque()
                }
            }
            val method = Dispatch.resolve(globalCtx, insn, types)
            if (method == null) {
                if (virtual && types.map { globalCtx.implementation(declared, it) }.distinct().size > 1) {
                    gaps.add(Gap("dispatch", insn.source, "${label(declared)} has several implementations on ${types.joinToString { it.simpleName }}"))
                }
                return opaque()
            }
            val receiverType = if (Modifier.STATIC in method.element.modifiers) null else types.singleOrNull()
            return enter(method, receiverType, receiver, insn.args.map { values[it.index] }, insn, index) ?: opaque()
        }

        /** The class [obj] was constructed as, when that is known here. */
        private fun typeOf(obj: Obj): Element? = when (obj) {
            is AllocObj -> obj.type
            is PathObj -> receiverType.takeIf { obj == PathObj(This) }
            is StandInObj -> null
        }

        /**
         * `new X(...)`, the builder's `construct`: a constructor in the sources runs on the new
         * object, and one that is not - outside the sources, or the one attribution inserted - is
         * opaque, with the class's initializers still run on the new object, since they are code
         * somebody wrote. The value is the object; nothing flows out of a constructor's return.
         */
        private fun construct(insn: New, index: Int, values: Array<AbsVal>): AbsVal {
            val cls = insn.constructor?.enclosingElement
            val created = setOf<Obj>(AllocObj(listOf(insn.source), cls))
            val constructor = globalCtx.findMethod(insn.constructor)
            if (constructor != null) {
                enter(constructor, cls, created, insn.args.map { values[it.index] }, insn, index)?.let { return AbsVal(it.flows, created) }
            } else {
                initializers(cls)?.let { instantiate(it, "${cls!!.simpleName} initializers", created, emptyList(), insn, index) }
            }
            return AbsVal(opaqueCall(insn.typeName, insn.args, insn, values).flows, created)
        }

        /**
         * `super(...)` or `this(...)`, the builder's `delegate`: the constructor runs on this run's own
         * object, so what it writes is this object's.
         *
         * One outside the sources is a sink here, where the builder draws nothing: the arguments
         * still went somewhere, and a summary says where its inputs go.
         */
        private fun delegate(insn: Delegate, index: Int, values: Array<AbsVal>): AbsVal {
            val label = insn.target.enclosingElement.simpleName.toString()
            val constructor = globalCtx.findMethod(insn.target)
            val run = constructor?.let { enter(it, receiverType, thisObjs, insn.args.map { values[it.index] }, insn, index) }
            if (run == null) opaqueCall(label, insn.args, insn, values)
            return EMPTY
        }

        /**
         * A source method run at this call site on [receiver], or null when its body could not be
         * lowered - then the call is opaque, and the `body` gap is inherited to say why.
         */
        private fun enter(method: Method, receiverType: Element?, receiver: Set<Obj>, args: List<AbsVal>, insn: Insn, index: Int): AbsVal? {
            val callee = summary(method, receiverType)
            if (method.element in unlowerable) {
                gaps.addAll(callee.gaps)
                return null
            }
            return instantiate(callee, label(method.element), receiver, bind(method, args, insn.source), insn, index)
        }

        /**
         * The builder's `invoke` and `connectParameters`: each argument's objects go to its own
         * parameter, and every argument from the last parameter on flows into it when it is varargs.
         * Any other count mismatch is the analysis having gone wrong, and says so.
         */
        private fun bind(method: Method, args: List<AbsVal>, at: String): List<AbsVal> {
            val element = method.element
            val n = element.parameters.size
            if (args.size > n && !(element.isVarArgs && n > 0)) {
                throw GraphException("${args.size} arguments for $n parameters of '${method.displayName()}' at $at")
            }
            return List(n) { i ->
                val flows = if (element.isVarArgs && i == n - 1) args.drop(i).flatMapTo(LinkedHashSet()) { it.flows }
                else args.getOrNull(i)?.flows ?: emptySet()
                AbsVal(flows, args.getOrNull(i)?.objs ?: emptySet())
            }
        }

        /**
         * [callee]'s summary read at this call site, with [receiver] as its `this` and [args] as its
         * parameters.
         *
         * Everything the callee names, it names as it was when it began, so everything is read off
         * this run's heap *before* any effect is applied: a path is followed field by field from
         * what its root is here, and a field the callee read at entry is what that field holds
         * here. That is what keeps the caller's own value of a field the callee wrote on only some
         * paths - its effect carries the entry, which is this value.
         *
         * An object the callee made is renamed by this call site, so two calls make two objects - and
         * only the [K_LIMIT] newest sites are kept, or a recursive factory would name a new object on
         * every pass. Two the callee told apart only by a site that was dropped are one object here,
         * which joins their fields: that is a `depth` gap, as a path cut at [DEPTH_CAP] is. A
         * value the callee stored that has no object here - an argument from an opaque call - gets a
         * stand-in per call site *and* field it was stored in, as the builder's `write` gives one per
         * write: one per call site would make two unrelated objects one.
         *
         * An effect is a strong update on every object its holder is here, including either of
         * several a join left the receiver pointing at: the builder's inlined write is the same, and
         * so is its known gap (`docs/decisions/joins.md`). Two effects landing on one field - the same
         * object passed twice - are both possible, since nothing here says which the callee wrote last.
         *
         * A path the caller has no object for cannot carry a read or a write, and says so rather than
         * dropping it. Nor can two the callee took as distinct that are one object here - see [aliases].
         */
        private fun instantiate(callee: Summary, label: String, receiver: Set<Obj>, args: List<AbsVal>, insn: Insn, index: Int): AbsVal {
            val site = insn.source
            fun field(holders: Set<Obj>, field: Element): AbsVal =
                holders.fold(EMPTY) { acc, holder -> acc + read(holder to field, field.asType().kind.isPrimitive, site) }
            val paths = HashMap<PathObj, Set<Obj>>()
            val names = HashMap<Obj, Obj>()
            fun chain(sites: List<String>) = (listOf(site) + sites).take(K_LIMIT)
            fun renamed(obj: Obj, name: Obj): Obj = name.also {
                val first = names.putIfAbsent(name, obj)
                if (first != null && first != obj) {
                    gaps.add(Gap("depth", site, "$label makes two objects one past $K_LIMIT call sites: $first and $obj"))
                }
            }
            fun objs(obj: Obj): Set<Obj> = when (obj) {
                is PathObj -> paths.getOrPut(obj) {
                    val root: Set<Obj> = when (obj.root) {
                        This -> receiver
                        is ParamRoot -> args.getOrNull(obj.root.i)?.objs ?: emptySet()
                        is StaticRoot -> setOf(PathObj(obj.root))
                    }
                    obj.fields.fold(root) { holders, f -> field(holders, f).objs }
                }
                is AllocObj -> setOf(renamed(obj, AllocObj(chain(obj.site), obj.type)))
                is StandInObj -> setOf(renamed(obj, StandInObj(chain(obj.site))).also { standInsMadeAt.putIfAbsent(it, base + index) })
            }
            fun lost(what: String) = gaps.add(Gap("unmodelled", site, "$what by $label on no object this can name"))
            fun value(v: AbsVal) = AbsVal(
                v.flows.flatMapTo(LinkedHashSet()) { atom ->
                    when (atom) {
                        is ParamAtom -> args.getOrNull(atom.i)?.flows ?: emptySet()
                        is FieldAtom -> {
                            val holders = objs(atom.obj)
                            if (holders.isEmpty()) lost("${spell(atom.obj, atom.field)} read")
                            field(holders, atom.field).flows
                        }
                        is ExtResult -> setOf(atom)
                    }
                },
                v.objs.flatMapTo(LinkedHashSet()) { objs(it) }
            )

            aliases(callee, label, site, ::objs)
            val result = value(callee.ret)
            val calleeSinks = callee.sinks.mapValues { value(it.value) }
            val updates = LinkedHashMap<Pair<Obj, Element>, AbsVal>()
            for ((key, effect) in callee.effects) {
                val (holder, f) = key
                val holders = objs(holder)
                if (holders.isEmpty()) lost("${if (holder is PathObj) spell(holder, f) else qualifiedField(f)} written")
                val mapped = value(effect)
                val written = when {
                    f.asType().kind.isPrimitive -> AbsVal(mapped.flows)
                    mapped.objs.isNotEmpty() -> mapped
                    else -> AbsVal(mapped.flows, setOf(StandInObj(listOf(site, effectName(key))).also { standInsMadeAt.putIfAbsent(it, base + index) }))
                }
                for (target in holders) {
                    val k = target to f
                    updates[k] = updates[k]?.plus(written) ?: written
                }
            }

            calleeSinks.forEach { (k, v) -> sinks[k] = (sinks[k] ?: EMPTY) + v }
            gaps.addAll(callee.gaps)
            for ((k, v) in updates) write(k, v, site)
            return result
        }

        /** The callee's field [key], as a link in a stand-in's site chain: `this.A.b1`, `new <site>.A.b`. */
        private fun effectName(key: Pair<Obj, Element>): String = when (val holder = key.first) {
            is PathObj -> spell(holder, key.second)
            is AllocObj -> "new ${holder.site.joinToString(" ")}." + qualifiedField(key.second)
            is StandInObj -> "obj ${holder.site.joinToString(" ")}." + qualifiedField(key.second)
        }

        /**
         * Whether the callee read a field through one of its paths that it wrote through another,
         * where the two are one object here.
         *
         * The callee was read as if `this` and each parameter were distinct objects, so the read
         * found what the field held at entry - which is not what it holds once the write through the
         * other name has run. The summary cannot give that flow, and says so: `g(b, b)`, `o.g(o)`.
         * A path a summary names is followed off the entry heap, so each field it goes through is a
         * read too.
         *
         * Two writes of one field through two such paths are the same: an effect is a field's final
         * value, and which of the two the callee made last - the one the field keeps - is not in
         * it, so joining them would give a flow the builder does not draw (`g(b, b)` writing `q.v`
         * then `p.v`).
         *
         * The check is order-insensitive, so it may over-report: a read that came after the write
         * through the other name, or two writes on paths that never both run, is still a gap. Too
         * many gaps is a summary that says it is unsure; too few is one that is silently wrong.
         */
        private fun aliases(callee: Summary, label: String, site: String, objs: (Obj) -> Set<Obj>) {
            val reads = LinkedHashSet<Pair<PathObj, Element>>()
            fun path(obj: Obj) {
                if (obj is PathObj) obj.fields.indices.forEach { reads.add(PathObj(obj.root, obj.fields.take(it)) to obj.fields[it]) }
            }
            fun value(v: AbsVal) {
                v.flows.forEach { if (it is FieldAtom) { reads.add(it.obj to it.field); path(it.obj) } }
                v.objs.forEach(::path)
            }
            value(callee.ret)
            callee.sinks.values.forEach(::value)
            callee.effects.forEach { (key, v) -> path(key.first); value(v) }
            val writes = callee.effects.keys.mapNotNull { (holder, f) -> (holder as? PathObj)?.let { it to f } }
            for ((read, f) in reads) for ((write, g) in writes) {
                if (f != g || read == write || objs(read).none { it in objs(write) }) continue
                gaps.add(Gap("alias", site, "$label reads ${spell(read, f)} and writes ${spell(write, f)}, which are one object here"))
            }
            for ((i, w) in writes.withIndex()) for ((other, g) in writes.drop(i + 1)) {
                val (write, f) = w
                if (f != g || write == other || objs(write).none { it in objs(other) }) continue
                gaps.add(Gap("alias", site, "$label writes ${spell(write, f)} and ${spell(other, f)}, which are one object here"))
            }
        }

        /**
         * A call whose body is not followed: its result is what the call returned plus everything
         * that went in, and everything that went in is a sink.
         *
         * That is the reachability the builder's EXTERNAL node gives.
         */
        private fun opaqueCall(label: String, inputs: List<Val>, insn: Insn, values: Array<AbsVal>): AbsVal {
            val inFlows = inputs.flatMapTo(LinkedHashSet()) { values[it.index].flows }
            val key = extArg(label, insn.source)
            sinks[key] = (sinks[key] ?: EMPTY) + AbsVal(inFlows)
            return AbsVal(setOf(ExtResult(label, insn.source)) + inFlows)
        }

        /**
         * A value as a write leaves it. An object nothing here tracks gets a position of its own at
         * the write, as the builder's `write` does, so a field set on it later has somewhere to hang.
         */
        private fun stored(value: AbsVal, isPrimitive: Boolean, insn: Insn, index: Int): AbsVal =
            if (isPrimitive) AbsVal(value.flows)
            else AbsVal(value.flows, value.objs.ifEmpty { setOf(standIn(insn, index)) })

        private fun standIn(insn: Insn, index: Int): StandInObj =
            StandInObj(listOf(insn.source)).also { standInsMadeAt.putIfAbsent(it, base + index) }

        /**
         * The objects an access happens on - the builder's `holderOf`. A static field is held by its
         * class whichever way it is written; empty means no object this can name.
         */
        private fun holders(receiver: Receiver, element: Element, values: Array<AbsVal>): Set<Obj> {
            if (element.kind == ElementKind.FIELD && Modifier.STATIC in element.modifiers &&
                globalCtx.symbols.isDeclaredInSources(element)
            ) {
                return setOf(PathObj(StaticRoot(element.enclosingElement)))
            }
            return when (receiver) {
                Receiver.Enclosing, Receiver.Super -> thisObjs
                Receiver.TypeName -> emptySet()
                is Receiver.Value -> values[receiver.value.index].objs
            }
        }

        /**
         * A field read, on every object the receiver can be.
         *
         * An enum constant is its own declaration and brings nothing in, as in the builder. So does
         * a static field of a class outside the sources, named through its type: it is the
         * builder's EXTERNAL with no inputs, a value from outside rather than one lost here. Any
         * other read with no holder is - see [untracked].
         */
        private fun readField(insn: ReadField, values: Array<AbsVal>): AbsVal {
            val element = insn.element
            if (element?.kind == ElementKind.ENUM_CONSTANT) return EMPTY
            val holders = element?.let { holders(insn.receiver, it, values) } ?: emptySet()
            if (holders.isEmpty()) {
                if (insn.receiver != Receiver.TypeName) untracked(insn, insn.name, element)
                return EMPTY
            }
            return holders.fold(EMPTY) { acc, holder -> acc + read(holder to element!!, insn.isPrimitive, insn.source) }
        }

        /**
         * A field access on an object nothing here can name - a receiver from an opaque call, or a
         * field javac did not resolve. What flows through it is not followed, so the summary says
         * where it stopped instead of leaving the read empty or the write gone without a trace.
         */
        private fun untracked(insn: Insn, name: String, element: Element?) {
            val what = if (element == null) "unresolved field $name" else "field $name on an untracked receiver"
            gaps.add(Gap("unmodelled", insn.source, what))
        }

        /**
         * What a field holds, and - when nothing has written it - what it held when the method began.
         *
         * That first read is kept, not journaled, as the builder's `unassigned` keeps its box: a join
         * below finds it as the value from before the fork. On a path object it is an input, followed
         * at most [DEPTH_CAP] fields deep; an object reached at the cap is not followed further, and
         * says so with a `depth` gap rather than leaving the reads off it silently empty.
         */
        private fun read(key: Pair<Obj, Element>, isPrimitive: Boolean, at: String): AbsVal =
            (heap[key] ?: entry(key, isPrimitive, at)).value

        /**
         * What [key] held when the method began, recorded as its box without journaling it.
         *
         * A [PathObj]'s field is the caller's and held something before the call, so it is an input.
         * An object this method allocated starts with nothing in its fields. A [StandInObj]'s held
         * something too, but nothing here can say what: it is nothing, and a gap. Nothing, because
         * the builder draws that read as a box with no inputs - the receiver does not flow into it.
         */
        private fun entry(key: Pair<Obj, Element>, isPrimitive: Boolean, at: String): Cell {
            val (holder, field) = key
            val value = when (holder) {
                is PathObj -> {
                    val objs: Set<Obj> = when {
                        isPrimitive -> emptySet()
                        holder.fields.size + 1 < DEPTH_CAP -> setOf(PathObj(holder.root, holder.fields + field))
                        else -> emptySet<Obj>().also { gaps.add(Gap("depth", at, spell(holder, field))) }
                    }
                    AbsVal(setOf(FieldAtom(holder, field)), objs)
                }
                is StandInObj -> EMPTY.also {
                    gaps.add(Gap("unmodelled", at, "${qualifiedField(field)} read on an object with no tracked origin"))
                }
                is AllocObj -> EMPTY
            }
            return Cell(value).also { heap[key] = it }
        }

        /** A strong update, journaled while a fork is open so the arm that made it can undo it. */
        private fun write(key: Pair<Obj, Element>, value: AbsVal, at: String) {
            set(key, Cell(value))
            written.putIfAbsent(key, at)
        }

        /** The builder's `setFieldState`: nothing to do when the box is already there. */
        private fun set(key: Pair<Obj, Element>, cell: Cell) {
            val before = heap[key]
            if (before === cell) return
            if (openForks.isNotEmpty()) journal.add(key to before)
            heap[key] = cell
        }

        /** The builder's `endArm`: set aside what this path left, and put the fields back. */
        private fun endArm(fork: Fork) {
            val left = LinkedHashMap<Pair<Obj, Element>, Cell>()
            for (index in fork.mark until journal.size) {
                val key = journal[index].first
                if (key !in left) heap[key]?.let { left[key] = it }
            }
            for (index in journal.size - 1 downTo fork.mark) {
                val (key, before) = journal.removeAt(index)
                if (before == null) heap.remove(key) else heap[key] = before
            }
            fork.arms.add(left)
        }

        /**
         * The builder's `mergeFields` and `fieldJoin`: every field any selected arm wrote, with each
         * box that can arrive. One box is kept as it is; several are a new one taking all of them,
         * and the gate when something chose.
         *
         * Merged before the fork closes and applied after, so the result is journaled into the fork
         * around this one.
         *
         * A field of the caller's that nothing here touched before the fork still held something:
         * the arm that did not write it arrives with that, so it is the entry. This is where a summary
         * parts from the builder drawing the method as a root, which has no box for it and lets the
         * one arm's write stand alone - a summary is read in a caller, where the field may well hold
         * a value. A field of an object allocated here has no entry, and arrives only from the arms;
         * one of a [StandInObj] that existed before the fork has an entry nobody can name, which is a
         * gap. A stand-in made inside the fork - a loop's element - had no fields before it.
         */
        private fun hasEntry(obj: Obj, fork: Fork): Boolean = when (obj) {
            is PathObj -> true
            is StandInObj -> standInsMadeAt.getValue(obj) < fork.index
            is AllocObj -> false
        }

        private fun fieldJoin(insn: FieldJoin, fork: Fork, values: Array<AbsVal>) {
            val keys = LinkedHashSet<Pair<Obj, Element>>()
            insn.arms.forEach { keys.addAll(fork.arms[it].keys) }
            val merges = keys.map { key ->
                val entry = heap[key] ?: if (hasEntry(key.first, fork)) {
                    entry(key, key.second.asType().kind.isPrimitive, insn.source)
                } else {
                    null
                }
                val arrived = LinkedHashSet<Cell>()
                insn.arms.forEach { arm -> (fork.arms[arm][key] ?: entry)?.let { arrived.add(it) } }
                if (insn.withEntry && entry != null) arrived.add(entry)
                key to arrived
            }
            if (insn.closes) {
                check(openForks.lastOrNull() === fork) { "fields forked and joined out of order" }
                openForks.removeAt(openForks.size - 1)
            }
            for ((key, arrived) in merges) {
                val single = arrived.singleOrNull()
                if (single != null) {
                    set(key, single)
                    continue
                }
                val joined = arrived.fold(EMPTY) { acc, cell -> acc + cell.value }
                val gate = insn.gate?.let { AbsVal(values[it.value.index].flows) } ?: EMPTY
                set(key, Cell(joined + gate))
            }
        }
    }

    companion object {
        /** How many fields deep a path is followed: `this.a.b` is named, `this.a.b.c` is not. */
        const val DEPTH_CAP = 2

        /**
         * How many call sites name an object a callee made: `[outer, inner]`, the oldest dropped.
         * Recursion would otherwise name a new object on every pass, and never settle.
         */
        const val K_LIMIT = 2

        /** How many runs of one summary, or passes over one loop, before it is taken as unsettled. */
        const val DEFAULT_BUDGET = 20

        /** What a summary is before its first run has finished: what a cycle reads of itself. */
        private val NOTHING = Summary(EMPTY, emptyMap(), emptyMap(), emptySet())
    }
}
