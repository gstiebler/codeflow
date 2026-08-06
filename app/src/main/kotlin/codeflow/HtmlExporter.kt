package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException

/**
 * The graph as one self-contained page.
 *
 * Everything is inlined - four vendored libraries and the payload - so the file opens from disk
 * with no server and no network, and can be handed to someone else as a single artifact.
 *
 * There is deliberately no logic here beyond substitution. Anything that could make the graph wrong
 * lives in JsonExporter, where the tests are.
 */
class HtmlExporter {
    private fun asset(name: String): String =
        javaClass.getResource("/viewer/$name")?.readText()
        // A page whose libraries are missing renders as an empty canvas, which is indistinguishable
        // from a graph with no nodes. Failing here is the whole difference.
            ?: throw GraphException("viewer asset '/viewer/$name' is missing from the jar")

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        val payload = StringBuilder()
        JsonExporter().processMainMethod(mainMethod) { payload.append(it).append("\n") }

        // Literal replacement, not regex: the libraries are full of $ and \ that Regex.replace
        // would read as group references and mangle.
        val page = asset("template.html")
            .replace("/*__CYTOSCAPE__*/", asset("cytoscape.min.js"))
            .replace("/*__ELK__*/", asset("elk.bundled.js"))
            .replace("/*__CYTOSCAPE_ELK__*/", asset("cytoscape-elk.js"))
            .replace("/*__VIEWER__*/", asset("viewer.mjs"))
            .replace("/*__PAYLOAD__*/", payload.toString())

        page.lineSequence().forEach(writer)
    }
}
