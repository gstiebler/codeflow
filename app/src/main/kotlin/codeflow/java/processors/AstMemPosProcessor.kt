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
        // `Size.SMALL` is one object for the whole program, and its constructor is what fills it,
        // so it goes through the caller's block processor for the same reason `new X(...)` does.
        callerBlockProcessor.enumConstantMemPos(node, ctx)?.let { return it }
        val expr = node.expression
        // A static field is held by its class, whose name before the dot is a type and so has no
        // memory position of its own to ask for. See [GlobalContext.staticHolder].
        val exprMemPos = globalCtx.staticHolder(node) ?: expr.accept(this, ctx)
        val nodeId = JNodeId(stack, node.identifier.toString(), globalCtx.symbols.element(node), exprMemPos)
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
        callerBlockProcessor.enumConstantMemPos(node, ctx)?.let { return it }
        globalCtx.staticHolder(node)?.let { holder ->
            return globalCtx.findMemPos(JNodeId(stack, node.name.toString(), globalCtx.symbols.element(node), holder))
        }
        // A bare type name - the `System` of `System.out`, or the class in front of a static - is
        // not a value and has no memory position. That is the answer, not a surprise, so it does
        // not go through the warning below, which used to fire on ordinary code and bury the
        // static-field bug it was reporting.
        if (globalCtx.symbols.element(node)?.kind?.isClass == true) return null
        try {
            val nodeId = JNodeId(stack, node.name.toString(), globalCtx.symbols.element(node), memPos)
            return globalCtx.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
