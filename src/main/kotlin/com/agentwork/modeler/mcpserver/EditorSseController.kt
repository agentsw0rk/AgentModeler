package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/editor")
class EditorSseController(
    private val stateService: EditorStateService
) {
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): SseEmitter {
        val emitter = SseEmitter(0L)
        var listenerRef: ((EditorEvent) -> Unit)? = null
        val listener: (EditorEvent) -> Unit = { event ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(event.type)
                        .data(event.toJson())
                )
            } catch (_: Exception) {
                listenerRef?.let { stateService.unsubscribe(it) }
            }
        }
        listenerRef = listener
        stateService.subscribe(listener)
        emitter.onCompletion { stateService.unsubscribe(listener) }
        emitter.onTimeout { stateService.unsubscribe(listener) }
        emitter.onError { stateService.unsubscribe(listener) }
        return emitter
    }
}
