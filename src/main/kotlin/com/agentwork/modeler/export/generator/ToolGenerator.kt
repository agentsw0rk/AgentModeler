package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String = buildString {
        appendLine("package generated.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.annotations.Tool")
        appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
        appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
        appendLine("import generated.*")
        appendLine()
        if (tool.description.isNotBlank()) {
            appendLine("""@LLMDescription("${tool.description}")""")
        }
        appendLine("class ${tool.name} : ToolSet {")
        for (fn in tool.functions) {
            appendLine()
            appendLine("    @Tool")
            if (fn.description.isNotBlank()) {
                appendLine("""    @LLMDescription("${fn.description}")""")
            }
            val paramStr = generateParams(fn.parameters)
            val returnSuffix = if (fn.returnType == "Unit") "" else ": String"
            if (paramStr.isEmpty()) {
                appendLine("    fun ${fn.name}()$returnSuffix {")
            } else {
                appendLine("    fun ${fn.name}(")
                append(paramStr)
                appendLine("    )$returnSuffix {")
            }
            if (fn.body.isBlank()) {
                appendLine("        // TODO: Implement tool logic")
                if (fn.returnType != "Unit") {
                    appendLine("        return \"Not implemented\"")
                }
            } else {
                for (line in fn.body.lines()) {
                    if (line.isBlank()) appendLine() else appendLine("        $line")
                }
            }
            appendLine("    }")
        }
        appendLine("}")
    }

    private fun generateParams(params: List<KoogToolParameter>): String {
        if (params.isEmpty()) return ""
        val required = params.filter { it.required }
        val optional = params.filter { !it.required }
        val all = required + optional

        return buildString {
            for ((i, param) in all.withIndex()) {
                val comma = if (i < all.size - 1) "," else ""
                val desc = if (param.description.isNotBlank()) {
                    """@LLMDescription("${param.description}") """
                } else ""
                val default = if (!param.required) " = ${defaultLiteral(param.type, param.defaultValue)}" else ""
                appendLine("        $desc${param.name}: ${param.type.kotlinType}$default$comma")
            }
        }
    }

    private fun defaultLiteral(type: KoogParamType, value: String?): String {
        if (value == null || value.isBlank()) {
            return when (type) {
                KoogParamType.STRING -> "\"\""
                KoogParamType.INT -> "0"
                KoogParamType.DOUBLE -> "0.0"
                KoogParamType.FLOAT -> "0.0f"
                KoogParamType.BOOLEAN -> "false"
                KoogParamType.LIST_STRING -> "emptyList()"
                KoogParamType.MAP_STRING_STRING -> "emptyMap()"
            }
        }
        return when (type) {
            KoogParamType.STRING -> "\"$value\""
            KoogParamType.INT -> value.toIntOrNull()?.toString() ?: "0"
            KoogParamType.DOUBLE -> value.toDoubleOrNull()?.toString() ?: "0.0"
            KoogParamType.FLOAT -> value.toFloatOrNull()?.let { "${it}f" } ?: "0.0f"
            KoogParamType.BOOLEAN -> if (value.lowercase() == "true") "true" else "false"
            KoogParamType.LIST_STRING -> "emptyList()"
            KoogParamType.MAP_STRING_STRING -> "emptyMap()"
        }
    }
}
