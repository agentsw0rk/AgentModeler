package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.BuiltinToolType
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AgentGeneratorTest {

    private val generator = AgentGenerator()

    @Test
    fun `generates Spring Configuration with Bean`() {
        val agent = KoogAgent("MyAgent", "You are a helpful assistant.", ModelProvider.OPENAI, "gpt-4o", 0.7, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "@Configuration")
        assertContains(code, "@Bean")
        assertContains(code, "fun agent(): AIAgent<String, String>")
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
        assertContains(code, "toolRegistry = createToolRegistry()")
        assertContains(code, "AnthropicModels")
        assertContains(code, "ANTHROPIC_API_KEY")
    }

    @Test
    fun `omits toolRegistry when no tools`() {
        val agent = KoogAgent("SimpleAgent", "Help", ModelProvider.GOOGLE, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("toolRegistry"))
        assertContains(code, "GoogleModels")
    }

    @Test
    fun `does not produce old standalone class pattern`() {
        val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("class MyAgent"))
        assertFalse(code.contains("suspend fun run("))
    }

    @Test
    fun `appends think instruction to system prompt when THINK builtin tool present`() {
        val agent = KoogAgent("Agent1", "You are helpful.", ModelProvider.OPENAI, null, null, emptyList(),
            listOf(KoogBuiltinTool(BuiltinToolType.THINK)))
        val code = generator.generate(agent)
        assertContains(code, "Think step by step")
    }

    @Test
    fun `does not append think instruction when no THINK builtin tool`() {
        val agent = KoogAgent("Agent1", "You are helpful.", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("Think step by step"))
    }
}
