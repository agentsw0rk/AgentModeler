package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GraphToKoogAdapterTest {

    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts a valid graph with one agent and one tool`() {
        val graph = ProjectGraph(
            version = 1,
            name = "TestProject",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyAgent",
                    "systemPrompt" to "You are helpful.",
                    "modelProvider" to "openai",
                    "modelId" to "gpt-4o",
                    "temperature" to 0.7
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool",
                    "description" to "Searches the web",
                    "parameters" to listOf(
                        mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "Search query", "required" to true)
                    )
                ))
            ),
            edges = listOf(GraphEdge("e1", "t1", "a1"))
        )

        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals("MyAgent", agents[0].name)
        assertEquals(ModelProvider.OPENAI, agents[0].modelProvider)
        assertEquals(1, agents[0].tools.size)
        assertEquals("searchTool", agents[0].tools[0].name)
        assertEquals(1, agents[0].tools[0].parameters.size)
        assertEquals(KoogParamType.STRING, agents[0].tools[0].parameters[0].type)
    }

    @Test
    fun `throws on agent without name`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "Prompt", "modelProvider" to "openai"))),
            edges = emptyList()
        )
        assertThrows<IllegalArgumentException> { adapter.convert(graph) }
    }

    @Test
    fun `agent with no connected tools gets empty tool list`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Prompt", "modelProvider" to "openai"))),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertEquals(0, agents[0].tools.size)
    }
}
