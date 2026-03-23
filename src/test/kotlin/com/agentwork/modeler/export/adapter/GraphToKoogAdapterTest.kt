package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.BuiltinToolType
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

    @Test
    fun `converts builtin-tool nodes to KoogBuiltinTool list`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell", "workingDir" to "/app")),
                GraphNode("bt2", "builtin-tool", Position(400.0, 0.0), mapOf("toolType" to "think"))
            ),
            edges = listOf(GraphEdge("e1", "bt1", "a1"), GraphEdge("e2", "bt2", "a1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(2, agents[0].builtinTools.size)
        assertEquals(BuiltinToolType.SHELL, agents[0].builtinTools[0].type)
        assertEquals("/app", agents[0].builtinTools[0].config["workingDir"])
        assertEquals(BuiltinToolType.THINK, agents[0].builtinTools[1].type)
    }

    @Test
    fun `converts sub-agent nodes with their own tools`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
                )),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "ResearchAgent", "description" to "Researches topics",
                    "systemPrompt" to "You research.", "inputDescription" to "A topic",
                    "modelProvider" to "anthropic", "modelId" to "claude-sonnet-4", "temperature" to 0.5,
                    "delegationMode" to "automatic"
                )),
                GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                    "name" to "fetchTool", "description" to "Fetches data",
                    "parameters" to emptyList<Any>()
                ))
            ),
            edges = listOf(
                GraphEdge("e1", "sa1", "a1"),
                GraphEdge("e2", "t1", "sa1")
            )
        )

        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals("MainAgent", agents[0].name)
        assertEquals(1, agents[0].subAgents.size)

        val sa = agents[0].subAgents[0]
        assertEquals("ResearchAgent", sa.name)
        assertEquals("Researches topics", sa.description)
        assertEquals("You research.", sa.systemPrompt)
        assertEquals("A topic", sa.inputDescription)
        assertEquals(ModelProvider.ANTHROPIC, sa.modelProvider)
        assertEquals("claude-sonnet-4", sa.modelId)
        assertEquals(0.5, sa.temperature)
        assertEquals("automatic", sa.delegationMode)
        assertEquals(1, sa.tools.size)
        assertEquals("fetchTool", sa.tools[0].name)
    }

    @Test
    fun `sub-agent tools are not included in main agent tools`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
                )),
                GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                    "name" to "SA", "description" to "Desc",
                    "systemPrompt" to "Prompt", "inputDescription" to "Input",
                    "modelProvider" to "openai", "delegationMode" to "automatic"
                )),
                GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                    "name" to "subTool", "description" to "SubTool",
                    "parameters" to emptyList<Any>()
                )),
                GraphNode("t2", "tool", Position(400.0, 0.0), mapOf(
                    "name" to "mainTool", "description" to "MainTool",
                    "parameters" to emptyList<Any>()
                ))
            ),
            edges = listOf(
                GraphEdge("e1", "sa1", "a1"),
                GraphEdge("e2", "t1", "sa1"),
                GraphEdge("e3", "t2", "a1")
            )
        )

        val agents = adapter.convert(graph)
        assertEquals(1, agents[0].tools.size)
        assertEquals("mainTool", agents[0].tools[0].name)
        assertEquals(1, agents[0].subAgents[0].tools.size)
        assertEquals("subTool", agents[0].subAgents[0].tools[0].name)
    }
}
