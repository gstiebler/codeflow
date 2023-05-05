package codeflow.java

import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path
import javax.lang.model.element.Name


data class MethodRefs(
    val methodName: Name,
    val instanceName: Name?
)
class AstMethodInvocationProcessor() : TreeScanner<MethodRefs, Path>() {

    override fun visitMemberSelect(node: MemberSelectTree, path: Path): MethodRefs {
        val instance = node.expression.accept(this, path)
        return MethodRefs(node.identifier, instance.methodName)
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): MethodRefs {
        return MethodRefs(node.name, null)
    }
}