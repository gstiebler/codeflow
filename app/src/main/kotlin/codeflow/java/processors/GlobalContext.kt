package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.Symbols
import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import mu.KotlinLogging
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.ElementFilter

class GlobalContext(val symbols: Symbols) {
    /**
     * Keyed by the declaration javac resolved, not by the method's name.
     *
     * A name cannot tell `Account.close()` from `Connection.close()`, nor `helper(int)` from
     * `helper(String)`, so keying by one silently inlined whichever body happened to be registered
     * last for every call to either.
     */
    private val methods = HashMap<Element, Method>()
    private val idToMemPos = HashMap<GraphNodeId, Set<MemPos>>()

    /**
     * One memory position per class, holding that class's static fields.
     *
     * A static belongs to the class rather than to any instance, so there is no object to hang it
     * on, and the block-parent chain cannot stand in: a method that writes a static is a sibling of
     * the one that reads it, not an ancestor. Giving the class a position of its own makes a static
     * the same kind of thing as an instance field, found the same way, and makes `Counter.total`
     * and a bare `total` inside `Counter` one variable rather than two.
     *
     * Keyed by the class's Element, so two classes each declaring `total` stay apart.
     */
    private val staticMemPositions = HashMap<Element, MemPos>()

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

    /** See [enumConstantMemPos]. */
    private val enumConstantMemPositions = HashMap<Element, MemPos>()
    private val logger = KotlinLogging.logger {}

    class SourceClass(val tree: ClassTree, val ctx: ProcessorContext)

    /** See [unmodelledConstructs]. */
    fun recordUnmodelled(description: String) {
        unmodelledConstructs.add(description)
    }

    /** See [unmodelledConstructs]. */
    fun unmodelled(): List<String> = unmodelledConstructs.toList()

    fun addMethod(methodTree: MethodTree, element: ExecutableElement, ctx: ProcessorContext) {
        methods[element] = Method(methodTree, ctx, element)
    }

    fun addClass(element: Element, classTree: ClassTree, ctx: ProcessorContext) {
        classes[element] = SourceClass(classTree, ctx)
    }

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

    /**
     * The objects a variable can point at, which is a set because a name is not one object.
     *
     * `if (c) p = i1; else p = i2;` leaves `p` standing for either, and a field read through it
     * finds a field on each. Keeping one position per variable could only answer with an arm, and
     * which arm was decided by the order the walk happened to take - the branch drawn straight
     * through, moved into the alias model where nothing on the diagram shows it happened.
     *
     * Empty for anything not tracked, such as an object from outside the analysed sources.
     */
    fun objectsOf(nodeId: GraphNodeId): Set<MemPos> = idToMemPos[nodeId] ?: emptySet()

    /** [type] is the class the object was constructed as - see [MemPos.type]. */
    fun createMemPos(label: String, type: Element? = null): MemPos = MemPos(label, type)

    /**
     * The class's memory position when [tree] names a static field, and null for anything else.
     *
     * Callers read it as "the holder this name has of its own", falling back to the instance they
     * were going to use otherwise. A static has no instance to fall back to - a static method has
     * no `this` at all - so without this the qualified form found no memory position, took the
     * receiver-is-from-outside path, and came out as an opaque EXTERNAL node: two reads of one
     * field drawn as one box, with the write that happened between them reaching neither.
     *
     * Only for a field these sources declare. `System.out` is a static field too, and resolves just
     * as confidently, but it is not ours to model: tracking it would draw a variable standing for
     * an object we know nothing about, where the opaque EXTERNAL node is the honest answer.
     *
     * The tree only labels the position in a log line, so the first caller's will do.
     */
    fun staticHolder(tree: Tree): MemPos? = staticHolder(symbols.element(tree), "$tree")

    /** The same question asked of the declaration directly, which is what the IR carries. */
    fun staticHolder(element: Element?, label: String): MemPos? {
        if (element?.kind != ElementKind.FIELD || Modifier.STATIC !in element.modifiers) return null
        if (!symbols.isDeclaredInSources(element)) return null
        return staticMemPositions.getOrPut(element.enclosingElement) { MemPos(label) }
    }

    /**
     * The object an enum constant is, which is one object for the whole program.
     *
     * `Size.SMALL` names the same instance at every mention, so unlike `new X(...)` - a fresh object
     * per call site - the memory position has to outlive the invocation that first asked for it.
     * The constructor is still inlined per mention, as every other call is; each run writes the
     * constant's fields again, to the same values, on this one position.
     */
    fun enumConstantMemPos(element: Element, declaration: String): MemPos =
        enumConstantMemPositions.getOrPut(element) { MemPos(declaration) }

    /** Records what a variable now points at. A later write replaces it, as the assignment does. */
    fun setObjects(nodeId: GraphNodeId, objects: Set<MemPos>) {
        logger.debug { "setObjects: $nodeId -> $objects" }
        idToMemPos[nodeId] = objects
    }
}
