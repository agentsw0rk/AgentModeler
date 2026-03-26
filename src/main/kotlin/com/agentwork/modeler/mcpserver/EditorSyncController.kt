package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/editor")
class EditorSyncController(
    private val stateService: EditorStateService
) {
    @PostMapping("/sync")
    fun syncGraph(@RequestBody graph: WorkspaceGraph): ResponseEntity<Map<String, Any>> {
        stateService.syncGraph(graph)
        return ResponseEntity.ok(mapOf(
            "message" to "Graph synced successfully",
            "nodeCount" to graph.nodes.size,
            "edgeCount" to graph.edges.size
        ))
    }
}
