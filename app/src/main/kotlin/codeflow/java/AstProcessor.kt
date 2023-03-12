package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import javax.tools.Diagnostic
import javax.tools.JavaFileObject

class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Path>() {

    override fun visitCompilationUnit(node: CompilationUnitTree?, p: Path?): GraphNode? {
        println("Package name: ${node?.packageName}")
        return super.visitCompilationUnit(node, p)
    }

    override fun visitClass(node: ClassTree, p: Path): GraphNode? {
        println("Class name: ${node.simpleName}")
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
        val methodProcessor = AstProcessorMethod(newMethod)
        node.parameters.map {
            newMethod.addParameter(GraphNode.Base(p, it.name.hashCode(), it.name.toString()))
        }
        node.receiverParameter?.accept(this, p)
        node.body.accept(methodProcessor, p)
        return null
    }
}

open class AstProcessorMethod(private val graphBuilder: GraphBuilderMethod) : TreeScanner<GraphNode, Path>() {

    override fun visitAssignment(node: AssignmentTree, path: Path): GraphNode? {
        val variable = node.variable as IdentifierTree
        val expressionNode = node.expression.accept(this, path)
        val varNode = graphBuilder.graph.getNode(variable.name.hashCode()) ?: throw Exception("Variable node not found")
        graphBuilder.addAssignment(varNode, expressionNode)
        return null
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): GraphNode? {
        return graphBuilder.graph.getNode(node.name.hashCode())
    }

    override fun visitVariable(node: VariableTree, path: Path): GraphNode {
        val name = node.name
        val newNode = graphBuilder.addVariable(GraphNode.Base(path, name.hashCode(), name.toString()))
        // The return is the init node just because the init is the last item processed in TreeScanner.visitVariable()
        val initNode = super.visitVariable(node, path)
        if (initNode != null) {
            graphBuilder.addInitializer(newNode, initNode)
        }
        return newNode
    }

    override fun visitLiteral(node: LiteralTree, path: Path): GraphNode {
        val newNode = graphBuilder.addLiteral(GraphNode.Base(path, node.hashCode(), node.toString()))
        super.visitLiteral(node, path)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, path: Path): GraphNode {
        val rightNode = node.leftOperand.accept(this, path)
        val leftNode = node.rightOperand.accept(this, path)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            else -> "UNKNOWN"
        }
        return graphBuilder.addBinOp(GraphNode.Base(path, node.hashCode(), label), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, p: Path): GraphNode? {
        val parameterExpressions = node.arguments.map { it.accept(this, p) }
        val methodIdentifier = (node.methodSelect as IdentifierTree).name
        return graphBuilder.callMethod(p, methodIdentifier.hashCode(), parameterExpressions)
    }

    override fun visitReturn(node: ReturnTree, p: Path): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilder.setReturnNode(newNode)
        return newNode
    }
}
