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
                    val params = node.data["parameters"] as? List<*> ?: emptyList<Any>()
                    for (param in params) {
                        val map = param as? Map<*, *> ?: continue
                        val paramName = map["name"] as? String ?: ""
                        if (paramName.isBlank()) errors.add("[${node.id}] Parameter requires a name")
                    }
                }
            }
        }
        return errors
    }
}
