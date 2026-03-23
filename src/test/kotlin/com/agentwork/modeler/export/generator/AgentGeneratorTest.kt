package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class AgentGeneratorTest {

    private val generator = AgentGenerator()

    @Test
    fun `generates agent definition with correct model and prompt`() {
        val agent = KoogAgent("MyAgent", "You are a helpful assistant.", ModelProvider.OPENAI, "gpt-4o", 0.7, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "class MyAgent")
        assertContains(code, "simpleOpenAIExecutor")
        assertContains(code, "You are a helpful assistant.")
        assertContains(code, "GPT4o")
        assertContains(code, "temperature = 0.7")
    }

    @Test
    fun `generates agent with tool registry reference`() {
        val agent = KoogAgent("AgentWithTools", "Help", ModelProvider.ANTHROPIC, null, null,
            listOf(KoogTool("searchTool", "Searches", emptyList(), null)))
        val code = generator.generate(agent)
        assertContains(code, "toolRegistry")
        assertContains(code, "AnthropicModels")
    }
}
