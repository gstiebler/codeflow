package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.Symbols
import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.ElementFilter

class GlobalContext(val symbols: Symbols, val assumeSingleImpl: Boolean = false) {
    /**
     * Keyed by the declaration javac resolved, not by the method's name.
     *
     * A name cannot tell `Account.close()` from `Connection.close()`, nor `helper(int)` from
     * `helper(String)`, so keying by one silently inlined whichever body happened to be registered
     * last for every call to either.
     */
    private val methods = HashMap<Element, Method>()

    /**
     * The class declarations these sources contain, so that construction can reach what a class
     * declares *outside* any method body - its field initializers and its initializer blocks.
     *
     * The context is stored alongside the tree because it is per compilation unit: a class
     * constructed from another file has its positions and its error locations in its own.
     */
    private val classes = HashMap<Element, SourceClass>()

    /**
     * Every construct met that codeflow does not model, as `KIND at file:line:col`.
     *
     * A set, because a method is inlined once per call site and one cast written once would
     * otherwise be reported as many as there are callers - a count nobody could reconcile with the
     * source. Ordered, so the same input reports the same way twice.
     *
     * These are drawn on the graph as well, as their own node type; this is
     * what lets the run *say* how many there were, so a reader who never scrolls to one still
     * knows to look.
     */
    private val unmodelledConstructs = LinkedHashSet<String>()

    /** Assumed single-implementation dispatches made during this run, reported on stderr so nothing is silent. */
    private val assumedDispatches = LinkedHashSet<String>()

    /** Memoised single-implementation lookup per abstract/interface method. */
    private val singleImplCache = HashMap<ExecutableElement, Method?>()

    class SourceClass(val tree: ClassTree, val ctx: ProcessorContext)

    /**
     * The classes `--boundary` named. Their methods are not read: `Lowering` gives each one the
     * assumed implementation instead - see `docs/decisions/calls-and-dispatch.md`.
     */
    var boundaries: Set<Element> = emptySet()
        private set

    /** The boundaries `--pure` named: their calls take no part in the boundary order. A subset of [boundaries]. */
    private var pures: Set<Element> = emptySet()

    /**
     * Makes [classes] the boundaries, and registers what they declare with no body.
     *
     * An abstract or interface method is not registered as it is met (`AstProcessor.visitMethod`):
     * there is nothing to inline. On a boundary class there is - the assumed implementation, which
     * never reads a body - and it is what such a class is usually made of: a service reaches its
     * gateways through interface-typed fields, so a boundary that is an interface would otherwise
     * draw nothing at all. Bodyless methods of every other class stay unregistered.
     *
     * Here rather than in `AstProcessor` because the boundaries are only known once every class has
     * been registered: `--boundary` is matched against them.
     */
    fun markBoundaries(classes: Set<Element>, pure: Set<Element> = emptySet()) {
        require(classes.containsAll(pure)) { "a pure class that is not a boundary: ${pure - classes}" }
        boundaries = classes
        pures = pure
        for (boundary in classes) {
            val declaring = findClass(boundary) ?: continue
            for (member in declaring.tree.members) {
                if (member !is MethodTree || member.body != null || !symbols.isWrittenInSource(member)) continue
                val element = symbols.element(member, ElementKind.METHOD) as? ExecutableElement
                    ?: throw GraphException("javac resolved no declaration for '${member.name}' at ${declaring.ctx.location(member)}")
                addMethod(member, element, declaring.ctx)
            }
        }
    }

    /** Whether [method] is a method (not a constructor) declared directly on a boundary class. */
    fun isBoundary(method: Method): Boolean =
        method.element.kind == ElementKind.METHOD && method.element.enclosingElement in boundaries

    /** Whether [method] is a boundary `--pure` named, whose calls are not ordered. */
    fun isPure(method: Method): Boolean = isBoundary(method) && method.element.enclosingElement in pures

    /** See [unmodelledConstructs]. */
    fun recordUnmodelled(description: String) {
        unmodelledConstructs.add(description)
    }

    /** See [unmodelledConstructs]. */
    fun unmodelled(): List<String> = unmodelledConstructs.toList()

    /** Records an assumed single-implementation dispatch so the CLI reports it on stderr. */
    fun recordAssumed(description: String) {
        assumedDispatches.add(description)
    }

    /** Every assumed single-implementation dispatch made during this run. */
    fun assumed(): List<String> = assumedDispatches.toList()

    /**
     * The sole concrete implementation of [declared] in the analysed sources, or null when zero or
     * several concrete implementations exist.
     *
     * Used only when `--assume-single-impl` is enabled (`assumeSingleImpl = true`) for an interface
     * or abstract method whose receiver has no tracked constructed type (e.g. a Spring-injected
     * service field). Never picks among multiple implementations.
     */
    fun singleImplementation(declared: ExecutableElement): Method? =
        singleImplCache.getOrPut(declared) {
            val owner = declared.enclosingElement as? TypeElement ?: return@getOrPut null
            val impls = LinkedHashMap<Element, Method>()
            for (elem in classes.keys) {
                val type = elem as? TypeElement ?: continue
                if (type.kind == ElementKind.INTERFACE || type.kind == ElementKind.ANNOTATION_TYPE) continue
                if (Modifier.ABSTRACT in type.modifiers) continue
                if (!symbols.isSubtype(type, owner)) continue
                implementation(declared, type)?.let { impls[it.element] = it }
            }
            impls.values.singleOrNull()
        }

    /**
     * Methods in the analysed sources annotated as entrypoints (HTTP routes, `@Scheduled`, event/queue
     * listeners, `@CommandType`), ordered deterministically as [sourceMethods] is.
     */
    fun annotatedEntrypoints(): List<Method> = sourceMethods().filter { method ->
        codeflow.java.Semantics.entrypointLabel(method, findClass(method.element.enclosingElement)) != null
    }

    fun addMethod(methodTree: MethodTree, element: ExecutableElement, ctx: ProcessorContext) {
        methods[element] = Method(methodTree, ctx, element)
    }

    fun addClass(element: Element, classTree: ClassTree, ctx: ProcessorContext) {
        classes[element] = SourceClass(classTree, ctx)
    }

    /** Every class these sources declare, ordered by qualified name so a listing reads the same twice. */
    fun sourceClasses(): List<Element> = classes.keys.sortedBy { it.toString() }

    /** Null for a class outside the analysed sources, whose members are not ours to walk. */
    fun findClass(element: Element?): SourceClass? = element?.let { classes[it] }

    /** Null for a method outside the analysed sources, which has no body to inline. */
    fun findMethod(element: Element?): Method? = element?.let { methods[it] }

    /**
     * The body a value of [type] runs for a call to [declared], or null when these sources have none.
     *
     * Which implementation a call reaches is decided at run time by the receiver's class, so asking
     * the class rather than the call site is the whole of dispatch. The walk goes up the superclass
     * chain because a class that does not override inherits, and the inherited declaration is the
     * one registered - `Child` declaring no `shift` runs `Parent.shift`, which is what the
     * parentMethod fixture already draws.
     *
     * Null means these sources register nothing for it: an interface method whose implementation is
     * outside the corpus, or a class javac loaded from the classpath. The caller falls back to what
     * javac resolved statically, which is the opaque EXTERNAL path when that has no body either.
     */
    fun implementation(declared: ExecutableElement, type: Element): Method? {
        // `overrides` is asked *seen from* the receiver's own class, not from whichever superclass
        // the walk has reached - that is the parameter's whole purpose, and passing `current`
        // instead would ask whether Base.f overrides Base.f as seen from Base.
        val receiverType = type as? TypeElement ?: return null
        var current: TypeElement? = receiverType
        while (current != null) {
            for (candidate in ElementFilter.methodsIn(current.enclosedElements)) {
                if (candidate == declared || symbols.overrides(candidate, declared, receiverType)) {
                    return methods[candidate]
                }
            }
            current = (current.superclass as? DeclaredType)?.asElement() as? TypeElement
        }
        return null
    }

    /**
     * Every method named `main`, ordered by where it was written.
     *
     * Ordered because [methods] is keyed by an Element and a HashMap of those iterates in no order
     * anybody chose: on a corpus with more than one entry point this took whichever came out first.
     * Which `main` is graphed decides the entire diagram - on one real codebase it was the
     * difference between one file and hundreds - so the choice has to come out the same way twice,
     * and be reported rather than made silently. See [codeflow.java.AstReader].
     */
    fun mainMethods(): List<Method> = sourceMethods().filter { it.name.name.toString() == "main" }

    /**
     * Every method these sources declare, in the order above.
     *
     * Any of them can be the entry point - most Java has no `main` at all, so a tool that could
     * only start from one excluded most of its own subject matter. The same ordering applies for
     * the same reason: the list is what a failed `--from` names back to the reader, and a list that
     * comes out in a different order every run is one nobody can compare against the source.
     */
    fun sourceMethods(): List<Method> = methods.values
        .sortedWith(compareBy({ it.ctx.path.toString() }, { it.ctx.getPosId(it.name) }))
}
