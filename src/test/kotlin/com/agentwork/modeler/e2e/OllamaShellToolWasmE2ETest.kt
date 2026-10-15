package com.agentwork.modeler.e2e

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.generator.ktor.WasmSettingsGradleGenerator
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.fail

/**
 * Same scenario as [OllamaShellToolE2ETest], but for the Ktor/WasmJS export running in its Docker image:
 *
 * 1. Model a graph: Ollama agent + built-in Shell tool
 * 2. Export it with the real ExportService as a Ktor/Wasm project
 * 3. Build it with Gradle, build the generated Dockerfile and run the container
 * 4. Ask the agent to read a file via the shell tool and summarize it (see [OllamaE2eScenario])
 *
 * Runtime (env E2E_WASM_RUNTIME):
 * - `docker`: always use Docker (fails if it is not available)
 * - `node`: run the image's command (`node <project>.mjs`) directly, e.g. where Docker is missing
 * - `auto` (default): Docker if available, otherwise Node — the output says which one ran
 *
 * The container reaches Ollama via host.docker.internal. On Linux, Ollama must listen on all
 * interfaces for that (`OLLAMA_HOST=0.0.0.0 ollama serve`). The notes directory is mounted at the
 * same path, because the shell tool's working directory is baked into the exported code.
 *
 * Not part of `./gradlew test`. Run with a local Ollama:
 *   ollama pull llama3.2:3b
 *   ./gradlew ollamaE2eTest --tests '*Wasm*'
 */
@Tag("ollama-e2e")
@SpringBootTest
class OllamaShellToolWasmE2ETest {

    @Autowired
    private lateinit var exportService: ExportService

    private val projectName = "ollama-shell-wasm-e2e"

    @Test
    fun `wasm agent in its docker image reads a file with the shell tool and summarizes it`() {
        val scenario = OllamaE2eScenario("ollama-e2e-wasm")
        scenario.requireOllamaWithModel()
        val useDocker = when (val runtime = System.getenv("E2E_WASM_RUNTIME") ?: "auto") {
            "docker" -> if (dockerAvailable()) true else fail("E2E_WASM_RUNTIME=docker, but Docker is not available")
            "node" -> false
            "auto" -> dockerAvailable()
            else -> fail("Unknown E2E_WASM_RUNTIME '$runtime' (use docker, node or auto)")
        }

        val app = if (useDocker) DockerApp(scenario) else NodeApp(scenario)
        try {
            scenario.log("1/4 Modeling agent + shell tool and exporting Ktor/Wasm project")
            scenario.writeProject(exportService.generateFiles(scenario.modeledGraph(projectName), "ktor-wasm"))

            scenario.log("2/4 Building exported project (Kotlin/Wasm)")
            scenario.gradle("build")

            val port = ServerSocket(0).use { it.localPort }
            scenario.log("3/4 Starting exported agent app: ${app.description}")
            app.start(port)

            scenario.log("4/4")
            scenario.askAgentAndVerify(port)
        } finally {
            app.stop()
            scenario.cleanup()
        }
    }

    private fun dockerAvailable(): Boolean = try {
        val process = ProcessBuilder("docker", "info").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private interface AgentApp {
        val description: String
        fun start(port: Int)
        fun stop()
    }

    /** Builds and runs the image from the generated Dockerfile. */
    private inner class DockerApp(private val scenario: OllamaE2eScenario) : AgentApp {
        private val tag = "koog-ollama-wasm-e2e:${scenario.codeword.lowercase()}"
        private val container = "koog-ollama-wasm-e2e-${scenario.codeword.lowercase()}"
        private var started = false

        override val description = "Docker image from the generated Dockerfile"

        override fun start(port: Int) {
            scenario.run(listOf("docker", "build", "-t", tag, "."), "docker-build", TimeUnit.MINUTES.toMillis(10))
            // localhost inside the container is the container itself
            val ollamaUrl = scenario.ollamaUrl.replace("localhost", "host.docker.internal").replace("127.0.0.1", "host.docker.internal")
            val filesDir = scenario.filesDir.absolutePath
            scenario.run(
                listOf(
                    "docker", "run", "-d", "--name", container,
                    "-p", "$port:8080",
                    "--add-host", "host.docker.internal:host-gateway",
                    "-e", "OLLAMA_BASE_URL=$ollamaUrl",
                    "-v", "$filesDir:$filesDir:ro",
                    tag
                ),
                "docker-run", TimeUnit.MINUTES.toMillis(1)
            )
            started = true
            val log = File(scenario.projectDir, "container.log")
            scenario.awaitPort(port, log) {
                log.writeText(capture("docker", "logs", container))
                capture("docker", "inspect", "-f", "{{.State.Running}}", container).trim() == "true"
            }
        }

        override fun stop() {
            if (started) capture("docker", "rm", "-f", container)
            capture("docker", "rmi", "-f", tag)
        }

        private fun capture(vararg command: String): String = try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(60, TimeUnit.SECONDS)
            output
        } catch (_: Exception) {
            ""
        }
    }

    /** Runs the image's command (`node <project>.mjs`) directly; requires Node.js >= 22.3. */
    private inner class NodeApp(private val scenario: OllamaE2eScenario) : AgentApp {
        private var process: Process? = null

        override val description = "Docker not available — running the image's command with local Node.js"

        override fun start(port: Int) {
            val name = WasmSettingsGradleGenerator.npmName(projectName)
            val log = File(scenario.projectDir, "node-app.log")
            val app = ProcessBuilder("node", "$name.mjs")
                .directory(File(scenario.projectDir, "build/wasm/packages/$name/kotlin"))
                .apply {
                    environment()["SERVER_PORT"] = port.toString()
                    environment()["OLLAMA_BASE_URL"] = scenario.ollamaUrl
                }
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            process = app
            scenario.awaitPort(port, log) { app.isAlive }
        }

        override fun stop() {
            process?.destroy()
            process?.waitFor(10, TimeUnit.SECONDS)
        }
    }
}
