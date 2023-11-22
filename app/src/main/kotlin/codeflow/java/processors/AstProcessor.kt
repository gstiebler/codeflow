package codeflow.java.processors

import codeflow.graph.GraphNode
import codeflow.java.Constructors
import codeflow.java.ids.JMethodId
import com.sun.source.tree.*
import com.sun.source.util.TreeScanner
import mu.KotlinLogging
import javax.lang.model.element.Name

class AstProcessor(private val globalCtx: GlobalContext) : TreeScanner<GraphNode, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    // mutable list of method names
    val methodNames = mutableListOf<Name>()

    override fun visitClass(node: ClassTree, ctx: ProcessorContext): GraphNode? {
        val className = node.simpleName.toString()
        logger.info { "Class name: $className" }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ProcessorContext(ctx, className)) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        val paramsStr = node.parameters.joinToString(", ") {
            "${it.type} ${it.name}"
        }
        logger.debug { "visitMethod: ${node.name}, params: ($paramsStr)" }
        methodNames.add(node.name)
        globalCtx.addMethod(node, JMethodId(node.name), ctx.getPosId(node), ctx)
        val isConstructor = node.name.contentEquals("<init>")
        if (isConstructor) {
            val types = node.parameters.map { it.type }
            val typesNames = types.map {
                it.accept(TypeNameExtractor(), ctx)
            }
            val className = ctx.getClassName()!!
            globalCtx.constructors.add(className, Constructors.JavaConstructor(typesNames, node))
        }
        return null
    }
}

