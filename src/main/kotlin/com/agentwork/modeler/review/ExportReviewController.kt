package com.agentwork.modeler.review

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.ExportValidationException
import com.agentwork.modeler.export.model.WorkspaceGraph
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/export/review")
class ExportReviewController(
    private val exportService: ExportService,
    private val reviewService: ReviewService,
    private val runManager: ExportReviewRunManager
) {
    private val objectMapper = ObjectMapper()
    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun exportAndReview(@RequestBody graph: WorkspaceGraph): SseEmitter {
        val emitter = SseEmitter(0L)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        emitter.onCompletion { scope.cancel() }
        emitter.onTimeout { scope.cancel() }
        emitter.onError { scope.cancel() }

        scope.launch {
            try {
                val (zipBytes, files) = exportService.exportWithFiles(graph)
                val runId = runManager.createRun(zipBytes)

                sendEvent(emitter, "export-ready", mapOf(
                    "runId" to runId,
                    "downloadUrl" to "/api/export/review/$runId/download",
                    "reviewJsonUrl" to "/api/export/review/$runId/review.json"
                ))

                val result = reviewService.review(files, graph)
                runManager.updateReview(runId, result)

                sendEvent(emitter, "review-result", result)
                emitter.complete()
            } catch (e: ExportValidationException) {
                sendEvent(emitter, "review-failed", mapOf("message" to "Export validation failed: ${e.errors.joinToString("; ")}"))
                emitter.complete()
            } catch (e: Exception) {
                sendEvent(emitter, "review-failed", mapOf("message" to (e.message ?: "Unknown error")))
                emitter.complete()
            }
        }

        return emitter
    }

    @GetMapping("/{runId}/download")
    fun downloadZip(@PathVariable runId: String): ResponseEntity<ByteArray> {
        val run = runManager.getRun(runId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(run.zipBytes)
    }

    @GetMapping("/{runId}/review.json")
    fun downloadReview(@PathVariable runId: String): ResponseEntity<ReviewResult> {
        val run = runManager.getRun(runId) ?: return ResponseEntity.notFound().build()
        val result = run.reviewResult ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @ExceptionHandler(ReviewException::class)
    fun handleReviewError(e: ReviewException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(429).body(mapOf("error" to (e.message ?: "Review error")))
    }

    private fun sendEvent(emitter: SseEmitter, type: String, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
            emitter.send(SseEmitter.event().data(json))
        } catch (_: Exception) {}
    }
}
