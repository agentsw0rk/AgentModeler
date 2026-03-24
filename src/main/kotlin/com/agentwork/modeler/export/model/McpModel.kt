package com.agentwork.modeler.export.model

data class KoogMcpServer(
    val name: String,
    val transportType: String,
    val url: String?,
    val command: String?,
    val args: List<String>?,
    val envVars: List<McpEnvVarName>
)

data class McpEnvVarName(
    val name: String
)
