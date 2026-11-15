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
 * [kind] is one of `dispatch`, `fixpoint`, `depth`, `unmodelled`, `body`; [at] is `file:line:col`.
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
 * Fields on an [AllocObj] are not spelled: the caller cannot name the object, so they are kept for
 * instantiation and are not an output of their own.
 */
fun Summary.pairs(): List<Pair<String, String>> {
    val out = LinkedHashSet<Pair<String, String>>()
    ret.flows.forEach { out.add(spell(it) to "RET") }
    for ((key, value) in effects) {
        val holder = key.first as? PathObj ?: continue
        val target = spell(holder, key.second)
        value.flows.forEach { out.add(spell(it) to target) }
    }
    for ((key, value) in sinks) value.flows.forEach { out.add(spell(it) to key) }
    return out.sortedWith(compareBy({ it.first }, { it.second }))
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
    }
    return root + obj.fields.joinToString("") { "." + qualifiedField(it) }
}

/** The sink an opaque call at [site] is, as [Summary.sinks] keys it. */
fun extArg(label: String, site: String) = "ext-arg:$label@$site"

/** The declaring class's qualified name, then the field's: `pkg.Loan.rate`. Taken from the element. */
private fun qualifiedField(field: Element): String {
    val owner = field.enclosingElement
    val ownerName = (owner as? TypeElement)?.qualifiedName?.toString() ?: owner.toString()
    return "$ownerName.${field.simpleName}"
}
