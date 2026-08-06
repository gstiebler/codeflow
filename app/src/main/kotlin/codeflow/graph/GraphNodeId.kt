package codeflow.graph

/**
 * The key a node is looked up under, which is not the same thing as the node's identity.
 *
 * A key answers "which variable is this?" and so is deliberately coarse: every occurrence of `x`
 * has to find the same variable. Identity answers "which box on the diagram?" and is per
 * occurrence, because `y = 1; y = y + 1;` is two boxes. Those are different questions, so this
 * class only answers the first; the second is [GraphNode.serial], handed out at creation.
 *
 * Equality compares the components directly. Folding them into a number first, as this used to,
 * means two unrelated keys can collide and silently become one entry in a map - and, when the same
 * number was also used as the rendered id, two unrelated nodes could merge into a single box on a
 * diagram that gave no sign anything had happened.
 */
open class GraphNodeId(private val stack: PosStack, val label: String) {

    /** The components identity is decided on. */
    protected open fun key(): List<Any?> = listOf(label, stack)

    override fun hashCode(): Int = key().hashCode()

    override fun equals(other: Any?): Boolean = other is GraphNodeId && other.key() == key()

    override fun toString() = "GraphNodeId=('$label', stack=$stack)"
}
