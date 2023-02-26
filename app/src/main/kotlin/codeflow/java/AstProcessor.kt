package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner

class AstProcessor(val graphBuilder: GraphBuilder) : TreeScanner<GraphNode, Void>() {

    override fun visitAssignment(node: AssignmentTree?, p: Void?): GraphNode? {
        println("visitAssignment: $node")
        return super.visitAssignment(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree?, p: Void?): GraphNode? {
        val graphNode = graphBuilder.graph.getNode(node?.name.hashCode())
        super.visitIdentifier(node, p)
        return graphNode
    }

    override fun visitVariable(node: VariableTree?, p: Void?): GraphNode {
        val name = node?.name
        val newNode = graphBuilder.addVariable(name.hashCode(), name.toString())
        val init = node?.initializer
        if (init != null) {
            val initNode = init.accept(this, null)
            // TODO: create new method that receives 2 nodes instead of IDs
            graphBuilder.addInitializer(newNode.id, initNode.id)
        }
        super.visitVariable(node, p)
        return newNode
    }

    override fun visitLiteral(node: LiteralTree?, p: Void?): GraphNode {
        val newNode = graphBuilder.addLiteral(node.hashCode(), node.toString())
        super.visitLiteral(node, p)
        return newNode
    }
}
