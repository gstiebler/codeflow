package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree
import javax.lang.model.element.Element

/**
 * A method whose body the graph can be built from.
 *
 * @property name the method tree
 * @property ctx the processor context
 * @property element what javac resolved the declaration to, and the key it is registered under.
 *   Two methods sharing a name are two elements, which is the whole reason it is here.
 */
data class Method(
    val name: MethodTree,
    val ctx: ProcessorContext,
    val element: Element
) {
    /** `<init>` says nothing on a diagram; the class whose constructor it is does. */
    fun displayName(): String {
        val methodName = name.name.toString()
        return if (methodName == "<init>") {
            "${element.enclosingElement.simpleName}.constructor"
        } else {
            methodName
        }
    }
}
