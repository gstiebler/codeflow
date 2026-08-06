package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.Symbols
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodTree
import mu.KotlinLogging
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement

class GlobalContext(val symbols: Symbols) {
    /**
     * Keyed by the declaration javac resolved, not by the method's name.
     *
     * A name cannot tell `Account.close()` from `Connection.close()`, nor `helper(int)` from
     * `helper(String)`, so keying by one silently inlined whichever body happened to be registered
     * last for every call to either.
     */
    private val methods = HashMap<Element, Method>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()
    private val logger = KotlinLogging.logger {}

    fun addMethod(methodTree: MethodTree, element: ExecutableElement, ctx: ProcessorContext) {
        methods[element] = Method(methodTree, ctx, element)
    }

    /** Null for a method outside the analysed sources, which has no body to inline. */
    fun findMethod(element: Element?): Method? = element?.let { methods[it] }

    fun getMainMethod(): Method {
        val method = methods.firstNotNullOf {
            if (it.value.name.name.toString() == "main") it.value else null
        }
        return method
    }

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found: $nodeId")
    }

    /** Null for anything whose memory position is not tracked, such as an object from outside. */
    fun findMemPos(nodeId: GraphNodeId): MemPos? = idToMemPos[nodeId]

    fun createMemPos(label: ExpressionTree): MemPos {
        return MemPos(label)
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}
