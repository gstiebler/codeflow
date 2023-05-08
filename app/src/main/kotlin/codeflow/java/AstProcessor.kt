package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import mu.KotlinLogging

class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Path>() {
    private val logger = KotlinLogging.logger {}

    override fun visitCompilationUnit(node: CompilationUnitTree?, p: Path?): GraphNode? {
        logger.info { "Package name: ${node?.packageName}" }
        return super.visitCompilationUnit(node, p)
    }

    override fun visitClass(node: ClassTree, p: Path): GraphNode? {
        logger.info { "Class name: ${node.simpleName}" }
        node.modifiers?.accept(this, p)
        node.typeParameters.forEach { it.accept(this, p) }
        node.extendsClause?.accept(this, p)
        node.implementsClause.forEach { it.accept(this, p) }
        node.permitsClause.forEach { it.accept(this, p) }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.VARIABLE]?.forEach { it.accept(this, p) }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, p) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitVariable(node: VariableTree, p: Path): GraphNode? {
        val type = node.type
        val typeKind = type.kind
        graphBuilder.registerIsPrimitive(JavaGraphNodeId(node.name), typeKind == Tree.Kind.PRIMITIVE_TYPE)
        return null
    }

    override fun visitMethod(node: MethodTree, p: Path): GraphNode? {
        val newMethod = graphBuilder.addMethod(node.name.toString(), JavaGraphNodeId(node.name))
        val methodProcessor = AstMethodProcessor(newMethod)
        node.parameters.map {
            newMethod.addParameter(GraphNode.Base(p, it.name.hashCode(), it.name.toString()))
        }
        node.receiverParameter?.accept(this, p)
        node.body.accept(methodProcessor, p)
        return null
    }
}

