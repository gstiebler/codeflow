package codeflow.java

import codeflow.graph.GraphBuilder
import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.LiteralTree
import com.sun.source.util.TreeScanner

class AstProcessor(val graphBuilder: GraphBuilder) : TreeScanner<Void, Void>() {

    override fun visitAssignment(node: AssignmentTree?, p: Void?): Void {
        println("visitAssignment: $node")
        return super.visitAssignment(node, p)
    }

    override fun visitLiteral(node: LiteralTree?, p: Void?): Void? {
        graphBuilder.addLiteral(node.hashCode(), node.toString())
        return super.visitLiteral(node, p)
    }
}
