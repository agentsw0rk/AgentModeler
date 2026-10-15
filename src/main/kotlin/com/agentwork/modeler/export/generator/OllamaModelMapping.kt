package com.agentwork.modeler.export.generator

/**
 * Maps Ollama model tags (as used with `ollama pull`) to Kotlin code for generated sources.
 *
 * Curated, tool-capable tags map to `OllamaModels` constants. Any other tag becomes a custom
 * `LLModel` — Ollama users often run models outside a fixed list, so unlike the cloud providers
 * there is no fallback to the default model. Fully qualified names keep generated imports unchanged.
 */
object OllamaModelMapping {

    const val BASE_URL_ENV = "OLLAMA_BASE_URL"
    const val DEFAULT_BASE_URL = "http://localhost:11434"

    private val curated = mapOf(
        "llama3.2:latest" to "OllamaModels.Meta.LLAMA_3_2",
        "llama3.2" to "OllamaModels.Meta.LLAMA_3_2",
        "llama3.2:3b" to "OllamaModels.Meta.LLAMA_3_2_3B",
        "qwen3.5:9b" to "OllamaModels.Alibaba.QWEN_3_5_9B",
        "qwen2.5-coder:32b" to "OllamaModels.Alibaba.QWEN_CODER_2_5_32B",
        "gpt-oss:20b" to "OllamaModels.OpenAI.GPT_OSS_20B",
        "llama3-groq-tool-use:8b" to "OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B",
    )

    fun modelRef(id: String): String = curated[id.lowercase()] ?: customModel(id)

    private fun customModel(id: String): String {
        val literal = id.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        return "ai.koog.prompt.llm.LLModel(ai.koog.prompt.llm.LLMProvider.Ollama, \"$literal\", " +
            "listOf(ai.koog.prompt.llm.LLMCapability.Temperature, ai.koog.prompt.llm.LLMCapability.Schema.JSON.Standard, " +
            "ai.koog.prompt.llm.LLMCapability.Tools))"
    }
}
