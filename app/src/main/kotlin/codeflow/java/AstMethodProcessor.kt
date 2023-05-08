package codeflow.java

import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import java.nio.file.Path

/**
 * This class is responsible for building the graph for a single method.
 * It's called for every method in a class.
 */
open class AstMethodProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<GraphNode, Path>() {

    override fun visitAssignment(node: AssignmentTree, path: Path): GraphNode? {
        val lhs = node.variable
        val rhs = node.expression

        val lhsName = lhs.accept(AstLastNameProcessor(), path)
        val lhsMemPos = lhs.accept(AstMemPosProcessor(graphBuilder), path)

        val lhsIsPrimitive = graphBuilder.parent.isPrimitive(JavaGraphNodeId(lhsName))
        if (lhsIsPrimitive) {
            // for y.x.memberX = 8:
            // get the memory for y
            // get the memory for x
            // associate the pair (name (memberX), mempos (x)) with a new graph node (8)

            val varNode = lhs.accept(this, path)
            val expressionNode = rhs.accept(this, path)
            graphBuilder.addAssignment(varNode, expressionNode)
        } else {
            val rhsMemPos = rhs.accept(AstMemPosProcessor(graphBuilder), path)
            graphBuilder.parent.addMemPos(JavaGraphNodeId(lhsName), rhsMemPos)
        }
        return null
    }

    override fun visitVariable(node: VariableTree, path: Path): GraphNode? {
        val typeKind = node.type.kind

        val isPrimitive = typeKind == Tree.Kind.PRIMITIVE_TYPE
        graphBuilder.parent.registerIsPrimitive(JavaGraphNodeId(node.name), isPrimitive)

        if (isPrimitive) {
            val initNode = node.initializer?.accept(this, path)
            if (initNode != null) {
                val name = node.name
                val newNode = graphBuilder.addVariable(GraphNode.Base(path, name.hashCode(), name.toString()))
                graphBuilder.addAssignment(newNode, initNode)
                return newNode
            }
        } else {
            val memPos = node.accept(AstMemPosProcessor(graphBuilder), path)
            graphBuilder.parent.addMemPos(JavaGraphNodeId(node.name), memPos)
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
        // the identifier may or may not be initialized at this point
        val graphNode = graphBuilder.graph.getNode(node.name.hashCode())
        return graphNode ?: graphBuilder.addVariable(GraphNode.Base(path, node.name.hashCode(), node.name.toString()))
    }

    override fun visitLiteral(node: LiteralTree, path: Path): GraphNode {
        val newNode = graphBuilder.addLiteral(GraphNode.Base(path, node.hashCode(), node.toString()))
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
        return graphBuilder.addBinOp(GraphNode.Base(path, node.hashCode(), label), leftNode, rightNode)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree, p: Path): GraphNode? {
        val parameterExpressions = node.arguments.map { it.accept(this, p) }
        val methodIdentifier = node.methodSelect.accept(AstMethodInvocationProcessor(), p)
        return graphBuilder.callMethod(p, JavaGraphNodeId(methodIdentifier.methodName), parameterExpressions)
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
}