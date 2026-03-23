package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {
    fun generate(tools: List<KoogTool>, builtinTools: List<KoogBuiltinTool> = emptyList(), subAgents: List<KoogSubAgent> = emptyList()): String {
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
            }
            appendLine()
            appendLine("fun createToolRegistry(): ToolRegistry {")

            // Custom tool instances
            if (tools.isNotEmpty()) {
                val toolInstances = tools.joinToString("\n    ") { tool ->
                    val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                    "val ${tool.name}Tools = $className()"
                }
                appendLine("    $toolInstances")
                appendLine()
            }

            appendLine("    return ToolRegistry {")

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
                appendLine("        tool(")
                appendLine("            AIAgentService.fromAgent($funcName() as GraphAIAgent<String, String>).createAgentTool<String, String>(")
                appendLine("                agentName = \"${sa.name}\",")
                appendLine("                agentDescription = \"$desc\",")
                appendLine("                inputDescription = \"$inputDesc\"")
                appendLine("            )")
                appendLine("        )")
            }

            appendLine("    }")
            appendLine("}")
        }
    }
}
