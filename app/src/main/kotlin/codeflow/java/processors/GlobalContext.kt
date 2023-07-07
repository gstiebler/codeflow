package codeflow.java.processors

import codeflow.graph.GraphException
import codeflow.graph.IdentifierId

class GlobalContext {
    private val isPrimitiveMap = HashMap<IdentifierId, Boolean>()


    fun registerIsPrimitive(id: IdentifierId, isPrimitive: Boolean) {
        isPrimitiveMap[id] = isPrimitive
    }

    fun isPrimitive(id: IdentifierId): Boolean {
        // return the value, or throw an exception if it's not found
        return isPrimitiveMap[id] ?: throw GraphException("Variable not found")
    }
}