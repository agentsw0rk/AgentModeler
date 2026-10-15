package com.agentwork.modeler.e2e

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.WorkspaceGraph
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.ConnectException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * End-to-end proof that a modeled agent can use a tool against a real local LLM:
 *
 * 1. Model a graph: Ollama agent + built-in Shell tool (as the editor would send it)
 * 2. Export it with the real ExportService as a Spring Boot project
 * 3. Build the project with Gradle and start the jar
 * 4. Ask the agent to read a file via the shell tool and summarize it
 *
 * The file contains a codeword generated per run. The model cannot guess it, so seeing it
 * in the answer proves the agent really executed the shell tool and read the file.
 *
 * Not part of `./gradlew test`. Run with a local Ollama:
 *   ollama pull llama3.2:3b
 *   ./gradlew ollamaE2eTest
 * Env: OLLAMA_BASE_URL (default http://localhost:11434), OLLAMA_E2E_MODEL (default llama3.2:3b)
 */
@Tag("ollama-e2e")
@SpringBootTest
class OllamaShellToolE2ETest {

    @Autowired
    private lateinit var exportService: ExportService

    private val ollamaUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
    private val model = System.getenv("OLLAMA_E2E_MODEL") ?: "llama3.2:3b"
    private val json = JsonMapper.builder().build()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Test
    fun `agent reads a file with the shell tool and summarizes it`() {
        requireOllamaWithModel()

        val codeword = "KOOG-" + UUID.randomUUID().toString().take(8).uppercase()
        val workDir = Files.createTempDirectory("ollama-e2e-files").toFile()
        val projectDir = Files.createTempDirectory("ollama-e2e-project").toFile()
        File(workDir, "projekt-notizen.txt").writeText(meetingNotes(codeword))

        var app: Process? = null
        try {
            log("1/4 Modeling agent + shell tool and exporting Spring Boot project")
            exportService.generateFiles(modeledGraph(workDir)).forEach { (path, content) ->
                File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
            }

            log("2/4 Building exported project")
            buildProject(projectDir)

            log("3/4 Starting exported agent app")
            val port = ServerSocket(0).use { it.localPort }
            app = startApp(projectDir, port)

            log("4/4 Asking the agent (model: $model)")
            val summary = chat(
                port,
                "Lies die Datei projekt-notizen.txt mit dem Shell-Tool (Befehl: cat projekt-notizen.txt) " +
                    "und fasse sie in drei kurzen Stichpunkten zusammen. Nenne am Ende das Codewort aus der Datei."
            )

            println(
                """
                |
                |================ Zusammenfassung des Agents ================
                |$summary
                |=============================================================
                |Erwartetes Codewort: $codeword
                |""".trimMargin()
            )
            assertContains(summary, codeword, message = "Agent did not read the file via the shell tool")
        } finally {
            app?.destroy()
            app?.waitFor(10, TimeUnit.SECONDS)
            workDir.deleteRecursively()
            projectDir.deleteRecursively()
        }
    }

    private fun meetingNotes(codeword: String) = """
        Projektnotizen – Sprint-Review "Falkenstein"
        Datum: 14.10.2026
        Teilnehmende: Dr. Ilse Brandt (Produkt), Jonas Weber (Backend), Mia Kaya (Frontend)

        - Der Export nach Spring Boot ist fertig und wurde mit drei Kunden getestet.
        - Offener Punkt: Ladezeit des Editors bei mehr als 200 Nodes ist zu hoch.
        - Entscheidung: Ollama wird als lokaler LLM-Provider fuer Offline-Demos aufgenommen.
        - Naechstes Review: 28.10.2026.

        Codewort fuer die Freigabe: $codeword
    """.trimIndent()

    /** Same structure the editor sends: an agent node with a built-in shell tool connected to it. */
    private fun modeledGraph(workDir: File) = WorkspaceGraph(
        version = 1,
        name = "ollama-shell-e2e",
        nodes = listOf(
            GraphNode(
                "agent", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "FileSummarizer",
                    "systemPrompt" to "You are an assistant that answers questions about local files. " +
                        "Always use the executeCommand tool to read files; never guess their content.",
                    "modelProvider" to "ollama",
                    "modelId" to model,
                    "temperature" to 0.0
                )
            ),
            GraphNode(
                "shell", "builtin-tool", Position(300.0, 0.0), mapOf(
                    "toolType" to "shell",
                    "workingDir" to workDir.absolutePath
                )
            )
        ),
        edges = listOf(GraphEdge("e1", "shell", "agent"))
    )

    private fun requireOllamaWithModel() {
        val tags = try {
            http.send(HttpRequest.newBuilder(URI("$ollamaUrl/api/tags")).build(), HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: ConnectException) {
            fail("Ollama is not reachable at $ollamaUrl. Start it with: ollama serve")
        }
        val name = if (':' in model) model else "$model:latest"
        if ("\"$name\"" !in tags) fail("Ollama model '$model' is not installed. Run: ollama pull $model")
    }

    private fun buildProject(projectDir: File) {
        val gradlew = File(System.getProperty("user.dir"), "gradlew").absolutePath
        val log = File(projectDir, "build-output.log")
        val process = ProcessBuilder(gradlew, "-p", projectDir.absolutePath, "bootJar", "--console=plain")
            .apply { environment()["JAVA_HOME"] = System.getProperty("java.home") }
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        if (!process.waitFor(15, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            fail("Gradle build of the exported project timed out\n${log.readText().takeLast(4000)}")
        }
        assertEquals(0, process.exitValue(), "Gradle build of the exported project failed\n${log.readText().takeLast(4000)}")
    }

    private fun startApp(projectDir: File, port: Int): Process {
        val jar = File(projectDir, "build/libs").listFiles { f -> f.name.endsWith(".jar") && !f.name.endsWith("-plain.jar") }
            ?.singleOrNull() ?: fail("No boot jar found in ${projectDir}/build/libs")
        val log = File(projectDir, "app-output.log")
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val app = ProcessBuilder(java, "-jar", jar.absolutePath)
            .apply {
                environment()["SERVER_PORT"] = port.toString()
                environment()["OLLAMA_BASE_URL"] = ollamaUrl
            }
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()

        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (!app.isAlive) fail("Exported app exited during startup\n${log.readText().takeLast(4000)}")
            if ("Started " in log.readText()) return app
            Thread.sleep(1000)
        }
        app.destroyForcibly()
        fail("Exported app did not start within 5 minutes\n${log.readText().takeLast(4000)}")
    }

    private fun chat(port: Int, message: String): String {
        val body = json.writeValueAsString(mapOf("message" to message))
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/chat"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), "Chat request failed: ${response.body()}")
        return json.readTree(response.body()).get("response")?.asString()
            ?: fail("Unexpected chat response: ${response.body()}")
    }

    private fun log(step: String) = println("[ollama-e2e] $step")
}
