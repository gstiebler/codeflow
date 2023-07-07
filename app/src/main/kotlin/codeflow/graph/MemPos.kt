package codeflow.graph

import com.sun.source.tree.ExpressionTree

class MemPos(val expr: ExpressionTree) {



    companion object {
        var counter = 0
    }

    val id = counter++

    override fun toString(): String {
        return "MemPos($id, '$expr')"
    }
}
