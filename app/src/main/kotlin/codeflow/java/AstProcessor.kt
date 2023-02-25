package codeflow.java

import com.sun.source.tree.AssignmentTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.util.TreeScanner

class AstProcessor : TreeScanner<Void, Void>() {
    override fun visitAssignment(node: AssignmentTree?, p: Void?): Void {
        print("visitAssignment: $node");
        return super.visitAssignment(node, p)
    }

    override fun visitCompilationUnit(node: CompilationUnitTree?, p: Void?): Void {
        print("visitCompilationUnit: $node");
        return super.visitCompilationUnit(node, p)
    }
}
