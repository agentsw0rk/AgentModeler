package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.McpEnvVarName
import org.springframework.stereotype.Component

@Component
class GraphToKoogAdapter {

    fun convert(graph: ProjectGraph): List<KoogAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
        val agentNodes = graph.nodes.filter { it.type == "agent" }
        val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
        val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }

        return agentNodes.map { agentNode ->
            val name = agentNode.data["name"] as? String ?: ""
            require(name.isNotBlank()) { "Agent requires a name" }

            val connectedToAgent = graph.edges
                .filter { it.target == agentNode.id }
                .map { it.source }

            // Sub-agent IDs connected to this agent
            val subAgentIds = subAgentNodes
                .filter { it.id in connectedToAgent }
                .map { it.id }
                .toSet()

            // Tool/builtin IDs connected to sub-agents (not main agent)
            val connectedToSubAgents = graph.edges
                .filter { it.target in subAgentIds }
                .map { it.source }
                .toSet()

            // Main agent tools: connected to agent, NOT also connected to a sub-agent
            val tools = toolNodes
                .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
                .map { convertTool(it) }

            val builtinTools = builtinToolNodes
                .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
                .map { convertBuiltinTool(it) }

            // Sub-agents with their own tools
            val subAgents = subAgentNodes
                .filter { it.id in connectedToAgent }
                .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes) }

            val mcpServers = mcpServerNodes
                .filter { it.id in connectedToAgent }
                .map { convertMcpServer(it) }

            KoogAgent(
                name = name,
                systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
                modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
                modelId = agentNode.data["modelId"] as? String,
                temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
                tools = tools,
                builtinTools = builtinTools,
                subAgents = subAgents,
                mcpServers = mcpServers
            )
        }
    }

    private fun convertSubAgent(
        saNode: GraphNode,
        graph: ProjectGraph,
        toolNodes: List<GraphNode>,
        builtinToolNodes: List<GraphNode>
    ): KoogSubAgent {
        val connectedIds = graph.edges
            .filter { it.target == saNode.id }
            .map { it.source }

        val tools = toolNodes
            .filter { it.id in connectedIds }
            .map { convertTool(it) }

        val builtinTools = builtinToolNodes
            .filter { it.id in connectedIds }
            .map { convertBuiltinTool(it) }

        return KoogSubAgent(
            name = saNode.data["name"] as? String ?: "",
            description = saNode.data["description"] as? String ?: "",
            systemPrompt = saNode.data["systemPrompt"] as? String ?: "",
            modelProvider = parseProvider(saNode.data["modelProvider"] as? String ?: "openai"),
            modelId = saNode.data["modelId"] as? String,
            temperature = (saNode.data["temperature"] as? Number)?.toDouble(),
            delegationMode = saNode.data["delegationMode"] as? String ?: "automatic",
            inputDescription = saNode.data["inputDescription"] as? String ?: "",
            tools = tools,
            builtinTools = builtinTools
        )
    }

    private fun convertBuiltinTool(node: GraphNode): KoogBuiltinTool {
        val typeStr = node.data["toolType"] as? String ?: ""
        val type = BuiltinToolType.valueOf(typeStr.uppercase())
        val config = node.data.filterKeys { it != "toolType" }
        return KoogBuiltinTool(type = type, config = config)
    }

    private fun convertTool(node: GraphNode): KoogTool {
        val params = (node.data["parameters"] as? List<*>)?.map { raw ->
            val map = raw as Map<*, *>
            KoogToolParameter(
                name = map["name"] as? String ?: "",
                type = parseParamType(map["type"] as? String ?: "String"),
                description = map["description"] as? String ?: "",
                required = map["required"] as? Boolean ?: true
            )
        } ?: emptyList()

        return KoogTool(
            name = node.data["name"] as? String ?: "",
            description = node.data["description"] as? String ?: "",
            parameters = params,
            handlerStubName = node.data["handlerStubName"] as? String
        )
    }

    private fun convertMcpServer(node: GraphNode): KoogMcpServer {
        val envVars = (node.data["envVars"] as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            McpEnvVarName(name = name)
        } ?: emptyList()

        val args = (node.data["args"] as? List<*>)?.mapNotNull { it as? String }

        return KoogMcpServer(
            name = node.data["name"] as? String ?: "",
            transportType = node.data["transportType"] as? String ?: "sse",
            url = node.data["url"] as? String,
            command = node.data["command"] as? String,
            args = args,
            envVars = envVars
        )
    }

    private fun parseProvider(value: String): ModelProvider = when (value.lowercase()) {
        "openai" -> ModelProvider.OPENAI
        "anthropic" -> ModelProvider.ANTHROPIC
        "google" -> ModelProvider.GOOGLE
        else -> ModelProvider.OPENAI
    }

    private fun parseParamType(value: String): KoogParamType = when (value) {
        "Int" -> KoogParamType.INT
        "Float" -> KoogParamType.FLOAT
        "Boolean" -> KoogParamType.BOOLEAN
        else -> KoogParamType.STRING
    }
}
