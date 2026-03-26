package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentChatServiceTest {

    @Test
    fun `chat history starts empty`() {
        val service = AgentChatService(chatClient = null)
        assertTrue(service.getHistory().isEmpty())
    }

    @Test
    fun `addUserMessage adds to history`() {
        val service = AgentChatService(chatClient = null)
        service.addUserMessage("Hello")
        assertEquals(1, service.getHistory().size)
        assertEquals("user", service.getHistory()[0].role)
        assertEquals("Hello", service.getHistory()[0].content)
    }

    @Test
    fun `addAssistantMessage adds to history`() {
        val service = AgentChatService(chatClient = null)
        service.addAssistantMessage("Hi there")
        assertEquals(1, service.getHistory().size)
        assertEquals("assistant", service.getHistory()[0].role)
    }

    @Test
    fun `resetHistory clears all messages`() {
        val service = AgentChatService(chatClient = null)
        service.addUserMessage("Hello")
        service.addAssistantMessage("Hi")
        service.resetHistory()
        assertTrue(service.getHistory().isEmpty())
    }

    @Test
    fun `chat with null client emits error`() {
        val service = AgentChatService(chatClient = null)
        val events = mutableListOf<AgentChatEvent>()
        service.chat("Hello") { events.add(it) }
        assertTrue(events.any { it.type == "error" })
        assertTrue(events.any { it.type == "stream-complete" })
    }
}
