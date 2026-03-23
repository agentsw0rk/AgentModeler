package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ControllerGeneratorTest {
    private val generator = ControllerGenerator()

    @Test
    fun `generates REST controller with chat endpoint`() {
        val code = generator.generate()
        assertContains(code, "package generated")
        assertContains(code, "@RestController")
        assertContains(code, """@PostMapping("/api/chat")""")
        assertContains(code, "ChatController")
        assertContains(code, "AIAgent<String, String>")
        assertContains(code, "agent.run(request.message)")
        assertContains(code, "runBlocking")
        assertContains(code, "data class ChatRequest")
        assertContains(code, "data class ChatResponse")
    }
}
