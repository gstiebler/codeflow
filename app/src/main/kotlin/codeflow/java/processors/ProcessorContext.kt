package codeflow.java.processors

import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.Tree
import com.sun.source.util.SourcePositions
import java.nio.file.Path

class ProcessorContext(
    val path: Path,
    private val cut: CompilationUnitTree,
    private val sourcePos: SourcePositions
) {
    fun getStartPos(tree: Tree): Long {
        return sourcePos.getStartPosition(cut, tree)
    }

    fun getEndPos(tree: Tree): Long {
        return sourcePos.getStartPosition(cut, tree)
    }
}