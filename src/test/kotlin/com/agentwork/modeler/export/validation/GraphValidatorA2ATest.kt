package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphValidatorA2ATest {

    private val validator = GraphValidator()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    private fun agentNode(id: String = "a1") =
        node(id, "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))

    @Test
    fun `a2a-agent without url is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("URL") && it.contains("a2a1") })
    }

    @Test
    fun `a2a-agent not connected to agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://x", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("a2a1") })
    }

    @Test
    fun `a2a-agent without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "", "url" to "http://x", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("a2a1") })
    }

    @Test
    fun `valid a2a-agent produces no errors`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://remote:8080/a2a", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.none { it.contains("a2a1") })
    }

    @Test
    fun `a2a enabled but agentCard name missing is an error`() {
        val a2aConfig = mapOf(
            "enabled" to true,
            "agentCard" to mapOf("name" to "", "url" to "http://x", "description" to "d", "version" to "1"),
            "server" to mapOf("port" to 9000, "basePath" to "/a2a")
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi", "a2aConfig" to a2aConfig))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("AgentCard") && it.contains("name") })
    }

    @Test
    fun `a2a enabled but agentCard url missing is an error`() {
        val a2aConfig = mapOf(
            "enabled" to true,
            "agentCard" to mapOf("name" to "Bot", "url" to "", "description" to "d", "version" to "1"),
            "server" to mapOf("port" to 9000, "basePath" to "/a2a")
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi", "a2aConfig" to a2aConfig))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("AgentCard") && it.contains("url") })
    }

    @Test
    fun `a2a-agent with timeout zero is a warning`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://x", "authType" to "none", "timeout" to 0, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("timeout") })
    }
}
