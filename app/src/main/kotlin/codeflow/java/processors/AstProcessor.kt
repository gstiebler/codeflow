package codeflow.java.processors

import codeflow.graph.GraphException
import codeflow.graph.GraphNode
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name

class AstProcessor(private val globalCtx: GlobalContext) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    // mutable list of method names
    val methodNames = mutableListOf<Name>()

    override fun visitClass(node: ClassTree, ctx: ProcessorContext): GraphNode? {
        logger.info { "Class name: ${node.simpleName}" }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ctx) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        // Attribution supplies a constructor for a class that declares none. Graphing it draws an
        // empty box standing for code nobody wrote, on every `new` of such a class.
        if (!globalCtx.symbols.isWrittenInSource(node)) {
            return null
        }
        val paramsStr = node.parameters.joinToString(", ") {
            "${it.type} ${it.name}"
        }
        logger.debug { "visitMethod: ${node.name}, params: ($paramsStr)" }
        methodNames.add(node.name)
        // The declaration is the key every call site will be resolved to. Without one there is
        // nothing to register the body under, so no call could ever find it and the method would
        // silently vanish from every diagram that should have shown it.
        val element = globalCtx.symbols.element(node) as? ExecutableElement
            ?: throw GraphException("javac resolved no declaration for '${node.name}' at ${ctx.location(node)}")
        globalCtx.addMethod(node, element, ctx)
        return null
    }
}
