package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JIdentifierId
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
import codeflow.java.ids.RandomGraphNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import java.nio.file.Path
import javax.lang.model.element.Name

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstBlockProcessor(
    private val parent: AstBlockProcessor?,
    val graphBuilderBlock: GraphBuilderBlock,
    private val pos: Position,
    private val memPos: MemPos?
) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    data class Position(val pos: Long, val path: Path)

    override fun toString() = graphBuilderBlock.method.name.name.toString()

    fun getStack(): List<String> {
        val parentStack = parent?.getStack() ?: emptyList()
        return parentStack + "${pos.path}:${pos.pos}"
    }

    override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), ctx)
        val lhsIsPrimitive = graphBuilderBlock.parent.isPrimitive(JIdentifierId(lhsName))

        val lhsParentExpr = lhs.accept(AstParentExprProcessor(), ctx)
        val lhsMemPos = if (lhsParentExpr == null) {
            memPos
        } else {
            getMemPos(lhsParentExpr, ctx)
        }
        val stack = getStack()
        val lhsId = JNodeId(lhsName, lhsMemPos, stack, ctx.getPosId(lhs))
        if (lhsIsPrimitive) {
            // val lhsId = JNodeId(lhsName, memPos)
            assignPrimitive(lhsName, lhsId, rhs, ctx)
        } else {
            assignMemPos(lhsId, rhs, ctx)
        }
        return null
    }

    override fun visitVariable(node: VariableTree, ctx: ProcessorContext): GraphNode? {
        val typeKind = node.type.kind

        val isPrimitive = typeKind == Tree.Kind.PRIMITIVE_TYPE
        graphBuilderBlock.parent.registerIsPrimitive(JIdentifierId(node.name), isPrimitive)
        val name = node.name

        if (node.initializer != null) {
            val variableNodeId = JNodeId(name, memPos, getStack(), ctx.getPosId(node))
            if (isPrimitive) {
                return assignPrimitive(name, variableNodeId, node.initializer, ctx)
            } else {
                assignMemPos(variableNodeId, node.initializer, ctx)
            }
        }

        return null
    }

    private fun assignPrimitive(lhsName: Name, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext): GraphNode {
        val lhsNode = graphBuilderBlock.addVariable(GraphNode.Base(getStack(), lhsId, lhsName.toString()))
        val rhsNode = rhs.accept(this, ctx)
        graphBuilderBlock.addAssignment(lhsNode, rhsNode)
        return lhsNode
    }

    /**
     * Assigns the mem pos of the rhs to the lhs.
     */
    private fun assignMemPos(lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val rhsMemPos = getMemPos(rhs, ctx) ?: throw GraphException("Mem pos of $rhs is null")
        graphBuilderBlock.parent.addMemPos(lhsId, rhsMemPos)
    }

    /**
     * Returns the mem pos of the given expression.
     */
    private fun getMemPos(node: ExpressionTree?, ctx: ProcessorContext): MemPos? {
        return node?.accept(AstMemPosProcessor(graphBuilderBlock, getStack(), memPos), ctx)
    }


    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): GraphNode {
        val expression = node.expression
        val identifier = node.identifier
        val exprMemPos = getMemPos(expression, ctx)
        val nodeId = JNodeId(identifier, exprMemPos, getStack(), ctx.getPosId(node))
        return graphBuilderBlock.graph.getNode(nodeId)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: ProcessorContext): GraphNode? {
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): GraphNode {
        val nId = JNodeId(node.name, memPos, getStack(), ctx.getPosId(node))
        val graphNode = graphBuilderBlock.graph.getNode(nId)
        return graphNode
        // return graphNode ?: graphBuilder.addVariable(GraphNode.Base(ctx, node.name.hashCode(), node.name.toString()))
    }

    override fun visitLiteral(node: LiteralTree, ctx: ProcessorContext): GraphNode {
        val newNode = graphBuilderBlock.addLiteral(GraphNode.Base(getStack(), RandomGraphNodeId(), node.toString()))
        super.visitLiteral(node, ctx)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, ctx: ProcessorContext): GraphNode {
        val rightNode = node.leftOperand.accept(this, ctx)
        val leftNode = node.rightOperand.accept(this, ctx)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            else -> "UNKNOWN"
        }
        val jId = RandomGraphNodeId()
        return graphBuilderBlock.addBinOp(GraphNode.Base(getStack(), jId, label), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode? {
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        val method = graphBuilderBlock.parent.getMethod(JMethodId(methodIdentifier.methodName))
        val methodArguments = node.arguments.map { it.accept(this, ctx) }

        val instanceName = methodIdentifier.instanceName
        val memPosLocal = if (instanceName == null) {
            null
        } else {
            graphBuilderBlock.parent.getMemPos(JNodeId(instanceName, memPos, getStack(), ctx.getPosId(node)))
        }

        val graphBlock = GraphBuilderBlock(graphBuilderBlock.parent, method, getStack(), memPosLocal, ctx)
        val localPos = Position(ctx.getPos(node), ctx.path)
        val blockProcessor = AstBlockProcessor(this, graphBlock, localPos, memPosLocal)
        blockProcessor.process(methodArguments)
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    fun process(methodArguments: List<GraphNode>) {
        val method = graphBuilderBlock.method
        val methodName =  method.name
        methodName.receiverParameter?.accept(this, method.ctx)
        methodName.body.accept(this, method.ctx)

        methodArguments.forEachIndexed { index, callingParameter ->
            val methodParameter = graphBuilderBlock.parameterNodes[index]
            callingParameter.addEdge(methodParameter)
        }
    }

    override fun visitReturn(node: ReturnTree, p: ProcessorContext): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilderBlock.setReturnNode(newNode)
        return newNode
    }

    override fun visitExpressionStatement(node: ExpressionStatementTree, ctx: ProcessorContext): GraphNode? {
        val expression = node.expression
        return super.visitExpressionStatement(node, ctx)
    }

    override fun visitBlock(node: BlockTree, ctx: ProcessorContext): GraphNode? {
        val statements = node.statements
        statements.forEach() {
            logger.debug { "Processing statement: '$it'" }
            it.accept(this, ctx)
        }
        return null
    }
}