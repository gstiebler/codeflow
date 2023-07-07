package codeflow.java.processors

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.MemPos
import codeflow.graph.Method
import codeflow.java.ids.JNodeId
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class AstMemPosProcessor(
    private val graphBuilder: GraphBuilderBlock,
    private val blockProcesor: AstBlockProcessor,
    private val stack: List<String>,
    private val memPos: MemPos?
) : TreeScanner<MemPos, ProcessorContext>()  {
    private val logger = KotlinLogging.logger {}

    override fun visitNewClass(node: NewClassTree, ctx: ProcessorContext): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments

        val createdMemPos = graphBuilder.parentGB.createMemPos(identifier)
        val invocationPos = ctx.getPosId(node)

        val argumentTypes = arguments.map {
            val nodeName = it.accept(NameExtractor(), ctx) ?: return@map null
            val nodeId = JNodeId(stack, ctx.getPosId(it), nodeName, memPos)
            val gNode = graphBuilder.parentGB.getMemPos(nodeId)

            gNode.expr.accept(NameExtractor(), ctx)
        }
        val constructor = graphBuilder.parentGB.constructors[argumentTypes]
        if (constructor != null) {
            val method = Method(constructor, invocationPos, ctx)
            val graphBlock = GraphBuilderBlock(graphBuilder.parentGB, graphBuilder, method, stack, invocationPos,
                createdMemPos, ctx)
            val localPos = AstBlockProcessor.Position(invocationPos, ctx.path)
            val blockProcessor = AstBlockProcessor(blockProcesor, graphBlock, localPos, createdMemPos)
            val argumentNodes = arguments.map {
                it.accept(blockProcessor, ctx)
            }
            blockProcessor.invokeMethod(argumentNodes)
            graphBuilder.addCalledMethod(graphBlock)
        }

        logger.debug { "visitNewClass: $identifier, argument types: $argumentTypes" }

        return createdMemPos
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): MemPos {
        val expr = node.expression
        val exprMemPos = expr.accept(this, ctx)
        val nodeId = JNodeId(stack, ctx.getPosId(node), node.identifier, exprMemPos)
        return graphBuilder.parentGB.getMemPos(nodeId)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): MemPos? {
        if (node.name.toString() == "this") {
            return memPos
        }
        try {
            val nodeId = JNodeId(stack, ctx.getPosId(node), node.name, memPos)
            return graphBuilder.parentGB.getMemPos(nodeId)
        } catch (e: Exception) {
            logger.warn { "Exception in AstMemPosProcessor: ${e.message}" }
        }
        return null
    }
}
