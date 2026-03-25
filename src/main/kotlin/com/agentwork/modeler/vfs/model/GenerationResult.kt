package com.agentwork.modeler.vfs.model

enum class GenerationStatus { VALID, INVALID }

data class GenerationResult(
    val status: GenerationStatus,
    val fileCount: Int? = null,
    val generatedAt: String? = null,
    val errors: List<String>? = null
)
