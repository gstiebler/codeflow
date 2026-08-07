package codeflow.java

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException
import codeflow.graph.Method
import codeflow.graph.PosStack
import codeflow.graph.Position
import codeflow.java.processors.*
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.util.JavacTask
import com.sun.source.util.SourcePositions
import com.sun.source.util.Trees
import java.nio.file.Path
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

    fun process(fileNames: List<Path>, entryPoint: String? = null): GraphBuilderBlock {
        val analysis = analyse(fileNames)
        val globalCtx = analysis.globalCtx

        val mainMethod = selectEntry(globalCtx, entryPoint)
        val mainMethodGraphBuilderBlock =
            GraphBuilderBlock(null, mainMethod, PosStack(), null, mainMethod.ctx)
        val pos = Position(0, Path.of(""))
        val mainAstBlockProcessor = AstBlockProcessor(globalCtx, null, mainMethodGraphBuilderBlock, pos, null)
        mainAstBlockProcessor.invokeMethod(emptyList())

        unmodelled = globalCtx.unmodelled()
        reportUnmodelled()

        analysis.close()

        return mainAstBlockProcessor.graphBuilderBlock
    }

    /**
     * Everything javac has to say about these sources, and nothing built on top of it yet.
     *
     * Split out from [process] because parse-and-attribute is the input to more than one thing: the
     * graph is one consumer, the lowering to IR (`codeflow.ir`) is another, and a test of what a
     * method *means* should not have to render a diagram to ask.
     */
    fun analyse(fileNames: List<Path>): Analysis {
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
        // and cannot say whether a variable holds a value or a reference. It does not throw on
        // sources that do not compile: what it cannot resolve it marks, and Symbols only believes
        // a symbol of the kind the caller asked for.
        task.analyze()
        val symbols = Symbols.collect(trees, task.elements, compUnitTrees)
        System.err.println(
            "codeflow: ${symbols.unresolved} of ${symbols.total} references unresolved"
        )
        val globalCtx = GlobalContext(symbols)
        for (compUnitTree in compUnitTrees) {
            val ctx = getContext(compUnitTree, sourcePositions)
            compUnitTree.accept(AstProcessor(globalCtx), ctx)
        }
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

    private fun getContext(compUnitTree: CompilationUnitTree, sourcePositions: SourcePositions): ProcessorContext {
        val compUnitPath = compUnitTree.sourceFile.toUri().toPath()
        val relativePath = basePath.relativize(compUnitPath)
        return ProcessorContext(relativePath, compUnitTree, sourcePositions)
    }

}
