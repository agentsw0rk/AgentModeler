package com.agentwork.modeler.build

import com.agentwork.modeler.export.model.WorkspaceGraph

data class BuildRequest(
    val graph: WorkspaceGraph,
    val platform: String = "jvm",
    val backend: String = "spring-boot"
)

data class BuildResult(
    val success: Boolean,
    val errors: List<BuildError>,
    val log: String
)

data class BuildError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String,
    val nodeId: String?
)
