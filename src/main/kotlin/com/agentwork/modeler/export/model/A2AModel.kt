package com.agentwork.modeler.export.model

data class KoogA2AConfig(
    val enabled: Boolean,
    val agentCard: KoogAgentCard,
    val server: KoogA2AServerConfig
)

data class KoogAgentCard(
    val name: String,
    val description: String,
    val url: String,
    val version: String,
    val capabilities: KoogA2ACapabilities,
    val skills: List<KoogA2ASkill>,
    val defaultInputModes: List<String>,
    val defaultOutputModes: List<String>
)

data class KoogA2ACapabilities(
    val streaming: Boolean,
    val pushNotifications: Boolean,
    val stateTransitionHistory: Boolean
)

data class KoogA2ASkill(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>
)

data class KoogA2AServerConfig(
    val transportType: String,
    val port: Int,
    val basePath: String
)

data class KoogA2AAgent(
    val name: String,
    val description: String,
    val url: String,
    val authType: String,
    val authToken: String?,
    val timeout: Int,
    val communicationMode: String
)
