package com.agentwork.modeler.mcpserver.model

import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.WorkspaceGraph

sealed class EditorEvent(val type: String) {
    companion object {
        private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    }

    data class NodeCreated(val node: GraphNode) : EditorEvent("node-created")
    data class NodeUpdated(val nodeId: String, val data: Map<String, Any?>) : EditorEvent("node-updated")
    data class NodeDeleted(val nodeId: String) : EditorEvent("node-deleted")
    data class EdgeCreated(val edge: GraphEdge) : EditorEvent("edge-created")
    data class EdgeDeleted(val edgeId: String) : EditorEvent("edge-deleted")
    data class GraphReset(val graph: WorkspaceGraph) : EditorEvent("graph-reset")
    data class WorkspaceRenamed(val name: String) : EditorEvent("workspace-renamed")
    data class ValidationUpdated(val errors: List<ValidationError>) : EditorEvent("validation-updated")

    fun toJson(): String {
        val dataMap = when (this) {
            is NodeCreated -> mapOf("node" to node)
            is NodeUpdated -> mapOf("nodeId" to nodeId, "data" to data)
            is NodeDeleted -> mapOf("nodeId" to nodeId)
            is EdgeCreated -> mapOf("edge" to edge)
            is EdgeDeleted -> mapOf("edgeId" to edgeId)
            is GraphReset -> mapOf("graph" to graph)
            is WorkspaceRenamed -> mapOf("name" to name)
            is ValidationUpdated -> mapOf("errors" to errors)
        }
        return objectMapper.writeValueAsString(mapOf("type" to type) + dataMap)
    }
}

data class ValidationError(
    val nodeId: String,
    val severity: String,
    val message: String
)
