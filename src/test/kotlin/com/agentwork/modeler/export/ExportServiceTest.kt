package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.AgentGenerator
import com.agentwork.modeler.export.generator.MainGenerator
import com.agentwork.modeler.export.generator.RegistryGenerator
import com.agentwork.modeler.export.generator.ToolGenerator
import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.export.zip.ZipBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ExportServiceTest {

    private val service = ExportService(
        validator = GraphValidator(),
        adapter = GraphToKoogAdapter(),
        agentGenerator = AgentGenerator(),
        toolGenerator = ToolGenerator(),
        registryGenerator = RegistryGenerator(),
        mainGenerator = MainGenerator(),
        zipBuilder = ZipBuilder()
    )

    @Test
    fun `exports a valid graph as ZIP with expected files`() {
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
        assertTrue(entries.any { it.contains("MyAgent.kt") })
        assertTrue(entries.any { it.contains("SearchToolToolSet.kt") })
        assertTrue(entries.any { it.contains("ToolRegistry.kt") })
        assertTrue(entries.any { it.contains("Main.kt") })
    }

    @Test
    fun `throws ExportValidationException for invalid graph`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.isNotEmpty())
    }

    private fun readZipEntries(data: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) { entries.add(entry.name); entry = zis.nextEntry }
        }
        return entries
    }
}
