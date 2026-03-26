package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class A2AClientGeneratorTest {

    private val generator = A2AClientGenerator()

    @Test
    fun `generates client class with sendMessage`() {
        val agent = KoogA2AAgent("RemoteBot", "A remote bot", "http://remote:8080/a2a", "none", null, 30000, "message")
        val code = generator.generate(agent)
        assertContains(code, "package generated.a2a")
        assertContains(code, "class RemoteBotA2AClient")
        assertContains(code, "sendMessage")
        assertContains(code, "http://remote:8080/a2a")
    }

    @Test
    fun `generates sendTask for task communication mode`() {
        val agent = KoogA2AAgent("Helper", "Helps", "http://helper:8080", "bearer", "tok123", 5000, "task")
        val code = generator.generate(agent)
        assertContains(code, "sendTask")
        assertContains(code, "getTaskStatus")
        assertContains(code, "tok123")
        assertContains(code, "Authorization")
    }

    @Test
    fun `generates timeout configuration`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "none", null, 15000, "message")
        val code = generator.generate(agent)
        assertContains(code, "15000")
    }

    @Test
    fun `no auth header when authType is none`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "none", null, 30000, "message")
        val code = generator.generate(agent)
        assertFalse(code.contains("Authorization"))
    }

    @Test
    fun `generates api-key auth header`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "api-key", "key123", 30000, "message")
        val code = generator.generate(agent)
        assertContains(code, "x-api-key")
        assertContains(code, "key123")
    }
}
