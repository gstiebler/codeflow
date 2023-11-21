package codeflow.java.processors

import codeflow.graph.*
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodTree
import mu.KotlinLogging
import javax.lang.model.element.Name

class GlobalContext {
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val methods = HashMap<MethodId, Method>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()
    val constructors = HashMap<List<Name>, MethodTree>()
    private val logger = KotlinLogging.logger {}

    fun registerIsPrimitive(id: IdentifierId, isPrimitive: Boolean) {
        isPrimitiveMap[id] = isPrimitive
    }

    fun isPrimitive(id: IdentifierId): Boolean {
        // return the value, or throw an exception if it's not found
        return isPrimitiveMap[id] ?: throw GraphException("Variable not found")
    }

    fun addMethod(methodTree: MethodTree, hashCode: MethodId, posId: Long, ctx: ProcessorContext) {
        methods[hashCode] = Method(methodTree, ctx)
    }

    fun getMethod(hashCode: MethodId): Method {
        return methods[hashCode] ?: throw GraphException("Method not found")
    }

    fun getMainMethod(): Method {
        val method = methods.firstNotNullOf {
            if (it.value.name.name.toString() == "main") it.value else null
        }
        return method
    }

    fun getMemPos(nodeId: GraphNodeId): MemPos {
        return idToMemPos[nodeId] ?: throw GraphException("Variable not found: $nodeId")
    }

    fun createMemPos(label: ExpressionTree, graphBuilder: GraphBuilderBlock): MemPos {
        return MemPos(label)
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}