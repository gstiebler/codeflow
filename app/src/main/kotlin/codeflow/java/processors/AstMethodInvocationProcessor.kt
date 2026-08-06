package codeflow.java.processors

import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.TreeScanner
import javax.lang.model.element.Name


data class MethodRefs(
    val methodName: Name,
    val expression: ExpressionTree?
)
class AstMethodInvocationProcessor() : TreeScanner<MethodRefs, ProcessorContext>() {

    /**
     * The receiver is kept as the expression, not as a name, because it is not always a name: in
     * `items.stream().toList()` the receiver of `toList` is another invocation. Scanning it here to
     * pull a name out of it returned null for those and took the caller down with it. Whoever needs
     * a value from the receiver evaluates the expression instead.
     */
    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MethodRefs {
        return MethodRefs(node.identifier, node.expression)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MethodRefs {
        return MethodRefs(node.name, null)
    }
}