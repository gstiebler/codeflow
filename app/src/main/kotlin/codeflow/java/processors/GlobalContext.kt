package codeflow.java.processors

import codeflow.graph.*
import codeflow.java.Constructors
import com.sun.source.tree.ExpressionTree
import com.sun.source.tree.MethodTree
import mu.KotlinLogging
import javax.lang.model.element.Name

class GlobalContext {
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()
    private val methods = HashMap<MethodId, Method>()
    private val idToMemPos = HashMap<GraphNodeId, MemPos>()
    // Simple class name -> simple name of its superclass. Needed to resolve `super(...)`.
    private val superclasses = HashMap<String, String>()
    val constructors = Constructors()
    private val logger = KotlinLogging.logger {}

    fun registerSuperclass(className: String, superclassName: String) {
        logger.debug { "registerSuperclass: $className extends $superclassName" }
        superclasses[className] = superclassName
    }

    fun getSuperclass(className: String?): String? = superclasses[className]

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

    /** Null for a method outside the analysed sources, which has no body to inline. */
    fun findMethod(hashCode: MethodId): Method? = methods[hashCode]

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

    fun createMemPos(label: ExpressionTree, graphBuilder: GraphBuilderBlock): MemPos {
        return MemPos(label)
    }

    fun addMemPos(nodeId: GraphNodeId, rhsMemPos: MemPos) {
        logger.debug { "addMemPos: $nodeId -> $rhsMemPos" }
        idToMemPos[nodeId] = rhsMemPos
    }
}