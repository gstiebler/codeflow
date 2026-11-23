package codeflow.ir

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/*
 * What a method does, said once for every caller: which of its inputs reach which of its outputs.
 *
 * The builder answers that question again at every call site by inlining the body. A summary
 * answers it per method and is instantiated at the call site instead, which is what lets every
 * method be a root at no extra cost - see `docs/superpowers/specs/2026-11-15-method-summaries-design.md`.
 *
 * Everything here is a value: two summaries computed twice compare equal, so a fixpoint can tell
 * when it has stopped changing. Fields are named by their `Element`, never by text, for the reason
 * the rest of codeflow is - two classes each declaring `total` are two fields.
 */

/** Where an object reached through the method's interface starts. */
sealed interface Root

/** The object the method runs on. */
data object This : Root

/** The object the caller passed as parameter [i]. */
data class ParamRoot(val i: Int) : Root

/** The holder of [cls]'s static fields, which belongs to no instance - the builder's static holder. */
data class StaticRoot(val cls: Element) : Root

/**
 * The object the method returns, as an output's name only: `RET.pkg.B.v` is a field of an object
 * only the result leads to. Never an object a method runs on or reads through.
 */
data object RetRoot : Root

/** An object a value can be. */
sealed interface Obj

/**
 * An object the caller can name: a root, then the fields followed from it, at most
 * [Summarizer.DEPTH_CAP] - 1 of them so a field of it is still within the cap.
 */
data class PathObj(val root: Root, val fields: List<Element> = emptyList()) : Obj

/**
 * An object allocated where the caller cannot see it.
 *
 * Named by the chain of call sites that led to the allocation, outermost first, so the same
 * allocation reached two ways is two objects and the same one reached twice is one. Compared by
 * value, never by identity: a fixpoint recomputes it and must find the one it had.
 */
data class AllocObj(val site: List<String>, val type: Element?) : Obj

/**
 * The object a value is drawn as when nothing here knows where it came from - what an opaque call
 * returned, a loop element, a caught exception - so a field set on it has somewhere to hang, as the
 * builder's `write` gives it a position.
 *
 * Not an allocation: its fields held something before the method looked, which this cannot name.
 * A read of one, or a write that dies with the method, is a gap rather than a quiet nothing.
 */
data class StandInObj(val site: List<String>) : Obj

/** An input: something a value in the method can have come from. */
sealed interface Atom

/** What the caller bound to parameter [i]. */
data class ParamAtom(val i: Int) : Atom

/** What [field] held on [obj] when the method began. */
data class FieldAtom(val obj: PathObj, val field: Element) : Atom

/**
 * What an opaque call at [site] returned.
 *
 * The position is absolute, so it means the same call in every caller that inherits it.
 */
data class ExtResult(val label: String, val site: String) : Atom

/**
 * An abstract value: the inputs that flow into it, and the objects it can be.
 *
 * Two halves for the reason the builder's `Frame.Value` has them: a value can come from `a` and
 * still not *be* `a` - `a + 1` - and only what it can be decides whose fields a read through it
 * finds.
 */
data class AbsVal(val flows: Set<Atom> = emptySet(), val objs: Set<Obj> = emptySet()) {
    operator fun plus(other: AbsVal): AbsVal = when {
        other == EMPTY -> this
        this == EMPTY -> other
        else -> AbsVal(flows + other.flows, objs + other.objs)
    }

    companion object {
        val EMPTY = AbsVal()
    }
}

/**
 * Where a summary is incomplete, and why - so that "complete except through ..." can be said.
 *
 * [kind] is one of `dispatch`, `fixpoint`, `depth`, `unmodelled`, `body`, `alias`, `escape`; [at] is `file:line:col`.
 */
data class Gap(val kind: String, val at: String, val detail: String)

/**
 * One method's summary.
 *
 * [effects] is the final value of every field the method wrote on an object its caller can reach;
 * [sinks] is what flowed into each opaque call, keyed by its spelling `ext-arg:<label>@<site>`.
 */
data class Summary(
    val ret: AbsVal,
    val effects: Map<Pair<Obj, Element>, AbsVal>,
    val sinks: Map<String, AbsVal>,
    val gaps: Set<Gap>
)

/**
 * Every `input -> output` pair, spelled as the output document spells them, sorted.
 *
 * A field of an object the method made is spelled through the field of the caller's that holds it,
 * `this.A.b.B.v` for `this.b = x; x.v = a`, and one the result leads to through `RET`:
 * `RET.B.v` for `x.v = a; return x`.
 */
fun Summary.pairs(): List<Pair<String, String>> {
    val out = LinkedHashSet<Pair<String, String>>()
    val names = names(effects, ret.objs).names
    ret.flows.forEach { out.add(spell(it) to "RET") }
    for ((key, value) in effects) {
        val holders = (key.first as? PathObj)?.let { setOf(it) } ?: names[key.first] ?: continue
        for (holder in holders) {
            val target = spell(holder, key.second)
            value.flows.forEach { out.add(spell(it) to target) }
        }
    }
    for ((key, value) in sinks) value.flows.forEach { out.add(spell(it) to key) }
    return out.sortedWith(compareBy({ it.first }, { it.second }))
}

/**
 * What the caller can call each object [effects] leaves in one of its fields or the result is, and
 * the objects it can reach only past [Summarizer.DEPTH_CAP] - the name each was cut at.
 */
class Names(val names: Map<Obj, Set<PathObj>>, val tooDeep: Map<Obj, PathObj>)

/**
 * Follows the effects from the caller's paths, and from the result's objects [ret] as `RET`, into
 * the objects they store, one field at a time.
 *
 * A name keeps the [PathObj] cap, so a field of it is still within [Summarizer.DEPTH_CAP] and
 * spelling one never needs a deeper path than a read could have made.
 */
fun names(effects: Map<Pair<Obj, Element>, AbsVal>, ret: Set<Obj>): Names {
    val names = LinkedHashMap<Obj, LinkedHashSet<PathObj>>()
    val tooDeep = LinkedHashMap<Obj, PathObj>()
    ret.forEach { if (it !is PathObj) names.getOrPut(it) { LinkedHashSet() }.add(PathObj(RetRoot)) }
    fun namesOf(obj: Obj): Set<PathObj> = if (obj is PathObj) setOf(obj) else names[obj] ?: emptySet()
    var changed = true
    while (changed) {
        changed = false
        for ((key, value) in effects) {
            // A copy: the set being walked can be the one a name is added to.
            for (base in namesOf(key.first).toList()) {
                val name = PathObj(base.root, base.fields + key.second)
                for (obj in value.objs) {
                    if (obj is PathObj) continue
                    if (name.fields.size < Summarizer.DEPTH_CAP) {
                        if (names.getOrPut(obj) { LinkedHashSet() }.add(name)) changed = true
                    } else {
                        tooDeep.putIfAbsent(obj, name)
                    }
                }
            }
        }
    }
    return Names(names, tooDeep.filterKeys { it !in names })
}

fun spell(atom: Atom): String = when (atom) {
    is ParamAtom -> "P${atom.i}"
    is FieldAtom -> spell(atom.obj, atom.field)
    is ExtResult -> "ext:${atom.label}@${atom.site}"
}

/** A field on a path, `P0.pkg.Loan.rate`. */
fun spell(obj: PathObj, field: Element): String = spell(obj) + "." + qualifiedField(field)

fun spell(obj: PathObj): String {
    val root = when (obj.root) {
        This -> "this"
        is ParamRoot -> "P${obj.root.i}"
        is StaticRoot -> "static"
        RetRoot -> "RET"
    }
    return root + obj.fields.joinToString("") { "." + qualifiedField(it) }
}

/** The sink an opaque call at [site] is, as [Summary.sinks] keys it. */
fun extArg(label: String, site: String) = "ext-arg:$label@$site"

/** The sink an escaping `throw` at [site] is, as [Summary.sinks] keys it. */
fun throwSink(exceptionType: String, site: String) = "throw:$exceptionType@$site"

/** The declaring class's qualified name, then the field's: `pkg.Loan.rate`. Taken from the element. */
internal fun qualifiedField(field: Element): String {
    val owner = field.enclosingElement
    val ownerName = (owner as? TypeElement)?.qualifiedName?.toString() ?: owner.toString()
    return "$ownerName.${field.simpleName}"
}
