package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class KtorMainGenerator {

    fun generate(agent: KoogAgent, strategyCall: String? = null): String {
        val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
        val effectivePrompt = if (hasThink) {
            agent.systemPrompt + "\nThink step by step before answering."
        } else {
            agent.systemPrompt
        }
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel
        val envVarName = "${agent.modelProvider.name}_API_KEY"
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import ai.koog.prompt.executor.llms.all.${agent.modelProvider.executorFactory}")
            appendLine("import ${agent.modelProvider.importPackage}")
            if (hasTools) {
                appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            }
            appendLine("import io.ktor.server.engine.*")
            appendLine("import io.ktor.server.cio.*")
            appendLine("import kotlinx.coroutines.runBlocking")
            appendLine()
            appendLine("fun main() {")
            appendLine("""    val apiKey = js("process.env.$envVarName").unsafeCast<String?>()""")
            appendLine("""        ?: error("$envVarName environment variable must be set")""")
            appendLine()

            val escapedPrompt = effectivePrompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

            val params = mutableListOf<String>()
            params.add("""promptExecutor = ${agent.modelProvider.executorFactory}(apiKey)""")
            params.add("""systemPrompt = "$escapedPrompt"""")
            params.add("llmModel = $modelRef")
            agent.temperature?.let { params.add("temperature = $it") }
            if (strategyCall != null) {
                params.add("strategy = $strategyCall")
            }
            if (hasTools) {
                params.add("toolRegistry = createToolRegistry()")
            }

            val paramBlock = params.joinToString(",\n            ")

            if (agent.mcpServers.isNotEmpty()) {
                appendLine("    val agent = runBlocking {")
                appendLine("        AIAgent(")
                appendLine("            $paramBlock")
                appendLine("        )")
                appendLine("    }")
            } else {
                appendLine("    val agent = AIAgent(")
                appendLine("        $paramBlock")
                appendLine("    )")
            }

            appendLine()
            appendLine("    embeddedServer(CIO, port = 8080) {")
            appendLine("        configureChatRoutes(agent)")
            appendLine("    }.start(wait = true)")
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
