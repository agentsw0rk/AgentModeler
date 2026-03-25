package com.agentwork.modeler.preview

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

data class StartPreviewRequest(
    val graph: WorkspaceGraph,
    val message: String
)

data class SendMessageRequest(
    val message: String
)

@RestController
@RequestMapping("/api/preview")
class PreviewController(
    private val previewService: PreviewService
) {

    @PostMapping("/start", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun startPreview(@RequestBody request: StartPreviewRequest): SseEmitter {
        return previewService.startSession(request.graph, request.message)
    }

    @PostMapping("/{sessionId}/message")
    fun sendMessage(
        @PathVariable sessionId: String,
        @RequestBody request: SendMessageRequest
    ): ResponseEntity<Void> {
        previewService.sendMessage(sessionId, request.message)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{sessionId}/stop")
    fun stopSession(@PathVariable sessionId: String): ResponseEntity<Void> {
        previewService.stopSession(sessionId)
        return ResponseEntity.ok().build()
    }

    @ExceptionHandler(PreviewValidationException::class)
    fun handleValidation(e: PreviewValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(mapOf(
            "error" to "Validation failed",
            "details" to e.errors
        ))
    }

    @ExceptionHandler(PreviewException::class)
    fun handlePreviewError(e: PreviewException): ResponseEntity<Map<String, String>> {
        val status = if (e.message?.contains("Too many") == true) HttpStatus.TOO_MANY_REQUESTS else HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(mapOf("error" to (e.message ?: "Preview error")))
    }
}
