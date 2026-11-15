package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.IntersectionType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable

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
    val element: ExecutableElement
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

    /**
     * `pkg.Class#name(int,java.lang.String[])`: the class javac resolved, never text from the source.
     *
     * Overloads are told apart by their erased parameter types, the way the JVM tells them apart.
     * One spelling for every document that names a method: `--summaries` and the page's payload.
     */
    fun key(): String {
        val owner = element.enclosingElement as TypeElement
        val params = element.parameters.joinToString(",") { erase(it.asType()) }
        return "${owner.qualifiedName}#${name.name}($params)"
    }

    /** Erased by hand: no `Types` instance reaches here, and erasure is only these three cases. */
    private fun erase(type: TypeMirror): String = when (type.kind) {
        TypeKind.DECLARED -> ((type as DeclaredType).asElement() as TypeElement).qualifiedName.toString()
        TypeKind.ARRAY -> erase((type as ArrayType).componentType) + "[]"
        TypeKind.TYPEVAR -> erase((type as TypeVariable).upperBound)
        TypeKind.INTERSECTION -> erase((type as IntersectionType).bounds.first())
        else -> type.toString()
    }
}
