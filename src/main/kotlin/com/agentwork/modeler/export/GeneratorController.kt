package com.agentwork.modeler.export

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class GeneratorController(
    private val backends: List<GeneratorBackend>
) {
    @GetMapping("/generators")
    fun listGenerators(): List<GeneratorInfo> {
        return backends.map { GeneratorInfo(it.id, it.displayName, it.supportedPlatforms) }
    }
}

data class GeneratorInfo(
    val id: String,
    val displayName: String,
    val supportedPlatforms: List<String>
)
