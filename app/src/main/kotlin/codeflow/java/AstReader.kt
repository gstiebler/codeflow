package codeflow.java

import com.sun.source.util.JavacTask
import java.nio.file.Path
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

class AstReader {

    fun process(fileNames: List<Path>) {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val files = fileNames.map { it.toFile() }
        val compilationUnits1 = manager.getJavaFileObjectsFromFiles(files)
        val task = compiler.getTask(null, manager, null, null, null, compilationUnits1) as JavacTask

        for (compUnitTree in task.parse()) {
            compUnitTree.accept(AstProcessor(), null)
        }

        // task.call()

        manager.close()
    }

}
