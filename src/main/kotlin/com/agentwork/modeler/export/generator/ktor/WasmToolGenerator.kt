package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import org.springframework.stereotype.Component

/**
 * Generates Wasm-compatible tool files using SimpleTool + @Serializable args classes.
 * The JVM ToolGenerator uses ToolSet + @Tool annotations (reflection-based),
 * which is not available on WasmJS. This generator produces explicit Tool instances instead.
 */
@Component
class WasmToolGenerator {

    fun generate(tool: KoogTool): String = buildString {
        appendLine("package generated.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.SimpleTool")
        appendLine("import kotlinx.serialization.Serializable")
        appendLine("import kotlinx.serialization.SerialName")
        appendLine("import generated.*")
        appendLine()

        // Generate Args data class + SimpleTool instance for each function
        for (fn in tool.functions) {
            generateArgsClass(fn)
            appendLine()
            generateToolInstance(fn)
            appendLine()
        }

        // List of all tools for registry
        appendLine("val ${tool.name.replaceFirstChar { it.lowercase() }}AllTools = listOf(")
        for ((i, fn) in tool.functions.withIndex()) {
            val comma = if (i < tool.functions.size - 1) "," else ""
            appendLine("    ${fn.name}Tool$comma")
        }
        appendLine(")")
    }

    private fun StringBuilder.generateArgsClass(fn: KoogToolFunction) {
        val className = "${fn.name.replaceFirstChar { it.uppercase() }}Args"
        if (fn.parameters.isEmpty()) {
            appendLine("@Serializable")
            appendLine("class $className")
            return
        }
        appendLine("@Serializable")
        appendLine("data class $className(")
        for ((i, param) in fn.parameters.withIndex()) {
            val comma = if (i < fn.parameters.size - 1) "," else ""
            val default = if (!param.required) " = ${defaultLiteral(param.type, param.defaultValue)}" else ""
            appendLine("    @SerialName(\"${param.name}\") val ${param.name}: ${param.type.kotlinType}$default$comma")
        }
        appendLine(")")
    }

    private fun StringBuilder.generateToolInstance(fn: KoogToolFunction) {
        val argsClass = "${fn.name.replaceFirstChar { it.uppercase() }}Args"
        val escapedDesc = fn.description.replace("\"", "\\\"")

        // Generate a private helper function with the original body (returns String)
        val helperName = "${fn.name}Impl"
        appendLine("private fun $helperName(${fn.parameters.joinToString(", ") { "${it.name}: ${it.type.kotlinType}" }}): String {")
        val body = sanitizeBodyForWasm(fn.body)
        if (body.isBlank()) {
            appendLine("    return \"Not implemented\"")
        } else {
            for (line in body.lines()) {
                if (line.isBlank()) appendLine() else appendLine("    $line")
            }
        }
        appendLine("}")
        appendLine()

        // Generate the SimpleTool that delegates to the helper
        appendLine("val ${fn.name}Tool = object : SimpleTool<$argsClass>(")
        appendLine("    argsSerializer = $argsClass.serializer(),")
        appendLine("    name = \"${fn.name}\",")
        appendLine("    description = \"$escapedDesc\"")
        appendLine(") {")
        appendLine("    override suspend fun execute(args: $argsClass): String {")
        val callArgs = fn.parameters.joinToString(", ") { "args.${it.name}" }
        appendLine("        return $helperName($callArgs)")
        appendLine("    }")
        appendLine("}")
    }

    private fun sanitizeBodyForWasm(body: String): String {
        var result = body
        // String.format() is not available on WasmJS
        // Replace any `.format(` with `.wasmFormat(` — a polyfill extension function
        if (result.contains(".format(")) {
            result = result.replace(".format(", ".wasmFormat(")
        }
        return result
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
