package codeflow.java.processors

import codeflow.graph.GraphNode
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
        logger.info { "Class name: ${node.simpleName}" }
        val memberByType = node.members.groupBy { it.kind }
        memberByType[Tree.Kind.METHOD]?.forEach { it.accept(this, ctx) }
        // TODO: throw exception if there are other types of members

        return null
    }

    override fun visitMethod(node: MethodTree, ctx: ProcessorContext): GraphNode? {
        logger.debug { "visitMethod: ${node.name}" }
        methodNames.add(node.name)
        globalCtx.addMethod(node, JMethodId(node.name), ctx.getPosId(node), ctx)
        val isConstructor = node.name.contentEquals("<init>")
        if (isConstructor) {
            val types = node.parameters.map { it.type }
            val typesNames = types.map {
                it.accept(NameExtractor(), ctx)
            }
            globalCtx.constructors[typesNames] = node
        }
        return null
    }
}

