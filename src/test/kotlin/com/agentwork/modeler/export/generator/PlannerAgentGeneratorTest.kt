package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PlannerAgentGeneratorTest {
    private val generator = PlannerAgentGenerator()

    private fun llmPlanner(
        tools: List<KoogTool> = emptyList(),
        mcpServers: List<KoogMcpServer> = emptyList()
    ) = KoogPlannerAgent(
        name = "DeliveryPlanner",
        description = "Plans deliveries",
        plannerType = PlannerType.LLM,
        modelProvider = ModelProvider.ANTHROPIC,
        modelId = "claude-sonnet-4",
        temperature = 0.7,
        maxReplanAttempts = 5,
        goals = listOf(
            PlannerGoal("all_delivered", listOf(StateCondition("delivered", ConditionOperator.EQUALS, true)), 1)
        ),
        actions = listOf(
            PlannerAction(
                "pickup", "Pick up item",
                listOf(StateCondition("at_warehouse", ConditionOperator.EQUALS, true)),
                listOf(StateEffect("has_item", true)),
                2
            )
        ),
        initialState = mapOf("at_warehouse" to true, "has_item" to false),
        tools = tools,
        mcpServers = mcpServers
    )

    private fun goapPlanner() = llmPlanner().copy(
        plannerType = PlannerType.GOAP,
        modelProvider = null,
        modelId = null,
        temperature = null
    )

    @Test
    fun `generates LLM planner Spring Configuration`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "@Configuration")
        assertContains(config, "@Bean")
        assertContains(config, "AnthropicModels")
        assertContains(config, "simpleAnthropicExecutor")
        assertContains(config, "all_delivered")
        assertContains(config, "pickup")
        assertContains(config, "maxReplanAttempts = 5")
    }

    @Test
    fun `generates GOAP planner with solver`() {
        val files = generator.generate(goapPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "GOAP")
        assertContains(config, "cost")
        assertContains(config, "all_delivered")
        assertContains(config, "pickup")
        assertFalse(config.contains("simpleAnthropicExecutor"))
    }

    @Test
    fun `generates goals and actions data classes`() {
        val files = generator.generate(llmPlanner())
        val goals = files["PlannerGoals.kt"]!!
        assertContains(goals, "all_delivered")
        assertContains(goals, "delivered")
        assertContains(goals, "EQUALS")

        val actions = files["PlannerActions.kt"]!!
        assertContains(actions, "pickup")
        assertContains(actions, "at_warehouse")
        assertContains(actions, "has_item")
        assertContains(actions, "cost = 2")
    }

    @Test
    fun `generates initial state`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "at_warehouse")
        assertContains(config, "has_item")
    }

    @Test
    fun `generates tool registry when tools present`() {
        val tool = KoogTool("SearchTool", "Searches things", emptyList())
        val files = generator.generate(llmPlanner(tools = listOf(tool)))
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "toolRegistry")
    }

    @Test
    fun `omits tool registry when no tools`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertFalse(config.contains("toolRegistry"))
    }

    @Test
    fun `maps ollama model tag in planner config`() {
        val planner = llmPlanner().copy(modelProvider = ModelProvider.OLLAMA, modelId = "llama3.2:3b")
        val config = generator.generate(planner).values.joinToString("\n")
        assertContains(config, "val model = OllamaModels.Meta.LLAMA_3_2_3B")
    }

    @Test
    fun `ollama planner uses base url with default instead of api key`() {
        val planner = llmPlanner().copy(modelProvider = ModelProvider.OLLAMA, modelId = "llama3.2:latest")
        val config = generator.generate(planner).values.joinToString("\n")
        assertContains(config, "ollama.base-url:http://localhost:11434}")
        assertContains(config, "private val baseUrl: String")
        assertContains(config, "val executor = simpleOllamaAIExecutor(baseUrl)")
        assertFalse(config.contains("apiKey"))
    }
}
