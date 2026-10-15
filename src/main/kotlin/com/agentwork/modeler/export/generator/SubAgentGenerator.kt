package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class SubAgentGenerator {

    fun generate(subAgent: KoogSubAgent): String {
        val modelImport = subAgent.modelProvider.importPackage
        val modelRef = subAgent.modelId?.let { mapModelId(subAgent.modelProvider, it) }
            ?: subAgent.modelProvider.defaultModel
        val funcName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}"
        val hasInputs = subAgent.inputs.isNotEmpty()
        val hasOutputs = subAgent.outputs.isNotEmpty()
        val inputType = if (hasInputs) "${subAgent.name.replaceFirstChar { it.uppercase() }}Input" else null
        val outputType = if (hasOutputs) "${subAgent.name.replaceFirstChar { it.uppercase() }}Output" else null
        val hasTools = subAgent.tools.isNotEmpty() || subAgent.builtinTools.isNotEmpty()
        val registryFuncName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"

        val params = mutableListOf(
            """promptExecutor = ${subAgent.modelProvider.executorFactory}(System.getenv("${subAgent.modelProvider.name}_API_KEY"))""",
            """systemPrompt = "${subAgent.systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")}"""",
            "llmModel = $modelRef"
        )
        subAgent.temperature?.let { params.add("temperature = $it") }
        if (hasTools) {
            params.add("toolRegistry = $registryFuncName()")
        }

        val paramBlock = params.joinToString(",\n        ")

        return buildString {
            appendLine("package generated.subagents")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            if (hasTools) {
                appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            }
            appendLine("import ai.koog.prompt.executor.llms.all.${subAgent.modelProvider.executorFactory}")
            appendLine("import $modelImport")
            if (inputType != null) {
                appendLine("import generated.subagents.$inputType")
            }
            if (outputType != null) {
                appendLine("import generated.subagents.$outputType")
            }
            appendLine()
            if (inputType != null || outputType != null) {
                val i = inputType ?: "String"
                val o = outputType ?: "String"
                appendLine("fun $funcName(): AIAgent<$i, $o> = AIAgent(")
            } else {
                appendLine("fun $funcName() = AIAgent(")
            }
            appendLine("        $paramBlock")
            appendLine(")")
        }
    }

    fun generateRegistry(subAgent: KoogSubAgent): String? {
        if (subAgent.tools.isEmpty() && subAgent.builtinTools.isEmpty()) return null

        val funcName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"
        val hasUserPrompt = subAgent.builtinTools.any { it.type == BuiltinToolType.USER_PROMPT }
        val hasFilesystem = subAgent.builtinTools.any { it.type == BuiltinToolType.FILESYSTEM }
        val customBuiltins = subAgent.builtinTools.filter {
            it.type in setOf(BuiltinToolType.SHELL, BuiltinToolType.FETCH, BuiltinToolType.MEMORY, BuiltinToolType.TODO)
        }

        return buildString {
            appendLine("package generated.subagents")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.agents.core.tools.reflect.tools")
            if (subAgent.tools.isNotEmpty() || customBuiltins.isNotEmpty()) {
                appendLine("import generated.tools.*")
            }
            if (hasUserPrompt) {
                appendLine("import ai.koog.agents.ext.tool.AskUser")
                appendLine("import ai.koog.agents.ext.tool.SayToUser")
            }
            if (hasFilesystem) {
                appendLine("import ai.koog.agents.ext.tool.file.ReadFileTool")
                appendLine("import ai.koog.agents.ext.tool.file.WriteFileTool")
                appendLine("import ai.koog.agents.ext.tool.file.ListDirectoryTool")
                appendLine("import ai.koog.rag.base.files.JVMFileSystemProvider")
            }
            appendLine()
            appendLine("fun $funcName(): ToolRegistry {")
            appendLine("    return ToolRegistry {")

            for (tool in subAgent.tools) {
                appendLine("        tools(${tool.name}())")
            }
            for (bt in customBuiltins) {
                val reg = when (bt.type) {
                    BuiltinToolType.SHELL -> {
                        val wd = bt.config["workingDir"] as? String ?: "."
                        """tools(ShellToolSet(workingDir = "$wd"))"""
                    }
                    BuiltinToolType.FETCH -> {
                        val bu = bt.config["baseUrl"] as? String ?: ""
                        """tools(FetchToolSet(baseUrl = "$bu"))"""
                    }
                    BuiltinToolType.MEMORY -> {
                        val p = bt.config["path"] as? String ?: "memory.db"
                        """tools(MemoryToolSet(path = "$p"))"""
                    }
                    BuiltinToolType.TODO -> "tools(TodoToolSet())"
                    else -> null
                }
                reg?.let { appendLine("        $it") }
            }
            if (hasUserPrompt) {
                appendLine("        tool(AskUser)")
                appendLine("        tool(SayToUser)")
            }
            if (hasFilesystem) {
                appendLine("        tool(ReadFileTool(JVMFileSystemProvider.ReadWrite))")
                appendLine("        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))")
                appendLine("        tool(ListDirectoryTool(JVMFileSystemProvider.ReadWrite))")
            }

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
