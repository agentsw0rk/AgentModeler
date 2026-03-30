package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.backend.SpringBootBackend
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.generator.GraphAgentGenerator
import com.agentwork.modeler.export.generator.McpServerGenerator
import com.agentwork.modeler.export.generator.SubAgentGenerator
import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.export.zip.ZipBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ExportServiceTest {

    private val springBootBackend = SpringBootBackend(
        agentGenerator = AgentGenerator(),
        controllerGenerator = ControllerGenerator(),
        registryGenerator = RegistryGenerator(),
        buildGradleGenerator = BuildGradleGenerator(),
        applicationGenerator = ApplicationGenerator(),
        applicationPropertiesGenerator = ApplicationPropertiesGenerator(),
        dockerfileGenerator = DockerfileGenerator(),
        settingsGradleGenerator = SettingsGradleGenerator(),
        gradlePropertiesGenerator = GradlePropertiesGenerator(),
        plannerAgentGenerator = PlannerAgentGenerator(),
        a2aServerGenerator = A2AServerGenerator()
    )

    private val service = ExportService(
        validator = GraphValidator(),
        adapter = GraphToKoogAdapter(),
        toolGenerator = ToolGenerator(),
        registryGenerator = RegistryGenerator(),
        builtinToolGenerator = BuiltinToolGenerator(),
        subAgentGenerator = SubAgentGenerator(),
        mcpServerGenerator = McpServerGenerator(),
        a2aClientGenerator = A2AClientGenerator(),
        inputOutputGenerator = InputOutputGenerator(),
        graphAgentGenerator = GraphAgentGenerator(),
        zipBuilder = ZipBuilder(),
        backends = listOf(springBootBackend)
    )

    @Test
    fun `exports a valid graph as ZIP with full project structure`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "searchTool", "description" to "Searches",
                    "functions" to listOf(mapOf("name" to "search", "description" to "Searches", "parameters" to listOf(mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "query", "required" to true))))))
            ),
            listOf(GraphEdge("e1", "t1", "a1"))
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)

        // Infrastructure files
        assertTrue(entries.contains("build.gradle.kts"))
        assertTrue(entries.contains("settings.gradle.kts"))
        assertTrue(entries.contains("gradle.properties"))
        assertTrue(entries.contains("Dockerfile"))
        assertTrue(entries.contains("src/main/resources/application.properties"))

        // Kotlin sources
        assertTrue(entries.contains("src/main/kotlin/generated/Application.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ChatController.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/tools/searchTool.kt"))

        // Verify content wiring (spot check)
        val settingsContent = readZipFileContent(zip, "settings.gradle.kts")
        assertContains(settingsContent, "TestProject")
        val buildContent = readZipFileContent(zip, "build.gradle.kts")
        assertContains(buildContent, "koog-spring-boot-starter")
    }

    @Test
    fun `exports agent without tools omits ToolRegistry`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "SimpleAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))
            ),
            emptyList()
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)
        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
        assertTrue(!entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
    }

    @Test
    fun `throws ExportValidationException for invalid graph`() {
        val graph = WorkspaceGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.isNotEmpty())
    }

    @Test
    fun `exports graph with builtin shell tool`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell", "workingDir" to "/app"))
            ),
            listOf(GraphEdge("e1", "bt1", "a1"))
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)
        assertTrue(entries.contains("src/main/kotlin/generated/tools/ShellToolSet.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))

        val shellContent = readZipFileContent(zip, "src/main/kotlin/generated/tools/ShellToolSet.kt")
        assertContains(shellContent, "ProcessBuilder")
    }

    @Test
    fun `exports graph with filesystem builtin creates no file but has registry`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "filesystem"))
            ),
            listOf(GraphEdge("e1", "bt1", "a1"))
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)
        assertTrue(!entries.any { it.contains("FilesystemToolSet") })
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))

        val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
        assertContains(registryContent, "ReadFileTool")
    }

    @Test
    fun `throws ExportValidationException for multi-agent graph`() {
        val graph = WorkspaceGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "openai"))
            ),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.any { it.contains("single-agent") || it.contains("Only single") })
    }

    @Test
    fun `exports graph with sub-agent and sub-agent tools`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "ResearchAgent", "description" to "Researches",
                    "systemPrompt" to "Research.", "inputDescription" to "A topic",
                    "modelProvider" to "anthropic", "delegationMode" to "automatic"
                )),
                GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                    "name" to "fetchTool", "description" to "Fetches",
                    "functions" to emptyList<Any>()
                ))
            ),
            listOf(
                GraphEdge("e1", "sa1", "a1"),
                GraphEdge("e2", "t1", "sa1")
            )
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)

        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/subagents/ResearchAgentSubAgent.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/subagents/ResearchAgentToolRegistry.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/tools/fetchTool.kt"))

        val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
        assertContains(registryContent, "createResearchAgent()")
        assertContains(registryContent, "createAgentTool")
    }

    @Test
    fun `exports graph with MCP server`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Google Maps", "transportType" to "sse",
                    "url" to "http://localhost:8931",
                    "envVars" to listOf(mapOf("id" to "1", "name" to "GOOGLE_MAPS_API_KEY"))
                ))
            ),
            listOf(GraphEdge("e1", "mcp1", "a1"))
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)

        assertTrue(entries.contains("src/main/kotlin/generated/mcp/GoogleMapsMcp.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))

        val mcpContent = readZipFileContent(zip, "src/main/kotlin/generated/mcp/GoogleMapsMcp.kt")
        assertContains(mcpContent, "defaultSseTransport")
        assertContains(mcpContent, "http://localhost:8931")

        val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
        assertContains(registryContent, "suspend")
        assertContains(registryContent, "createGoogleMapsRegistry()")

        val agentContent = readZipFileContent(zip, "src/main/kotlin/generated/AgentConfig.kt")
        assertContains(agentContent, "runBlocking")

        val buildContent = readZipFileContent(zip, "build.gradle.kts")
        assertContains(buildContent, "agents-ext-mcp")
    }

    @Test
    fun `export with OTel enabled includes OTel configuration in generated files`() {
        val graph = WorkspaceGraph(1, "OtelProject",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            emptyList(),
            otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces", serviceName = "my-svc")
        )
        val (_, files) = service.exportWithFiles(graph)

        // build.gradle.kts has OTel deps
        assertContains(files["build.gradle.kts"]!!, "koog-opentelemetry")
        assertContains(files["build.gradle.kts"]!!, "opentelemetry-bom")

        // AgentConfig.kt has install block
        assertContains(files["src/main/kotlin/generated/AgentConfig.kt"]!!, "install(OpenTelemetry)")
        assertContains(files["src/main/kotlin/generated/AgentConfig.kt"]!!, "OtlpHttpSpanExporter")

        // application.properties has OTel props
        assertContains(files["src/main/resources/application.properties"]!!, "otel.endpoint=http://localhost:4318/v1/traces")
        assertContains(files["src/main/resources/application.properties"]!!, "otel.service-name=my-svc")
    }

    @Test
    fun `export without OTel has no OTel artifacts`() {
        val graph = WorkspaceGraph(1, "NoOtelProject",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            emptyList()
        )
        val (_, files) = service.exportWithFiles(graph)
        assertTrue(!files["build.gradle.kts"]!!.contains("opentelemetry"))
        assertTrue(!files["src/main/kotlin/generated/AgentConfig.kt"]!!.contains("OpenTelemetry"))
        assertTrue(!files["src/main/resources/application.properties"]!!.contains("otel."))
    }

    @Test
    fun `generates typed agent and controller when IO nodes present`() {
        val graph = WorkspaceGraph(1, "TypedAgent",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("i1", "input", Position(-200.0, 0.0), mapOf("className" to null, "fields" to listOf(mapOf("name" to "query", "type" to "String", "description" to "Search query", "optional" to false)))),
                GraphNode("o1", "output", Position(200.0, 0.0), mapOf("className" to null, "fields" to listOf(mapOf("name" to "result", "type" to "String", "description" to "The result", "optional" to false))))
            ),
            listOf(
                GraphEdge("e1", "i1", "a1"),
                GraphEdge("e2", "a1", "o1")
            )
        )
        val (_, files) = service.exportWithFiles(graph)

        val agentCode = files["src/main/kotlin/generated/AgentConfig.kt"]!!
        assertContains(agentCode, "AIAgent<MyAgentInput, MyAgentOutput>")

        val controllerCode = files["src/main/kotlin/generated/ChatController.kt"]!!
        assertContains(controllerCode, "AIAgent<MyAgentInput, MyAgentOutput>")
        assertContains(controllerCode, "MyAgentInput")

        // I/O classes still generated
        assertTrue(files.containsKey("src/main/kotlin/generated/MyAgentInput.kt"))
        assertTrue(files.containsKey("src/main/kotlin/generated/MyAgentOutput.kt"))
    }

    private fun readZipEntries(data: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) { entries.add(entry.name); entry = zis.nextEntry }
        }
        return entries
    }

    private fun readZipFileContent(data: ByteArray, fileName: String): String {
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == fileName) return zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        error("File $fileName not found in ZIP")
    }
}
