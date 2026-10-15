package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class AgentFactoryGenerator {

    fun generateAgentFactory(agent: KoogAgent, strategyCall: String? = null, inputClassName: String? = null, outputClassName: String? = null): String {
        val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
        val effectivePrompt = if (hasThink) {
            agent.systemPrompt + "\nThink step by step before answering."
        } else {
            agent.systemPrompt
        }
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val funcName = "create${agent.name.replaceFirstChar { it.uppercase() }}Agent"

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor")
            appendLine("import ai.koog.prompt.llm.LLMModel")
            appendLine("import ${agent.modelProvider.importPackage}")
            appendLine()

            val escapedPrompt = effectivePrompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

            appendLine("fun $funcName(")
            appendLine("    executor: SingleLLMPromptExecutor,")
            appendLine("    model: LLMModel = $modelRef")
            appendLine("): AIAgent = AIAgent(")
            appendLine("    promptExecutor = executor,")
            appendLine("    systemPrompt = \"$escapedPrompt\",")
            append("    llmModel = model")
            agent.temperature?.let { append(",\n    temperature = $it") }
            if (strategyCall != null) {
                append(",\n    strategy = $strategyCall")
            }
            if (hasTools) {
                append(",\n    toolRegistry = createToolRegistry()")
            }
            appendLine()
            appendLine(")")
        }
    }

    fun generatePlannerFactory(planner: KoogPlannerAgent): String {
        val modelRef = if (planner.modelProvider != null) {
            planner.modelId?.let { mapModelId(planner.modelProvider, it) } ?: planner.modelProvider.defaultModel
        } else {
            "OpenAIModels.Chat.GPT4o"
        }
        val funcName = "create${planner.name.replaceFirstChar { it.uppercase() }}Planner"
        val importPkg = planner.modelProvider?.importPackage ?: "ai.koog.prompt.executor.clients.openai.OpenAIModels"

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor")
            appendLine("import ai.koog.prompt.llm.LLMModel")
            appendLine("import $importPkg")
            appendLine("import generated.planner.*")
            appendLine()
            appendLine("fun $funcName(")
            appendLine("    executor: SingleLLMPromptExecutor,")
            appendLine("    model: LLMModel = $modelRef")
            appendLine(") {")
            appendLine("    // Planner agent setup — see PlannerGoals.kt and PlannerActions.kt")
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
        ModelProvider.BEDROCK -> when (id.lowercase()) {
            "claude-4.5-sonnet" -> "BedrockModels.AnthropicClaude4_5Sonnet"
            "claude-4-sonnet" -> "BedrockModels.AnthropicClaude4Sonnet"
            "claude-4-opus" -> "BedrockModels.AnthropicClaude4Opus"
            "nova-pro" -> "BedrockModels.AmazonNovaPro"
            "nova-lite" -> "BedrockModels.AmazonNovaLite"
            "nova-micro" -> "BedrockModels.AmazonNovaMicro"
            "llama-3.3-70b" -> "BedrockModels.MetaLlama3_3_70BInstruct"
            "llama-3.1-405b" -> "BedrockModels.MetaLlama3_1_405BInstruct"
            else -> provider.defaultModel
        }
        ModelProvider.OLLAMA -> provider.defaultModel
    }
}
