package codeflow.java.processors

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.MemPos
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstMemPosProcessor(private val graphBuilder: GraphBuilderBlock, private val memPos: MemPos?) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments
        return graphBuilder.parent.createMemPos(identifier.toString())
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(node.identifier, exprMemPos)
        return graphBuilder.parent.getMemPos(nodeId)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
        if (node.name.toString() == "this") {
            return memPos
        }
        try {
            val nodeId = JNodeId(node.name, memPos)
            return graphBuilder.parent.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
