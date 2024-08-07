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
    private val blockProcessor: AstBlockProcessor,
    private val stack: PosStack,
    private val memPos: MemPos?
) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments

        val className = node.identifier.toString()

        val createdMemPos = globalCtx.createMemPos(identifier, graphBuilder)
        val invocationPos = ctx.getPosId(node)

        val argumentsTypes = arguments.map {
            val nodeName = it.accept(NameExtractor(), ctx)
            if (nodeName != null) {
                val nodeId = JNodeId(stack, nodeName, memPos)
                val gNode = globalCtx.getMemPos(nodeId)

                val typeName = gNode.expr.accept(NameExtractor(), ctx)
                typeName.toString()
            } else {
                it.accept(TypeNameExtractor(), ctx)
            }
        }
        val constructor = globalCtx.constructors.get(className, argumentsTypes)
        if (constructor != null) {
            val method = Method(constructor, ctx)
            val graphBlock = GraphBuilderBlock(graphBuilder, method, stack.push(ctx, node), createdMemPos, className, ctx)
            val localPos = Position(invocationPos, ctx.path)
            val blockProcessor = AstBlockProcessor(globalCtx, blockProcessor, graphBlock, localPos, createdMemPos, blockProcessor.ifConditionNode)
            val argumentNodes = arguments.map {
                it.accept(blockProcessor, ctx)
            }
            blockProcessor.invokeMethod(argumentNodes)
            graphBuilder.addCalledMethod(graphBlock)
        } else {
            logger.debug { "No constructor found: $node" }
        }

        logger.debug { "visitNewClass: $identifier, argument types: $argumentsTypes" }

        return createdMemPos
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(stack, node.identifier, exprMemPos)
        return globalCtx.getMemPos(nodeId)
    }

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
