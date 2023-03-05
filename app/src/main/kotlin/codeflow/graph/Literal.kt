package codeflow.graph

import java.nio.file.Path

class Literal(private val base: Base) : GraphNode(base) {

    override fun toString() = "Literal GraphNode: ${base.label} ($id)"

}