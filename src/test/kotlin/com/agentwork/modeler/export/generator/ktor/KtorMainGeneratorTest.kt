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
        assertContains(code, "promptExecutor = simpleOllamaAIExecutor(envOllamaBaseUrl, ")
        assertFalse(code.contains("_API_KEY"))
    }

    @Test
    fun `starts the server with startSuspend because blocking start is unavailable on Wasm`() {
        val code = generator.generate(KoogAgent("A", "Help", ModelProvider.OLLAMA, null, null, emptyList()))
        assertContains(code, "suspend fun main() {")
        assertContains(code, "}.startSuspend(wait = true)")
    }

    @Test
    fun `installs json content negotiation for the chat route`() {
        val code = generator.generate(KoogAgent("A", "Help", ModelProvider.OLLAMA, null, null, emptyList()))
        assertContains(code, "install(ContentNegotiation) { json() }")
    }

    @Test
    fun `passes a Ktor http client factory to the executor on Wasm`() {
        val ollama = generator.generate(KoogAgent("A", "Help", ModelProvider.OLLAMA, null, null, emptyList()))
        assertContains(ollama, "simpleOllamaAIExecutor(envOllamaBaseUrl, KtorKoogHttpClient.Factory())")
        val openAi = generator.generate(KoogAgent("A", "Help", ModelProvider.OPENAI, null, null, emptyList()))
        assertContains(openAi, "simpleOpenAIExecutor(envApiKey, KtorKoogHttpClient.Factory())")
    }

    @Test
    fun `reads the server port from SERVER_PORT with 8080 as default`() {
        val code = generator.generate(KoogAgent("A", "Help", ModelProvider.OLLAMA, null, null, emptyList()))
        assertContains(code, "process.env.SERVER_PORT")
        assertContains(code, "embeddedServer(CIO, port = serverPort)")
    }
}
