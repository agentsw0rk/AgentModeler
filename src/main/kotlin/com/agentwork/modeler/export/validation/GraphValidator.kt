package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.ProjectGraph
import org.springframework.stereotype.Component

@Component
class GraphValidator {
    fun validate(graph: ProjectGraph): List<String> {
        val errors = mutableListOf<String>()
        val agentNodes = graph.nodes.filter { it.type == "agent" }
        if (agentNodes.size > 1) {
            errors.add("Only single-agent graphs are supported for export")
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
