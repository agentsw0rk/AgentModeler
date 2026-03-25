package com.agentwork.modeler.export.model

data class WorkspaceGraph(
    val version: Int,
    val name: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val otelConfig: OtelConfig? = null
)

data class GraphNode(
    val id: String,
    val type: String,
    val position: Position,
    val data: Map<String, Any?>
)

data class Position(val x: Double, val y: Double)

data class GraphEdge(
    val id: String,
    val source: String,
    val target: String
)

data class OtelConfig(
    val enabled: Boolean = false,
    val exporterType: String = "http",
    val endpoint: String? = null,
    val serviceName: String? = null,
    val serviceVersion: String? = null,
    val authorizationHeader: String? = null,
    val verbose: Boolean = false
)
