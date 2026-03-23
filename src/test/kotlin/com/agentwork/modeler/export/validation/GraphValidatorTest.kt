package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GraphValidatorTest {
    private val validator = GraphValidator()

    @Test
    fun `valid graph passes validation`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to listOf(mapOf("id" to "p1", "name" to "q", "type" to "String", "description" to "d", "required" to true))))
            ),
            listOf(GraphEdge("e1", "t1", "a1"))
        )
        assertTrue(validator.validate(graph).isEmpty())
    }

    @Test
    fun `reports agent without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("a1") })
    }

    @Test
    fun `reports agent without system prompt`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("prompt") })
    }

    @Test
    fun `reports tool without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("t1") })
    }

    @Test
    fun `reports tool without connected agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList())
        assertTrue(validator.validate(graph).any { it.contains("connected") })
    }

    @Test
    fun `reports parameter without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc",
                "parameters" to listOf(mapOf("id" to "p1", "name" to "", "type" to "String", "description" to "", "required" to true))))),
            listOf(GraphEdge("e1", "t1", "a1")))
        assertTrue(validator.validate(graph).any { it.contains("Parameter") })
    }

    @Test
    fun `reports error for multiple agent nodes`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "anthropic"))
            ),
            emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("single-agent") || it.contains("Only single") })
    }
}
