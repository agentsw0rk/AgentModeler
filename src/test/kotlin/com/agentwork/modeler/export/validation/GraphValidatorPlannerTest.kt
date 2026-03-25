package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GraphValidatorPlannerTest {
    private val validator = GraphValidator()

    private fun plannerNode(data: Map<String, Any?>) = GraphNode(
        id = "node-1", type = "planner-agent", position = Position(0.0, 0.0), data = data
    )

    private fun validPlannerData() = mapOf<String, Any?>(
        "name" to "MyPlanner",
        "plannerType" to "llm",
        "modelProvider" to "anthropic",
        "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)))),
        "actions" to listOf(mapOf("name" to "a1", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true))))
    )

    @Test
    fun `valid planner agent produces no errors`() {
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(validPlannerData())), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `planner agent without name produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["name"] = "" }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") }, "Expected name error")
    }

    @Test
    fun `planner agent without goals produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["goals"] = emptyList<Any>() }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("goal") }, "Expected goal error")
    }

    @Test
    fun `planner agent without actions produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["actions"] = emptyList<Any>() }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("action") }, "Expected action error")
    }

    @Test
    fun `planner and regular agent cannot coexist`() {
        val graph = WorkspaceGraph(1, "ws", listOf(
            plannerNode(validPlannerData()),
            GraphNode("node-2", "agent", Position(0.0, 0.0), mapOf("name" to "Agent", "systemPrompt" to "Hi"))
        ), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("coexist") || it.contains("both") }, "Expected coexistence error")
    }
}
