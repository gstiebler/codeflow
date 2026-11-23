package codeflow.java

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.ir.IrGraphBuilder
import codeflow.ir.Summarizer
import codeflow.ir.Summary
import codeflow.java.processors.*
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.util.JavacTask
import com.sun.source.util.SourcePositions
import com.sun.source.util.Trees
import java.nio.file.Path
import javax.lang.model.element.Element
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.toPath


class AstReader(private val basePath: Path) {

    /**
     * The constructs this run could not model, as `KIND at file:line:col`, filled in by [process].
     *
     * Exposed so the caller can set an exit status from it. The document on stdout is complete and
     * usable either way; what the status says is whether everything in it was understood, which is
     * a question a pipeline can act on and a reader watching stdout scroll past cannot.
     */
    var unmodelled: List<String> = emptyList()
        private set

    /** Assumed single-implementation dispatches made during [process] or [summarise]. */
    var assumed: List<String> = emptyList()
        private set

    fun process(
        fileNames: List<Path>,
        entryPoints: List<String> = emptyList(),
        boundaries: List<String> = emptyList(),
        pures: List<String> = emptyList(),
        assumeSingleImpl: Boolean = false,
        autoBoundaries: Boolean = false,
        discoverRoots: Boolean = false
    ): List<GraphBuilderBlock> {
        val analysis = analyse(fileNames, boundaries, pures, assumeSingleImpl, autoBoundaries)
        val entries = when {
            entryPoints.isNotEmpty() -> entryPoints.map { selectEntry(analysis.globalCtx, it) }
            discoverRoots -> discoverEntries(analysis.globalCtx)
            else -> listOf(selectEntry(analysis.globalCtx, null))
        }
        val root = IrGraphBuilder(analysis.globalCtx).build(entries)
        unmodelled = analysis.globalCtx.unmodelled()
        assumed = analysis.globalCtx.assumed()
        reportUnmodelled()
        reportAssumed()
        analysis.close()
        return root
    }

    /**
     * Every method's summary, or those `--from` names. No graph is built: the builder is only asked
     * for the bodies the summarizer reads.
     *
     * A spec that matches nothing fails as [selectEntry] does, listing the candidates; the filter
     * itself is [MethodSpec.matches], so what `--from` accepts means the same in both modes.
     */
    fun summarise(
        fileNames: List<Path>,
        entryPoints: List<String> = emptyList(),
        boundaries: List<String> = emptyList(),
        pures: List<String> = emptyList(),
        assumeSingleImpl: Boolean = false,
        autoBoundaries: Boolean = false
    ): List<Pair<Method, Summary>> {
        val analysis = analyse(fileNames, boundaries, pures, assumeSingleImpl, autoBoundaries)
        entryPoints.forEach { analysis.method(it) }
        val builder = IrGraphBuilder(analysis.globalCtx)
        val all = Summarizer(analysis.globalCtx, builder::bodyOf, builder::initializersOf).summariseAll()
        val chosen = all.entries
            .filter { (method, _) -> entryPoints.isEmpty() || entryPoints.any { MethodSpec.matches(method, it) } }
            .map { it.key to it.value }
        unmodelled = analysis.globalCtx.unmodelled()
        assumed = analysis.globalCtx.assumed()
        reportUnmodelled()
        reportAssumed()
        analysis.close()
        return chosen
    }

    fun analyse(
        fileNames: List<Path>,
        boundaries: List<String> = emptyList(),
        pures: List<String> = emptyList(),
        assumeSingleImpl: Boolean = false,
        autoBoundaries: Boolean = false
    ): Analysis {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val files = fileNames.map { it.toFile() }
        val compilationUnits1 = manager.getJavaFileObjectsFromFiles(files)
        // The diagnostic listener is what stops javac printing "cannot find symbol" to stderr for
        // every unresolved import. codeflow is pointed at sources with no classpath, so on real
        // input that is most of them, and the summary below is what the reader gets instead.
        // -proc:none because there is no processor path to discover anything on.
        val options = listOf("-proc:none")
        val task = compiler.getTask(
            null, manager, diagnostics, options, null, compilationUnits1
        ) as JavacTask

        val trees = Trees.instance(task)
        val sourcePositions = trees.sourcePositions

        val compUnitTrees = task.parse()
        // Attribution. Without it there is no symbol table, and every name has to be resolved by
        // matching text - which cannot tell two same-named methods apart, cannot pick an overload,
        // and cannot say whether a variable holds a value or a reference. Ordinarily it does not
        // throw on sources that do not compile: what it cannot resolve it marks, and Symbols only
        // believes a symbol of the kind the caller asked for.
        //
        // Ordinarily. javac *itself* can die here: on 21 it did, on Fineract's largest module -
        // attributing a `yield` whose type it never worked out trips an internal assertion in
        // `Attr`, and every root in that module produced nothing at all. Which is why the build
        // runs on 25 (see the comment on the `test` task in app/build.gradle); the compiler is an
        // input to the output, so its version is part of the configuration and not an accident of
        // whichever JDK the shell had.
        //
        // Nothing here catches it, deliberately. Catching it would not help - `Symbols` forces
        // attribution again through `Trees.getElement`, one tree at a time, and the same assertion
        // fires there. So tolerating a javac crash means tolerating it per *tree*, at the lookup,
        // where "javac cannot tell me what this is" is already an answer Symbols has. That is a
        // decision about what a partly attributed corpus may be drawn as, and is not taken yet.
        task.analyze()
        val symbols = Symbols.collect(trees, task.elements, task.types, compUnitTrees)
        System.err.println(
            "codeflow: ${symbols.unresolved} of ${symbols.total} references unresolved"
        )
        val globalCtx = GlobalContext(symbols, assumeSingleImpl = assumeSingleImpl)
        for (compUnitTree in compUnitTrees) {
            val ctx = getContext(compUnitTree, sourcePositions)
            compUnitTree.accept(AstProcessor(globalCtx), ctx)
        }
        val ordered = boundaries.flatMapTo(LinkedHashSet()) { selectBoundary(globalCtx, it, "--boundary") }
        val pure = pures.flatMapTo(LinkedHashSet()) { selectBoundary(globalCtx, it, "--pure") }
        // Both flags on one class: whether its calls are ordered is not ours to guess.
        val both = ordered.intersect(pure)
        if (both.isNotEmpty()) throw GraphException("${both.joinToString(", ")} named by both --boundary and --pure")
        if (autoBoundaries) {
            for (cls in globalCtx.sourceClasses()) {
                if (cls in ordered || cls in pure) continue
                val role = globalCtx.findClass(cls)?.let { Semantics.boundaryRole(it.tree) } ?: continue
                if (role == Semantics.ROLE_NOISE) {
                    pure.add(cls)
                    System.err.println("codeflow: auto-pure '$cls' ($role)")
                } else {
                    ordered.add(cls)
                    System.err.println("codeflow: auto-boundary '$cls' ($role)")
                }
            }
        }
        globalCtx.markBoundaries(ordered + pure, pure)
        return Analysis(globalCtx, manager)
    }

    /**
     * What the run could not model, on stderr in the style of the counters above it.
     *
     * Each one is on the graph too, as its own node type, but a reader who opens a diagram of a
     * thousand nodes has no reason to go looking for them. The summary is what makes the gaps
     * something you are told about rather than something you happen across.
     */
    private fun reportUnmodelled() {
        if (unmodelled.isEmpty()) return
        val plural = if (unmodelled.size == 1) "construct" else "constructs"
        System.err.println("codeflow: ${unmodelled.size} $plural not modelled")
        unmodelled.forEach { System.err.println("codeflow:   $it") }
    }

    private fun reportAssumed() {
        if (assumed.isEmpty()) return
        val plural = if (assumed.size == 1) "dispatch" else "dispatches"
        System.err.println("codeflow: ${assumed.size} assumed single-implementation $plural")
        assumed.forEach { System.err.println("codeflow:   $it") }
    }

    private fun discoverEntries(globalCtx: GlobalContext): List<Method> {
        val found = globalCtx.annotatedEntrypoints()
        if (found.isEmpty()) return listOf(selectEntry(globalCtx, null))
        found.forEach {
            val label = Semantics.entrypointLabel(it, globalCtx.findClass(it.element.enclosingElement))
            System.err.println("codeflow: discovered root '${MethodSpec.of(it)}' ($label) in ${it.ctx.path}")
        }
        return found
    }

    /**
     * The entry point to graph, and a note to stderr saying so.
     *
     * The whole diagram is whatever this one method reaches, so which method that is *is* the
     * diagram. `main` is the default because a program that has one starts there, but it was for a
     * long time the only possibility, and most Java has no `main`: a service, a controller or a
     * library is entered from a framework or a caller that is not in the corpus. Those were not
     * graphable at all, which excluded most of the tool's own subject matter, and a reader who
     * wanted one method's dataflow had to find a path to it from an entry point and then pick it
     * out of everything else that entry point reached. [entryPoint] names one directly.
     *
     * Either way the choice is *reported*, and the alternatives with it. Silence here reads as
     * "this is the codebase" when it is one of several, which is the same failure as a wrong edge:
     * complete-looking, plausible, and giving no sign anything is missing.
     */
    private fun selectEntry(globalCtx: GlobalContext, entryPoint: String?): Method {
        val candidates = globalCtx.sourceMethods()
        val matches =
            if (entryPoint == null) globalCtx.mainMethods() else candidates.filter { MethodSpec.matches(it, entryPoint) }
        val asked = if (entryPoint == null) "'main'" else "'$entryPoint'"
        val chosen = matches.firstOrNull() ?: throw GraphException(
            "No method matching $asked in the analysed sources. " +
                    "Name one with --from Class#method: ${candidates.joinToString(", ") { MethodSpec.of(it) }}"
        )
        System.err.println("codeflow: graphing '${MethodSpec.of(chosen)}' in ${chosen.ctx.path}")
        if (matches.size > 1) {
            System.err.println(
                "codeflow: ${matches.size - 1} other match for $asked not graphed: " +
                        matches.drop(1).joinToString(", ") { "${MethodSpec.of(it)} in ${it.ctx.path}" }
            )
        }
        return chosen
    }

    /**
     * The classes one `--boundary` names, matched as `--from` matches its class part.
     *
     * A name that matches nothing fails, as an unknown `--from` does: a misspelt boundary would
     * otherwise read the body it was meant to hide, and the diagram would give no sign of it. A
     * simple name naming several classes makes every one of them a boundary, and each is reported.
     */
    private fun selectBoundary(globalCtx: GlobalContext, spec: String, flag: String): List<Element> {
        val candidates = globalCtx.sourceClasses()
        val matches = candidates.filter { MethodSpec.matchesClass(it, spec) }
        if (matches.isEmpty()) throw GraphException(
            "No class matching '$spec' for $flag in the analysed sources: ${candidates.joinToString(", ")}"
        )
        matches.forEach { System.err.println("codeflow: ${flag.removePrefix("--")} '$it'") }
        return matches
    }

    private fun getContext(compUnitTree: CompilationUnitTree, sourcePositions: SourcePositions): ProcessorContext {
        val compUnitPath = compUnitTree.sourceFile.toUri().toPath()
        val relativePath = basePath.relativize(compUnitPath)
        return ProcessorContext(relativePath, compUnitTree, sourcePositions)
    }

}
