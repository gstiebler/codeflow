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
        node.members.forEach { it.accept(this, p) }
        return null
    }

    override fun visitMethod(node: MethodTree, p: Path): GraphNode? {
        val newMethod = graphBuilder.addMethod(node.name.toString(), node.name.hashCode())
        val methodProcessor = AstMethodProcessor(newMethod)
        node.parameters.map {
            newMethod.addParameter(GraphNode.Base(p, it.name.hashCode(), it.name.toString()))
        }
        node.receiverParameter?.accept(this, p)
        node.body.accept(methodProcessor, p)
        return null
    }
}

