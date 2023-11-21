package codeflow.graph

import codeflow.java.processors.ProcessorContext
import com.sun.source.tree.MethodTree

/**
 * Represents a method in the graph.
 * @property name the method tree
 * @property posId the position of the method in the graph
 * @property ctx the processor context
 */
data class Method(
    val name: MethodTree,
    val ctx: ProcessorContext
)