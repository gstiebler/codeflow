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
        val task = compiler.getTask(null, manager, null, null, null, compilationUnits1) as JavacTask

        val trees = Trees.instance(task)
        val sourcePositions = trees.sourcePositions

        val compUnitTrees = task.parse()
        val globalCtx = GlobalContext()
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
