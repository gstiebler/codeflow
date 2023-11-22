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
    private var className: String? = null
    constructor(other: ProcessorContext, className: String) : this(other.path, other.cut, other.sourcePos) {
        this.className = className
    }

    fun getClassName() = className

    /**
     * Not the best place to put it, but it's good for now
     */
    fun getPosId(tree: Tree) = getPos(tree)

    private fun getPos(node: Tree) = sourcePos.getStartPosition(cut, node)
}