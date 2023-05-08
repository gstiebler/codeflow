package codeflow.java

import codeflow.graph.GraphBuilderMethod
import codeflow.graph.GraphNode
import codeflow.graph.MemPos
import com.sun.source.tree.IdentifierTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.util.TreeScanner
import java.nio.file.Path

class AstMemPosProcessor(private val graphBuilder: GraphBuilderMethod) : TreeScanner<MemPos, Path>()  {
    override fun visitNewClass(node: NewClassTree, path: Path): MemPos {
        val identifier = node.identifier
        val arguments = node.arguments
        return graphBuilder.parent.createMemPos(identifier.toString())
    }

    override fun visitMemberSelect(node: MemberSelectTree, path: Path): MemPos {
        // Example x.y.z.w
        // x.y.z is the expression
        // w is the identifier
        val name = node.accept(AstFirstNameProcessor(), path)
        val expr = node.expression
        val ident = node.identifier
        val memPos = graphBuilder.parent.getMemPos(name.hashCode())
        return memPos
    }

    override fun visitIdentifier(node: IdentifierTree, path: Path): MemPos {
        // should return "this" for a local variable
        return graphBuilder.parent.getMemPos(node.name.hashCode())
    }
}
