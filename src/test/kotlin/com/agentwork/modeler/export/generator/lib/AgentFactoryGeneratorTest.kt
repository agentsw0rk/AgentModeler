package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AgentFactoryGeneratorTest {

    private val generator = AgentFactoryGenerator()

    @Test
    fun `generates factory function for simple agent`() {
        val agent = KoogAgent(
            name = "banking",
            systemPrompt = "You are a banking assistant.",
            modelProvider = ModelProvider.OPENAI,
            modelId = "gpt-4o",
            temperature = 0.7,
            tools = listOf(KoogTool("transfer", "Transfers money", emptyList()))
        )
        val result = generator.generateAgentFactory(agent)

        assertContains(result, "package generated")
        assertContains(result, "fun createBankingAgent(")
        assertContains(result, "executor: SingleLLMPromptExecutor")
        assertContains(result, "model: LLMModel = OpenAIModels.Chat.GPT4o")
        assertContains(result, "AIAgent(")
        assertContains(result, "promptExecutor = executor")
        assertContains(result, "systemPrompt = \"You are a banking assistant.\"")
        assertContains(result, "llmModel = model")
        assertContains(result, "temperature = 0.7")
        assertContains(result, "toolRegistry = createToolRegistry()")
    }

    @Test
    fun `generates factory with strategy call for graph agent`() {
        val result = generator.generateAgentFactory(
            KoogAgent(
                name = "workflow",
                systemPrompt = "Help",
                modelProvider = ModelProvider.ANTHROPIC,
                modelId = null,
                temperature = null,
                tools = emptyList()
            ),
            strategyCall = "createWorkflowStrategy()"
        )
        assertContains(result, "fun createWorkflowAgent(")
        assertContains(result, "strategy = createWorkflowStrategy()")
        assertContains(result, "AnthropicModels.Sonnet_4")
    }

    @Test
    fun `omits toolRegistry when no tools`() {
        val result = generator.generateAgentFactory(
            KoogAgent(
                name = "simple",
                systemPrompt = "Help",
                modelProvider = ModelProvider.GOOGLE,
                modelId = null,
                temperature = null,
                tools = emptyList()
            )
        )
        assertTrue(!result.contains("toolRegistry"))
    }

    @Test
    fun `maps ollama model tag in factory default model`() {
        val agent = KoogAgent(
            name = "local", systemPrompt = "Help.", modelProvider = ModelProvider.OLLAMA,
            modelId = "qwen3.5:9b", temperature = null, tools = emptyList()
        )
        val result = generator.generateAgentFactory(agent)
        assertContains(result, "model: LLMModel = OllamaModels.Alibaba.QWEN_3_5_9B")
        assertContains(result, "import ai.koog.prompt.executor.ollama.client.OllamaModels")
    }
}
