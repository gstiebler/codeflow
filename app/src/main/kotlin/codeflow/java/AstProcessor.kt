package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner

class AstProcessor(val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Void>() {

    override fun visitAssignment(node: AssignmentTree, p: Void?): GraphNode? {
        println("visitAssignment: $node")
        return super.visitAssignment(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, p: Void?): GraphNode? {
        return graphBuilder.graph.getNode(node.name.hashCode())
    }

    override fun visitVariable(node: VariableTree, p: Void?): GraphNode {
        val name = node.name
        val newNode = graphBuilder.addVariable(name.hashCode(), name.toString())
        // The return is the init node just because the init is the last item processed in TreeScanner.visitVariable()
        val initNode = super.visitVariable(node, p)
        if (initNode != null) {
            graphBuilder.addInitializer(newNode, initNode)
        }
        return newNode
    }

    override fun visitLiteral(node: LiteralTree, p: Void?): GraphNode {
        val newNode = graphBuilder.addLiteral(node.hashCode(), node.toString())
        super.visitLiteral(node, p)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, p: Void?): GraphNode {
        val rightNode = node.leftOperand.accept(this, null)
        val leftNode = node.rightOperand.accept(this, null)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            else -> "UNKNOWN"
        }
        return graphBuilder.addBinOp(node.hashCode(), label, leftNode, rightNode)
    }
}
