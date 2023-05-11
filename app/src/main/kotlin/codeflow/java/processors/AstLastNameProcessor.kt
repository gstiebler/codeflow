package codeflow.java.processors

import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import javax.lang.model.element.Name

class AstLastNameProcessor : TreeScanner<Name, Path>()  {

    override fun visitIdentifier(node: IdentifierTree, path: Path): Name {
        return node.name
    }

    override fun visitMemberSelect(node: MemberSelectTree, p: Path?): Name {
        // Example x.y.z.w
        // x.y.z is the expression
        // w is the identifier
        val ident = node.identifier
        val expr = node.expression
        return ident
    }

}