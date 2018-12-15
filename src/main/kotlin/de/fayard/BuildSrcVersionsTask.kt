package de.fayard

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File

open class BuildSrcVersionsTask : DefaultTask() {

    init {
        description = "Update buildSrc/src/main/kotlin/{Versions.kt,Libs.kt}"
        group = "build"
    }

    companion object {
        val moshiAdapter: JsonAdapter<DependencyGraph> by lazy {
            Moshi.Builder().build().adapter(DependencyGraph::class.java)
        }

        fun readGraphFromJsonFile(jsonInput: File): DependencyGraph {
            return moshiAdapter.fromJson(jsonInput.source().buffer())!!
        }

    }


    var jsonInputPath = "build/dependencyUpdates/report.json"


    @TaskAction
    fun taskAction() {
        val jsonInput = project.file(jsonInputPath)
        val outputDir = project.file(OutputFile.OUTPUTDIR.path).also {
            if (!it.isDirectory) it.mkdirs()
        }

        checkIfFilesExistInitially(project)

        val initializationMap = mapOf(
            OutputFile.BUILD to INITIAL_BUILD_GRADLE_KTS,
            OutputFile.GIT_IGNORE to INITIAL_GITIGNORE,
            OutputFile.SETTINGS to INITIAL_SETTINGS)

        for ((outputFile, initialContent) in initializationMap) {
            if (outputFile.existed.not()) {
                project.file(outputFile.path).writeText(initialContent)
                outputFile.logFileWasModified()
            }
        }

        val dependencyGraph = readGraphFromJsonFile(jsonInput)

        val dependencies: List<Dependency> = parseGraph(dependencyGraph)

        val kotlinPoetry: KotlinPoetry = kotlinpoet(dependencies, dependencyGraph.gradle)

        kotlinPoetry.Libs.writeTo(outputDir)
        OutputFile.LIBS.logFileWasModified()

        kotlinPoetry.Versions.writeTo(outputDir)
        OutputFile.VERSIONS.logFileWasModified()
    }

    fun checkIfFilesExistInitially(project: Project) {
        for (output in OutputFile.values()) {
            output.existed = project.file(output.path).exists()
        }
    }



}

internal enum class OutputFile(val path: String, var existed: Boolean = false) {
    OUTPUTDIR("buildSrc/src/main/kotlin"),
    BUILD("buildSrc/build.gradle.kts"),
    SETTINGS("buildSrc/settings.gradle.kts"),
    GIT_IGNORE("buildSrc/.gitignore"),
    LIBS("buildSrc/src/main/kotlin/Libs.kt"),
    VERSIONS("buildSrc/src/main/kotlin/Versions.kt");

    fun logFileWasModified() {
        val ANSI_RESET = "\u001B[0m"
        val ANSI_GREEN = "\u001B[32m"

        val status = if (existed) {
            "        modified:   "
        } else {
            "        new file:   "
        }
        println("$ANSI_GREEN$status$path$ANSI_RESET")
    }
}
