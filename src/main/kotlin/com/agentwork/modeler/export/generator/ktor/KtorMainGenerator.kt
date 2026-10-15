package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.generator.OllamaModelMapping
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
        val isOllama = agent.modelProvider == ModelProvider.OLLAMA
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
            appendLine("import kotlin.js.JsString")
            appendLine()
            // WasmJS: use @JsFun for JS interop (js() has strict restrictions in Wasm)
            if (isOllama) {
                appendLine("""@JsFun("() => { try { return process.env.${OllamaModelMapping.BASE_URL_ENV} || '' } catch(e) { return '' } }")""")
                appendLine("""private external fun readOllamaBaseUrl(): JsString""")
                appendLine()
                appendLine("""private val envOllamaBaseUrl: String = readOllamaBaseUrl().toString().ifEmpty { "${OllamaModelMapping.DEFAULT_BASE_URL}" }""")
            } else {
                appendLine("""@JsFun("() => { try { return process.env.$envVarName || '' } catch(e) { return '' } }")""")
                appendLine("""private external fun readApiKey(): JsString""")
                appendLine()
                appendLine("""private val envApiKey: String = readApiKey().toString().also {""")
                appendLine("""    require(it.isNotEmpty()) { "$envVarName environment variable must be set" }""")
                appendLine("""}""")
            }
            appendLine()
            appendLine("fun main() {")

            val escapedPrompt = effectivePrompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

            val params = mutableListOf<String>()
            val executorArg = if (isOllama) "envOllamaBaseUrl" else "envApiKey"
            params.add("promptExecutor = ${agent.modelProvider.executorFactory}($executorArg)")
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

            appendLine("    val agent = AIAgent(")
            appendLine("        $paramBlock")
            appendLine("    )")

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
        ModelProvider.OLLAMA -> OllamaModelMapping.modelRef(id)
    }
}
