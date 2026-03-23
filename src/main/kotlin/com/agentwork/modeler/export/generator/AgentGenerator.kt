package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class AgentGenerator {

    fun generate(agent: KoogAgent): String {
        val modelImport = agent.modelProvider.importPackage
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel

        val params = mutableListOf(
            """promptExecutor = ${agent.modelProvider.executorFactory}(System.getenv("${agent.modelProvider.name}_API_KEY"))""",
            """systemPrompt = "${agent.systemPrompt.replace("\"", "\\\"")}"""",
            "llmModel = $modelRef"
        )
        agent.temperature?.let { params.add("temperature = $it") }
        if (agent.tools.isNotEmpty()) {
            params.add("toolRegistry = createToolRegistry()")
        }

        val paramBlock = params.joinToString(",\n            ")

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.prompt.executor.llms.all.${agent.modelProvider.executorFactory}")
            appendLine("import $modelImport")
            appendLine()
            appendLine("class ${agent.name} {")
            appendLine()
            appendLine("    suspend fun run(userMessage: String): String {")
            appendLine("        val agent = AIAgent(")
            appendLine("            $paramBlock")
            appendLine("        )")
            appendLine()
            appendLine("        return agent.run(userMessage)")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun mapModelId(provider: ModelProvider, id: String): String = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
            "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
            "gpt-4-1" -> "OpenAIModels.Chat.GPT4_1"
            else -> provider.defaultModel
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
            "claude-opus-4" -> "AnthropicModels.Opus_4"
            "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
            else -> provider.defaultModel
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
            "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
            else -> provider.defaultModel
        }
    }
}
