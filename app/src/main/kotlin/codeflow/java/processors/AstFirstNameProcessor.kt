package codeflow.java.processors

import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import javax.lang.model.element.Name

class AstFirstNameProcessor : TreeScanner<Name, Path>()  {

    override fun visitIdentifier(node: IdentifierTree, path: Path): Name {
        return node.name
    }

}