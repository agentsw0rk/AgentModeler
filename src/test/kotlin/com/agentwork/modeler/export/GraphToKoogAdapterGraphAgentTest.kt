package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphToKoogAdapterGraphAgentTest {

    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts graph-agent node with strategy nodes and edges`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "SearchAgent",
                    "description" to "A search agent",
                    "systemPrompt" to "You are a search agent",
                    "modelProvider" to "anthropic",
                    "modelId" to "claude-sonnet-4",
                    "temperature" to 0.7,
                    "strategyNodes" to listOf(
                        mapOf("id" to "sn1", "type" to "node-start"),
                        mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llmRequest"),
                        mapOf("id" to "sn3", "type" to "node-execute-tool", "label" to "executeTool", "toolName" to "searchTool"),
                        mapOf("id" to "sn4", "type" to "node-llm-send-tool-result", "label" to "sendResult"),
                        mapOf("id" to "sn5", "type" to "node-finish")
                    ),
                    "strategyEdges" to listOf(
                        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
                        mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onToolCall"),
                        mapOf("source" to "sn2", "target" to "sn5", "edgeType" to "onAssistantMessage"),
                        mapOf("source" to "sn3", "target" to "sn4", "edgeType" to "onCondition"),
                        mapOf("source" to "sn4", "target" to "sn2", "edgeType" to "onCondition")
                    )
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool", "description" to "Searches the web",
                    "functions" to listOf(mapOf("name" to "search", "description" to "Searches", "parameters" to listOf(mapOf("name" to "query", "type" to "String", "description" to "Search query", "required" to true))))
                ))
            ),
            listOf(GraphEdge("e1", "t1", "ga1"))
        )

        val result = adapter.convertGraphAgents(graph)
        assertEquals(1, result.size)

        val agent = result.first()
        assertEquals("SearchAgent", agent.name)
        assertEquals("You are a search agent", agent.systemPrompt)
        assertEquals(ModelProvider.ANTHROPIC, agent.modelProvider)
        assertEquals(5, agent.strategyNodes.size)
        assertEquals(5, agent.strategyEdges.size)
        assertEquals(1, agent.tools.size)
        assertEquals("searchTool", agent.tools.first().name)

        val llmNode = agent.strategyNodes.find { it.type == "node-llm-request" }!!
        assertEquals("llmRequest", llmNode.label)

        val toolEdge = agent.strategyEdges.find { it.edgeType == "onToolCall" }!!
        assertEquals("sn2", toolEdge.source)
        assertEquals("sn3", toolEdge.target)
    }

    @Test
    fun `converts graph-agent with inputs and outputs`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyAgent",
                    "description" to "",
                    "systemPrompt" to "Help",
                    "modelProvider" to "openai",
                    "strategyNodes" to listOf(
                        mapOf("id" to "sn1", "type" to "node-start"),
                        mapOf("id" to "sn2", "type" to "node-finish")
                    ),
                    "strategyEdges" to listOf(
                        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition")
                    )
                )),
                GraphNode("i1", "input", Position(-100.0, 0.0), mapOf("className" to null, "fields" to listOf(mapOf("name" to "query", "type" to "String", "description" to "User query", "optional" to false)))),
                GraphNode("o1", "output", Position(300.0, 0.0), mapOf("className" to null, "fields" to listOf(mapOf("name" to "result", "type" to "String", "description" to "Result", "optional" to false))))
            ),
            listOf(
                GraphEdge("e1", "i1", "ga1"),
                GraphEdge("e2", "ga1", "o1")
            )
        )

        val result = adapter.convertGraphAgents(graph)
        assertEquals(1, result.size)
        assertEquals(1, result.first().inputs.size)
        assertEquals("query", result.first().inputs.first().fields.first().name)
        assertEquals(1, result.first().outputs.size)
        assertEquals("result", result.first().outputs.first().fields.first().name)
    }
}
