package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.ids.JIdentifierId
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
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
    private val globalCtx: GlobalContext,
    private val parent: AstBlockProcessor?,
    val graphBuilderBlock: GraphBuilderBlock,
    private val pos: Position,
    private val memPos: MemPos?
) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    data class Position(val pos: Long, val path: Path)

    init {
        logger.debug { "AstBlockProcessor created: $memPos" }
    }

    override fun toString() = graphBuilderBlock.method.name.name.toString()

    private fun getStack(): PosStack {
        val parentStack = parent?.getStack() ?: PosStack()
        return parentStack.push(pos)
    }

    override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), ctx)
        val lhsIsPrimitive = globalCtx.isPrimitive(JIdentifierId(lhsName))

        val lhsParentExpr = lhs.accept(AstParentExprProcessor(), ctx)
        val lhsMemPos = if (lhsParentExpr == null) {
            memPos
        } else {
            getMemPos(lhsParentExpr, ctx)
        }
        val stack = getStack()
        val lhsId = JNodeId(stack, ctx.getPosId(lhs), lhsName, lhsMemPos)
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
        globalCtx.registerIsPrimitive(JIdentifierId(node.name), isPrimitive)
        val name = node.name

        if (node.initializer != null) {
            val variableNodeId = JNodeId(getStack(), ctx.getPosId(node), name, memPos)
            if (isPrimitive) {
                return assignPrimitive(name, variableNodeId, node.initializer, ctx)
            } else {
                assignMemPos(variableNodeId, node.initializer, ctx)
            }
        }

        return null
    }

    private fun assignPrimitive(lhsName: Name, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext): GraphNode {
        val lhsNode = graphBuilderBlock.addVariable(GraphNode.Base(lhsId))
        val rhsNode = rhs.accept(this, ctx)
        graphBuilderBlock.addAssignment(lhsNode, rhsNode)
        return lhsNode
    }

    /**
     * Assigns the mem pos of the rhs to the lhs.
     */
    private fun assignMemPos(lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val rhsMemPos = getMemPos(rhs, ctx) ?: throw GraphException("Mem pos of $rhs is null")
        globalCtx.addMemPos(lhsId, rhsMemPos)
    }

    /**
     * Returns the mem pos of the given expression.
     */
    private fun getMemPos(node: Tree?, ctx: ProcessorContext): MemPos? {
        return node?.accept(AstMemPosProcessor(globalCtx, graphBuilderBlock, this, getStack(), memPos), ctx)
    }

    private fun getNode(id: GraphNodeId): GraphNode {
        return graphBuilderBlock.graph.getNode(id) ?: memPos?.getNode(id) ?:
                throw GraphException("Identifier '${id}' not found in graph: ${graphBuilderBlock.graph}")
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): GraphNode {
        // before the dot
        val expression = node.expression
        // after the dot
        val identifier = node.identifier
        // memory position of the class instance
        val exprMemPos = getMemPos(expression, ctx)
        val nodeId = JNodeId(getStack(), ctx.getPosId(node), identifier, exprMemPos)
        return getNode(nodeId)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: ProcessorContext): GraphNode? {
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): GraphNode {
        val nId = JNodeId(getStack(), ctx.getPosId(node), node.name, memPos)
        val graphNode = getNode(nId)
        return graphNode
        // return graphNode ?: graphBuilder.addVariable(GraphNode.Base(ctx, node.name.hashCode(), node.name.toString()))
    }

    override fun visitLiteral(node: LiteralTree, ctx: ProcessorContext): GraphNode {
        val nodeId = GraphNodeId(getStack(), ctx.getPosId(node), node.toString())
        val gNode = GraphNode.Base(nodeId)
        val newNode = graphBuilderBlock.addLiteral(gNode)
        super.visitLiteral(node, ctx)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, ctx: ProcessorContext): GraphNode {
        val rightNode = node.leftOperand.accept(this, ctx)
        val leftNode = node.rightOperand.accept(this, ctx)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            "DIVIDE" -> "div"
            else -> "UNKNOWN"
        }
        val jId = GraphNodeId(getStack(), ctx.getPosId(node), label)
        return graphBuilderBlock.addBinOp(GraphNode.Base(jId), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode {
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        val method = globalCtx.getMethod(JMethodId(methodIdentifier.methodName))
        val methodArguments = node.arguments.map { it.accept(this, ctx) }

        val invocationPos = ctx.getPosId(node)
        val exprMemPos = getMemPos(methodIdentifier.expression, ctx)

        val graphBlock = GraphBuilderBlock(graphBuilderBlock, method, getStack(), invocationPos, exprMemPos, ctx)
        val localPos = Position(invocationPos, ctx.path)
        val blockProcessor = AstBlockProcessor(globalCtx, this, graphBlock, localPos, exprMemPos)
        blockProcessor.invokeMethod(methodArguments)
        graphBuilderBlock.addCalledMethod(graphBlock)
        return graphBlock.returnNode
    }

    fun invokeMethod(methodArguments: List<GraphNode>) {
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
        graphBuilderBlock.addReturnNode(newNode)
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