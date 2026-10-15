package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class KtorMainGeneratorTest {

    private val generator = KtorMainGenerator()

    @Test
    fun `maps ollama model tag to OllamaModels constant`() {
        val agent = KoogAgent("LocalAgent", "Help", ModelProvider.OLLAMA, "llama3-groq-tool-use:8b", null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "llmModel = OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B")
    }

    @Test
    fun `ollama agent reads optional base url instead of required api key`() {
        val agent = KoogAgent("LocalAgent", "Help", ModelProvider.OLLAMA, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "process.env.OLLAMA_BASE_URL")
        assertContains(code, "ifEmpty { \"http://localhost:11434\" }")
        assertContains(code, "promptExecutor = simpleOllamaAIExecutor(envOllamaBaseUrl)")
        assertFalse(code.contains("_API_KEY"))
    }
}
