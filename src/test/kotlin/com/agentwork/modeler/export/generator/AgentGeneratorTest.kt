package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogMcpServer
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

    @Test
    fun `wraps agent in runBlocking when MCP servers present`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            mcpServers = listOf(KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())))
        val code = generator.generate(agent)
        assertContains(code, "runBlocking")
        assertContains(code, "import kotlinx.coroutines.runBlocking")
        assertContains(code, "createToolRegistry()")
    }

    @Test
    fun `no runBlocking without MCP servers`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("runBlocking"))
    }

    @Test
    fun `generates valid string literal for multi-line system prompt`() {
        val multiLinePrompt = "You are a helpful assistant.\nAlways be polite.\nNever lie."
        val agent = KoogAgent("Agent1", multiLinePrompt, ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        // Must be a single-line string literal with escaped newlines, not actual line breaks inside quotes
        val promptLine = code.lines().first { it.contains("systemPrompt") }
        assertContains(promptLine, "\\n")
        assertFalse(promptLine.endsWith("assistant."))
    }
}
