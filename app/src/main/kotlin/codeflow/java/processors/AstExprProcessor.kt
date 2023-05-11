package codeflow.java.processors

import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path

class AstExprProcessor : TreeScanner<ExpressionTree?, ProcessorContext>()   {

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): ExpressionTree {
        return node.expression
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): ExpressionTree? {
        return null
    }

}