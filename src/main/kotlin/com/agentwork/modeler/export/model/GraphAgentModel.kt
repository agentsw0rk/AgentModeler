package com.agentwork.modeler.export.model

data class KoogGraphAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val strategyNodes: List<KoogStrategyNode>,
    val strategyEdges: List<KoogStrategyEdge>,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>,
    val subAgents: List<KoogSubAgent>,
    val mcpServers: List<KoogMcpServer>,
    val inputs: List<KoogInput>,
    val outputs: List<KoogOutput>
)

data class KoogStrategyNode(
    val id: String,
    val type: String,
    val label: String?,
    val toolName: String?,
    val condition: String?,
    val temperature: Double?
)

data class KoogStrategyEdge(
    val source: String,
    val target: String,
    val edgeType: String,
    val condition: String?,
    val transformation: String?
)
