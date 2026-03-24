package com.agentwork.modeler.mcp

data class McpDiscoveryRequest(
    val transportType: String,
    val url: String?,
    val command: String?,
    val args: List<String>?,
    val envVars: List<McpEnvVarDto>?
)

data class McpEnvVarDto(
    val name: String,
    val value: String?
)

data class McpDiscoveryResponse(
    val tools: List<McpToolDto>? = null,
    val error: String? = null
)

data class McpToolDto(
    val name: String,
    val description: String
)
