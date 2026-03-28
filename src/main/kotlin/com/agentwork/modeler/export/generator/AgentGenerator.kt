package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class AgentGenerator {

    fun generate(agent: KoogAgent, strategyCall: String? = null, inputClassName: String? = null, outputClassName: String? = null): String {
        val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
        val effectivePrompt = if (hasThink) {
            agent.systemPrompt + "\nThink step by step before answering."
        } else {
            agent.systemPrompt
        }
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"
        val agentType = "AIAgent<$inputType, $outputType>"
        val modelImport = agent.modelProvider.importPackage
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel

        val params = mutableListOf(
            """promptExecutor = ${agent.modelProvider.executorFactory}(apiKey)""",
            """systemPrompt = "${effectivePrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")}"""",
            "llmModel = $modelRef"
        )
        agent.temperature?.let { params.add("temperature = $it") }
        if (strategyCall != null) {
            params.add("strategy = $strategyCall")
        }
        if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
            params.add("toolRegistry = createToolRegistry()")
        }

        val paramBlock = params.joinToString(",\n            ")

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
                appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            }
            appendLine("import ai.koog.prompt.executor.llms.all.${agent.modelProvider.executorFactory}")
            appendLine("import $modelImport")
            if (agent.mcpServers.isNotEmpty()) {
                appendLine("import kotlinx.coroutines.CoroutineScope")
                appendLine("import kotlinx.coroutines.Dispatchers")
                appendLine("import kotlinx.coroutines.SupervisorJob")
                appendLine("import kotlinx.coroutines.runBlocking")
            }
            if (agent.otelConfig?.enabled == true) {
                appendLine("import ai.koog.agents.features.otel.OpenTelemetry")
                when (agent.otelConfig?.exporterType) {
                    "http" -> appendLine("import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter")
                    "grpc" -> appendLine("import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter")
                    "logging" -> appendLine("import io.opentelemetry.exporter.logging.LoggingSpanExporter")
                }
                if (agent.otelConfig?.exporterType in listOf("http", "grpc")) {
                    appendLine("import java.util.concurrent.TimeUnit")
                }
            }
            appendLine("import jakarta.annotation.PostConstruct")
            appendLine("import org.springframework.beans.factory.annotation.Value")
            appendLine("import org.springframework.context.annotation.Bean")
            appendLine("import org.springframework.context.annotation.Configuration")
            appendLine("import org.springframework.context.annotation.Primary")
            appendLine()
            appendLine("@Configuration")
            appendLine("class AgentConfig(")
            val apiKeyTrailingComma = if (agent.otelConfig?.enabled == true) "," else ""
            appendLine("""    @Value("\${'$'}{${agent.modelProvider.name.lowercase()}.api-key}") private val apiKey: String$apiKeyTrailingComma""")
            if (agent.otelConfig?.enabled == true) {
                val otelParams = mutableListOf<String>()
                if (agent.otelConfig?.exporterType != "logging") {
                    otelParams.add("""    @Value("\${'$'}{otel.endpoint}") private val otelEndpoint: String""")
                }
                otelParams.add("""    @Value("\${'$'}{otel.service-name}") private val otelServiceName: String""")
                otelParams.add("""    @Value("\${'$'}{otel.service-version}") private val otelServiceVersion: String""")
                if (agent.otelConfig?.authorizationHeader != null) {
                    otelParams.add("""    @Value("\${'$'}{otel.authorization-header}") private val otelAuthHeader: String""")
                }
                otelParams.add("""    @Value("\${'$'}{otel.verbose}") private val otelVerbose: Boolean""")
                otelParams.forEachIndexed { i, param ->
                    val comma = if (i < otelParams.size - 1) "," else ""
                    appendLine("$param$comma")
                }
            }
            appendLine(") {")
            appendLine()
            appendLine("    @PostConstruct")
            appendLine("    fun validateApiKey() {")
            appendLine("""        require(apiKey.isNotBlank()) { "${agent.modelProvider.name}_API_KEY environment variable must be set" }""")
            appendLine("    }")
            if (agent.mcpServers.isNotEmpty()) {
                appendLine()
                appendLine("    private val agentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())")
                appendLine()
                appendLine("    @Bean")
                appendLine("    @Primary")
                appendLine("    fun agent(): $agentType = runBlocking(agentScope.coroutineContext) {")
                appendLine("        AIAgent(")
                appendLine("            $paramBlock")
                append("        )")
                appendOtelInstallBlock(agent)
                appendLine()
                appendLine("    }")
            } else {
                appendLine("    @Bean")
                appendLine("    @Primary")
                appendLine("    fun agent(): $agentType = AIAgent(")
                appendLine("            $paramBlock")
                append("    )")
                appendOtelInstallBlock(agent)
                appendLine()
            }
            appendLine("}")
        }
    }

    private fun StringBuilder.appendOtelInstallBlock(agent: KoogAgent) {
        if (agent.otelConfig?.enabled != true) return
        appendLine(" {")
        appendLine("        install(OpenTelemetry) {")
        appendLine("            setServiceInfo(otelServiceName, otelServiceVersion)")
        appendLine("            setVerbose(otelVerbose)")
        when (agent.otelConfig?.exporterType) {
            "http" -> {
                appendLine("            addSpanExporter(")
                appendLine("                OtlpHttpSpanExporter.builder()")
                appendLine("                    .setEndpoint(otelEndpoint)")
                appendLine("                    .setTimeout(30, TimeUnit.SECONDS)")
                if (agent.otelConfig?.authorizationHeader != null) {
                    appendLine("                    .addHeader(\"Authorization\", otelAuthHeader)")
                }
                appendLine("                    .build()")
                appendLine("            )")
            }
            "grpc" -> {
                appendLine("            addSpanExporter(")
                appendLine("                OtlpGrpcSpanExporter.builder()")
                appendLine("                    .setEndpoint(otelEndpoint)")
                if (agent.otelConfig?.authorizationHeader != null) {
                    appendLine("                    .addHeader(\"Authorization\", otelAuthHeader)")
                }
                appendLine("                    .build()")
                appendLine("            )")
            }
            "logging" -> {
                appendLine("            addSpanExporter(LoggingSpanExporter.create())")
            }
        }
        appendLine("        }")
        append("    }")
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
