package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {
    fun generate(tools: List<KoogTool>, builtinTools: List<KoogBuiltinTool> = emptyList(), subAgents: List<KoogSubAgent> = emptyList(), mcpServers: List<KoogMcpServer> = emptyList()): String {
        val hasUserPrompt = builtinTools.any { it.type == BuiltinToolType.USER_PROMPT }
        val hasFilesystem = builtinTools.any { it.type == BuiltinToolType.FILESYSTEM }
        val customBuiltins = builtinTools.filter {
            it.type in setOf(BuiltinToolType.SHELL, BuiltinToolType.FETCH, BuiltinToolType.MEMORY, BuiltinToolType.TODO)
        }

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.agents.core.tools.reflect.tools")
            if (tools.isNotEmpty() || customBuiltins.isNotEmpty()) {
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
            if (subAgents.isNotEmpty()) {
                appendLine("import generated.subagents.*")
                appendLine("import ai.koog.agents.core.agent.AIAgentService")
                appendLine("import ai.koog.agents.core.agent.GraphAIAgent")
                appendLine("import ai.koog.agents.core.agent.createAgentTool")
                for (sa in subAgents) {
                    if (sa.inputs.isNotEmpty()) {
                        appendLine("import generated.subagents.${sa.name.replaceFirstChar { it.uppercase() }}Input")
                    }
                    if (sa.outputs.isNotEmpty()) {
                        appendLine("import generated.subagents.${sa.name.replaceFirstChar { it.uppercase() }}Output")
                    }
                }
            }
            if (mcpServers.isNotEmpty()) {
                appendLine("import generated.mcp.*")
            }
            appendLine()
            if (subAgents.isNotEmpty()) {
                appendLine("@OptIn(kotlin.time.ExperimentalTime::class)")
            }
            val suspendPrefix = if (mcpServers.isNotEmpty()) "suspend " else ""
            appendLine("${suspendPrefix}fun createToolRegistry(): ToolRegistry {")

            // Custom tool instances
            if (tools.isNotEmpty()) {
                val toolInstances = tools.joinToString("\n    ") { tool ->
                    val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                    "val ${tool.name}Tools = $className()"
                }
                appendLine("    $toolInstances")
                appendLine()
            }

            val hasBaseTools = tools.isNotEmpty() || customBuiltins.isNotEmpty() || hasUserPrompt || hasFilesystem || subAgents.isNotEmpty()
            val hasMcp = mcpServers.isNotEmpty()

            if (hasMcp && hasBaseTools) {
                appendLine("    val baseRegistry = ToolRegistry {")
            } else if (hasMcp && !hasBaseTools) {
                // No ToolRegistry block needed
            } else {
                appendLine("    return ToolRegistry {")
            }

            if (hasBaseTools) {
                // Custom tools
                for (tool in tools) {
                    appendLine("        tools(${tool.name}Tools)")
                }

                // Built-in custom ToolSets
                for (bt in customBuiltins) {
                    val registration = when (bt.type) {
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
                    registration?.let { appendLine("        $it") }
                }

                // Koog framework built-ins
                if (hasUserPrompt) {
                    appendLine("        tool(AskUser)")
                    appendLine("        tool(SayToUser)")
                }
                if (hasFilesystem) {
                    appendLine("        tool(ReadFileTool(JVMFileSystemProvider.ReadWrite))")
                    appendLine("        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))")
                    appendLine("        tool(ListDirectoryTool(JVMFileSystemProvider.ReadWrite))")
                }

                // Sub-agents as tools
                for (sa in subAgents) {
                    val funcName = "create${sa.name.replaceFirstChar { it.uppercase() }}"
                    val desc = sa.description.replace("\"", "\\\"")
                    val inputDesc = sa.inputDescription.replace("\"", "\\\"")
                    val hasInputs = sa.inputs.isNotEmpty()
                    val hasOutputs = sa.outputs.isNotEmpty()
                    val i = if (hasInputs) "${sa.name.replaceFirstChar { it.uppercase() }}Input" else "String"
                    val o = if (hasOutputs) "${sa.name.replaceFirstChar { it.uppercase() }}Output" else "String"
                    appendLine("        tool(")
                    appendLine("            AIAgentService.fromAgent($funcName() as GraphAIAgent<$i, $o>).createAgentTool<$i, $o>(")
                    appendLine("                agentName = \"${sa.name}\",")
                    appendLine("                agentDescription = \"$desc\",")
                    appendLine("                inputDescription = \"$inputDesc\"")
                    appendLine("            )")
                    appendLine("        )")
                }

                appendLine("    }")
            }

            if (hasMcp) {
                // Generate MCP registry variables
                val mcpVarNames = mcpServers.map { server ->
                    val varName = server.name.replace(" ", "").replaceFirstChar { it.lowercase() } + "Registry"
                    val funcName = "create${server.name.replace(" ", "")}Registry"
                    appendLine("    val $varName = $funcName()")
                    varName
                }

                // Build the return expression
                val parts = if (hasBaseTools) listOf("baseRegistry") + mcpVarNames else mcpVarNames
                appendLine("    return ${parts.joinToString(" + ")}")
            }

            appendLine("}")
        }
    }
}
