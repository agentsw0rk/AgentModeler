package com.agentwork.modeler.export.model

data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>
)

enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash")
}

data class KoogTool(
    val name: String,
    val description: String,
    val parameters: List<KoogToolParameter>,
    val handlerStubName: String?
)

data class KoogToolParameter(
    val name: String,
    val type: KoogParamType,
    val description: String,
    val required: Boolean
)

enum class KoogParamType(val kotlinType: String) {
    STRING("String"),
    INT("Int"),
    FLOAT("Float"),
    BOOLEAN("Boolean")
}
