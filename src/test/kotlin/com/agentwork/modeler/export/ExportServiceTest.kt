package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.*
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

    private val service = ExportService(
        validator = GraphValidator(),
        adapter = GraphToKoogAdapter(),
        agentGenerator = AgentGenerator(),
        toolGenerator = ToolGenerator(),
        registryGenerator = RegistryGenerator(),
        applicationGenerator = ApplicationGenerator(),
        buildGradleGenerator = BuildGradleGenerator(),
        settingsGradleGenerator = SettingsGradleGenerator(),
        gradlePropertiesGenerator = GradlePropertiesGenerator(),
        applicationPropertiesGenerator = ApplicationPropertiesGenerator(),
        dockerfileGenerator = DockerfileGenerator(),
        controllerGenerator = ControllerGenerator(),
        zipBuilder = ZipBuilder()
    )

    @Test
    fun `exports a valid graph as ZIP with full project structure`() {
        val graph = ProjectGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "searchTool", "description" to "Searches",
                    "parameters" to listOf(mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "query", "required" to true))))
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
        assertTrue(entries.contains("src/main/kotlin/generated/tools/SearchToolToolSet.kt"))

        // Verify content wiring (spot check)
        val settingsContent = readZipFileContent(zip, "settings.gradle.kts")
        assertContains(settingsContent, "TestProject")
        val buildContent = readZipFileContent(zip, "build.gradle.kts")
        assertContains(buildContent, "koog-spring-boot-starter")
    }

    @Test
    fun `exports agent without tools omits ToolRegistry`() {
        val graph = ProjectGraph(1, "TestProject",
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
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.isNotEmpty())
    }

    @Test
    fun `throws ExportValidationException for multi-agent graph`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "openai"))
            ),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.any { it.contains("single-agent") || it.contains("Only single") })
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
