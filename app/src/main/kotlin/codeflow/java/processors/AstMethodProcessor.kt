package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.*
import codeflow.java.ids.JIdentifierId
import codeflow.java.ids.JMethodId
import codeflow.java.ids.JNodeId
import codeflow.java.ids.RandomGraphNodeId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import javax.lang.model.element.Name

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstMethodProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    override fun visitAssignment(node: AssignmentTree, ctx: ProcessorContext): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), ctx)
        val lhsExpr = lhs.accept(AstExprProcessor(), ctx)
        val lhsMemPos = getMemPos(lhsExpr, ctx)
        val lhsId = JNodeId(lhsName, lhsMemPos)

        val lhsIsPrimitive = graphBuilder.parent.isPrimitive(JIdentifierId(lhsName))
        if (lhsIsPrimitive) {
            assignPrimitive(lhs, lhsName, lhsId, rhs, ctx)
        } else {
            assignMemPos(lhsId, rhs, ctx)
        }
        return null
    }

    override fun visitVariable(node: VariableTree, ctx: ProcessorContext): GraphNode? {
        val typeKind = node.type.kind

        val isPrimitive = typeKind == Tree.Kind.PRIMITIVE_TYPE
        graphBuilder.parent.registerIsPrimitive(JIdentifierId(node.name), isPrimitive)
        val name = node.name

        if (node.initializer != null) {
            val variableNodeId = JNodeId(name, null)
            if (isPrimitive) {
                return assignPrimitive(node, name, variableNodeId, node.initializer, ctx)
            } else {
                assignMemPos(variableNodeId, node.initializer, ctx)
            }
        }

        return null
    }

    private fun assignPrimitive(lhsTree: Tree, lhsName: Name, lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext): GraphNode {
        val lhsNode = graphBuilder.addVariable(GraphNode.Base(ctx.getPosId(lhsTree), lhsId, lhsName.toString()))
        val rhsNode = rhs.accept(this, ctx)
        graphBuilder.addAssignment(lhsNode, rhsNode)
        return lhsNode
    }

    private fun assignMemPos(lhsId: JNodeId, rhs: ExpressionTree, ctx: ProcessorContext) {
        val rhsMemPos = getMemPos(rhs, ctx) ?: throw GraphException("Could not find mem pos for $rhs")
        graphBuilder.parent.addMemPos(lhsId, rhsMemPos)
    }

    private fun getMemPos(node: ExpressionTree?, ctx: ProcessorContext): MemPos? {
        return node?.accept(AstMemPosProcessor(graphBuilder), ctx)
    }

    override fun visitMemberSelect(node: MemberSelectTree, ctx: ProcessorContext): GraphNode {
        val expression = node.expression
        val identifier = node.identifier
        val exprMemPos = getMemPos(expression, ctx)
        val nodeId = JNodeId(identifier, exprMemPos)
        return graphBuilder.graph.getNode(nodeId)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: ProcessorContext): GraphNode? {
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, ctx: ProcessorContext): GraphNode {
        val nId = JNodeId(node.name, null)
        val graphNode = graphBuilder.graph.getNode(nId)
        return graphNode
        // return graphNode ?: graphBuilder.addVariable(GraphNode.Base(ctx, node.name.hashCode(), node.name.toString()))
    }

    override fun visitLiteral(node: LiteralTree, ctx: ProcessorContext): GraphNode {
        val newNode = graphBuilder.addLiteral(GraphNode.Base(ctx.getPosId(node), RandomGraphNodeId(), node.toString()))
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
        return graphBuilder.addBinOp(GraphNode.Base(ctx.getPosId(node), jId, label), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, ctx: ProcessorContext): GraphNode? {
        val parameterExpressions = node.arguments.map { it.accept(this, ctx) }
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), ctx)
        return graphBuilder.callMethod(ctx.getPosId(node), JMethodId(methodIdentifier.methodName), parameterExpressions)
    }

    override fun visitReturn(node: ReturnTree, p: ProcessorContext): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilder.setReturnNode(newNode)
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