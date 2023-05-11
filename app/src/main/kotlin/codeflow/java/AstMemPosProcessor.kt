package codeflow.java

import codeflow.graph.GraphBuilderMethod
import codeflow.graph.MemPos
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import java.nio.file.Path

class AstMemPosProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<MemPos, Path>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, path: Path): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments
        return graphBuilder.parent.createMemPos(identifier.toString())
    }

    override fun visitMemberSelect(node: MemberSelectTree, path: Path): MemPos {
        // Example x.y.z.w
        // x.y.z is the expression
        // w is the identifier
        val expr = node.expression
        val ident = node.identifier
        val exprMemPos = expr.accept(this, path)
        val nodeId = JNodeId(ident, exprMemPos)
        val memPos = graphBuilder.parent.getMemPos(nodeId)
        return memPos
        // return MemPos()
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): MemPos? {
        // TODO: should return "this" for a local variable
        try {
            val nodeId = JNodeId(node.name, null)
            val memPos = graphBuilder.parent.getMemPos(nodeId)
            return memPos
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
