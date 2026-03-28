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
        val graph = WorkspaceGraph(
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
                    "functions" to listOf(
                        mapOf("name" to "search", "description" to "Search the web", "parameters" to listOf(
                            mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "Search query", "required" to true)
                        ))
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
        assertEquals(1, agents[0].tools[0].functions.size)
        assertEquals(1, agents[0].tools[0].functions[0].parameters.size)
        assertEquals(KoogParamType.STRING, agents[0].tools[0].functions[0].parameters[0].type)
    }

    @Test
    fun `throws on agent without name`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "Prompt", "modelProvider" to "openai"))),
            edges = emptyList()
        )
        assertThrows<IllegalArgumentException> { adapter.convert(graph) }
    }

    @Test
    fun `agent with no connected tools gets empty tool list`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Prompt", "modelProvider" to "openai"))),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertEquals(0, agents[0].tools.size)
    }

    @Test
    fun `converts builtin-tool nodes to KoogBuiltinTool list`() {
        val graph = WorkspaceGraph(
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
        val graph = WorkspaceGraph(
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
                    "functions" to emptyList<Any>()
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
    fun `converts mcp-server nodes to KoogMcpServer list`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
                )),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Google Maps", "transportType" to "sse",
                    "url" to "http://localhost:8931",
                    "envVars" to listOf(mapOf("id" to "1", "name" to "GOOGLE_MAPS_API_KEY"))
                ))
            ),
            edges = listOf(GraphEdge("e1", "mcp1", "a1"))
        )

        val agents = adapter.convert(graph)
        assertEquals(1, agents[0].mcpServers.size)
        val mcp = agents[0].mcpServers[0]
        assertEquals("Google Maps", mcp.name)
        assertEquals("sse", mcp.transportType)
        assertEquals("http://localhost:8931", mcp.url)
        assertEquals(1, mcp.envVars.size)
        assertEquals("GOOGLE_MAPS_API_KEY", mcp.envVars[0].name)
    }

    @Test
    fun `converts stdio mcp-server with command and args`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "Agent", "systemPrompt" to "Help", "modelProvider" to "openai"
                )),
                GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                    "name" to "Playwright", "transportType" to "stdio",
                    "command" to "npx", "args" to listOf("@playwright/mcp", "--headless"),
                    "envVars" to emptyList<Any>()
                ))
            ),
            edges = listOf(GraphEdge("e1", "mcp1", "a1"))
        )

        val agents = adapter.convert(graph)
        val mcp = agents[0].mcpServers[0]
        assertEquals("stdio", mcp.transportType)
        assertEquals("npx", mcp.command)
        assertEquals(listOf("@playwright/mcp", "--headless"), mcp.args)
    }

    @Test
    fun `passes otelConfig from graph to KoogAgent`() {
        val otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces", serviceName = "test-svc")
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            edges = emptyList(),
            otelConfig = otelConfig
        )
        val agents = adapter.convert(graph)
        assertEquals(otelConfig, agents[0].otelConfig)
    }

    @Test
    fun `otelConfig defaults to null when not provided`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertEquals(null, agents[0].otelConfig)
    }

    @Test
    fun `sub-agent tools are not included in main agent tools`() {
        val graph = WorkspaceGraph(
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
                    "functions" to emptyList<Any>()
                )),
                GraphNode("t2", "tool", Position(400.0, 0.0), mapOf(
                    "name" to "mainTool", "description" to "MainTool",
                    "functions" to emptyList<Any>()
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
