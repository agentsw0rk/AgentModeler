package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class GraphToKoogAdapter {

    fun convert(graph: ProjectGraph): List<KoogAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val agentNodes = graph.nodes.filter { it.type == "agent" }

        return agentNodes.map { agentNode ->
            val name = agentNode.data["name"] as? String ?: ""
            require(name.isNotBlank()) { "Agent requires a name" }

            val connectedToolIds = graph.edges
                .filter { it.target == agentNode.id }
                .map { it.source }

            val tools = toolNodes
                .filter { it.id in connectedToolIds }
                .map { convertTool(it) }

            KoogAgent(
                name = name,
                systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
                modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
                modelId = agentNode.data["modelId"] as? String,
                temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
                tools = tools
            )
        }
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
