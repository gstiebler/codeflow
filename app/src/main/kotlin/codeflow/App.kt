/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package codeflow

import codeflow.java.AstReader
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val javaRootDir = args[0]
    val javaRootDirPath = Path.of(javaRootDir)

    val filesPaths = generateListOfJavaFilesFromDir(javaRootDirPath)
    val mainMethod = AstReader(javaRootDirPath).process(filesPaths)

    val result = ArrayList<String>()
    MermaidExporter()
        .processMainMethod(mainMethod) { result.add(it) }

    result.forEach { println(it) }
}

fun generateListOfJavaFilesFromDir(dir: Path): List<Path> {
    return Files.walk(dir, FileVisitOption.FOLLOW_LINKS)
        .filter { it.toString().endsWith(".java") }
        .toList()
}
