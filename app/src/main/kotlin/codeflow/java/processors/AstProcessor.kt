package codeflow.java.processors

import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import javax.lang.model.element.ExecutableElement

class AstProcessor(private val globalCtx: GlobalContext) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    /**
     * Registers the methods declared here, and those of any type nested inside.
     *
     * Only the top level used to be walked, so a method on a nested or inner class was never
     * registered and every call to one resolved to nothing. That does not fail: a call with no
     * body to inline is the opaque EXTERNAL node, which is what a call into the standard library
     * looks like. The diagram came out complete and readable with real source silently redrawn as
     * a black box.
     */
    override fun visitClass(node: ClassTree, ctx: ProcessorContext): GraphNode? {
        logger.info { "Class name: ${node.simpleName}" }
        // Recorded so that construction can run what the class declares outside any method body:
        // its field initializers and its instance initializer blocks. Registering the methods
        // alone left those unreachable, and they are the only writer of most fields.
        globalCtx.symbols.element(node)?.let { globalCtx.addClass(it, node, ctx) }
        node.members.forEach {
            if (it.kind == Tree.Kind.METHOD || it is ClassTree) it.accept(this, ctx)
        }
        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        // Attribution supplies a constructor for a class that declares none. Graphing it draws an
        // empty box standing for code nobody wrote, on every `new` of such a class.
        if (!globalCtx.symbols.isWrittenInSource(node)) {
            return null
        }
        // An abstract or interface method has nothing to inline. Which implementation a call to one
        // reaches is decided at run time by the receiver's actual class, and codeflow does not track
        // that - picking one would be a guess drawn as fact. So it is left unregistered and the call
        // takes the opaque EXTERNAL path, which says the value goes in and something comes out
        // without claiming to know what happened in between.
        if (node.body == null) {
            logger.debug { "No body to inline: ${node.name}" }
            return null
        }
        val paramsStr = node.parameters.joinToString(", ") {
            "${it.type} ${it.name}"
        }
        logger.debug { "visitMethod: ${node.name}, params: ($paramsStr)" }
        // The declaration is the key every call site will be resolved to. Without one there is
        // nothing to register the body under, so no call could ever find it and the method would
        // silently vanish from every diagram that should have shown it.
        val element = globalCtx.symbols.element(node) as? ExecutableElement
            ?: throw GraphException("javac resolved no declaration for '${node.name}' at ${ctx.location(node)}")
        globalCtx.addMethod(node, element, ctx)
        return null
    }
}
