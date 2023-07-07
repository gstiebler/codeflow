package codeflow.java.processors

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import codeflow.java.ids.JIdentifierId
import com.sun.source.tree.ClassTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstClassProcessor(
    private val globalCtx: GlobalContext
) : TreeScanner<GraphNode, ProcessorContext>() {
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
        // memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ctx) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitVariable(node: VariableTree, ctx: ProcessorContext): GraphNode? {
        logger.debug { "visitVariable: $node" }
        val type = node.type
        val typeKind = type.kind
        val nodeId = JIdentifierId(node.name)
        globalCtx.registerIsPrimitive(nodeId, typeKind == Tree.Kind.PRIMITIVE_TYPE)
        return null
    }
}