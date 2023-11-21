package codeflow.graph

import codeflow.java.ids.JNodeId
import codeflow.java.processors.GlobalContext
import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodTree
import mu.KotlinLogging
import javax.lang.model.element.Name

class GraphBuilderBlock(
    // val globalCtx: GlobalContext,
    val parent: GraphBuilderBlock?,
    // should it be here? or on a MethodBlock class?
    val method: Method,
    stack: PosStack,
    invocationPos: Long,
    // instance of the class that contains the method
    private val memPos: MemPos?,
    private val ctx: ProcessorContext
) {
    val graph: Graph = Graph(this)
    val localId = stack.hashCode() * 37 + 4308977
    val calledMethods = ArrayList<GraphBuilderBlock>()
    var returnNode = createReturnNode(stack, invocationPos)

    // should it be here? or on a MethodBlock class?
    val parameterNodes = method.name.parameters.map {
        // posId should be unique for each parameter, each invocation
        val posId = ctx.getPosId(it) + localId
        GraphNode.FuncParam(GraphNode.Base(JNodeId(stack, posId, it.name, memPos)))
    }

    init {
        graph.addNode(returnNode)
        parameterNodes.forEach { graph.addNode(it) }
    }

    private fun createReturnNode(stack: PosStack, invocationPos: Long): GraphNode {
        val nodeId = JNodeId(stack, invocationPos, method.name.name, memPos)
        val nodeBase = GraphNode.Base(nodeId)
        return GraphNode.MethodReturn(nodeBase)
    }

    override fun toString() = method.name.name.toString()

    fun addCalledMethod(graphBlock: GraphBuilderBlock) {
        calledMethods.add(graphBlock)
    }

    fun addLiteral(base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.Literal(base)
        graph.addNode(newNode)
        return newNode
    }

    fun addVariable(base: GraphNode.Base): GraphNode {
        val newNode = GraphNode.Variable(base)
        graph.addNode(newNode)
        return newNode
    }

    fun addBinOp(base: GraphNode.Base, leftNode: GraphNode, rightNode: GraphNode): GraphNode {
        val binOpNode = GraphNode.BinOp(base)
        graph.addNode(binOpNode)
        leftNode.addEdge(binOpNode)
        rightNode.addEdge(binOpNode)
        return binOpNode
    }

    fun addAssignment(lhsNode: GraphNode, rhsNode: GraphNode) {
        rhsNode.addEdge(lhsNode)
    }

    fun addReturnNode(newReturnNode: GraphNode) {
        newReturnNode.addEdge(returnNode)
    }

}