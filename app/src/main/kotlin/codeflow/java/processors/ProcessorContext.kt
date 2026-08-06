package codeflow.java.processors

import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.Tree
import com.sun.source.util.SourcePositions
import java.nio.file.Path
import javax.tools.Diagnostic

class ProcessorContext(
    val path: Path,
    private val cut: CompilationUnitTree,
    private val sourcePos: SourcePositions
) {
    private var className: String? = null
    constructor(other: ProcessorContext, className: String) : this(other.path, other.cut, other.sourcePos) {
        this.className = className
    }

    fun getClassName() = className

    /**
     * Identifies an AST node by its source span.
     *
     * The start position alone does not identify a node: in a chained expression like `a + b + c`
     * the outer and the inner `+` both start at `a`, so a start-only id gives the two operations
     * the same id and collapses them into a single node with an edge to itself.
     *
     * Not the best place to put it, but it's good for now
     */
    fun getPosId(tree: Tree): Long {
        val start = sourcePos.getStartPosition(cut, tree)
        val end = sourcePos.getEndPosition(cut, tree)
        return start * 31 + end
    }

    /**
     * Where a node came from, as `path:line:column`.
     *
     * codeflow is meant to be pointed at code nobody has read yet, so the first question about
     * any failure is which line of which file it was on. Without this an error only names a
     * node id, which is a hash, and says nothing about where to look.
     */
    fun location(tree: Tree): String {
        val start = sourcePos.getStartPosition(cut, tree)
        if (start == Diagnostic.NOPOS) {
            return "$path:unknown position"
        }
        val lineMap = cut.lineMap ?: return "$path:offset $start"
        return "$path:${lineMap.getLineNumber(start)}:${lineMap.getColumnNumber(start)}"
    }
}