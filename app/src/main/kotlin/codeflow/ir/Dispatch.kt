package codeflow.ir

import codeflow.graph.Method
import codeflow.java.processors.GlobalContext
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement

/**
 * Which body a call runs - one rule, shared by the graph builder and the summarizer.
 *
 * Two readers deciding this separately would eventually disagree, and a summary that picked a
 * different body than the graph drew is a silently wrong graph. So the decision lives here and
 * both ask it.
 */
object Dispatch {
    /**
     * Which body this call runs, which is a question about the receiver rather than about the name.
     *
     * javac resolves a call against the *declared* type, so `Base b = new Sub(); b.f(7)` comes back
     * as `Base.f` - a correct answer to a different question. Taking it drew the superclass body
     * with no sign anything had been chosen, which is the silently wrong graph in its purest form.
     *
     * Dispatch is on the objects the receiver actually holds, not on the declared type, because
     * those are already known: inlining is per call site and arguments carry their memory positions
     * into the callee, so a receiver is a concrete set here even when it is a parameter several
     * frames down. [types] is the distinct constructed types of those objects; empty means they say
     * nothing about their class, and the static answer stands.
     *
     * Only when they agree on **one** implementation. Several is a real answer this cannot draw yet
     * - it needs a box saying "one of these" - and picking one of them would be the guess this whole
     * change exists to stop. So several takes the opaque EXTERNAL path, as does a receiver whose
     * objects say nothing about their class.
     */
    fun resolve(globalCtx: GlobalContext, insn: Call, types: List<Element>): Method? {
        val declared = insn.target as? ExecutableElement ?: return null
        val statically = globalCtx.findMethod(declared)
        if (!isVirtual(insn.receiver, declared)) return statically
        if (types.isEmpty()) return statically
        return types.map { globalCtx.implementation(declared, it) }.distinct().singleOrNull()
    }

    /**
     * Whether the receiver's class gets to choose, which is what "virtual" means.
     *
     * `super.f()` names the implementation the written class has, and its receiver is the same
     * object as `this` - so dispatching on the object would send it back to the method it is
     * written inside. A `static` method has no receiver, and a `private` one is not inherited, so
     * neither can be overridden. Anything that is not a method declaration - a constructor, or a
     * name javac could not resolve - is not dispatched either.
     */
    fun isVirtual(receiver: Receiver, declared: ExecutableElement): Boolean =
        receiver != Receiver.Super &&
            declared.kind == ElementKind.METHOD &&
            Modifier.STATIC !in declared.modifiers &&
            Modifier.PRIVATE !in declared.modifiers

    /**
     * Whether some source method other than [declared] overrides it, so that a call through the
     * declared type may run a body other than the one javac named.
     *
     * Asked of each source method *seen from its own class*, the same way
     * [GlobalContext.implementation] asks it. Not on the builder's path, which dispatches on the
     * receiver's objects instead; the caller may memoise per [declared].
     */
    fun overriddenInSources(globalCtx: GlobalContext, declared: ExecutableElement): Boolean =
        globalCtx.sourceMethods().any {
            val candidate = it.element
            candidate != declared &&
                candidate.kind == ElementKind.METHOD &&
                Modifier.STATIC !in candidate.modifiers &&
                (candidate.enclosingElement as? TypeElement)
                    ?.let { type -> globalCtx.symbols.overrides(candidate, declared, type) } == true
        }
}
