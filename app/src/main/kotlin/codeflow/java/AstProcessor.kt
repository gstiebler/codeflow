package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path

class MethodProcessor(private val graphBuilder: GraphBuilder) : AstProcessor(graphBuilder) {

    // process only the declaration of the method
    override fun visitMethod(node: MethodTree, p: Path): GraphNode? {
        val parameterNodes = node.parameters.map { it.accept(this, p) }
        node.receiverParameter?.accept(this, p)
        graphBuilder.addMethod(node.name.toString(), parameterNodes, node.name.hashCode())
        return null
    }
}

open class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Path>() {

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

    override fun visitClass(node: ClassTree, p: Path): GraphNode? {
        node.modifiers?.accept(this, p)
        node.typeParameters.forEach { it.accept(this, p) }
        node.extendsClause?.accept(this, p)
        node.implementsClause.forEach { it.accept(this, p) }
        node.permitsClause.forEach { it.accept(this, p) }
        // process declararations only
        node.members.forEach { it.accept(MethodProcessor(graphBuilder), p) }
        // process whole method, including body
        node.members.forEach { it.accept(this, p) }
        return null
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, p: Path): GraphNode? {
        node.arguments.forEach {
            var paramExpr = it.accept(this, p)
        }
        // TODO: reference the new Method object by node.methodSelect.name, to get the parameters GraphNodes, and return node
        return null // TODO: return the return variable, if available
    }
}
