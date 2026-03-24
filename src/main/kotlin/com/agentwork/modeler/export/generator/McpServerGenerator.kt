package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogMcpServer
import org.springframework.stereotype.Component

@Component
class McpServerGenerator {

    fun generate(mcp: KoogMcpServer): String {
        val funcName = "create${mcp.name.replace(" ", "").replaceFirstChar { it.uppercase() }}Registry"
        val safeName = mcp.name.lowercase().replace(" ", "-")

        return buildString {
            appendLine("package generated.mcp")
            appendLine()
            appendLine("import ai.koog.agents.mcp.McpToolRegistryProvider")
            appendLine("import ai.koog.agents.mcp.metadata.McpServerInfo")
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine()
            if (mcp.envVars.isNotEmpty()) {
                val envNames = mcp.envVars.joinToString(", ") { it.name }
                appendLine("// Required environment variables: $envNames")
            }
            appendLine("suspend fun $funcName(): ToolRegistry {")

            when (mcp.transportType) {
                "sse" -> {
                    appendLine("    val transport = McpToolRegistryProvider.defaultSseTransport(\"${mcp.url}\")")
                    appendLine("    return McpToolRegistryProvider.fromTransport(")
                    appendLine("        transport = transport,")
                    appendLine("        serverInfo = McpServerInfo(url = \"${mcp.url}\"),")
                    appendLine("        name = \"$safeName\",")
                    appendLine("        version = \"1.0.0\"")
                    appendLine("    )")
                }
                "stdio" -> {
                    val allArgs = mutableListOf("\"${mcp.command}\"")
                    mcp.args?.forEach { allArgs.add("\"$it\"") }
                    val pbArgs = allArgs.joinToString(", ")
                    appendLine("    val process = ProcessBuilder($pbArgs)")
                    if (mcp.envVars.isNotEmpty()) {
                        appendLine("        .apply {")
                        for (env in mcp.envVars) {
                            appendLine("            environment()[\"${env.name}\"] = System.getenv(\"${env.name}\")")
                        }
                        appendLine("        }")
                    }
                    appendLine("        .start()")
                    val cmdStr = listOfNotNull(mcp.command).plus(mcp.args ?: emptyList()).joinToString(" ")
                    appendLine("    val transport = McpToolRegistryProvider.defaultStdioTransport(process)")
                    appendLine("    return McpToolRegistryProvider.fromTransport(")
                    appendLine("        transport = transport,")
                    appendLine("        serverInfo = McpServerInfo(command = \"$cmdStr\"),")
                    appendLine("        name = \"$safeName\",")
                    appendLine("        version = \"1.0.0\"")
                    appendLine("    )")
                }
            }

            appendLine("}")
        }
    }
}
