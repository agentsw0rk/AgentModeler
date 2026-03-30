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
        if (tools.isNotEmpty()) appendLine("import generated.tools.*")
        appendLine()

        appendLine("fun createToolRegistry(): ToolRegistry {")
        appendLine("    return ToolRegistry {")
        // WasmJS: tools are generated as lists of SimpleTool instances (not ToolSet)
        for (tool in tools) {
            val listName = "${tool.name.replaceFirstChar { it.lowercase() }}AllTools"
            appendLine("        for (t in $listName) tool(t)")
        }
        appendLine("    }")
        appendLine("}")
    }
}
