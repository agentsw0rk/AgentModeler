package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphToKoogAdapterIOTest {

    private val adapter = GraphToKoogAdapter()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    @Test
    fun `converts input nodes connected to agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("i1", "input", mapOf("name" to "query", "description" to "Search query", "dataType" to "string", "required" to true, "defaultValue" to null)),
                node("i2", "input", mapOf("name" to "limit", "description" to "Max results", "dataType" to "number", "required" to false, "defaultValue" to "10"))
            ),
            edges = listOf(edge("i1", "a1"), edge("i2", "a1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(2, agents[0].inputs.size)
        val q = agents[0].inputs.find { it.name == "query" }!!
        assertEquals("string", q.dataType)
        assertTrue(q.required)
        val l = agents[0].inputs.find { it.name == "limit" }!!
        assertEquals("number", l.dataType)
        assertEquals("10", l.defaultValue)
    }

    @Test
    fun `converts output nodes connected from agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("o1", "output", mapOf("name" to "result", "description" to "The result", "dataType" to "string"))
            ),
            edges = listOf(edge("a1", "o1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].outputs.size)
        assertEquals("result", agents[0].outputs[0].name)
        assertEquals("string", agents[0].outputs[0].dataType)
    }

    @Test
    fun `agent without io has empty lists`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertTrue(agents[0].inputs.isEmpty())
        assertTrue(agents[0].outputs.isEmpty())
    }

    @Test
    fun `converts input and output nodes connected to sub-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("sa1", "sub-agent", mapOf("name" to "Research", "description" to "d", "systemPrompt" to "p", "inputDescription" to "i", "delegationMode" to "automatic")),
                node("i1", "input", mapOf("name" to "topic", "description" to "Research topic", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "findings", "description" to "Research findings", "dataType" to "string"))
            ),
            edges = listOf(edge("sa1", "a1"), edge("i1", "sa1"), edge("sa1", "o1"))
        )
        val agents = adapter.convert(graph)
        val sa = agents[0].subAgents[0]
        assertEquals(1, sa.inputs.size)
        assertEquals("topic", sa.inputs[0].name)
        assertEquals(1, sa.outputs.size)
        assertEquals("findings", sa.outputs[0].name)
    }

    @Test
    fun `converts io nodes connected to planner-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("p1", "planner-agent", mapOf(
                    "name" to "Planner", "plannerType" to "llm", "modelProvider" to "openai",
                    "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)), "priority" to 0)),
                    "actions" to listOf(mapOf("name" to "a1", "description" to "act", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true)), "cost" to 1))
                )),
                node("i1", "input", mapOf("name" to "goal", "description" to "Goal description", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "plan", "description" to "Generated plan", "dataType" to "object"))
            ),
            edges = listOf(edge("i1", "p1"), edge("p1", "o1"))
        )
        val planners = adapter.convertPlannerAgents(graph)
        assertEquals(1, planners[0].inputs.size)
        assertEquals("goal", planners[0].inputs[0].name)
        assertEquals(1, planners[0].outputs.size)
        assertEquals("plan", planners[0].outputs[0].name)
    }
}
