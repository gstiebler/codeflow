package codeflow.java.processors

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}
    override fun visitClass(node: ClassTree, ctx: ProcessorContext): GraphNode? {
        logger.info { "Class name: ${node.simpleName}" }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ctx) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        logger.debug { "visitMethod: ${node.name}" }
        graphBuilder.addMethod(node, JMethodId(node.name), ctx.getPosId(node), ctx)
        return null
    }
}

