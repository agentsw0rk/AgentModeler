package com.agentwork.modeler.preview

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreviewSessionManagerTest {

    private lateinit var manager: PreviewSessionManager

    @BeforeEach
    fun setUp() {
        manager = PreviewSessionManager()
    }

    @Test
    fun `generates unique session IDs`() {
        val id1 = manager.generateId()
        val id2 = manager.generateId()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `stores and retrieves session`() {
        val session = createTestSession("test-1")
        manager.createSession(session)
        assertNotNull(manager.getSession("test-1"))
        assertEquals(1, manager.activeCount())
    }

    @Test
    fun `removes session`() {
        val session = createTestSession("test-1")
        manager.createSession(session)
        manager.removeSession("test-1")
        assertNull(manager.getSession("test-1"))
        assertEquals(0, manager.activeCount())
    }

    @Test
    fun `returns null for unknown session`() {
        assertNull(manager.getSession("unknown"))
    }

    @Test
    fun `enforces max session limit`() {
        repeat(5) { manager.createSession(createTestSession("s-$it")) }
        assertThrows<PreviewException> {
            manager.createSession(createTestSession("s-overflow"))
        }
    }

    private fun createTestSession(id: String): PreviewSession {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
        )
        return PreviewSession(
            sessionId = id,
            agent = org.mockito.Mockito.mock(),
            scope = scope,
            emitter = org.springframework.web.servlet.mvc.method.annotation.SseEmitter()
        )
    }
}
