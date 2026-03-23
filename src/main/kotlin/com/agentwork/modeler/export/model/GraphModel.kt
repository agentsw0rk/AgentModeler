package com.agentwork.modeler.export.model

data class ProjectGraph(
    val version: Int,
    val name: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
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
