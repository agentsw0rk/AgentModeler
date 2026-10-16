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
    val mcpServers: List<KoogMcpServer> = emptyList(),
    val otelConfig: OtelConfig? = null,
    val a2aConfig: KoogA2AConfig? = null,
    val a2aAgents: List<KoogA2AAgent> = emptyList(),
    val inputs: List<KoogInput> = emptyList(),
    val outputs: List<KoogOutput> = emptyList(),
    val ragNodes: List<KoogRagNode> = emptyList()
)

enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash"),
    BEDROCK("ai.koog.prompt.executor.clients.bedrock.BedrockModels", "simpleBedrockExecutor", "BedrockModels.AnthropicClaude4Sonnet"),
    OLLAMA("ai.koog.prompt.executor.ollama.client.OllamaModels", "simpleOllamaAIExecutor", "OllamaModels.Meta.LLAMA_3_2")
}

data class KoogTool(
    val name: String,
    val description: String,
    val functions: List<KoogToolFunction>
)

data class KoogToolFunction(
    val name: String,
    val description: String,
    val parameters: List<KoogToolParameter>,
    val returnType: String = "String",
    val body: String = ""
)

data class KoogToolParameter(
    val name: String,
    val type: KoogParamType,
    val description: String,
    val required: Boolean,
    val defaultValue: String?
)

enum class KoogParamType(val kotlinType: String) {
    STRING("String"),
    INT("Int"),
    DOUBLE("Double"),
    FLOAT("Float"),
    BOOLEAN("Boolean"),
    LIST_STRING("List<String>"),
    MAP_STRING_STRING("Map<String, String>")
}

enum class BuiltinToolType {
    THINK, SHELL, FILESYSTEM, FETCH, MEMORY, TODO, USER_PROMPT, JIRA, CONFLUENCE;

    fun toFileName(): String? = when (this) {
        SHELL -> "ShellToolSet.kt"
        FETCH -> "FetchToolSet.kt"
        MEMORY -> "MemoryToolSet.kt"
        TODO -> "TodoToolSet.kt"
        JIRA -> "JiraToolSet.kt"
        CONFLUENCE -> "ConfluenceToolSet.kt"
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
    val builtinTools: List<KoogBuiltinTool>,
    val inputs: List<KoogInput> = emptyList(),
    val outputs: List<KoogOutput> = emptyList()
)

data class KoogSharedCode(
    val name: String,
    val code: String
)
