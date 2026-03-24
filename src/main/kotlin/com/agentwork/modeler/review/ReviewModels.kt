package com.agentwork.modeler.review

enum class ReviewStatus { PASS, WARNING, CRITICAL, UNAVAILABLE }
enum class FindingSeverity { CRITICAL, WARNING, SUGGESTION }

data class ReviewRequest(
    val files: Map<String, String>,
    val projectName: String,
    val graphSummary: String
)

data class ReviewResult(
    val status: ReviewStatus,
    val summary: String,
    val findings: List<ReviewFinding>,
    val metadata: ReviewMetadata
)

data class ReviewFinding(
    val severity: FindingSeverity,
    val category: String,
    val file: String?,
    val message: String
)

data class ReviewMetadata(
    val model: String,
    val reviewedFiles: Int,
    val skippedFiles: Int,
    val durationMs: Long
)
