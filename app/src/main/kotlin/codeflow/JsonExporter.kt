package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet

/**
 * The graph as JSON: the viewer's payload, and what the tests assert on.
 *
 * A block becomes a node like any other, distinguished only by carrying type METHOD, and every
 * node inside it names it as `parent`. Nesting rather than a separate list of groups, because that
 * is how a graph library models a container - React Flow's `parentId` is this field - and it is
 * what the viewer draws as a foldable box. So `parent` is the method boundary, and a payload that
 * drops it is a correct graph with every boundary silently gone.
 *
 * [sourceRoot] is what every node's `source` path is relative to - the directory AstReader was
 * pointed at - so that the lines those sources name can be read back for the page's hover.
 */
class JsonExporter(private val sourceRoot: Path) {
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

    fun processMainMethod(roots: List<GraphBuilderBlock>, writer: (String) -> Unit) {
        val nodes = ArrayList<String>()
        val edges = ArrayList<String>()
        val sources = ArrayList<String>()
        roots.forEach { collect(it, null, nodes, edges, sources) }
        val lines = linesOf(sources)

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
        writer("  ],")
        writer("""  "lines": {""")
        lines.entries.forEachIndexed { index, (file, texts) ->
            val entries = texts.entries.joinToString(", ") { (line, text) -> """"$line": "${escape(text)}"""" }
            writer("""    "${escape(file)}": {$entries}${if (index < lines.size - 1) "," else ""}""")
        }
        writer("  }")
        writer("}")
    }

    /**
     * The text of every line some node's `source` names, trimmed: file -> line -> text.
     *
     * Only those lines, so the page carries the few it can hover rather than the whole corpus. Read
     * from disk rather than from the blocks' compilation units, because a node is not always drawn
     * in a block of its own file: a class with no constructor of its own has its field initialisers
     * run inside the block that constructs it. A source that names no line - `unknown position`,
     * `offset N` - names nothing to read.
     */
    private fun linesOf(sources: List<String>): Map<String, Map<Int, String>> {
        val wanted = TreeMap<String, TreeSet<Int>>()
        for (source in sources) {
            val match = SOURCE.matchEntire(source) ?: continue
            wanted.getOrPut(match.groupValues[1]) { TreeSet() }.add(match.groupValues[2].toInt())
        }
        return wanted.mapValues { (file, numbers) ->
            val text = Files.readAllLines(sourceRoot.resolve(file))
            numbers.associateWith { text[it - 1].trim() }
        }
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
        edges: MutableList<String>,
        sources: MutableList<String>
    ) {
        val ownId = blockId(block)
        val boundary = if (block.boundary) """, "boundary": true""" else ""
        // Where this block was called from: the hover's stack. A root was called by nothing, and its
        // `openedAt` is its own declaration, so it carries none - absent meaning "the root".
        val called = if (parentId == null) "" else """, "called": "${escape(block.openedAt)}""""
        nodes.add(
            entry(ownId, block.getMethodName(), "METHOD", block.getSource(), parentId) +
                    """, "method": "${escape(block.method.key())}"$boundary$called}"""
        )
        sources.add(block.getSource())
        if (parentId != null) sources.add(block.openedAt)

        for (node in block.graph.getNodes()) {
            nodes.add(entry(nodeId(node), node.label, node.getType().toString(), node.source, ownId, node.lead) + "}")
            sources.add(node.source)
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
            collect(calledMethod, ownId, nodes, edges, sources)
        }
    }

    /**
     * The outermost block has no enclosing box, so it carries no `parent` key at all.
     *
     * `source` is on every node, including the method boxes: it is what lets the viewer answer
     * "which line is this?", and a key present on only some kinds is worse than one on none, since
     * nothing on screen distinguishes a node with no position from one the viewer failed to use.
     * It is escaped like the label because a path on Windows contains backslashes.
     *
     * `lead` is the opposite case on purpose: only a boundary call has one, and its absence is
     * the answer for every other node - the label is the whole caption. See [GraphNode.lead].
     *
     * The object is left open, so a box can add its own keys before closing it: `method`,
     * `boundary` on a boundary method's block - absent meaning no, the same rule as `lead` - and
     * `called`, the `path:line:col` of the call that opened it, on every box but a root.
     */
    private fun entry(
        id: String, label: String, type: String, source: String, parentId: String?, lead: String? = null
    ): String {
        val parent = if (parentId == null) "" else """, "parent": "$parentId""""
        val leading = if (lead == null) "" else """, "lead": "${escape(lead)}""""
        return """{"id": "$id", "label": "${escape(label)}", "type": "$type", """ +
                """"source": "${escape(source)}"$parent$leading"""
    }

    private companion object {
        /** `path:line:column`, as `ProcessorContext.location` writes it. */
        val SOURCE = Regex("""(.*):(\d+):(\d+)""")
    }
}
