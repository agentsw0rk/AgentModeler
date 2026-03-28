package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogField
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.springframework.stereotype.Component

@Component
class InputOutputGenerator {

    private fun kotlinType(fieldType: String): String = when (fieldType) {
        "Int" -> "Int"
        "Double" -> "Double"
        "Boolean" -> "Boolean"
        "List" -> "List<Any>"
        "Map" -> "Map<String, Any>"
        else -> "String"
    }

    private fun generateFields(fields: List<KoogField>): String {
        val required = fields.filter { !it.optional }
        val optional = fields.filter { it.optional }
        val all = required + optional

        return buildString {
            for ((i, field) in all.withIndex()) {
                val kt = kotlinType(field.type)
                val comma = if (i < all.size - 1) "," else ""
                if (field.description.isNotBlank()) {
                    appendLine("    @property:LLMDescription(\"${field.description}\")")
                }
                if (field.optional) {
                    appendLine("    val ${field.name}: $kt? = null$comma")
                } else {
                    appendLine("    val ${field.name}: $kt$comma")
                }
            }
        }
    }

    private fun needsLLMDescriptionImport(fields: List<KoogField>): Boolean =
        fields.any { it.description.isNotBlank() }

    fun generateInputClass(agentName: String, input: KoogInput): String? {
        if (input.fields.isEmpty()) return null
        val className = input.className?.ifBlank { null }
            ?: "${agentName.replaceFirstChar { it.uppercase() }}Input"
        return buildClassFile(className, input.fields)
    }

    fun generateOutputClass(agentName: String, output: KoogOutput): String? {
        if (output.fields.isEmpty()) return null
        val className = output.className?.ifBlank { null }
            ?: "${agentName.replaceFirstChar { it.uppercase() }}Output"
        return buildClassFile(className, output.fields)
    }

    // TODO: Remove these List overloads after ExportService is updated (Task 9)
    @Deprecated("Use single KoogInput overload", replaceWith = ReplaceWith("generateInputClass(agentName, inputs.first())"))
    fun generateInputClass(agentName: String, inputs: List<KoogInput>): String? =
        inputs.firstOrNull()?.let { generateInputClass(agentName, it) }

    @Deprecated("Use single KoogOutput overload", replaceWith = ReplaceWith("generateOutputClass(agentName, outputs.first())"))
    fun generateOutputClass(agentName: String, outputs: List<KoogOutput>): String? =
        outputs.firstOrNull()?.let { generateOutputClass(agentName, it) }

    private fun buildClassFile(className: String, fields: List<KoogField>): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import kotlinx.serialization.Serializable")
        if (needsLLMDescriptionImport(fields)) {
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
        }
        appendLine()
        appendLine("@Serializable")
        appendLine("data class $className(")
        append(generateFields(fields))
        appendLine(")")
    }
}
