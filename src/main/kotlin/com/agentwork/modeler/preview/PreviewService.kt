package com.agentwork.modeler.preview

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.model.WorkspaceGraph
import com.agentwork.modeler.export.validation.GraphValidator
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class PreviewService(
    private val validator: GraphValidator,
    private val adapter: GraphToKoogAdapter,
    private val agentBuilder: PreviewAgentBuilder,
    private val sessionManager: PreviewSessionManager
) {

    fun startSession(graph: WorkspaceGraph, initialMessage: String): SseEmitter {
        val errors = validator.validate(graph)
        if (errors.isNotEmpty()) {
            throw PreviewValidationException(errors)
        }

        val agents = adapter.convert(graph)
        if (agents.isEmpty()) {
            throw PreviewException("No agent found in graph")
        }
        val koogAgent = agents.first()

        val sessionId = sessionManager.generateId()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val emitter = SseEmitter(0L) // no timeout

        emitter.onCompletion { sessionManager.removeSession(sessionId) }
        emitter.onTimeout { sessionManager.removeSession(sessionId) }
        emitter.onError { sessionManager.removeSession(sessionId) }

        scope.launch {
            try {
                sendEvent(emitter, PreviewEvent.SessionCreated(sessionId))

                val start = System.currentTimeMillis()
                val agent = agentBuilder.build(koogAgent)
                val buildTime = System.currentTimeMillis() - start
                sendEvent(emitter, PreviewEvent.AgentInitialized(buildTime))

                val session = PreviewSession(
                    sessionId = sessionId,
                    agent = agent,
                    scope = scope,
                    emitter = emitter
                )
                sessionManager.createSession(session)

                executeMessage(session, initialMessage)
            } catch (e: Exception) {
                sendEvent(emitter, PreviewEvent.Error(describePreviewError(e)))
                sendEvent(emitter, PreviewEvent.StreamComplete)
                emitter.complete()
            }
        }

        return emitter
    }

    fun sendMessage(sessionId: String, message: String) {
        val session = sessionManager.getSession(sessionId)
            ?: throw PreviewException("Session not found: $sessionId")

        session.scope.launch {
            try {
                executeMessage(session, message)
            } catch (e: Exception) {
                sendEvent(session.emitter, PreviewEvent.Error(describePreviewError(e)))
                sendEvent(session.emitter, PreviewEvent.StreamComplete)
            }
        }
    }

    fun stopSession(sessionId: String) {
        sessionManager.removeSession(sessionId)
    }

    private suspend fun executeMessage(session: PreviewSession, message: String) {
        session.history.add(ConversationMessage("user", message))

        sendEvent(session.emitter, PreviewEvent.LlmCallStarted("LLM"))

        val start = System.currentTimeMillis()
        val response = session.agent.run(message)
        val duration = System.currentTimeMillis() - start

        sendEvent(session.emitter, PreviewEvent.LlmCallCompleted(duration, 0))
        sendEvent(session.emitter, PreviewEvent.AgentMessage(response))

        session.history.add(ConversationMessage("agent", response))
        sendEvent(session.emitter, PreviewEvent.StreamComplete)
    }

    private fun sendEvent(emitter: SseEmitter, event: PreviewEvent) {
        try {
            emitter.send(SseEmitter.event()
                .name(event.type)
                .data(event.toJson()))
        } catch (_: Exception) {
            // Client disconnected
        }
    }
}

class PreviewValidationException(val errors: List<String>) : RuntimeException("Validation failed")
