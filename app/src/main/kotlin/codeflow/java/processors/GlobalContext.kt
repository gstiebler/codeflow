package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.Symbols
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
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

    /**
     * Every method named `main`, ordered by where it was written.
     *
     * Ordered because [methods] is keyed by an Element and a HashMap of those iterates in no order
     * anybody chose: on a corpus with more than one entry point this took whichever came out first.
     * Which `main` is graphed decides the entire diagram - on one real codebase it was the
     * difference between one file and hundreds - so the choice has to come out the same way twice,
     * and be reported rather than made silently. See [codeflow.java.AstReader].
     */
    fun mainMethods(): List<Method> = methods.values
        .filter { it.name.name.toString() == "main" }
        .sortedWith(compareBy({ it.ctx.path.toString() }, { it.ctx.getPosId(it.name) }))

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found: $nodeId")
    }

    /** Null for anything whose memory position is not tracked, such as an object from outside. */
    fun findMemPos(nodeId: GraphNodeId): MemPos? = idToMemPos[nodeId]

    fun createMemPos(label: Tree): MemPos {
        return MemPos(label)
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}
