package codeflow.java

import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.Tree
import com.sun.source.util.TreePath
import com.sun.source.util.TreePathScanner
import com.sun.source.util.Trees
import java.util.IdentityHashMap
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.util.Elements
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * What javac resolved each tree to.
 *
 * `Trees.getElement` needs a [TreePath], but the scanners that read the sources are plain
 * `TreeScanner`s holding a bare [Tree] - `AstProcessor` and the lowering in `codeflow.ir` alike.
 * Attribution writes symbols into the same tree objects `parse()` returned, so one path-aware pass
 * can record everything up front and the scanners can then ask a question about a tree without
 * carrying a path to it.
 *
 * Keyed by tree identity, not equality: two structurally identical expressions at two call sites
 * are two different references and must stay two different keys.
 */
class Symbols private constructor(
    private val elementUtils: Elements,
    private val elements: IdentityHashMap<Tree, Element>,
    private val types: IdentityHashMap<Tree, TypeMirror>,
    /** See [isDeclaredInSources]. */
    private val declared: Set<Element>,
    /** References javac could not work out the type of. */
    val unresolved: Int,
    /** References looked at, resolved or not. */
    val total: Int
) {

    /** The declaration this tree resolved to, or null if javac could not resolve it. */
    fun element(tree: Tree): Element? = elements[tree]

    /**
     * As [element], but null unless the resolution is of the kind expected at this site.
     *
     * Attribution marks what it could not work out rather than guessing, but it marks it by
     * handing back something of the wrong shape: a call on a receiver whose type is an error comes
     * back as an `Element` of kind `CLASS` where a `METHOD` was asked for. Taking that at face
     * value would resolve a call to a class. So a symbol is only believed when it is the kind the
     * caller asked for, and anything else is treated as coming from outside the analysed sources.
     */
    fun element(tree: Tree, expected: ElementKind): Element? =
        elements[tree]?.takeIf { it.kind == expected }

    /** Whether the tree's type is a primitive, and so holds a value rather than a reference. */
    fun isPrimitive(tree: Tree): Boolean = types[tree]?.kind?.isPrimitive ?: false

    /**
     * Whether this declaration is one of the analysed sources', rather than one javac loaded from
     * the platform or the classpath to resolve a reference into.
     *
     * `element(tree)` answers both alike - `System.out` resolves as confidently as a field of a
     * class in the directory - and the two get opposite treatment: what these sources declare is
     * modelled, and everything else takes the opaque EXTERNAL path. Nothing could ask the
     * difference before, because every question was about a tree, and a tree is by construction
     * from these sources.
     */
    fun isDeclaredInSources(element: Element?): Boolean = element != null && element in declared

    /**
     * Whether this declaration was written in the source.
     *
     * Attribution does not only annotate the trees, it adds to them: a class that declares no
     * constructor gains one, and a constructor that does not start with `super(...)` or `this(...)`
     * gains a `super()`. Those carry a real source position, so position cannot tell them apart,
     * but javac records that it supplied them and [Elements.getOrigin] reports it.
     *
     * They are left out because codeflow is a tool for reading source: a graph of a constructor
     * nobody wrote is an empty box on the diagram standing for no code, and every `new` of a class
     * with a default constructor would draw one.
     */
    fun isWrittenInSource(tree: Tree): Boolean {
        val element = elements[tree] ?: return true
        return elementUtils.getOrigin(element) == Elements.Origin.EXPLICIT
    }

    companion object {

        /** The trees that name something, and so are the ones worth counting as resolved or not. */
        private val REFERENCE_KINDS = setOf(
            Tree.Kind.IDENTIFIER, Tree.Kind.MEMBER_SELECT,
            Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS
        )

        /** The trees that *declare* something, and so name what these sources contain. */
        private val DECLARATION_KINDS = setOf(
            Tree.Kind.VARIABLE, Tree.Kind.METHOD, Tree.Kind.CLASS, Tree.Kind.INTERFACE,
            Tree.Kind.ENUM, Tree.Kind.RECORD, Tree.Kind.ANNOTATION_TYPE
        )

        fun collect(
            trees: Trees,
            elementUtils: Elements,
            compilationUnits: Iterable<CompilationUnitTree>
        ): Symbols {
            val elements = IdentityHashMap<Tree, Element>()
            val types = IdentityHashMap<Tree, TypeMirror>()
            val declared = HashSet<Element>()
            var unresolved = 0
            var total = 0

            val collector = object : TreePathScanner<Void?, Void?>() {
                override fun scan(tree: Tree?, p: Void?): Void? {
                    if (tree == null) {
                        return null
                    }
                    // TreePathScanner only extends the path once it dispatches to a visitXxx, and
                    // overriding every one of those to catch it there is the alternative. The path
                    // to this tree is the current one with this tree on the end.
                    val path = TreePath(currentPath, tree)
                    trees.getElement(path)?.let {
                        elements[tree] = it
                        if (tree.kind in DECLARATION_KINDS) declared.add(it)
                    }
                    val type = trees.getTypeMirror(path)
                    type?.let { types[tree] = it }

                    if (tree.kind in REFERENCE_KINDS) {
                        total++
                        if (type == null || type.kind == TypeKind.ERROR) {
                            unresolved++
                        }
                    }
                    return super.scan(tree, p)
                }
            }
            compilationUnits.forEach { collector.scan(it, null) }

            return Symbols(elementUtils, elements, types, declared, unresolved, total)
        }
    }
}
