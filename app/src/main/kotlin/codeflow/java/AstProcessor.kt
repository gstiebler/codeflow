package codeflow.java

import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.BlockTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.LiteralTree
import com.sun.source.util.TreeScanner

class AstProcessor : TreeScanner<Void, Void>() {
    override fun visitAssignment(node: AssignmentTree?, p: Void?): Void {
        println("visitAssignment: $node")
        return super.visitAssignment(node, p)
    }


    override fun visitLiteral(node: LiteralTree?, p: Void?): Void? {
        println("visitLiteral: $node")
        return super.visitLiteral(node, p)
    }
    /*
    override fun visitBlock(node: BlockTree?, p: Void?): Void {
        return super.visitBlock(node, p)
    }
    */
}
