package codeflow

import codeflow.graph.EdgeKind
import codeflow.graph.Graph
import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode
import mu.KotlinLogging


class MermaidExporter() {
    private val logger = KotlinLogging.logger {}
    // The `n` prefix keeps a node id from ever being read as a subgraph id, which is prefixed `b`,
    // and makes both greppable in a snapshot.
    private fun getNodeStr(node: GraphNode) = "n${node.serial}[${node.label}]:::${node.getType()}"

    private fun getClasses() = listOf(
        "classDef LITERAL fill:#00FF0030",
        "classDef VARIABLE fill:#80808030",
        "classDef BIN_OP fill:#80808080",
        "classDef FUNC_PARAM fill:#8080FF30",
        "classDef RETURN fill:#FF808080",
        "classDef EXTERNAL fill:#FFA50040",
        // Dashed, because it is the one node type that is not a claim about the code: it marks
        // where codeflow stopped, and it should not sit on the diagram looking like the rest.
        "classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2"
    )

    /**
     * The stroke each marked edge is drawn in, keyed by kind. [EdgeKind.FLOW] is absent on purpose:
     * an edge with no entry here gets no `linkStyle` line, which is what keeps the colouring to the
     * few edges that mean something beyond "this value goes there".
     */
    private val strokes = mapOf(
        EdgeKind.TRUE to "#2e7d32",
        EdgeKind.FALSE to "#c62828",
        EdgeKind.CONDITION to "#6a6a6a"
    )

    private fun genSpaces(n: Int) = " ".repeat(n)

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        writer("```mermaid")
        writer("flowchart TD")
        val links = Links()
        processMethod(mainMethod, 2, writer, links, placeEdges(mainMethod))
        links.styles.forEach { writer(genSpaces(2) + it) }
        getClasses().forEach { writer(genSpaces(2) + it) }
        writer("```")
    }

    /**
     * The running link count and the styles collected against it.
     *
     * Mermaid numbers links across the whole flowchart in the order they are declared and
     * `linkStyle` addresses them by that number, so a style cannot be written beside the edge it
     * applies to: the index is only settled once every nested block has been walked.
     */
    private class Links {
        var count = 0
        val styles = ArrayList<String>()
    }

    /**
     * Which block each edge is written in: the innermost one enclosing *both* of its endpoints.
     *
     * Not the source's block, which is what this used to do. Mermaid decides which subgraph a node
     * belongs to from the statements naming it and the deeper claim wins, so an edge from a callee's
     * RETURN to the caller's variable - written inside the callee, because that is where its source
     * is - drew `int b = classify(...)`'s `b` *inside the classify box*. Every arrow was right and
     * the value was in the wrong method, which is the readable kind of wrong this whole tool is
     * arranged against.
     *
     * Naming a node from an enclosing block is safe and is left alone: an argument edge is written
     * in the caller and its target is the callee's parameter, and the declaration below still wins.
     * So only the edges that pointed outwards move, and they move just far enough.
     *
     * This is the rule [GraphmlExporter] follows by declaring every edge at the root - GraphML
     * requires an edge to sit in a graph enclosing both endpoints, and the root always does. Mermaid
     * does not require it, so the edges that do not need moving stay beside the block they describe.
     */
    private fun placeEdges(root: GraphBuilderBlock): Map<Int, List<Pair<GraphNode, GraphNode.Edge>>> {
        val blockOfNode = HashMap<Int, Int>()
        val ancestry = HashMap<Int, List<Int>>()

        fun index(block: GraphBuilderBlock, enclosing: List<Int>) {
            val chain = listOf(block.serial) + enclosing
            ancestry[block.serial] = chain
            block.graph.getNodes().forEach { blockOfNode[it.serial] = block.serial }
            block.calledMethods.forEach { index(it, chain) }
        }
        index(root, emptyList())

        val placed = HashMap<Int, MutableList<Pair<GraphNode, GraphNode.Edge>>>()
        fun collect(block: GraphBuilderBlock) {
            for (node in block.graph.getNodes()) {
                for (edge in node.edgesIterator()) {
                    val target = blockOfNode[edge.target.serial] ?: block.serial
                    val enclosingTarget = ancestry[target].orEmpty()
                    val owner = ancestry.getValue(block.serial).firstOrNull { it in enclosingTarget }
                        ?: root.serial
                    placed.getOrPut(owner) { ArrayList() }.add(node to edge)
                }
            }
            block.calledMethods.forEach { collect(it) }
        }
        collect(root)
        return placed
    }

    private fun processMethod(
        method: GraphBuilderBlock,
        depth: Int,
        writer: (String) -> Unit,
        links: Links,
        placed: Map<Int, List<Pair<GraphNode, GraphNode.Edge>>>
    ) {
        val nodes = method.graph.getNodes()
        writer(genSpaces(depth) + "subgraph b${method.serial}[\"${method.getMethodName()}\"]")
        logger.debug { "processMethod: ${method.getMethodName()}" }
        logger.debug { "Graph: ${method.graph}" }
        for (node in nodes) {
            writer(genSpaces(depth + 2) + getNodeStr(node))
        }
        for ((node, edge) in placed[method.serial].orEmpty()) {
            val arrow = edge.kind.label?.let { "-->|$it|" } ?: "-->"
            strokes[edge.kind]?.let {
                links.styles.add("linkStyle ${links.count} stroke:$it,color:$it")
            }
            links.count++
            writer(genSpaces(depth + 2) + "${getNodeStr(node)} $arrow ${getNodeStr(edge.target)}")
        }
        for (calledMethod in method.calledMethods) {
            processMethod(calledMethod, depth + 2, writer, links, placed)
        }
        writer(genSpaces(depth) + "end")
    }
}

