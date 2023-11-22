package codeflow.java.processors

import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.LiteralTree
import com.sun.source.tree.PrimitiveTypeTree
import com.sun.source.util.TreeScanner
import mu.KotlinLogging

class TypeNameExtractor : TreeScanner<String, ProcessorContext>() {
    private val logger = KotlinLogging.logger {}

    override fun visitIdentifier(node: IdentifierTree, p: ProcessorContext): String {
        return node.name.toString()
    }

    override fun visitPrimitiveType(node: PrimitiveTypeTree, p: ProcessorContext): String {
        return node.primitiveTypeKind.toString().uppercase()
    }

    override fun visitLiteral(node: LiteralTree, p: ProcessorContext): String {
        val fullKind = node.kind.toString()
        return fullKind.split("_").first().uppercase()
    }

}