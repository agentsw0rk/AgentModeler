package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.validation.GraphValidator
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GraphValidatorGraphAgentTest {

    private val validator = GraphValidator()

    private fun validStrategyNodes() = listOf(
        mapOf("id" to "sn1", "type" to "node-start"),
        mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llmRequest"),
        mapOf("id" to "sn3", "type" to "node-finish")
    )

    private fun validStrategyEdges() = listOf(
        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
        mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onAssistantMessage")
    )

    private fun graphAgentNode(overrides: Map<String, Any?> = emptyMap()): GraphNode {
        val data = mapOf(
            "name" to "TestAgent",
            "description" to "A test agent",
            "systemPrompt" to "Help the user",
            "modelProvider" to "openai",
            "strategyNodes" to validStrategyNodes(),
            "strategyEdges" to validStrategyEdges()
        ) + overrides
        return GraphNode("ga1", "graph-agent", Position(0.0, 0.0), data)
    }

    @Test
    fun `valid graph-agent passes validation`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode()), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `graph-agent without name fails`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("name" to ""))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("requires a name") })
    }

    @Test
    fun `graph-agent without system prompt fails`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("systemPrompt" to ""))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("requires a system prompt") })
    }

    @Test
    fun `graph-agent without node-start fails`() {
        val noStart = listOf(
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onAssistantMessage"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to noStart, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("exactly one node-start") })
    }

    @Test
    fun `graph-agent without node-finish fails`() {
        val noFinish = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm")
        )
        val edges = listOf(mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to noFinish, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("exactly one node-finish") })
    }

    @Test
    fun `graph-agent start without outgoing edge fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-finish")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to emptyList<Any>()))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-start must have at least one outgoing edge") })
    }

    @Test
    fun `graph-agent finish not reachable from start fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-finish must be reachable") })
    }

    @Test
    fun `graph-agent node-execute-tool without toolName fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-execute-tool", "label" to "exec"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-execute-tool requires a toolName") })
    }

    @Test
    fun `graph-agent node-condition without condition fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-condition", "label" to "check"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition", "condition" to "true")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-condition requires a condition") })
    }

    @Test
    fun `graph-agent onCondition edge without condition fails`() {
        val nodes = validStrategyNodes()
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("onCondition edge requires a condition") })
    }

    @Test
    fun `cannot mix graph-agent with regular agent`() {
        val graph = WorkspaceGraph(1, "Test", listOf(
            graphAgentNode(),
            GraphNode("a1", "agent", Position(200.0, 0.0), mapOf("name" to "Regular", "systemPrompt" to "Hi", "modelProvider" to "openai"))
        ), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("cannot mix") || it.contains("cannot have both") })
    }
}
