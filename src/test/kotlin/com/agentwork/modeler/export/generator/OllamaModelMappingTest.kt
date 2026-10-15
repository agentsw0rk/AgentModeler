package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OllamaModelMappingTest {

    @Test
    fun `maps curated ollama tags to OllamaModels constants`() {
        assertEquals("OllamaModels.Meta.LLAMA_3_2", OllamaModelMapping.modelRef("llama3.2:latest"))
        assertEquals("OllamaModels.Meta.LLAMA_3_2", OllamaModelMapping.modelRef("llama3.2"))
        assertEquals("OllamaModels.Meta.LLAMA_3_2_3B", OllamaModelMapping.modelRef("llama3.2:3b"))
        assertEquals("OllamaModels.Alibaba.QWEN_3_5_9B", OllamaModelMapping.modelRef("qwen3.5:9b"))
        assertEquals("OllamaModels.Alibaba.QWEN_CODER_2_5_32B", OllamaModelMapping.modelRef("qwen2.5-coder:32b"))
        assertEquals("OllamaModels.OpenAI.GPT_OSS_20B", OllamaModelMapping.modelRef("GPT-OSS:20B"))
        assertEquals("OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B", OllamaModelMapping.modelRef("llama3-groq-tool-use:8b"))
    }

    @Test
    fun `builds a custom LLModel for unknown ollama tags instead of falling back`() {
        assertEquals(
            "ai.koog.prompt.llm.LLModel(ai.koog.prompt.llm.LLMProvider.Ollama, \"mistral:7b\", " +
                "listOf(ai.koog.prompt.llm.LLMCapability.Temperature, ai.koog.prompt.llm.LLMCapability.Schema.JSON.Standard, " +
                "ai.koog.prompt.llm.LLMCapability.Tools))",
            OllamaModelMapping.modelRef("mistral:7b")
        )
    }

    @Test
    fun `escapes custom model ids so they cannot break the generated string literal`() {
        val ref = OllamaModelMapping.modelRef("evil\"\$x\\")
        assertEquals(true, ref.contains("\"evil\\\"\\\$x\\\\\""))
    }
}
