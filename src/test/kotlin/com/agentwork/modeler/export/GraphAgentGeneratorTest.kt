package com.agentwork.modeler.export

import com.agentwork.modeler.export.generator.GraphAgentGenerator
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class GraphAgentGeneratorTest {

    private val generator = GraphAgentGenerator()

    @Test
    fun `generates strategy with tool-calling loop`() {
        val agent = KoogGraphAgent(
            name = "searchAgent",
            description = "A search agent",
            systemPrompt = "Help",
            modelProvider = ModelProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            temperature = 0.7,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-llm-request", "llmRequest", null, null, null),
                KoogStrategyNode("sn3", "node-execute-tool", "executeTool", "searchTool", null, null),
                KoogStrategyNode("sn4", "node-llm-send-tool-result", "sendResult", null, null, null),
                KoogStrategyNode("sn5", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onToolCall", null, null),
                KoogStrategyEdge("sn2", "sn5", "onAssistantMessage", null, null),
                KoogStrategyEdge("sn3", "sn4", "onCondition", null, null),
                KoogStrategyEdge("sn4", "sn2", "onCondition", null, null)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)

        assertContains(code, "fun createSearchAgentStrategy()")
        assertContains(code, "strategy<String, String>(\"searchAgent\")")
        assertContains(code, "val llmRequest by nodeLLMRequest()")
        assertContains(code, "val executeTool by nodeExecuteTool()")
        assertContains(code, "val sendResult by nodeLLMSendToolResult()")
        assertContains(code, "edge(nodeStart forwardTo llmRequest)")
        assertContains(code, "edge(llmRequest forwardTo executeTool onToolCall { true })")
        assertContains(code, "edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })")
        assertContains(code, "edge(executeTool forwardTo sendResult)")
        assertContains(code, "edge(sendResult forwardTo llmRequest)")
        assertFalse(code.contains("val nodeStart"))
        assertFalse(code.contains("val nodeFinish"))
    }

    @Test
    fun `generates condition node and onCondition edge with expression`() {
        val agent = KoogGraphAgent(
            name = "condAgent",
            description = "",
            systemPrompt = "Help",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-condition", "checkResult", null, "result.isNotEmpty()", null),
                KoogStrategyNode("sn3", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onCondition", "result.isNotEmpty()", null)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "val checkResult by nodeCondition(\"result.isNotEmpty()\")")
        assertContains(code, "edge(checkResult forwardTo nodeFinish onCondition { result.isNotEmpty() })")
    }

    @Test
    fun `generates onCondition edge with transformation`() {
        val agent = KoogGraphAgent(
            name = "transformAgent",
            description = "",
            systemPrompt = "Help",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-llm-request", "llm", null, null, null),
                KoogStrategyNode("sn3", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onCondition", "true", "it.uppercase()")
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "onCondition { true } transformed { it.uppercase() }")
    }
}
