package codeflow.java.processors

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.MemPos
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstMemPosProcessor(private val graphBuilder: GraphBuilderBlock) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments
        return graphBuilder.parent.createMemPos(identifier.toString())
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos {
        val expr = node.expression
        val ident = node.identifier
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(ident, exprMemPos)
        val memPos = graphBuilder.parent.getMemPos(nodeId)
        return memPos
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
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
