package com.agentwork.modeler.ollama

data class OllamaModelsResponse(
    val models: List<OllamaModelDto>,
    val error: String? = null
)

data class OllamaModelDto(
    val id: String,
    val label: String,
    val tools: Boolean
)

/** Model as reported by the local Ollama instance. */
data class OllamaModelInfo(
    val name: String,
    val parameterCount: Long?,
    val quantizationLevel: String?,
    val supportsTools: Boolean
)
