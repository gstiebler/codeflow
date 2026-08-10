package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException

/**
 * The graph as one self-contained page.
 *
 * Everything is inlined - the renderer's bundle and the payload - so the file opens from disk with
 * no server and no network, and can be handed to someone else as a single artifact.
 *
 * `bundle` names which renderer, and has no default on purpose: a default would name a file, and a
 * name that is not in the jar fails at the one moment nobody is watching. Every call site says which
 * page it wants.
 *
 * There is deliberately no logic here beyond substitution. Anything that could make the graph wrong
 * lives in JsonExporter, where the tests are. The bundles are built by `npm run build:viewer` and
 * committed, so this never needs npm.
 */
class HtmlExporter(private val bundle: String) {
    private fun asset(name: String): String =
        javaClass.getResource("/viewer/$name")?.readText()
        // A page whose libraries are missing renders as an empty canvas, which is indistinguishable
        // from a graph with no nodes. Failing here is the whole difference.
            ?: throw GraphException("viewer asset '/viewer/$name' is missing from the jar")

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        val payload = StringBuilder()
        JsonExporter().processMainMethod(mainMethod) { payload.append(it).append("\n") }

        // Literal replacement, not regex: the bundle is full of $ and \ that Regex.replace would
        // read as group references and mangle.
        //
        // The bundle goes in first and the payload last, which is the order and not a preference.
        // The payload carries labels taken from the analysed source, so a Java file holding the
        // literal text of the bundle's marker would otherwise splice a megabyte of JavaScript into
        // the middle of the graph. The other direction is safe: a minified bundle has no comments.
        val page = asset("template.html")
            .replace("/*__BUNDLE__*/", asset(bundle))
            .replace("/*__PAYLOAD__*/", payload.toString())

        page.lineSequence().forEach(writer)
    }
}
