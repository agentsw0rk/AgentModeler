package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SubAgentGeneratorTest {

    private val generator = SubAgentGenerator()

    @Test
    fun `generates sub-agent function with correct provider and model`() {
        val subAgent = KoogSubAgent(
            name = "ResearchAgent", description = "Researches topics",
            systemPrompt = "You are a research specialist.",
            modelProvider = ModelProvider.ANTHROPIC, modelId = "claude-sonnet-4",
            temperature = 0.5, delegationMode = "automatic",
            inputDescription = "A topic to research",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "package generated.subagents")
        assertContains(code, "fun createResearchAgent()")
        assertContains(code, "simpleAnthropicExecutor")
        assertContains(code, "ANTHROPIC_API_KEY")
        assertContains(code, "You are a research specialist.")
        assertContains(code, "AnthropicModels")
    }

    @Test
    fun `generates sub-agent with temperature`() {
        val subAgent = KoogSubAgent(
            name = "SA", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = 0.7, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "temperature = 0.7")
    }

    @Test
    fun `generates sub-agent with tool registry when tools exist`() {
        val subAgent = KoogSubAgent(
            name = "ToolAgent", description = "Has tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList(), null)),
            builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "toolRegistry = createToolAgentToolRegistry()")
    }

    @Test
    fun `omits tool registry when no tools`() {
        val subAgent = KoogSubAgent(
            name = "SimpleAgent", description = "No tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertFalse(code.contains("toolRegistry"))
    }

    @Test
    fun `generates sub-agent registry file when tools exist`() {
        val subAgent = KoogSubAgent(
            name = "ToolAgent", description = "Has tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList(), null)),
            builtinTools = emptyList()
        )
        val code = generator.generateRegistry(subAgent)!!
        assertContains(code, "package generated.subagents")
        assertContains(code, "fun createToolAgentToolRegistry()")
        assertContains(code, "FetchToolToolSet")
    }

    @Test
    fun `returns null registry when no tools`() {
        val subAgent = KoogSubAgent(
            name = "SA", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val result = generator.generateRegistry(subAgent)
        assert(result == null)
    }
}
