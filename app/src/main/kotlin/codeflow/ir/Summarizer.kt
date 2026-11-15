package codeflow.ir

import codeflow.graph.Method
import codeflow.ir.AbsVal.Companion.EMPTY
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier

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
 * again. Calls are opaque for now ([opaqueCall]), each one an [ExtResult] and a sink.
 */
class Summarizer(
    private val globalCtx: GlobalContext,
    private val bodyOf: (Method) -> MethodBody,
    private val initializersOf: (Element?) -> List<Insn>
) {
    private val summaries = LinkedHashMap<Element, Summary>()

    fun summary(method: Method): Summary = summaries.getOrPut(method.element) { Interpretation(method).run() }

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
     * is the fork's instruction, so a join can tell an object from before the fork from one made
     * inside it.
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

    /** One run of one method: its abstract heap, and what it has returned, written and called. */
    private inner class Interpretation(private val method: Method) {
        private val thisObjs: Set<Obj> =
            if (Modifier.STATIC in method.element.modifiers) emptySet() else setOf(PathObj(This))

        private val params: List<AbsVal> = method.element.parameters.mapIndexed { i, parameter ->
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
        /** The instruction each [StandInObj] was made at - the same on every pass. */
        private val standInsMadeAt = HashMap<Obj, Int>()
        private var ret = EMPTY
        private var sinks = LinkedHashMap<String, AbsVal>()
        private var gaps = LinkedHashSet<Gap>()

        fun run(): Summary {
            val element = method.element
            if (element.kind == ElementKind.CONSTRUCTOR && !delegatesToSameClass()) {
                execute(initializersOf(element.enclosingElement))
            }
            execute(bodyOf(method).instructions)
            return Summary(ret, effects(), sinks, gaps)
        }

        /** The builder's rule: `this(...)` runs the initializers in the constructor it delegates to. */
        private fun delegatesToSameClass(): Boolean = bodyOf(method).instructions
            .any { it is Delegate && it.target.enclosingElement == method.element.enclosingElement }

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
         * the state it started in until a pass changes nothing. Values only grow and there are finitely
         * many atoms and objects in one method, so it stops.
         */
        private fun execute(instructions: List<Insn>) {
            check(openForks.isEmpty()) { "an instruction list started inside a fork" }
            val hasBackEdges = instructions.withIndex().any { (index, insn) ->
                insn is Phi && insn.paths.any { it.index >= index }
            }
            val start = State(LinkedHashMap(heap), LinkedHashMap(written), ret, LinkedHashMap(sinks), LinkedHashSet(gaps))
            val values = Array(instructions.size) { EMPTY }
            val forks = HashMap<Int, Fork>()
            while (true) {
                heap = LinkedHashMap(start.heap)
                written = LinkedHashMap(start.written)
                ret = start.ret
                sinks = LinkedHashMap(start.sinks)
                gaps = LinkedHashSet(start.gaps)
                val previous = values.copyOf()
                instructions.forEachIndexed { index, insn -> values[index] = eval(insn, index, values, forks) }
                if (!hasBackEdges || values.contentEquals(previous)) break
            }
        }

        private fun eval(insn: Insn, index: Int, values: Array<AbsVal>, forks: HashMap<Int, Fork>): AbsVal {
            fun v(value: Val) = values[value.index]
            fun flows(inputs: List<Val>) = AbsVal(inputs.flatMapTo(LinkedHashSet()) { v(it).flows })
            return when (insn) {
                is Const -> EMPTY
                is Param -> params[insn.index]
                is WriteLocal -> stored(v(insn.value), insn.isPrimitive, insn, index)
                is ReadField -> readField(insn, values)
                is WriteField -> {
                    val stored = stored(v(insn.value), insn.isPrimitive, insn, index)
                    val element = insn.element
                    val holders = element?.let { holders(insn.receiver, it, values) } ?: emptySet()
                    if (holders.isEmpty()) untracked(insn, insn.name, element)
                    holders.forEach { write(it to element!!, stored, insn.source) }
                    stored
                }
                is FieldFork -> EMPTY.also { forks[index] = Fork(journal.size, index).also { openForks.add(it) } }
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
                is Call -> opaqueCall(insn.name, insn.receiver.inputs + insn.args, insn, values)
                is New -> AbsVal(
                    opaqueCall(insn.typeName, insn.args, insn, values).flows,
                    setOf(AllocObj(listOf(insn.source), insn.constructor?.enclosingElement))
                )
                is Delegate -> opaqueCall(insn.target.enclosingElement.simpleName.toString(), insn.args, insn, values)
            }
        }

        /**
         * A call whose body is not followed: its result is what the call returned plus everything
         * that went in, and everything that went in is a sink.
         *
         * That is the reachability the builder's EXTERNAL node gives. The one place calls are
         * handled, so instantiating a callee's summary replaces this and nothing else.
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
            StandInObj(listOf(insn.source)).also { standInsMadeAt.putIfAbsent(it, index) }

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
            return holders.fold(EMPTY) { acc, holder -> acc + read(holder to element!!, insn) }
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
        private fun read(key: Pair<Obj, Element>, insn: ReadField): AbsVal =
            (heap[key] ?: entry(key, insn.isPrimitive, insn.source)).value

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
    }
}
