package codeflow.java

import codeflow.graph.GraphBuilderBlock
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

    fun process(fileNames: List<Path>): GraphBuilderBlock {
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
            compUnitTree.accept(AstClassProcessor(globalCtx), ctx)
        }
        var mainCtx: ProcessorContext? = null
        for (compUnitTree in compUnitTrees) {
            val ctx = getContext(compUnitTree, sourcePositions)
            val astProcessor = AstProcessor(globalCtx)
            compUnitTree.accept(astProcessor, ctx)
            if (astProcessor.methodNames.any { it.toString() == "main" }) {
                mainCtx = ctx
            }
        }

        // throw an exception if there is no main context
        if (mainCtx == null) {
            throw Exception("No main method found")
        }

        val mainMethod = globalCtx.getMainMethod()
        val mainMethodGraphBuilderBlock =
            GraphBuilderBlock( null, mainMethod, PosStack(), null, "main", mainCtx)
        val pos = Position(0, Path.of(""))
        val mainAstBlockProcessor = AstBlockProcessor(globalCtx, null, mainMethodGraphBuilderBlock, pos, null)
        mainAstBlockProcessor.invokeMethod(emptyList())

        manager.close()

        return mainAstBlockProcessor.graphBuilderBlock
    }

    private fun getContext(compUnitTree: CompilationUnitTree, sourcePositions: SourcePositions): ProcessorContext {
        val compUnitPath = compUnitTree.sourceFile.toUri().toPath()
        val relativePath = basePath.relativize(compUnitPath)
        return ProcessorContext(relativePath, compUnitTree, sourcePositions)
    }

}
