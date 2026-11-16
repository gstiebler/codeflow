package codeflow.ir

import codeflow.graph.EdgeKind
import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode
import codeflow.graph.GraphNodeId
import codeflow.graph.MemPos
import java.util.Objects

/**
 * Which field, in the sense a read looks it up: the declaration on one holder, or - where no holder
 * can be named - the declaration in one block.
 *
 * [owner] is the holder's [MemPos.id], null for a field with none (a receiver nothing here can
 * track). That one is looked up through the block-parent chain instead of the object's fields, so
 * the block it was written in is part of the key, and only for it: [block] is null otherwise.
 * [isPrimitive] is what a box standing for the field is drawn as; it is not part of the identity.
 */
class FieldKey(val owner: Int?, val id: GraphNodeId, val block: GraphBuilderBlock?, val isPrimitive: Boolean) {
    override fun equals(other: Any?) =
        other is FieldKey && owner == other.owner && id == other.id && block === other.block

    override fun hashCode() = Objects.hash(owner, id)
}

/** What a field holds: the box a read arrives at, and the objects the value could be. */
class FieldState(val node: GraphNode, val objects: Set<MemPos>)

/**
 * A fork the builder has opened and not closed: where its journal starts, and the writes each path
 * left behind, in the order the paths ended. See [FieldFork].
 */
class OpenFork internal constructor(internal val mark: Int, internal val split: Split?) {
    internal val arms = ArrayList<Map<FieldKey, FieldState>>()

    /** The name of the path being run now, the one the next [codeflow.ir.FieldArm] will end. */
    internal fun armInProgress(): String = split!!.armNames[arms.size]
}

/**
 * The boundary-order token's split at a gated fork - an `if` - drawn on the first read of the token
 * in either arm that nothing in that arm wrote before: one box taking the token from above and the
 * condition, handing the token on into each arm on that arm's edge. The mirror of the gated join;
 * see `docs/decisions/joins.md`. Only the token: ordinary values reaching into arms stay ungated.
 *
 * [draw] makes the box from the token as it stood at the fork and the edge it arrived on.
 */
class Split(val armNames: List<String>, private val draw: (GraphNode, EdgeKind) -> GraphNode) {
    var node: GraphNode? = null
        private set

    fun node(input: () -> Pair<GraphNode, EdgeKind>): GraphNode =
        node ?: input().let { (token, kind) -> draw(token, kind) }.also { node = it }
}

/**
 * One field at a join, with each distinct box that can arrive and which path it came down.
 *
 * [arm] is the name of the one path a value arrived down, null when it came down several or the
 * paths are unnamed - the rule [codeflow.ir.Lowering] applies to locals, applied to fields.
 * [kind] overrides the edge the join would draw, for the one value that says which on its own: the
 * order token as an enclosing [Split] hands it into the arm this join is inside.
 */
class FieldMerge(val key: FieldKey, val reaching: List<Reaching>) {
    class Reaching(val state: FieldState, val arm: String?, val kind: EdgeKind? = null)
}
