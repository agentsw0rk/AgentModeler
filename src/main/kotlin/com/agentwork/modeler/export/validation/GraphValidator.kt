package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.stereotype.Component

@Component
class GraphValidator {
    fun validate(graph: WorkspaceGraph): List<String> {
        val errors = mutableListOf<String>()
        val agentCount = graph.nodes.count { it.type == "agent" }
        val plannerCount = graph.nodes.count { it.type == "planner-agent" }
        if (agentCount == 0 && plannerCount == 0) {
            errors.add("Workspace must have at least one agent or planner-agent")
        }
        if (agentCount > 0 && plannerCount > 0) {
            errors.add("Workspace cannot have both a regular agent and a planner-agent")
        }
        if (agentCount > 1) {
            errors.add("Workspace can have at most one agent")
        }
        if (plannerCount > 1) {
            errors.add("Workspace can have at most one planner-agent")
        }

        for (node in graph.nodes) {
            when (node.type) {
                "agent" -> {
                    val name = node.data["name"] as? String ?: ""
                    val prompt = node.data["systemPrompt"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Agent requires a name")
                    if (prompt.isBlank()) errors.add("[${node.id}] Agent requires a system prompt")
                }
                "tool" -> {
                    val name = node.data["name"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Tool requires a name")
                    val isConnected = graph.edges.any { it.source == node.id }
                    if (!isConnected) errors.add("[${node.id}] Tool must be connected to an Agent")
                    val connectedAgentCount = graph.edges
                        .filter { it.source == node.id }
                        .map { edge -> graph.nodes.find { it.id == edge.target } }
                        .count { it?.type == "agent" || it?.type == "sub-agent" }
                    if (connectedAgentCount > 1) {
                        errors.add("[${node.id}] Tool can only be connected to one Agent or Sub-Agent")
                    }
                    val params = node.data["parameters"] as? List<*> ?: emptyList<Any>()
                    for (param in params) {
                        val map = param as? Map<*, *> ?: continue
                        val paramName = map["name"] as? String ?: ""
                        if (paramName.isBlank()) errors.add("[${node.id}] Parameter requires a name")
                    }
                }
                "builtin-tool" -> {
                    val toolType = node.data["toolType"] as? String ?: ""
                    val validTypes = setOf("think", "shell", "filesystem", "fetch", "memory", "todo", "user_prompt")
                    if (toolType.isBlank()) {
                        errors.add("[${node.id}] Built-in tool requires a toolType")
                    } else if (toolType !in validTypes) {
                        errors.add("[${node.id}] Invalid toolType: $toolType")
                    }
                    val isConnected = graph.edges.any { it.source == node.id }
                    if (!isConnected) errors.add("[${node.id}] Built-in tool must be connected to an Agent")
                    val connectedAgentCount = graph.edges
                        .filter { it.source == node.id }
                        .map { edge -> graph.nodes.find { it.id == edge.target } }
                        .count { it?.type == "agent" || it?.type == "sub-agent" }
                    if (connectedAgentCount > 1) {
                        errors.add("[${node.id}] Tool can only be connected to one Agent or Sub-Agent")
                    }
                }
                "mcp-server" -> {
                    val name = node.data["name"] as? String ?: ""
                    val transportType = node.data["transportType"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] MCP Server requires a name")

                    val validTransports = setOf("sse", "stdio")
                    if (transportType !in validTransports) {
                        errors.add("[${node.id}] MCP Server has invalid transport type")
                    } else if (transportType == "sse") {
                        val url = node.data["url"] as? String ?: ""
                        if (url.isBlank()) errors.add("[${node.id}] MCP Server (SSE) requires a URL")
                    } else if (transportType == "stdio") {
                        val command = node.data["command"] as? String ?: ""
                        if (command.isBlank()) errors.add("[${node.id}] MCP Server (STDIO) requires a command")
                    }

                    val isConnected = graph.edges.any { it.source == node.id }
                    if (!isConnected) errors.add("[${node.id}] MCP Server must be connected to an Agent")

                    // Warning: MCP connected to sub-agent (export only supports main agent)
                    val targets = graph.edges.filter { it.source == node.id }.map { edge ->
                        graph.nodes.find { it.id == edge.target }
                    }
                    for (target in targets) {
                        if (target?.type == "sub-agent") {
                            errors.add("[${node.id}] MCP Server connected to Sub-Agent will only be exported for the main Agent")
                        }
                    }
                }
                "planner-agent" -> {
                    val name = node.data["name"] as? String ?: ""
                    if (name.isBlank()) {
                        errors.add("[${node.id}] Planner agent requires a name")
                    }
                    val goals = node.data["goals"] as? List<*> ?: emptyList<Any>()
                    if (goals.isEmpty()) {
                        errors.add("[${node.id}] Planner agent must have at least one goal")
                    }
                    val actions = node.data["actions"] as? List<*> ?: emptyList<Any>()
                    if (actions.isEmpty()) {
                        errors.add("[${node.id}] Planner agent must have at least one action")
                    }
                    val plannerType = node.data["plannerType"] as? String ?: "llm"
                    if (plannerType == "llm") {
                        val provider = node.data["modelProvider"] as? String
                        if (provider.isNullOrBlank()) {
                            errors.add("[${node.id}] LLM planner requires a model provider")
                        }
                    }
                }
                "sub-agent" -> {
                    val name = node.data["name"] as? String ?: ""
                    val description = node.data["description"] as? String ?: ""
                    val prompt = node.data["systemPrompt"] as? String ?: ""
                    val inputDesc = node.data["inputDescription"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Sub-Agent requires a name")
                    if (description.isBlank()) errors.add("[${node.id}] Sub-Agent requires a description")
                    if (prompt.isBlank()) errors.add("[${node.id}] Sub-Agent requires a system prompt")
                    if (inputDesc.isBlank()) errors.add("[${node.id}] Sub-Agent requires an input description")

                    val isConnected = graph.edges.any { it.source == node.id }
                    if (!isConnected) errors.add("[${node.id}] Sub-Agent must be connected to the main Agent")

                    // Check target is an agent, not another sub-agent
                    val targets = graph.edges.filter { it.source == node.id }.map { edge ->
                        graph.nodes.find { it.id == edge.target }
                    }
                    for (target in targets) {
                        if (target?.type == "sub-agent") {
                            errors.add("[${node.id}] Sub-Agent cannot delegate to another Sub-Agent")
                        }
                    }
                }
            }
        }
        return errors
    }
}
