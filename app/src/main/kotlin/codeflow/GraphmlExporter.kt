package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode

/**
 * The graph as GraphML, for viewers that draw a method boundary as a container you can fold.
 *
 * Nesting is the whole point of the format here: a block becomes a node with a graph inside it, and
 * that containment is what a viewer turns into a rectangle. GraphML has no separate group
 * construct to fall back on, so a flat document would lose every boundary while still being a
 * perfectly valid graph - readable, plausible, and missing the structure it was exported for.
 *
 * Deliberately plain GraphML with no vendor extensions. Editors differ in how they render a nested
 * graph, and guessing at one's private markup risks a document it refuses to open at all.
 */
class GraphmlExporter {
    // The same `n` and `b` prefixes the Mermaid rendering uses, so a node found in one document can
    // be found in the other by the serial that identifies it.
    private fun nodeId(node: GraphNode) = "n${node.serial}"

    private fun blockId(block: GraphBuilderBlock) = "b${block.serial}"

    /**
     * Labels are XML syntax often enough that this is not an edge case: `<init>` names every
     * constructor's return, `<` and `>=` are operators, and a string literal carries its quotes.
     * Written raw they produce a document a viewer refuses to open, or one it opens with the label
     * cut short at the first offending character.
     *
     * Only text content is escaped because only labels are text; the attributes here are ids this
     * class generates.
     */
    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun genSpaces(n: Int) = " ".repeat(n)

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        writer("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer("""<graphml xmlns="http://graphml.graphdrawing.org/xmlns">""")
        writer("""  <key id="label" for="node" attr.name="label" attr.type="string"/>""")
        writer("""  <key id="type" for="node" attr.name="type" attr.type="string"/>""")
        writer("""  <key id="source" for="node" attr.name="source" attr.type="string"/>""")
        writer("""  <graph id="G" edgedefault="directed">""")
        processMethod(mainMethod, 4, writer)
        for ((source, target) in collectEdges(mainMethod)) {
            writer(genSpaces(4) + """<edge source="$source" target="$target"/>""")
        }
        writer("  </graph>")
        writer("</graphml>")
    }

    private fun processMethod(block: GraphBuilderBlock, depth: Int, writer: (String) -> Unit) {
        writer(genSpaces(depth) + """<node id="${blockId(block)}">""")
        writer(genSpaces(depth + 2) + """<data key="label">${escape(block.getMethodName())}</data>""")
        writer(genSpaces(depth + 2) + """<data key="type">METHOD</data>""")
        writer(genSpaces(depth + 2) + """<data key="source">${escape(block.getSource())}</data>""")
        writer(genSpaces(depth + 2) + """<graph id="${blockId(block)}:" edgedefault="directed">""")
        for (node in block.graph.getNodes()) {
            writer(genSpaces(depth + 4) + """<node id="${nodeId(node)}">""")
            writer(genSpaces(depth + 6) + """<data key="label">${escape(node.label)}</data>""")
            writer(genSpaces(depth + 6) + """<data key="type">${node.getType()}</data>""")
            writer(genSpaces(depth + 6) + """<data key="source">${escape(node.source)}</data>""")
            writer(genSpaces(depth + 4) + "</node>")
        }
        for (calledMethod in block.calledMethods) {
            processMethod(calledMethod, depth + 4, writer)
        }
        writer(genSpaces(depth + 2) + "</graph>")
        writer(genSpaces(depth) + "</node>")
    }

    /**
     * Every edge in the tree, so they can all be declared at the root.
     *
     * An edge crosses a method boundary whenever a value does - an argument reaches a parameter, a
     * return reaches the variable it was assigned to - and GraphML requires an edge to be declared
     * in a graph that encloses both of its ends. Mermaid takes an edge wherever it is written, so
     * the existing exporter puts one under whichever block owns its source. Here the root is the
     * one place that is always an ancestor of both.
     */
    private fun collectEdges(block: GraphBuilderBlock): List<Pair<String, String>> {
        val edges = ArrayList<Pair<String, String>>()
        for (node in block.graph.getNodes()) {
            for (toNode in node.edgesIterator()) {
                edges.add(nodeId(node) to nodeId(toNode))
            }
        }
        block.calledMethods.forEach { edges.addAll(collectEdges(it)) }
        return edges
    }
}
