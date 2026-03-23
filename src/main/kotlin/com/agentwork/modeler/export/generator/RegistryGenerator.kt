package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {
    fun generate(tools: List<KoogTool>): String {
        val toolInstances = tools.joinToString("\n    ") { tool ->
            val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
            "val ${tool.name}Tools = $className()"
        }
        val toolRegistrations = tools.joinToString("\n        ") { tool ->
            "tools(${tool.name}Tools)"
        }
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.agents.core.tools.reflect.tools")
            appendLine("import generated.tools.*")
            appendLine()
            appendLine("fun createToolRegistry(): ToolRegistry {")
            appendLine("    $toolInstances")
            appendLine()
            appendLine("    return ToolRegistry {")
            appendLine("        $toolRegistrations")
            appendLine("    }")
            appendLine("}")
        }
    }
}
