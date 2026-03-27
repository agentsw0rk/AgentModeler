package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.WorkspaceGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorMcpToolsTest {

    private lateinit var stateService: EditorStateService
    private lateinit var validator: GraphValidator
    private lateinit var exportService: ExportService
    private lateinit var tools: EditorMcpTools

    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        stateService = EditorStateService()
        validator = GraphValidator()
        exportService = mock()
        tools = EditorMcpTools(stateService, validator, exportService)
    }

    private fun extractNodeId(result: String): String {
        val match = Regex(""""nodeId"\s*:\s*"([^"]+)"""").find(result)
        return match?.groupValues?.get(1) ?: throw AssertionError("No nodeId found in: $result")
    }

    @Test
    fun `listNodeTypes returns all types`() {
        val result = tools.listNodeTypes()
        val parsed = mapper.readValue<Map<String, Any>>(result)
        val types = parsed["types"] as List<*>
        assertEquals(9, types.size)
    }

    @Test
    fun `createNode returns nodeId`() {
        val result = tools.createNode("agent", 100.0, 200.0, mapOf("name" to "TestAgent"))
        val nodeId = extractNodeId(result)
        assertTrue(nodeId.startsWith("node-"))
    }

    @Test
    fun `createNode fails for invalid type`() {
        val result = tools.createNode("invalid-type", 0.0, 0.0, emptyMap())
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("error"))
    }

    @Test
    fun `getGraph returns graph JSON`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val result = tools.getGraph()
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("name"))
        assertTrue(parsed.containsKey("nodes"))
        assertTrue(parsed.containsKey("edges"))
        val nodes = parsed["nodes"] as List<*>
        assertEquals(1, nodes.size)
    }

    @Test
    fun `getNode returns node data`() {
        val createResult = tools.createNode("agent", 10.0, 20.0, mapOf("name" to "MyAgent"))
        val nodeId = extractNodeId(createResult)
        val result = tools.getNode(nodeId)
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertEquals(nodeId, parsed["id"])
        assertEquals("agent", parsed["type"])
    }

    @Test
    fun `getNode fails for unknown id`() {
        val result = tools.getNode("node-999")
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("error"))
    }

    @Test
    fun `createEdge connects nodes`() {
        val agentId = extractNodeId(tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent")))
        val toolId = extractNodeId(tools.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool")))
        val result = tools.createEdge(toolId, agentId)
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("edgeId"))
        val edgeId = parsed["edgeId"] as String
        assertTrue(edgeId.startsWith("edge-"))
    }

    @Test
    fun `updateNode updates properties`() {
        val nodeId = extractNodeId(tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent")))
        tools.updateNode(nodeId, mapOf("name" to "Updated"))
        val nodeResult = tools.getNode(nodeId)
        val parsed = mapper.readValue<Map<String, Any>>(nodeResult)
        val data = parsed["data"] as Map<*, *>
        assertEquals("Updated", data["name"])
    }

    @Test
    fun `deleteNode removes node`() {
        val nodeId = extractNodeId(tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent")))
        tools.deleteNode(nodeId)
        val result = tools.getNode(nodeId)
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("error"))
    }

    @Test
    fun `validateGraph returns validation results`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "Do stuff"))
        val result = tools.validateGraph()
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("valid"))
    }

    @Test
    fun `setWorkspaceName updates name`() {
        tools.setWorkspaceName("New Name")
        val graphResult = tools.getGraph()
        val parsed = mapper.readValue<Map<String, Any>>(graphResult)
        assertEquals("New Name", parsed["name"])
    }

    @Test
    fun `resetGraph clears state`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        tools.resetGraph()
        val graphResult = tools.getGraph()
        val parsed = mapper.readValue<Map<String, Any>>(graphResult)
        val nodes = parsed["nodes"] as List<*>
        assertEquals(0, nodes.size)
    }

    @Test
    fun `applyTemplate creates basic-agent structure`() {
        val result = tools.applyTemplate("basic-agent")
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("template"))
        val graphResult = tools.getGraph()
        val graphParsed = mapper.readValue<Map<String, Any>>(graphResult)
        val nodes = graphParsed["nodes"] as List<*>
        assertTrue(nodes.isNotEmpty())
    }

    @Test
    fun `applyTemplate fails for unknown template`() {
        val result = tools.applyTemplate("nonexistent-template")
        val parsed = mapper.readValue<Map<String, Any>>(result)
        assertTrue(parsed.containsKey("error"))
    }
}
