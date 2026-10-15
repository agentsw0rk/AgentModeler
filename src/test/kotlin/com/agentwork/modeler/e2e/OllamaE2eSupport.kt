package com.agentwork.modeler.e2e

import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.WorkspaceGraph
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.net.ConnectException
import java.net.Socket
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
 * Shared scenario for the Ollama e2e tests: a modeled agent with the built-in shell tool
 * must read a file and summarize it. The file holds a codeword generated per run that the
 * model cannot guess, so finding it in the answer proves the shell tool was executed.
 *
 * Env: OLLAMA_BASE_URL (default http://localhost:11434), OLLAMA_E2E_MODEL (default llama3.2:3b)
 */
class OllamaE2eScenario(private val logPrefix: String) {

    val ollamaUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
    val model: String = System.getenv("OLLAMA_E2E_MODEL") ?: "llama3.2:3b"

    val codeword = "KOOG-" + UUID.randomUUID().toString().take(8).uppercase()
    val filesDir: File = Files.createTempDirectory("ollama-e2e-files").toFile()
    val projectDir: File = Files.createTempDirectory("ollama-e2e-project").toFile()

    private val json = JsonMapper.builder().build()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    init {
        File(filesDir, "projekt-notizen.txt").writeText(
            """
            Projektnotizen – Sprint-Review "Falkenstein"
            Datum: 14.10.2026
            Teilnehmende: Dr. Ilse Brandt (Produkt), Jonas Weber (Backend), Mia Kaya (Frontend)

            - Der Export nach Spring Boot ist fertig und wurde mit drei Kunden getestet.
            - Offener Punkt: Ladezeit des Editors bei mehr als 200 Nodes ist zu hoch.
            - Entscheidung: Ollama wird als lokaler LLM-Provider fuer Offline-Demos aufgenommen.
            - Naechstes Review: 28.10.2026.

            Codewort fuer die Freigabe: $codeword
            """.trimIndent()
        )
    }

    /** Same structure the editor sends: an agent node with a built-in shell tool connected to it. */
    fun modeledGraph(name: String) = WorkspaceGraph(
        version = 1,
        name = name,
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
                    "workingDir" to filesDir.absolutePath
                )
            )
        ),
        edges = listOf(GraphEdge("e1", "shell", "agent"))
    )

    fun writeProject(files: Map<String, String>) = files.forEach { (path, content) ->
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    fun requireOllamaWithModel() {
        val tags = try {
            http.send(HttpRequest.newBuilder(URI("$ollamaUrl/api/tags")).build(), HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: ConnectException) {
            fail("Ollama is not reachable at $ollamaUrl. Start it with: ollama serve")
        }
        val name = if (':' in model) model else "$model:latest"
        if ("\"$name\"" !in tags) fail("Ollama model '$model' is not installed. Run: ollama pull $model")
    }

    /**
     * Builds the exported project with this repo's Gradle wrapper. No daemons: lingering Gradle/Kotlin
     * daemons (2g heap for Wasm) would compete with the Ollama model for memory during the chat.
     */
    fun gradle(vararg tasks: String) = run(
        listOf(
            File(System.getProperty("user.dir"), "gradlew").absolutePath, "-p", projectDir.absolutePath, *tasks,
            "--no-daemon", "-Pkotlin.compiler.execution.strategy=in-process", "--console=plain"
        ),
        "gradle-${tasks.joinToString("-")}",
        TimeUnit.MINUTES.toMillis(20),
        mapOf("JAVA_HOME" to System.getProperty("java.home"))
    )

    /** Runs a command to completion in the project directory and fails with its output otherwise. */
    fun run(command: List<String>, logName: String, timeoutMillis: Long, env: Map<String, String> = emptyMap()): String {
        val log = File(projectDir, "$logName.log")
        val process = ProcessBuilder(command)
            .directory(projectDir)
            .apply { environment().putAll(env) }
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            fail("'${command.joinToString(" ")}' timed out\n${log.readText().takeLast(4000)}")
        }
        assertEquals(0, process.exitValue(), "'${command.joinToString(" ")}' failed\n${log.readText().takeLast(4000)}")
        return log.readText()
    }

    /** Waits until the app accepts connections on [port]; [isAlive] lets callers detect early exits. */
    fun awaitPort(port: Int, logFile: File, isAlive: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (!isAlive()) fail("Agent app exited during startup\n${logFile.readText().takeLast(4000)}")
            try {
                Socket("localhost", port).close()
                return
            } catch (_: Exception) {
                Thread.sleep(1000)
            }
        }
        fail("Agent app did not start within 5 minutes\n${logFile.readText().takeLast(4000)}")
    }

    /** Asks the running agent to read and summarize the notes, prints the answer and checks the codeword. */
    fun askAgentAndVerify(port: Int) {
        log("Asking the agent (model: $model)")
        val message = "Lies die Datei projekt-notizen.txt mit dem Shell-Tool (Befehl: cat projekt-notizen.txt) " +
            "und fasse sie in drei kurzen Stichpunkten zusammen. Nenne am Ende das Codewort aus der Datei."
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/chat"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(10))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mapOf("message" to message))))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), "Chat request failed: ${response.body()}")
        val summary = json.readTree(response.body()).get("response")?.asString()
            ?: fail("Unexpected chat response: ${response.body()}")

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
    }

    fun log(step: String) = println("[$logPrefix] $step")

    fun cleanup() {
        filesDir.deleteRecursively()
        projectDir.deleteRecursively()
    }
}
