package com.agentwork.modeler.preview

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.CoroutineScope
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

data class PreviewSession(
    val sessionId: String,
    val agent: AIAgent<String, String>,
    val scope: CoroutineScope,
    val emitter: SseEmitter,
    val history: MutableList<ConversationMessage> = mutableListOf(),
    val createdAt: Instant = Instant.now()
)

data class ConversationMessage(
    val role: String,
    val content: String
)
