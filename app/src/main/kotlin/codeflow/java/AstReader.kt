package codeflow.java

import codeflow.graph.Graph
import codeflow.graph.GraphBuilder
import codeflow.graph.GraphBuilderMethod
import com.sun.source.util.JavacTask
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.toPath

class AstReader(private val basePath: Path) {

    fun process(fileNames: List<Path>): List<GraphBuilderMethod> {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val files = fileNames.map { it.toFile() }
        val compilationUnits1 = manager.getJavaFileObjectsFromFiles(files)
        val task = compiler.getTask(null, manager, null, null, null, compilationUnits1) as JavacTask

        val graphBuilder = GraphBuilder()
        val astProcessor = AstProcessor(graphBuilder)
        for (compUnitTree in task.parse()) {
            val compUnitPath = compUnitTree.sourceFile.toUri().toPath()
            val relativePath = basePath.relativize(compUnitPath)
            compUnitTree.accept(astProcessor, relativePath)
        }
        // task.call()
        manager.close()

        return graphBuilder.getMethods()
    }

}
