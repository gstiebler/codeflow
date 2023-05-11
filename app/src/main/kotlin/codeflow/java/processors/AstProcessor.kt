package codeflow.java.processors

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import codeflow.java.ids.JIdentifierId
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import mu.KotlinLogging

class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    override fun visitCompilationUnit(node: CompilationUnitTree?, ctx: ProcessorContext?): GraphNode? {
        logger.info { "Package name: ${node?.packageName}" }
        return super.visitCompilationUnit(node, ctx)
    }

    override fun visitClass(node: ClassTree, ctx: ProcessorContext): GraphNode? {
        logger.info { "Class name: ${node.simpleName}" }
        node.modifiers?.accept(this, ctx)
        node.typeParameters.forEach { it.accept(this, ctx) }
        node.extendsClause?.accept(this, ctx)
        node.implementsClause.forEach { it.accept(this, ctx) }
        node.permitsClause.forEach { it.accept(this, ctx) }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.VARIABLE]?.forEach { it.accept(this, ctx) }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ctx) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitVariable(node: VariableTree, ctx: ProcessorContext): GraphNode? {
        val type = node.type
        val typeKind = type.kind
        val nodeId = JIdentifierId(node.name)
        graphBuilder.registerIsPrimitive(nodeId, typeKind == Tree.Kind.PRIMITIVE_TYPE)
        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        val newMethod = graphBuilder.addMethod(node.name.toString(), JMethodId(node.name), ctx)
        val methodProcessor = AstMethodProcessor(newMethod)
        node.parameters.map {
            newMethod.addParameter(GraphNode.Base(ctx, JNodeId(it.name, null), it.name.toString()))
        }
        node.receiverParameter?.accept(this, ctx)
        node.body.accept(methodProcessor, ctx)
        return null
    }
}

