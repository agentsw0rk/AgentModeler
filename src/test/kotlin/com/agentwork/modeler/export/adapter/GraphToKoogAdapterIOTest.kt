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
                node("i1", "input", mapOf("className" to null, "fields" to listOf(
                    mapOf("name" to "query", "type" to "String", "description" to "Search query", "optional" to false),
                    mapOf("name" to "limit", "type" to "Double", "description" to "Max results", "optional" to true)
                )))
            ),
            edges = listOf(edge("i1", "a1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        val input = agents[0].inputs.first()
        assertEquals(2, input.fields.size)
        val q = input.fields.find { it.name == "query" }!!
        assertEquals("String", q.type)
        val l = input.fields.find { it.name == "limit" }!!
        assertEquals("Double", l.type)
    }

    @Test
    fun `converts output nodes connected from agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("o1", "output", mapOf("className" to null, "fields" to listOf(mapOf("name" to "result", "type" to "String", "description" to "The result", "optional" to false))))
            ),
            edges = listOf(edge("a1", "o1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        val output = agents[0].outputs.first()
        assertEquals(1, output.fields.size)
        assertEquals("result", output.fields[0].name)
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
                node("i1", "input", mapOf("className" to null, "fields" to listOf(mapOf("name" to "topic", "type" to "String", "description" to "Research topic", "optional" to false)))),
                node("o1", "output", mapOf("className" to null, "fields" to listOf(mapOf("name" to "findings", "type" to "String", "description" to "Research findings", "optional" to false))))
            ),
            edges = listOf(edge("sa1", "a1"), edge("i1", "sa1"), edge("sa1", "o1"))
        )
        val agents = adapter.convert(graph)
        val sa = agents[0].subAgents[0]
        val saInput = sa.inputs.first()
        assertEquals(1, saInput.fields.size)
        assertEquals("topic", saInput.fields[0].name)
        val saOutput = sa.outputs.first()
        assertEquals(1, saOutput.fields.size)
        assertEquals("findings", saOutput.fields[0].name)
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
                node("i1", "input", mapOf("className" to null, "fields" to listOf(mapOf("name" to "goal", "type" to "String", "description" to "Goal description", "optional" to false)))),
                node("o1", "output", mapOf("className" to null, "fields" to listOf(mapOf("name" to "plan", "type" to "Map", "description" to "Generated plan", "optional" to false))))
            ),
            edges = listOf(edge("i1", "p1"), edge("p1", "o1"))
        )
        val planners = adapter.convertPlannerAgents(graph)
        val plannerInput = planners[0].inputs.first()
        assertEquals(1, plannerInput.fields.size)
        assertEquals("goal", plannerInput.fields[0].name)
        val plannerOutput = planners[0].outputs.first()
        assertEquals(1, plannerOutput.fields.size)
        assertEquals("plan", plannerOutput.fields[0].name)
    }
}
