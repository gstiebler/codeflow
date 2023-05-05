package codeflow.java

import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstMethodProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<GraphNode, Path>() {

    override fun visitAssignment(node: AssignmentTree, path: Path): GraphNode? {
        // left side of the assignment
        val varNode = node.variable.accept(this, path)
        // right side of the assignment
        val expressionNode = node.expression.accept(this, path)
        graphBuilder.addAssignment(varNode, expressionNode)
        return null
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: Path?): GraphNode {
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): GraphNode {
        // the identifier may or may not be initialized at this point
        val graphNode = graphBuilder.graph.getNode(node.name.hashCode())
        return graphNode ?: graphBuilder.addVariable(GraphNode.Base(path, node.name.hashCode(), node.name.toString()))
    }

    override fun visitVariable(node: VariableTree, path: Path): GraphNode? {
        // The return is the init node just because the init is the last item processed in TreeScanner.visitVariable()
        val initNode = node.initializer?.accept(this, path)
        if (initNode != null) {
            val name = node.name
            val newNode = graphBuilder.addVariable(GraphNode.Base(path, name.hashCode(), name.toString()))
            graphBuilder.addInitializer(newNode, initNode)
            return newNode
        }
        return null
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
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), p)
        return graphBuilder.callMethod(p, methodIdentifier.methodName.hashCode(), parameterExpressions)
    }

    override fun visitReturn(node: ReturnTree, p: Path): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilder.setReturnNode(newNode)
        return newNode
    }
}