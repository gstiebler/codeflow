package codeflow.java.processors

import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.Tree
import com.sun.source.util.SourcePositions
import java.nio.file.Path
import kotlin.math.absoluteValue

class ProcessorContext(
    val path: Path,
    private val cut: CompilationUnitTree,
    private val sourcePos: SourcePositions
) {
    /**
     * Not the best place to put it, but it's good for now
     */
    fun getPosId(tree: Tree): Long {
        val hash = path.toString().hashCode().absoluteValue
        val pos = getPos(tree)
        return hash + pos
    }

    fun getPos(node: Tree) = sourcePos.getStartPosition(cut, node)
}