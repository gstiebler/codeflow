package codeflow.java

import codeflow.graph.Graph
import codeflow.graph.GraphBuilder
import com.sun.source.util.JavacTask
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

class AstReader {

    fun process(fileNames: List<Path>): Graph {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val files = fileNames.map { it.toFile() }
        val compilationUnits1 = manager.getJavaFileObjectsFromFiles(files)
        val task = compiler.getTask(null, manager, null, null, null, compilationUnits1) as JavacTask

        val graphBuilder = GraphBuilder()
        val astProcessor = AstProcessor(graphBuilder)
        for (compUnitTree in task.parse()) {
            compUnitTree.accept(astProcessor, null)
        }
        // task.call()
        manager.close()

        graphBuilder.mingleEdges()
        return graphBuilder.graph
    }

}
