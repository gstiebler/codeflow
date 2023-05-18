package codeflow.java.processors

import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import javax.lang.model.element.Name


data class MethodRefs(
    val methodName: Name,
    val instanceName: Name?
)
class AstMethodInvocationProcessor() : TreeScanner<MethodRefs, ProcessorContext>() {

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MethodRefs {
        val instance = node.expression.accept(this, ctx)
        return MethodRefs(node.identifier, instance.methodName)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MethodRefs {
        return MethodRefs(node.name, null)
    }
}