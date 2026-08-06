package codeflow.java.processors

import codeflow.graph.MemPos
import codeflow.graph.PosStack
import codeflow.java.ids.JNodeId
import com.sun.source.tree.ExpressionTree

/**
 * Resolves the type names of a call's arguments, so an overload can be picked out of
 * [codeflow.java.Constructors].
 *
 * For an argument that is a plain identifier the declared type is not available (the AST is only
 * parsed, never attributed), so the type is recovered from the MemPos the variable points at. That
 * only works for object variables; for literals, and for identifiers with no known MemPos such as
 * primitive locals, the type is read straight off the expression instead.
 */
fun resolveArgumentTypeNames(
    arguments: List<ExpressionTree>,
    globalCtx: GlobalContext,
    stack: PosStack,
    memPos: MemPos?,
    ctx: ProcessorContext
): List<String> {
    return arguments.map { argument ->
        val argumentName = argument.accept(NameExtractor(), ctx)
        val memPosTypeName = if (argumentName == null) {
            null
        } else {
            runCatching {
                val nodeId = JNodeId(stack, argumentName, memPos)
                globalCtx.getMemPos(nodeId).expr.accept(NameExtractor(), ctx).toString()
            }.getOrNull()
        }
        memPosTypeName ?: argument.accept(TypeNameExtractor(), ctx)
    }
}
