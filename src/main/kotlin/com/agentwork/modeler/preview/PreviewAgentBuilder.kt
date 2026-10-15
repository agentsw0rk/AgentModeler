package com.agentwork.modeler.preview

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLModel
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class PreviewAgentBuilder {

    suspend fun build(agent: KoogAgent): AIAgent<String, String> {
        val apiKeyEnv = "${agent.modelProvider.name}_API_KEY"
        val apiKey = System.getenv(apiKeyEnv)
            ?: throw PreviewException("Missing environment variable: $apiKeyEnv")

        val executor = when (agent.modelProvider) {
            ModelProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            ModelProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            ModelProvider.GOOGLE -> simpleGoogleAIExecutor(apiKey)
            ModelProvider.BEDROCK -> throw PreviewException("Bedrock executor not yet implemented")
        }

        val model = agent.modelId?.let { resolveModel(agent.modelProvider, it) }
            ?: resolveDefaultModel(agent.modelProvider)

        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.mcpServers.isNotEmpty()

        return if (hasTools) {
            val registry = buildToolRegistry(agent)
            AIAgent(
                promptExecutor = executor,
                llmModel = model,
                systemPrompt = agent.systemPrompt,
                toolRegistry = registry
            )
        } else {
            AIAgent(
                promptExecutor = executor,
                llmModel = model,
                systemPrompt = agent.systemPrompt
            )
        }
    }

    private suspend fun buildToolRegistry(agent: KoogAgent): ToolRegistry {
        val registries = mutableListOf<ToolRegistry>()

        for (mcp in agent.mcpServers) {
            val transport = when (mcp.transportType) {
                "sse" -> McpToolRegistryProvider.defaultSseTransport(mcp.url!!)
                "stdio" -> {
                    val cmdList = mutableListOf(mcp.command!!)
                    mcp.args?.let { cmdList.addAll(it) }
                    val pb = ProcessBuilder(cmdList)
                    mcp.envVars.forEach { envVar ->
                        System.getenv(envVar.name)?.let { pb.environment()[envVar.name] = it }
                    }
                    McpToolRegistryProvider.defaultStdioTransport(pb.start())
                }
                else -> continue
            }
            val serverInfo = when (mcp.transportType) {
                "sse" -> McpServerInfo(url = mcp.url)
                "stdio" -> McpServerInfo(command = listOfNotNull(mcp.command).plus(mcp.args ?: emptyList()).joinToString(" "))
                else -> continue
            }
            registries.add(McpToolRegistryProvider.fromTransport(transport = transport, serverInfo = serverInfo))
        }

        return if (registries.isEmpty()) {
            ToolRegistry.builder().build()
        } else {
            registries.reduce { acc, reg -> acc.plus(reg) }
        }
    }

    private fun resolveModel(provider: ModelProvider, id: String): LLModel = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4-1" -> OpenAIModels.Chat.GPT4_1
            else -> OpenAIModels.Chat.GPT4o
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> AnthropicModels.Sonnet_4
            "claude-opus-4" -> AnthropicModels.Opus_4
            "claude-haiku-4.5" -> AnthropicModels.Haiku_4_5
            else -> AnthropicModels.Sonnet_4
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2.0-flash" -> GoogleModels.Gemini2_5Flash
            "gemini-2.5-pro" -> GoogleModels.Gemini2_5Pro
            "gemini-2.5-flash" -> GoogleModels.Gemini2_5Flash
            else -> GoogleModels.Gemini2_5Flash
        }
        ModelProvider.BEDROCK -> when (id.lowercase()) {
            "claude-4.5-sonnet" -> BedrockModels.AnthropicClaude4_5Sonnet
            "claude-4-sonnet" -> BedrockModels.AnthropicClaude4Sonnet
            "claude-4-opus" -> BedrockModels.AnthropicClaude4Opus
            "nova-pro" -> BedrockModels.AmazonNovaPro
            "nova-lite" -> BedrockModels.AmazonNovaLite
            "nova-micro" -> BedrockModels.AmazonNovaMicro
            "llama-3.3-70b" -> BedrockModels.MetaLlama3_3_70BInstruct
            "llama-3.1-405b" -> BedrockModels.MetaLlama3_1_405BInstruct
            else -> BedrockModels.AnthropicClaude4Sonnet
        }
    }

    private fun resolveDefaultModel(provider: ModelProvider): LLModel = when (provider) {
        ModelProvider.OPENAI -> OpenAIModels.Chat.GPT4o
        ModelProvider.ANTHROPIC -> AnthropicModels.Sonnet_4
        ModelProvider.GOOGLE -> GoogleModels.Gemini2_5Flash
        ModelProvider.BEDROCK -> BedrockModels.AnthropicClaude4Sonnet
    }
}
