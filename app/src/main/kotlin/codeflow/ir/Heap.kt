package codeflow.ir

import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import codeflow.java.Symbols
import mu.KotlinLogging
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier

/**
 * Which objects a name can point at - the alias model, holding no graph nodes.
 *
 * Kept apart from the graph so that "what can this be?" is state of its own that can be asked and
 * tested without drawing anything. It is still filled in while the graph is drawn, not by a pass
 * of its own.
 *
 * One per run, and ids are handed out from it rather than from a JVM-global counter, so the same
 * input numbers its objects the same way however many runs came before.
 */
class Heap(private val symbols: Symbols) {

    private val logger = KotlinLogging.logger {}
    private var nextId = 0
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

    /** See [enumConstantMemPos]. */
    private val enumConstantMemPositions = HashMap<Element, MemPos>()

    /** [type] is the class the object was constructed as - see [MemPos.type]. */
    fun allocate(label: String, type: Element? = null): MemPos = MemPos(nextId++, label, type).also {
        logger.debug { "MemPos created: $it" }
    }

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

    /** Records what a variable now points at. A later write replaces it, as the assignment does. */
    fun setObjects(nodeId: GraphNodeId, objects: Set<MemPos>) {
        logger.debug { "setObjects: $nodeId -> $objects" }
        idToMemPos[nodeId] = objects
    }

    /**
     * The class's memory position when [element] is a static field, and null for anything else.
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
     * The label only names the position in a log line, so the first caller's will do.
     */
    fun staticHolder(element: Element?, label: String): MemPos? {
        if (element?.kind != ElementKind.FIELD || Modifier.STATIC !in element.modifiers) return null
        if (!symbols.isDeclaredInSources(element)) return null
        return staticMemPositions.getOrPut(element.enclosingElement) { allocate(label) }
    }

    /** See [boundaryOrderHolder]. Allocated on first use, so a run with no boundary numbers its objects as before. */
    private var boundaryOrder: MemPos? = null

    /**
     * What holds the boundary-order token ([ReadBoundaryOrder]): a static holder of its own, as if
     * one class of the sources declared it as its only static field.
     *
     * It is this object, not the name `boundaries`, that makes the token the token. A field the
     * sources declare under that name is held by its own class and keyed by its own declaration,
     * so the two cannot meet.
     */
    fun boundaryOrderHolder(): MemPos = boundaryOrder ?: allocate(BOUNDARY_ORDER).also { boundaryOrder = it }

    /** Every class's static holder, and the boundary-order token's once it exists, in no order anybody chose. */
    fun staticHolders(): Collection<MemPos> = staticMemPositions.values + listOfNotNull(boundaryOrder)

    /**
     * The object an enum constant is, which is one object for the whole program.
     *
     * `Size.SMALL` names the same instance at every mention, so unlike `new X(...)` - a fresh object
     * per call site - the memory position has to outlive the invocation that first asked for it.
     * The constructor is still inlined per mention, as every other call is; each run writes the
     * constant's fields again, to the same values, on this one position.
     */
    fun enumConstantMemPos(element: Element, declaration: String): MemPos =
        enumConstantMemPositions.getOrPut(element) { allocate(declaration) }
}
