package com.agentwork.modeler.preview

import kotlinx.coroutines.cancel
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PreviewSessionManager {

    companion object {
        private const val TTL_MINUTES = 30L
        private const val MAX_SESSIONS = 5
    }

    private val sessions = ConcurrentHashMap<String, PreviewSession>()

    fun createSession(session: PreviewSession): String {
        if (sessions.size >= MAX_SESSIONS) {
            throw PreviewException("Too many active sessions (max $MAX_SESSIONS)")
        }
        sessions[session.sessionId] = session
        return session.sessionId
    }

    fun getSession(sessionId: String): PreviewSession? = sessions[sessionId]

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)?.let { session ->
            session.scope.cancel()
            session.emitter.complete()
        }
    }

    fun generateId(): String = UUID.randomUUID().toString()

    fun activeCount(): Int = sessions.size

    @Scheduled(fixedRate = 300_000)
    fun cleanupExpired() {
        val now = Instant.now()
        sessions.entries.removeIf { (_, session) ->
            val expired = session.createdAt.plusSeconds(TTL_MINUTES * 60).isBefore(now)
            if (expired) {
                session.scope.cancel()
                session.emitter.complete()
            }
            expired
        }
    }
}

class PreviewException(message: String) : RuntimeException(message)
