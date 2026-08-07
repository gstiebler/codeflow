package codeflow.java

import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.java.processors.GlobalContext
import javax.tools.StandardJavaFileManager

/**
 * What javac worked out about the analysed sources, before anything has been built from it.
 *
 * The graph is one thing built from this; the lowering to IR is another. Having the parse-and-
 * attribute step hand back a value rather than run straight on into the builder is what makes the
 * second one possible to test without rendering a diagram.
 */
class Analysis(
    val globalCtx: GlobalContext,
    private val fileManager: StandardJavaFileManager
) : AutoCloseable {

    val symbols: Symbols get() = globalCtx.symbols

    /** Every method these sources declare, in a stable order - see [GlobalContext.sourceMethods]. */
    fun methods(): List<Method> = globalCtx.sourceMethods()

    /**
     * The one method `Class#method` names.
     *
     * Fails naming the candidates rather than the request alone: a typo in a class or a method name
     * is the commonest way to get here, and an error that only repeats the name back leaves the
     * reader guessing at spelling, nesting and overloads.
     */
    fun method(spec: String): Method = methods().firstOrNull { MethodSpec.matches(it, spec) }
        ?: throw GraphException(
            "No method matching '$spec' in the analysed sources. " +
                    "Name one with --from Class#method: ${methods().joinToString(", ") { MethodSpec.of(it) }}"
        )

    override fun close() = fileManager.close()
}

/**
 * `Class#method`, the way an entry point is written on the command line.
 *
 * One implementation, because two would drift: the flag that selects a root and the error that
 * lists the alternatives have to agree about what a name means, or the list names candidates the
 * flag would not accept.
 */
object MethodSpec {

    /**
     * The class part matches either the simple name or the qualified one, so `App#run` works on a
     * corpus with one `App` and `com.example.App#run` disambiguates a corpus with two. Matched on
     * the resolved element rather than on source text, for the reason everything else here is: two
     * same-named classes in different packages are two elements.
     *
     * Overloads are not distinguished - there is nothing in the spec to distinguish them by - so
     * naming one reports the others as not taken rather than pretending the choice was exact.
     */
    fun matches(method: Method, spec: String): Boolean {
        val (className, methodName) = spec.split("#", limit = 2).takeIf { it.size == 2 }
            ?: throw GraphException("--from takes 'Class#method', not '$spec'")
        if (method.name.name.toString() != methodName) return false
        val owner = method.element.enclosingElement
        return owner.simpleName.toString() == className || owner.toString() == className
    }

    fun of(method: Method) = "${method.element.enclosingElement.simpleName}#${method.name.name}"
}
