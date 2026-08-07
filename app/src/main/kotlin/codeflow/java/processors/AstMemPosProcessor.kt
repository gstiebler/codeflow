package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import javax.lang.model.element.ElementKind
import mu.KotlinLogging

class AstMemPosProcessor(
    private val globalCtx: GlobalContext,
    private val graphBuilder: GraphBuilderBlock,
    private val callerBlockProcessor: AstBlockProcessor,
    private val stack: PosStack,
    private val memPos: MemPos?
) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    /**
     * `new X(...)` creates an object and produces it as a value, and both come from the caller's
     * own [AstBlockProcessor.constructedMemPos]. Building the constructor here as well would inline
     * its body twice, since an assignment asks for the memory position and the value separately.
     */
    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos =
        callerBlockProcessor.constructedMemPos(node, ctx)

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos? {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(stack, node.identifier, globalCtx.symbols.element(node), exprMemPos)
        // Null for a field of an object we know nothing about, and for the `System.out` half of a
        // call on a type from outside the analysed sources.
        return globalCtx.findMemPos(nodeId)
    }

    /**
     * The object a call returns, which for a method in the analysed sources is whatever its own
     * `return` named. Like [visitNewClass] this goes through the caller's block processor, so the
     * callee is inlined once and answers both what the call produced and which object that is.
     *
     * Null for a call with no body to look into, and for one that returns nothing being tracked.
     */
    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): MemPos? =
        callerBlockProcessor.invocationMemPos(node, ctx)

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
        if (node.name.toString() == "this") {
            return memPos
        }
        try {
            val nodeId = JNodeId(stack, node.name, globalCtx.symbols.element(node), memPos)
            return globalCtx.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
