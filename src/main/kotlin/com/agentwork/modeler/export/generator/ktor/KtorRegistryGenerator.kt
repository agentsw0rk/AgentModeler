package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class KtorRegistryGenerator {
    fun generate(
        tools: List<KoogTool>,
        builtinTools: List<KoogBuiltinTool> = emptyList(),
        subAgents: List<KoogSubAgent> = emptyList(),
        mcpServers: List<KoogMcpServer> = emptyList()
    ): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        appendLine("import ai.koog.agents.core.tools.reflect.tools")
        if (tools.isNotEmpty()) appendLine("import generated.tools.*")
        if (mcpServers.isNotEmpty()) appendLine("import generated.mcp.*")
        appendLine()

        val hasMcp = mcpServers.isNotEmpty()
        val suspendPrefix = if (hasMcp) "suspend " else ""
        appendLine("${suspendPrefix}fun createToolRegistry(): ToolRegistry {")

        if (tools.isNotEmpty()) {
            val toolInstances = tools.joinToString("\n    ") { tool ->
                "val ${tool.name.replaceFirstChar { it.lowercase() }}Tools = ${tool.name}()"
            }
            appendLine("    $toolInstances")
            appendLine()
        }

        val hasBaseTools = tools.isNotEmpty()

        if (hasMcp && hasBaseTools) {
            appendLine("    val baseRegistry = ToolRegistry {")
        } else if (hasMcp && !hasBaseTools) {
            // No ToolRegistry block needed
        } else {
            appendLine("    return ToolRegistry {")
        }

        if (hasBaseTools) {
            for (tool in tools) {
                appendLine("        tools(${tool.name.replaceFirstChar { it.lowercase() }}Tools)")
            }
            appendLine("    }")
        }

        if (hasMcp) {
            val mcpVarNames = mcpServers.map { server ->
                val varName = server.name.replace(" ", "").replaceFirstChar { it.lowercase() } + "Registry"
                val funcName = "create${server.name.replace(" ", "")}Registry"
                appendLine("    val $varName = $funcName()")
                varName
            }

            val parts = if (hasBaseTools) listOf("baseRegistry") + mcpVarNames else mcpVarNames
            appendLine("    return ${parts.joinToString(" + ")}")
        }

        appendLine("}")
    }
}
