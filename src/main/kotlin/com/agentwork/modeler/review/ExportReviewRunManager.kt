package com.agentwork.modeler.review

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExportReviewRun(
    val runId: String,
    val zipBytes: ByteArray,
    val reviewResult: ReviewResult? = null,
    val createdAt: Instant = Instant.now()
)

@Component
class ExportReviewRunManager {
    companion object {
        private const val TTL_MINUTES = 15L
        private const val MAX_RUNS = 10
    }

    private val runs = ConcurrentHashMap<String, ExportReviewRun>()

    fun createRun(zipBytes: ByteArray): String {
        if (runs.size >= MAX_RUNS) {
            throw ReviewException("Too many active export reviews (max $MAX_RUNS)")
        }
        val runId = UUID.randomUUID().toString()
        runs[runId] = ExportReviewRun(runId = runId, zipBytes = zipBytes)
        return runId
    }

    fun getRun(runId: String): ExportReviewRun? = runs[runId]

    fun updateReview(runId: String, result: ReviewResult) {
        runs.computeIfPresent(runId) { _, run -> run.copy(reviewResult = result) }
    }

    @Scheduled(fixedRate = 300_000)
    fun cleanupExpired() {
        val now = Instant.now()
        runs.entries.removeIf { (_, run) ->
            run.createdAt.plusSeconds(TTL_MINUTES * 60).isBefore(now)
        }
    }
}

class ReviewException(message: String) : RuntimeException(message)
