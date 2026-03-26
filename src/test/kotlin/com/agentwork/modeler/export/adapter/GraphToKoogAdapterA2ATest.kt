package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphToKoogAdapterA2ATest {

    private val adapter = GraphToKoogAdapter()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    @Test
    fun `converts a2a-agent node connected to agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = listOf(edge("a2a1", "agent1")),
            nodes = listOf(
                node("agent1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("a2a1", "a2a-agent", mapOf(
                    "name" to "RemoteBot",
                    "description" to "A remote agent",
                    "url" to "http://remote:8080/a2a",
                    "authType" to "bearer",
                    "authToken" to "tok123",
                    "timeout" to 30000,
                    "communicationMode" to "task"
                ))
            )
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].a2aAgents.size)
        val a2a = agents[0].a2aAgents[0]
        assertEquals("RemoteBot", a2a.name)
        assertEquals("http://remote:8080/a2a", a2a.url)
        assertEquals("bearer", a2a.authType)
        assertEquals("tok123", a2a.authToken)
        assertEquals(30000, a2a.timeout)
        assertEquals("task", a2a.communicationMode)
    }

    @Test
    fun `extracts a2aConfig from agent node data`() {
        val a2aConfigData = mapOf(
            "enabled" to true,
            "agentCard" to mapOf(
                "name" to "MyBot",
                "description" to "A helpful bot",
                "url" to "http://localhost:9000/a2a",
                "version" to "1.0.0",
                "capabilities" to mapOf(
                    "streaming" to true,
                    "pushNotifications" to false,
                    "stateTransitionHistory" to true
                ),
                "skills" to listOf(
                    mapOf("id" to "s1", "name" to "search", "description" to "Search the web", "tags" to listOf("web"))
                ),
                "defaultInputModes" to listOf("text"),
                "defaultOutputModes" to listOf("text")
            ),
            "server" to mapOf(
                "transportType" to "http-jsonrpc",
                "port" to 9000,
                "basePath" to "/a2a"
            )
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = emptyList(),
            nodes = listOf(
                node("agent1", "agent", mapOf(
                    "name" to "Bot", "systemPrompt" to "Hi",
                    "a2aConfig" to a2aConfigData
                ))
            )
        )
        val agents = adapter.convert(graph)
        val config = agents[0].a2aConfig
        assertNotNull(config)
        assertTrue(config.enabled)
        assertEquals("MyBot", config.agentCard.name)
        assertEquals("http://localhost:9000/a2a", config.agentCard.url)
        assertTrue(config.agentCard.capabilities.streaming)
        assertEquals(1, config.agentCard.skills.size)
        assertEquals("search", config.agentCard.skills[0].name)
        assertEquals(9000, config.server.port)
    }

    @Test
    fun `agent without a2aConfig has null a2aConfig`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = emptyList(),
            nodes = listOf(node("agent1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")))
        )
        val agents = adapter.convert(graph)
        assertNull(agents[0].a2aConfig)
        assertTrue(agents[0].a2aAgents.isEmpty())
    }

    @Test
    fun `converts a2a-agent connected to planner-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = listOf(edge("a2a1", "p1")),
            nodes = listOf(
                node("p1", "planner-agent", mapOf(
                    "name" to "Planner", "plannerType" to "llm", "modelProvider" to "openai",
                    "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)), "priority" to 0)),
                    "actions" to listOf(mapOf("name" to "a1", "description" to "act", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true)), "cost" to 1))
                )),
                node("a2a1", "a2a-agent", mapOf(
                    "name" to "Helper",
                    "description" to "Helps",
                    "url" to "http://helper:8080",
                    "authType" to "none",
                    "timeout" to 5000,
                    "communicationMode" to "message"
                ))
            )
        )
        val planners = adapter.convertPlannerAgents(graph)
        assertEquals(1, planners.size)
        assertEquals(1, planners[0].a2aAgents.size)
        assertEquals("Helper", planners[0].a2aAgents[0].name)
    }
}
