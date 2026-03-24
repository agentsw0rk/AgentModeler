package com.agentwork.modeler.mcp

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class McpDiscoveryController(private val service: McpDiscoveryService) {

    @PostMapping("/api/mcp/discover")
    fun discover(@RequestBody request: McpDiscoveryRequest): ResponseEntity<McpDiscoveryResponse> {
        val response = service.discover(request)
        return if (response.error != null) {
            ResponseEntity.badRequest().body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }
}
