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
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList())),
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
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList())),
            builtinTools = emptyList()
        )
        val code = generator.generateRegistry(subAgent)!!
        assertContains(code, "package generated.subagents")
        assertContains(code, "fun createToolAgentToolRegistry()")
        assertContains(code, "fetchTool()")
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

    @Test
    fun `generates typed sub-agent when inputs and outputs provided`() {
        val subAgent = KoogSubAgent(
            name = "Research", description = "Researches",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList(),
            inputs = listOf(KoogInput(null, listOf(KoogField("query", "String", "Query", false)))),
            outputs = listOf(KoogOutput(null, listOf(KoogField("result", "String", "Result", false))))
        )
        val code = generator.generate(subAgent)
        assertContains(code, "fun createResearch(): AIAgent<ResearchInput, ResearchOutput>")
        assertContains(code, "import generated.subagents.ResearchInput")
        assertContains(code, "import generated.subagents.ResearchOutput")
    }

    @Test
    fun `generates String String sub-agent when no IO`() {
        val subAgent = KoogSubAgent(
            name = "Simple", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "fun createSimple() = AIAgent(")
        assertFalse(code.contains("AIAgent<"))
    }

    @Test
    fun `maps custom ollama model tag to LLModel`() {
        val subAgent = KoogSubAgent(
            name = "LocalHelper", description = "Runs locally", systemPrompt = "Help.",
            modelProvider = ModelProvider.OLLAMA, modelId = "mistral:7b",
            temperature = null, delegationMode = "automatic", inputDescription = "A task",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "llmModel = ai.koog.prompt.llm.LLModel(ai.koog.prompt.llm.LLMProvider.Ollama, \"mistral:7b\"")
    }

    @Test
    fun `ollama sub-agent reads base url with default instead of api key`() {
        val subAgent = KoogSubAgent(
            name = "LocalHelper", description = "Runs locally", systemPrompt = "Help.",
            modelProvider = ModelProvider.OLLAMA, modelId = "llama3.2:latest",
            temperature = null, delegationMode = "automatic", inputDescription = "A task",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "promptExecutor = simpleOllamaAIExecutor(System.getenv(\"OLLAMA_BASE_URL\") ?: \"http://localhost:11434\")")
        assertFalse(code.contains("_API_KEY"))
    }

    @Test
    fun `sub-agent registry compiles against Koog 1_x without removed reflect tools import`() {
        val subAgent = KoogSubAgent(
            name = "ShellAgent", description = "Has shell",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(),
            builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app")))
        )
        val code = generator.generateRegistry(subAgent)!!
        assertFalse(code.contains("import ai.koog.agents.core.tools.reflect.tools"))
    }
}
