package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GraphValidatorIOTest {

    private val validator = GraphValidator()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    private fun agentNode(id: String = "a1") =
        node(id, "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))

    @Test
    fun `input without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", mapOf("name" to "", "dataType" to "string", "required" to true))),
            edges = listOf(edge("i1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("i1") })
    }

    @Test
    fun `output without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", mapOf("name" to "", "dataType" to "string"))),
            edges = listOf(edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("o1") })
    }

    @Test
    fun `input not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("i1") })
    }

    @Test
    fun `output not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", mapOf("name" to "result", "dataType" to "string"))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("o1") })
    }

    @Test
    fun `input connected to multiple agents is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot1", "systemPrompt" to "Hi")),
                node("a2", "agent", mapOf("name" to "Bot2", "systemPrompt" to "Hi")),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true))
            ),
            edges = listOf(edge("i1", "a1"), edge("i1", "a2"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("one") && it.contains("i1") })
    }

    @Test
    fun `duplicate input name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true)),
                node("i2", "input", mapOf("name" to "query", "dataType" to "number", "required" to false))
            ),
            edges = listOf(edge("i1", "a1"), edge("i2", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Duplicate") && it.contains("query") })
    }

    @Test
    fun `duplicate output name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("o1", "output", mapOf("name" to "result", "dataType" to "string")),
                node("o2", "output", mapOf("name" to "result", "dataType" to "number"))
            ),
            edges = listOf(edge("a1", "o1"), edge("a1", "o2"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Duplicate") && it.contains("result") })
    }

    @Test
    fun `valid input and output produce no errors`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "result", "dataType" to "string"))
            ),
            edges = listOf(edge("i1", "a1"), edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.none { it.contains("i1") || it.contains("o1") })
    }
}
