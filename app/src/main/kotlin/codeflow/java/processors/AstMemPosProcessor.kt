package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstMemPosProcessor(
    private val globalCtx: GlobalContext,
    private val graphBuilder: GraphBuilderBlock,
    private val blockProcesor: AstBlockProcessor,
    private val stack: PosStack,
    private val memPos: MemPos?
) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments

        val createdMemPos = globalCtx.createMemPos(identifier, graphBuilder)
        val invocationPos = ctx.getPosId(node)

        val argumentTypes = arguments.map {
            val nodeName = it.accept(NameExtractor(), ctx) ?: return@map null
            val nodeId = JNodeId(stack, ctx.getPosId(it), nodeName, memPos)
            val gNode = globalCtx.getMemPos(nodeId)

            gNode.expr.accept(NameExtractor(), ctx)
        }
        val constructor = globalCtx.constructors[argumentTypes]
        if (constructor != null) {
            val method = Method(constructor, invocationPos, ctx)
            val newStack = stack.push(AstBlockProcessor.Position(invocationPos, ctx.path))
            val graphBlock = GraphBuilderBlock(graphBuilder, method, newStack, invocationPos, createdMemPos, ctx)
            val localPos = AstBlockProcessor.Position(invocationPos, ctx.path)
            val blockProcessor = AstBlockProcessor(globalCtx, blockProcesor, graphBlock, localPos, createdMemPos)
            val argumentNodes = arguments.map {
                it.accept(blockProcessor, ctx)
            }
            blockProcessor.invokeMethod(argumentNodes)
            graphBuilder.addCalledMethod(graphBlock)
        } else {
            logger.debug { "No constructor found: $node" }
        }

        logger.debug { "visitNewClass: $identifier, argument types: $argumentTypes" }

        return createdMemPos
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(stack, ctx.getPosId(node), node.identifier, exprMemPos)
        return globalCtx.getMemPos(nodeId)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
        if (node.name.toString() == "this") {
            return memPos
        }
        try {
            val nodeId = JNodeId(stack, ctx.getPosId(node), node.name, memPos)
            return globalCtx.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
