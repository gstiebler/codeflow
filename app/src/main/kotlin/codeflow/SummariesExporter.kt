package codeflow

import codeflow.graph.Method
import codeflow.ir.Summary
import codeflow.ir.pairs
import java.util.TreeMap
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable

/**
 * Every method's summary as one JSON document.
 *
 * Sorted all the way down - methods, flows, gaps, counts - because the document is meant to be
 * diffed and compared across runs, and a map's iteration order is not something a reader can rely on.
 * Overloads are told apart by their erased parameter types, the way the JVM tells them apart.
 */
class SummariesExporter {

    fun export(entries: List<Pair<Method, Summary>>): String {
        val rows = entries.map { (method, summary) -> Row(methodKey(method), method.ctx.location(method.name), summary) }
            .sortedWith(compareBy({ it.method }, { it.at }))
        val gapCounts = TreeMap<String, Int>()
        rows.forEach { row -> row.summary.gaps.forEach { gapCounts.merge(it.kind, 1, Int::plus) } }
        val pairCount = rows.sumOf { it.summary.pairs().size }

        return buildString {
            appendLine("{")
            appendLine("""  "format": "codeflow-summaries",""")
            appendLine("""  "version": 1,""")
            appendLine("""  "counts": {"methods": ${rows.size}, "pairs": $pairCount, "gaps": {${
                gapCounts.entries.joinToString(", ") { """"${escape(it.key)}": ${it.value}""" }
            }}},""")
            appendLine("""  "methods": [""")
            rows.forEachIndexed { index, row ->
                val flows = row.summary.pairs().joinToString(", ") { """["${escape(it.first)}", "${escape(it.second)}"]""" }
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
    }

    private class Row(val method: String, val at: String, val summary: Summary)

    /** `pkg.Class#name(int,java.lang.String[])`: the class javac resolved, never text from the source. */
    private fun methodKey(method: Method): String {
        val owner = method.element.enclosingElement as TypeElement
        val params = method.element.parameters.joinToString(",") { erase(it.asType()) }
        return "${owner.qualifiedName}#${method.name.name}($params)"
    }

    /** Erased by hand: no `Types` instance reaches here, and erasure is only these three cases. */
    private fun erase(type: TypeMirror): String = when (type.kind) {
        TypeKind.DECLARED -> ((type as DeclaredType).asElement() as TypeElement).qualifiedName.toString()
        TypeKind.ARRAY -> erase((type as ArrayType).componentType) + "[]"
        TypeKind.TYPEVAR -> erase((type as TypeVariable).upperBound)
        TypeKind.INTERSECTION -> erase(type.let { (it as javax.lang.model.type.IntersectionType).bounds.first() })
        else -> type.toString()
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
