package codeflow.graph

import javax.lang.model.element.Element

/**
 * Represents a memory position. Multiple variables can point to the same memory position.
 *
 * It is an identity and nothing more: which fields an object holds is the builder's to record, so
 * that the alias model can be read and tested without any graph in it. Ids come from
 * [codeflow.ir.Heap], which is the only place a position is made.
 *
 * [label] is only what the memory position is called in a log line or an error, so it is whatever
 * names the object best where it is created - a `path:line:col`, or the expression that made it.
 *
 * [type] is the class the object was constructed as, which is what decides *which* implementation a
 * call on it runs. Null where nothing said - a loop element, a caught exception, an object from
 * outside the analysed sources - and a null type dispatches to nothing, which leaves the call
 * exactly where it was before.
 */
class MemPos(val id: Int, private val label: String, val type: Element? = null) {

    /**
     * The run-local id, so a `HashSet<MemPos>` iterates in allocation order. Without this it
     * iterates by identity hash, and which object a multi-holder write or read visits first - so
     * which box gets the lower serial - depended on what else the JVM had hashed before: removing
     * a logger field reordered the document. Equality stays identity; one [Heap] never reuses an id.
     */
    override fun hashCode() = id

    override fun toString(): String {
        return "MemPos($id, '$label')"
    }
}
