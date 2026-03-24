package com.agentwork.modeler.export.model

data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList(),
    val mcpServers: List<KoogMcpServer> = emptyList()
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

enum class BuiltinToolType {
    THINK, SHELL, FILESYSTEM, FETCH, MEMORY, TODO, USER_PROMPT;

    fun toFileName(): String? = when (this) {
        SHELL -> "ShellToolSet.kt"
        FETCH -> "FetchToolSet.kt"
        MEMORY -> "MemoryToolSet.kt"
        TODO -> "TodoToolSet.kt"
        else -> null
    }
}

data class KoogBuiltinTool(
    val type: BuiltinToolType,
    val config: Map<String, Any?> = emptyMap()
)

data class KoogSubAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val delegationMode: String,
    val inputDescription: String,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>
)
