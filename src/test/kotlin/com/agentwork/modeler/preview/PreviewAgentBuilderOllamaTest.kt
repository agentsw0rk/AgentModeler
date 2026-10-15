package com.agentwork.modeler.preview

import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLMProvider
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PreviewAgentBuilderOllamaTest {

    private val builder = PreviewAgentBuilder()

    @Test
    fun `builds ollama agent without any api key and uses curated model`() {
        val agent = KoogAgent("Local", "Help", ModelProvider.OLLAMA, "llama3.2:3b", null, emptyList())
        val built = runBlocking { builder.build(agent) }
        assertEquals(OllamaModels.Meta.LLAMA_3_2_3B, built.agentConfig.model)
    }

    @Test
    fun `builds ollama agent with custom model tag`() {
        val agent = KoogAgent("Local", "Help", ModelProvider.OLLAMA, "mistral:7b", null, emptyList())
        val model = runBlocking { builder.build(agent) }.agentConfig.model
        assertEquals(LLMProvider.Ollama, model.provider)
        assertEquals("mistral:7b", model.id)
    }

    @Test
    fun `defaults ollama agent without model to llama 3_2`() {
        val agent = KoogAgent("Local", "Help", ModelProvider.OLLAMA, null, null, emptyList())
        val model = runBlocking { builder.build(agent) }.agentConfig.model
        assertEquals(OllamaModels.Meta.LLAMA_3_2, model)
    }
}
