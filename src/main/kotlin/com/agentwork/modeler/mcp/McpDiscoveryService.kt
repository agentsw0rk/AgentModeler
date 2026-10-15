package com.agentwork.modeler.mcp

import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.metadata.McpServerInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Service

@Service
class McpDiscoveryService {

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 10_000L
    }

    fun discover(request: McpDiscoveryRequest): McpDiscoveryResponse {
        return try {
            when (request.transportType) {
                "sse" -> discoverSse(request)
                "stdio" -> discoverStdio(request)
                else -> McpDiscoveryResponse(error = "Unsupported transport type: ${request.transportType}")
            }
        } catch (e: Exception) {
            McpDiscoveryResponse(error = "Discovery failed: ${e.message}")
        }
    }

    private fun discoverSse(request: McpDiscoveryRequest): McpDiscoveryResponse {
        val url = request.url ?: return McpDiscoveryResponse(error = "URL is required for SSE transport")

        return runBlocking {
            withTimeout(DISCOVERY_TIMEOUT_MS) {
                val transport = McpToolRegistryProvider.defaultSseTransport(url)
                val toolRegistry = McpToolRegistryProvider.fromTransport(
                    transport = transport,
                    serverInfo = McpServerInfo(url = url)
                )
                extractTools(toolRegistry)
            }
        }
    }

    private fun discoverStdio(request: McpDiscoveryRequest): McpDiscoveryResponse {
        val command = request.command ?: return McpDiscoveryResponse(error = "Command is required for STDIO transport")

        val cmdList = mutableListOf(command)
        request.args?.let { cmdList.addAll(it) }

        val processBuilder = ProcessBuilder(cmdList)
        request.envVars?.forEach { envVar ->
            if (envVar.value != null) {
                processBuilder.environment()[envVar.name] = envVar.value
            }
        }

        val process = processBuilder.start()

        return try {
            runBlocking {
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                    val toolRegistry = McpToolRegistryProvider.fromTransport(
                        transport = transport,
                        serverInfo = McpServerInfo(command = cmdList.joinToString(" "))
                    )
                    extractTools(toolRegistry)
                }
            }
        } finally {
            process.destroy()
        }
    }

    private fun extractTools(toolRegistry: ToolRegistry): McpDiscoveryResponse {
        val tools = toolRegistry.tools.map { tool ->
            McpToolDto(
                name = tool.name,
                description = tool.descriptor.toString()
            )
        }
        return McpDiscoveryResponse(tools = tools)
    }
}
