package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path

class AstProcessor(private val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Path>() {

    override fun visitAssignment(node: AssignmentTree, path: Path): GraphNode {
        println("visitAssignment: $node")
        return super.visitAssignment(node, path)
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
}
