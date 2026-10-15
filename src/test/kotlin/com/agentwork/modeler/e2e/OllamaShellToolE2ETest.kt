package com.agentwork.modeler.e2e

import com.agentwork.modeler.export.ExportService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.fail

/**
 * End-to-end proof that a modeled agent can use a tool against a real local LLM (Spring Boot export):
 *
 * 1. Model a graph: Ollama agent + built-in Shell tool (as the editor would send it)
 * 2. Export it with the real ExportService as a Spring Boot project
 * 3. Build the project with Gradle and start the jar
 * 4. Ask the agent to read a file via the shell tool and summarize it (see [OllamaE2eScenario])
 *
 * Not part of `./gradlew test`. Run with a local Ollama:
 *   ollama pull llama3.2:3b
 *   ./gradlew ollamaE2eTest
 */
@Tag("ollama-e2e")
@SpringBootTest
class OllamaShellToolE2ETest {

    @Autowired
    private lateinit var exportService: ExportService

    @Test
    fun `agent reads a file with the shell tool and summarizes it`() {
        val scenario = OllamaE2eScenario("ollama-e2e")
        scenario.requireOllamaWithModel()

        var app: Process? = null
        try {
            scenario.log("1/4 Modeling agent + shell tool and exporting Spring Boot project")
            scenario.writeProject(exportService.generateFiles(scenario.modeledGraph("ollama-shell-e2e")))

            scenario.log("2/4 Building exported project")
            scenario.gradle("bootJar")

            scenario.log("3/4 Starting exported agent app")
            val port = ServerSocket(0).use { it.localPort }
            val jar = File(scenario.projectDir, "build/libs").listFiles { f -> f.name.endsWith(".jar") && !f.name.endsWith("-plain.jar") }
                ?.singleOrNull() ?: fail("No boot jar found in ${scenario.projectDir}/build/libs")
            val log = File(scenario.projectDir, "app-output.log")
            app = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath, "-jar", jar.absolutePath)
                .apply {
                    environment()["SERVER_PORT"] = port.toString()
                    environment()["OLLAMA_BASE_URL"] = scenario.ollamaUrl
                }
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            scenario.awaitPort(port, log) { app.isAlive }

            scenario.log("4/4")
            scenario.askAgentAndVerify(port)
        } finally {
            app?.destroy()
            app?.waitFor(10, TimeUnit.SECONDS)
            scenario.cleanup()
        }
    }
}
