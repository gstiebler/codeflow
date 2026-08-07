package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode

/**
 * The graph as JSON, in the shape Cytoscape.js consumes directly.
 *
 * A block becomes a node like any other, distinguished only by carrying type METHOD, and every
 * node inside it names it as `parent`. That is Cytoscape's compound-node model, and it is what a
 * viewer draws as a foldable box - so `parent` is the method boundary, and a payload that drops it
 * is a correct graph with every boundary silently gone.
 */
class JsonExporter {
    // The same `n` and `b` prefixes the other exporters use, so a node found in one document can be
    // found in the others by the serial that identifies it.
    private fun nodeId(node: GraphNode) = "n${node.serial}"

    private fun blockId(block: GraphBuilderBlock) = "b${block.serial}"

    /**
     * Labels are JSON syntax often enough that this is not an edge case: a string literal carries
     * its quotes, and a quote ends the string early and turns the rest of the payload into syntax
     * errors - which renders as a blank page, not as a partial graph.
     */
    private fun escape(text: String) = buildString {
        for (char in text) {
            when {
                char == '\\' -> append("\\\\")
                char == '"' -> append("\\\"")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        val nodes = ArrayList<String>()
        val edges = ArrayList<String>()
        collect(mainMethod, null, nodes, edges)

        writer("{")
        writer("""  "nodes": [""")
        nodes.forEachIndexed { index, node ->
            writer("    $node${if (index < nodes.size - 1) "," else ""}")
        }
        writer("  ],")
        writer("""  "edges": [""")
        edges.forEachIndexed { index, edge ->
            writer("    $edge${if (index < edges.size - 1) "," else ""}")
        }
        writer("  ]")
        writer("}")
    }

    /**
     * One pass over the tree filling both lists.
     *
     * Edges are a flat list with no placement rules, unlike GraphML, so an edge crossing a method
     * boundary needs no special handling at all.
     */
    private fun collect(
        block: GraphBuilderBlock,
        parentId: String?,
        nodes: MutableList<String>,
        edges: MutableList<String>
    ) {
        val ownId = blockId(block)
        nodes.add(entry(ownId, block.getMethodName(), "METHOD", block.getSource(), parentId))

        for (node in block.graph.getNodes()) {
            nodes.add(entry(nodeId(node), node.label, node.getType().toString(), node.source, ownId))
            for (edge in node.edgesIterator()) {
                // `kind` is on every edge, plain ones included, for the same reason `source` is on
                // every node: a key the viewer has to test for is one it can quietly stop using.
                edges.add(
                    """{"source": "${nodeId(node)}", "target": "${nodeId(edge.target)}", """ +
                            """"kind": "${edge.kind}"}"""
                )
            }
        }
        for (calledMethod in block.calledMethods) {
            collect(calledMethod, ownId, nodes, edges)
        }
    }

    /**
     * The outermost block has no enclosing box, so it carries no `parent` key at all.
     *
     * `source` is on every node, including the method boxes: it is what lets the viewer answer
     * "which line is this?", and a key present on only some kinds is worse than one on none, since
     * nothing on screen distinguishes a node with no position from one the viewer failed to use.
     * It is escaped like the label because a path on Windows contains backslashes.
     */
    private fun entry(id: String, label: String, type: String, source: String, parentId: String?): String {
        val parent = if (parentId == null) "" else """, "parent": "$parentId""""
        return """{"id": "$id", "label": "${escape(label)}", "type": "$type", """ +
                """"source": "${escape(source)}"$parent}"""
    }
}
