package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GraphToKoogAdapterPlannerTest {
    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts planner-agent node to KoogPlannerAgent`() {
        val graph = WorkspaceGraph(
            version = 1,
            name = "TestWorkspace",
            nodes = listOf(
                GraphNode(
                    id = "node-1",
                    type = "planner-agent",
                    position = Position(0.0, 0.0),
                    data = mapOf(
                        "name" to "DeliveryPlanner",
                        "description" to "Plans deliveries",
                        "plannerType" to "llm",
                        "modelProvider" to "anthropic",
                        "modelId" to "claude-sonnet-4",
                        "temperature" to 0.7,
                        "maxReplanAttempts" to 5,
                        "goals" to listOf(
                            mapOf(
                                "id" to "g1",
                                "name" to "all_delivered",
                                "conditions" to listOf(
                                    mapOf("key" to "delivered", "operator" to "equals", "value" to true)
                                ),
                                "priority" to 1
                            )
                        ),
                        "actions" to listOf(
                            mapOf(
                                "id" to "a1",
                                "name" to "pickup",
                                "description" to "Pick up item",
                                "preconditions" to listOf(
                                    mapOf("key" to "at_warehouse", "operator" to "equals", "value" to true)
                                ),
                                "effects" to listOf(
                                    mapOf("key" to "has_item", "value" to true)
                                ),
                                "cost" to 2
                            )
                        ),
                        "initialState" to listOf(
                            mapOf("key" to "at_warehouse", "value" to true),
                            mapOf("key" to "has_item", "value" to false)
                        )
                    )
                )
            ),
            edges = emptyList()
        )

        val result = adapter.convertPlannerAgents(graph)
        assertEquals(1, result.size)
        val planner = result[0]
        assertEquals("DeliveryPlanner", planner.name)
        assertEquals("Plans deliveries", planner.description)
        assertEquals(PlannerType.LLM, planner.plannerType)
        assertEquals(ModelProvider.ANTHROPIC, planner.modelProvider)
        assertEquals("claude-sonnet-4", planner.modelId)
        assertEquals(0.7, planner.temperature)
        assertEquals(5, planner.maxReplanAttempts)
        assertEquals(1, planner.goals.size)
        assertEquals("all_delivered", planner.goals[0].name)
        assertEquals(1, planner.goals[0].conditions.size)
        assertEquals(ConditionOperator.EQUALS, planner.goals[0].conditions[0].operator)
        assertEquals(1, planner.actions.size)
        assertEquals("pickup", planner.actions[0].name)
        assertEquals(2, planner.actions[0].cost)
        assertEquals(1, planner.actions[0].preconditions.size)
        assertEquals(1, planner.actions[0].effects.size)
        assertEquals(2, planner.initialState.size)
    }

    @Test
    fun `converts planner-agent with connected tools`() {
        val graph = WorkspaceGraph(
            version = 1,
            name = "TestWorkspace",
            nodes = listOf(
                GraphNode(
                    id = "node-1",
                    type = "planner-agent",
                    position = Position(0.0, 0.0),
                    data = mapOf(
                        "name" to "Planner",
                        "description" to "",
                        "plannerType" to "goap",
                        "maxReplanAttempts" to 3,
                        "goals" to emptyList<Any>(),
                        "actions" to emptyList<Any>(),
                        "initialState" to emptyList<Any>()
                    )
                ),
                GraphNode(
                    id = "node-2",
                    type = "tool",
                    position = Position(100.0, 0.0),
                    data = mapOf(
                        "name" to "SearchTool",
                        "description" to "Searches",
                        "parameters" to emptyList<Any>()
                    )
                )
            ),
            edges = listOf(
                GraphEdge(id = "edge-1", source = "node-2", target = "node-1")
            )
        )

        val result = adapter.convertPlannerAgents(graph)
        assertEquals(1, result.size)
        assertEquals(PlannerType.GOAP, result[0].plannerType)
        assertEquals(1, result[0].tools.size)
        assertEquals("SearchTool", result[0].tools[0].name)
    }
}
