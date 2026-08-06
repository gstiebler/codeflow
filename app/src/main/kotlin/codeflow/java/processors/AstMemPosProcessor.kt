package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.MethodInvocationTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstMemPosProcessor(
    private val globalCtx: GlobalContext,
    private val graphBuilder: GraphBuilderBlock,
    private val callerBlockProcessor: AstBlockProcessor,
    private val stack: PosStack,
    private val memPos: MemPos?
) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments

        val className = node.identifier.toString()

        val createdMemPos = globalCtx.createMemPos(identifier)
        val invocationPos = ctx.getPosId(node)

        val argumentsTypes = resolveArgumentTypeNames(arguments, globalCtx, stack, memPos, ctx)
        val constructor = globalCtx.constructors.get(className, argumentsTypes)
        if (constructor != null) {
            val method = Method(constructor, ctx)
            val graphBlock = GraphBuilderBlock(graphBuilder, method, stack.push(ctx, node), createdMemPos, className, ctx)
            val localPos = Position(invocationPos, ctx.path)
            val constructorBlockProcessor =
                AstBlockProcessor(globalCtx, callerBlockProcessor, graphBlock, localPos, createdMemPos)
            // The arguments belong to the caller, so they have to be resolved there. Resolving them
            // in the constructor's own block makes an argument that happens to share a name with a
            // parameter resolve to that parameter, which connects the parameter to itself and drops
            // the edge from the value actually passed in.
            val argumentNodes = arguments.map {
                callerBlockProcessor.evaluate(it, ctx)
            }
            constructorBlockProcessor.invokeMethod(argumentNodes)
            graphBuilder.addCalledMethod(graphBlock)
        } else {
            logger.debug { "No constructor found: $node" }
        }

        logger.debug { "visitNewClass: $identifier, argument types: $argumentsTypes" }

        return createdMemPos
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos? {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(stack, node.identifier, exprMemPos)
        // Null for a field of an object we know nothing about, and for the `System.out` half of a
        // call on a type from outside the analysed sources.
        return globalCtx.findMemPos(nodeId)
    }

    /**
     * The object a call returns is not one of the memory positions being tracked: an external
     * method has no body to look into, and a local one would mean following every return. The
     * caller gives the result a memory position of its own instead.
     */
    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): MemPos? = null

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
        if (node.name.toString() == "this") {
            return memPos
        }
        try {
            val nodeId = JNodeId(stack, node.name, memPos)
            return globalCtx.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
