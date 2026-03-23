package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String {
        val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
        val params = tool.parameters.joinToString(",\n        ") { param ->
            """@LLMDescription("${param.description}") ${param.name}: ${param.type.kotlinType}"""
        }
        val paramList = if (params.isNotEmpty()) "\n        $params\n    " else ""

        return buildString {
            appendLine("package generated.tools")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.annotations.Tool")
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            appendLine()
            appendLine("""@LLMDescription("${tool.description}")""")
            appendLine("class $className : ToolSet {")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("${tool.description}")""")
            appendLine("    fun ${tool.name}($paramList): String {")
            appendLine("        // TODO: Implement tool logic")
            appendLine("        return \"Not implemented\"")
            appendLine("    }")
            appendLine("}")
        }
    }
}
