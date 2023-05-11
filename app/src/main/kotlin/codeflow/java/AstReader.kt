package codeflow.java

import codeflow.graph.GraphBuilder
import codeflow.java.processors.AstProcessor
import codeflow.java.processors.ProcessorContext
import com.sun.source.util.JavacTask
import com.sun.source.util.Trees
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.toPath


class AstReader(private val basePath: Path) {

    fun process(fileNames: List<Path>): GraphBuilder {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val files = fileNames.map { it.toFile() }
        val compilationUnits1 = manager.getJavaFileObjectsFromFiles(files)
        val task = compiler.getTask(null, manager, null, null, null, compilationUnits1) as JavacTask

        val trees = Trees.instance(task)
        val sourcePositions = trees.sourcePositions

        val graphBuilder = GraphBuilder()
        val astProcessor = AstProcessor(graphBuilder)
        for (compUnitTree in task.parse()) {
            val compUnitPath = compUnitTree.sourceFile.toUri().toPath()
            val relativePath = basePath.relativize(compUnitPath)
            val ctx = ProcessorContext(relativePath, compUnitTree, sourcePositions)
            compUnitTree.accept(astProcessor, ctx)

            val test = sourcePositions.getEndPosition(compUnitTree, null)
        }
        manager.close()

        return graphBuilder
    }

}
