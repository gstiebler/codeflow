package codeflow.java

import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path

class AstExprProcessor : TreeScanner<ExpressionTree?, Path>()   {

    override fun visitMemberSelect(node: MemberSelectTree, path: Path): ExpressionTree {
        return node.expression
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): ExpressionTree? {
        return null
    }

}