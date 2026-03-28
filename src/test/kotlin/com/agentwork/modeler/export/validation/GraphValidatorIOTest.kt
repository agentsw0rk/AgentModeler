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

    private fun inputData(vararg fields: Map<String, Any?>): Map<String, Any?> =
        mapOf("className" to null, "fields" to fields.toList())

    private fun outputData(vararg fields: Map<String, Any?>): Map<String, Any?> =
        mapOf("className" to null, "fields" to fields.toList())

    private fun field(name: String, type: String = "String", description: String = "", optional: Boolean = false) =
        mapOf("name" to name, "type" to type, "description" to description, "optional" to optional)

    @Test
    fun `input with blank field name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", inputData(field("", "String")))),
            edges = listOf(edge("i1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("i1") })
    }

    @Test
    fun `output with blank field name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", outputData(field("", "String")))),
            edges = listOf(edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("o1") })
    }

    @Test
    fun `input not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", inputData(field("query")))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("i1") })
    }

    @Test
    fun `output not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", outputData(field("result")))),
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
                node("i1", "input", inputData(field("query")))
            ),
            edges = listOf(edge("i1", "a1"), edge("i1", "a2"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("one") && it.contains("i1") })
    }

    @Test
    fun `duplicate input field name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("i1", "input", inputData(field("query", "String"))),
                node("i2", "input", inputData(field("query", "Double")))
            ),
            edges = listOf(edge("i1", "a1"), edge("i2", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Duplicate") && it.contains("query") })
    }

    @Test
    fun `duplicate output field name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("o1", "output", outputData(field("result", "String"))),
                node("o2", "output", outputData(field("result", "Double")))
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
                node("i1", "input", inputData(field("query"))),
                node("o1", "output", outputData(field("result")))
            ),
            edges = listOf(edge("i1", "a1"), edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.none { it.contains("i1") || it.contains("o1") })
    }
}
