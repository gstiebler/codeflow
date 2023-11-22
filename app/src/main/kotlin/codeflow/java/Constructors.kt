package codeflow.java

import com.sun.source.tree.MethodTree

class Constructors {

    data class JavaConstructor(
        val parameterTypes: List<String>,
        val method: MethodTree
    )

    // Class name -> constructor
    private val constructors = HashMap<String, List<JavaConstructor>>()

    fun add(className: String, constructor: JavaConstructor) {
        val constructorsOfTheClass = constructors[className] ?: emptyList()
        constructors[className] = constructorsOfTheClass + constructor
    }

    fun get(className: String, parameterTypes: List<String>): MethodTree? {
        val constructorsOfTheClass = constructors[className] ?: return null
        val constructor = constructorMatch(constructorsOfTheClass, parameterTypes)
        return constructor?.method
    }

    /**
     * TODO: improve. This is a very naive implementation, there will be false positives.
     */
    private fun constructorMatch(
        constructorsOfTheClass: List<JavaConstructor>,
        parameterTypes: List<String>
    ): JavaConstructor? {
        return constructorsOfTheClass.firstOrNull {
            val inputParametersTypes = parameterTypes.map { p -> p.uppercase() }
            val constructorParametersTypes = it.parameterTypes.map { p -> p.uppercase() }
            inputParametersTypes == constructorParametersTypes
        }
    }
}