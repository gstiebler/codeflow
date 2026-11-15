package codeflow

import codeflow.graph.Method
import codeflow.ir.Summary
import codeflow.ir.pairs
import java.util.TreeMap

/**
 * Every method's summary as one JSON document.
 *
 * Sorted all the way down - methods, flows, gaps, counts - because the document is meant to be
 * diffed and compared across runs, and a map's iteration order is not something a reader can rely on.
 * Overloads are told apart by their erased parameter types, the way the JVM tells them apart.
 */
class SummariesExporter {

    /** The document, and the totals `main` prints on stderr - counted from the rows it lists. */
    class Document(val text: String, val methods: Int, val pairs: Int, val gaps: Int)

    fun export(entries: List<Pair<Method, Summary>>): String = document(entries).text

    fun document(entries: List<Pair<Method, Summary>>): Document {
        val rows = entries.map { (method, summary) -> Row(method.key(), method.ctx.location(method.name), summary) }
            .sortedWith(compareBy({ it.method }, { it.at }))
        val gapCounts = TreeMap<String, Int>()
        rows.forEach { row -> row.summary.gaps.forEach { gapCounts.merge(it.kind, 1, Int::plus) } }
        val pairCount = rows.sumOf { it.pairs.size }

        val text = buildString {
            appendLine("{")
            appendLine("""  "format": "codeflow-summaries",""")
            appendLine("""  "version": 1,""")
            appendLine("""  "counts": {"methods": ${rows.size}, "pairs": $pairCount, "gaps": {${
                gapCounts.entries.joinToString(", ") { """"${escape(it.key)}": ${it.value}""" }
            }}},""")
            appendLine("""  "methods": [""")
            rows.forEachIndexed { index, row ->
                val flows = row.pairs.joinToString(", ") { """["${escape(it.first)}", "${escape(it.second)}"]""" }
                val gaps = row.summary.gaps.sortedWith(compareBy({ it.at }, { it.kind }, { it.detail }))
                    .joinToString(", ") {
                        """{"kind": "${escape(it.kind)}", "at": "${escape(it.at)}", "detail": "${escape(it.detail)}"}"""
                    }
                appendLine(
                    """    {"method": "${escape(row.method)}", "at": "${escape(row.at)}", "flows": [$flows], "gaps": [$gaps]}""" +
                            if (index < rows.size - 1) "," else ""
                )
            }
            appendLine("  ]")
            append("}")
        }
        return Document(text, rows.size, pairCount, gapCounts.values.sum())
    }

    /** One method's line; its pairs are computed once, being the most expensive thing in it. */
    private class Row(val method: String, val at: String, val summary: Summary) {
        val pairs = summary.pairs()
    }

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
}
