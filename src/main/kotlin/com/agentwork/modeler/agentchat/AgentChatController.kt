package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlinx.coroutines.*

@RestController
@RequestMapping("/api/agent")
class AgentChatController(
    private val agentChatService: AgentChatService
) {
    private val mapper = jacksonObjectMapper()

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody request: AgentChatRequest): SseEmitter {
        val emitter = SseEmitter(0L)

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                agentChatService.chat(request.message) { event ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name(event.type)
                                .data(mapper.writeValueAsString(event))
                        )
                    } catch (_: Exception) {
                        // Client disconnected
                    }
                }
                emitter.complete()
            } catch (e: Exception) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(mapper.writeValueAsString(mapOf("type" to "error", "message" to (e.message ?: "Unknown error"))))
                    )
                    emitter.complete()
                } catch (_: Exception) {
                    emitter.completeWithError(e)
                }
            }
        }

        return emitter
    }

    @PostMapping("/chat/reset")
    fun resetChat(): ResponseEntity<Map<String, String>> {
        agentChatService.resetHistory()
        return ResponseEntity.ok(mapOf("message" to "Chat history cleared"))
    }
}
