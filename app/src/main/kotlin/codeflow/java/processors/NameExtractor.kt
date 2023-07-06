package codeflow.java.processors

import com.sun.source.tree.IdentifierTree
import com.sun.source.util.TreeScanner
import javax.lang.model.element.Name

class NameExtractor : TreeScanner<Name, ProcessorContext>() {

    override fun visitIdentifier(node: IdentifierTree, p: ProcessorContext): Name {
        return node.name
    }

}