package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.springframework.stereotype.Component

@Component
class InputOutputGenerator {

    private fun kotlinType(dataType: String): String = when (dataType) {
        "number" -> "Double"
        "boolean" -> "Boolean"
        "object" -> "Map<String, Any>"
        "array" -> "List<Any>"
        else -> "String"
    }

    private fun defaultLiteral(dataType: String, defaultValue: String): String = when (dataType) {
        "number" -> defaultValue.toDoubleOrNull()?.let { "$it" } ?: "0.0"
        "boolean" -> if (defaultValue.lowercase() == "true") "true" else "false"
        else -> "\"$defaultValue\""
    }

    fun generateInputClass(agentName: String, inputs: List<KoogInput>): String? {
        if (inputs.isEmpty()) return null
        val className = "${agentName.replaceFirstChar { it.uppercase() }}Input"
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("data class $className(")
            val required = inputs.filter { it.required }
            val optional = inputs.filter { !it.required }
            val all = required + optional
            for ((i, input) in all.withIndex()) {
                val kt = kotlinType(input.dataType)
                val comma = if (i < all.size - 1) "," else ""
                if (input.required) {
                    appendLine("    val ${input.name}: $kt$comma")
                } else {
                    val default = if (input.defaultValue != null) {
                        " = ${defaultLiteral(input.dataType, input.defaultValue)}"
                    } else {
                        " = null"
                    }
                    val type = if (input.defaultValue == null) "$kt?" else kt
                    appendLine("    val ${input.name}: $type$default$comma")
                }
            }
            appendLine(")")
        }
    }

    fun generateOutputClass(agentName: String, outputs: List<KoogOutput>): String? {
        if (outputs.isEmpty()) return null
        val className = "${agentName.replaceFirstChar { it.uppercase() }}Output"
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("data class $className(")
            for ((i, output) in outputs.withIndex()) {
                val kt = kotlinType(output.dataType)
                val comma = if (i < outputs.size - 1) "," else ""
                appendLine("    val ${output.name}: $kt$comma")
            }
            appendLine(")")
        }
    }
}
