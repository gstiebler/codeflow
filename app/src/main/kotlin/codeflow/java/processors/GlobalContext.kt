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

class GlobalContext(val symbols: Symbols) {
    /**
     * Keyed by the declaration javac resolved, not by the method's name.
     *
     * A name cannot tell `Account.close()` from `Connection.close()`, nor `helper(int)` from
     * `helper(String)`, so keying by one silently inlined whichever body happened to be registered
     * last for every call to either.
     */
    private val methods = HashMap<Element, Method>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()

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

    /** See [enumConstantMemPos]. */
    private val enumConstantMemPositions = HashMap<Element, MemPos>()
    private val logger = KotlinLogging.logger {}

    class SourceClass(val tree: ClassTree, val ctx: ProcessorContext)

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
     * Every method named `main`, ordered by where it was written.
     *
     * Ordered because [methods] is keyed by an Element and a HashMap of those iterates in no order
     * anybody chose: on a corpus with more than one entry point this took whichever came out first.
     * Which `main` is graphed decides the entire diagram - on one real codebase it was the
     * difference between one file and hundreds - so the choice has to come out the same way twice,
     * and be reported rather than made silently. See [codeflow.java.AstReader].
     */
    fun mainMethods(): List<Method> = methods.values
        .filter { it.name.name.toString() == "main" }
        .sortedWith(compareBy({ it.ctx.path.toString() }, { it.ctx.getPosId(it.name) }))

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found: $nodeId")
    }

    /** Null for anything whose memory position is not tracked, such as an object from outside. */
    fun findMemPos(nodeId: GraphNodeId): MemPos? = idToMemPos[nodeId]

    fun createMemPos(label: Tree): MemPos {
        return MemPos(label)
    }

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
    fun staticHolder(tree: Tree): MemPos? {
        val element = symbols.element(tree)
        if (element?.kind != ElementKind.FIELD || Modifier.STATIC !in element.modifiers) return null
        if (!symbols.isDeclaredInSources(element)) return null
        return staticMemPositions.getOrPut(element.enclosingElement) { MemPos(tree) }
    }

    /**
     * The object an enum constant is, which is one object for the whole program.
     *
     * `Size.SMALL` names the same instance at every mention, so unlike `new X(...)` - a fresh object
     * per call site - the memory position has to outlive the invocation that first asked for it.
     * The constructor is still inlined per mention, as every other call is; each run writes the
     * constant's fields again, to the same values, on this one position.
     */
    fun enumConstantMemPos(element: Element, declaration: Tree): MemPos =
        enumConstantMemPositions.getOrPut(element) { MemPos(declaration) }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}
