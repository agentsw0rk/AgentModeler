package com.agentwork.modeler.build

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class BuildService(
    private val exportService: ExportService,
    private val errorParser: GradleErrorParser
) {

    fun build(graph: WorkspaceGraph, platform: String): BuildResult {
        val (files, fileToNodeMap) = exportService.generateFilesWithMapping(graph)

        val tempDir = Files.createTempDirectory("modeler-build-").toFile()
        try {
            for ((path, content) in files) {
                val file = File(tempDir, path)
                file.parentFile.mkdirs()
                file.writeText(content)
            }

            val gradleTask = when (platform) {
                "native" -> "compileKotlinNative"
                else -> "compileKotlin"
            }

            val process = ProcessBuilder(
                "docker", "run", "--rm",
                "-v", "${tempDir.absolutePath}:/project",
                "modeler-builder",
                gradleTask
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(240, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return BuildResult(
                    success = false,
                    errors = listOf(BuildError("", 0, 0, "Build timed out after 240 seconds", "error", null)),
                    log = output
                )
            }

            val exitCode = process.exitValue()
            val errors = errorParser.parse(output, fileToNodeMap)

            return BuildResult(
                success = exitCode == 0 && errors.none { it.severity == "error" },
                errors = errors,
                log = output
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
