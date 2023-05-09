package codeflow.java

import codeflow.graph.Graph
import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import java.nio.file.Path

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstMethodProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<GraphNode, Path>() {
    private val logger = KotlinLogging.logger {}

    override fun visitAssignment(node: AssignmentTree, path: Path): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), path)
        val lhsExpr = lhs.accept(AstExprProcessor(), path)
        val lhsMemPos = lhsExpr?.accept(AstMemPosProcessor(graphBuilder), path)
        val lhsId = JNodeId(lhsName, lhsMemPos)

        val lhsIsPrimitive = graphBuilder.parent.isPrimitive(JIdentifierId(lhsName))
        if (lhsIsPrimitive) {
            // for y.x.memberX = 8:
            // get the memory for x
            // create a node for memory for x, and memberX
            // assign the node created above with a new graph node (8)

            val lhsNode = graphBuilder.addVariable(GraphNode.Base(path, lhsId, lhsName.toString()))
            val rhsNode = rhs.accept(this, path)
            graphBuilder.addAssignment(lhsNode, rhsNode)
        } else {
            val rhsMemPos = rhs.accept(AstMemPosProcessor(graphBuilder), path)
            graphBuilder.parent.addMemPos(lhsId, rhsMemPos)
        }
        return null
    }

    override fun visitVariable(node: VariableTree, path: Path): GraphNode? {
        val typeKind = node.type.kind

        val isPrimitive = typeKind == Tree.Kind.PRIMITIVE_TYPE
        graphBuilder.parent.registerIsPrimitive(JIdentifierId(node.name), isPrimitive)
        val name = node.name

        if (isPrimitive) {
            val initNode = node.initializer?.accept(this, path)
            if (initNode != null) {
                val newNode = graphBuilder.addVariable(GraphNode.Base(path, JNodeId(name, null), name.toString()))
                graphBuilder.addAssignment(newNode, initNode)
                return newNode
            }
        } else {
            val memPos = node.initializer.accept(AstMemPosProcessor(graphBuilder), path)
            val nodeId = JNodeId(name, null)
            graphBuilder.parent.addMemPos(nodeId, memPos)
        }
        return null
    }

    override fun visitMemberSelect(node: MemberSelectTree, path: Path): GraphNode {
        val expression = node.expression
        val identifier = node.identifier
        return expression.accept(this, path)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: Path): GraphNode? {
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): GraphNode {
        val nId = JNodeId(node.name, null)
        val graphNode = graphBuilder.graph.getNode(nId)
        return graphNode
        // return graphNode ?: graphBuilder.addVariable(GraphNode.Base(path, node.name.hashCode(), node.name.toString()))
    }

    override fun visitLiteral(node: LiteralTree, path: Path): GraphNode {
        val newNode = graphBuilder.addLiteral(GraphNode.Base(path, RandomGraphNodeId(), node.toString()))
        super.visitLiteral(node, path)
        return newNode
    }

    override fun visitBinary(node: BinaryTree, path: Path): GraphNode {
        val rightNode = node.leftOperand.accept(this, path)
        val leftNode = node.rightOperand.accept(this, path)
        val label = when(node.kind.name) {
            "PLUS" -> "+"
            else -> "UNKNOWN"
        }
        val lhsName = node.leftOperand.accept(AstLastNameProcessor(), path)
        val jId = JNodeId(lhsName, null)
        return graphBuilder.addBinOp(GraphNode.Base(path, jId, label), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, p: Path): GraphNode? {
        val parameterExpressions = node.arguments.map { it.accept(this, p) }
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), p)
        return graphBuilder.callMethod(p, JMethodId(methodIdentifier.methodName), parameterExpressions)
    }

    override fun visitReturn(node: ReturnTree, p: Path): GraphNode {
        val newNode = super.visitReturn(node, p)
        graphBuilder.setReturnNode(newNode)
        return newNode
    }

    override fun visitExpressionStatement(node: ExpressionStatementTree, path: Path): GraphNode? {
        val expression = node.expression
        return super.visitExpressionStatement(node, path)
    }

    override fun visitBlock(node: BlockTree, path: Path): GraphNode? {
        val statements = node.statements
        statements.forEach() {
            logger.debug { "Processing statement: '$it'" }
            it.accept(this, path)
        }
        return null
    }
}