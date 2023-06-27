package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree

data class Method(
    val name: MethodTree,
    val posId: Long,
    val ctx: ProcessorContext
)