package com.agentwork.modeler.ollama

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OllamaDiscoveryController(private val service: OllamaDiscoveryService) {

    /** Always 200: an unreachable Ollama yields an empty list plus a hint in [OllamaModelsResponse.error]. */
    @GetMapping("/api/ollama/models")
    suspend fun models(): OllamaModelsResponse = service.listModels()
}
