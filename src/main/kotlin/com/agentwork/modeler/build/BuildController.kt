package com.agentwork.modeler.build

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BuildController(private val buildService: BuildService) {

    @PostMapping("/api/build")
    fun build(@RequestBody request: BuildRequest): BuildResult {
        return buildService.build(request.graph, request.platform, request.backend, request.targets)
    }

    @ExceptionHandler(Exception::class)
    fun handleError(e: Exception): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(mapOf(
            "error" to "Build failed",
            "details" to (e.message ?: "Unknown error")
        ))
    }
}
