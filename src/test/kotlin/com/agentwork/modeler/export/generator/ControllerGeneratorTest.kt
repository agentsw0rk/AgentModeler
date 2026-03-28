package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

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

    @Test
    fun `generates typed controller with custom IO`() {
        val code = generator.generate(inputClassName = "MyAgentInput", outputClassName = "MyAgentOutput")
        assertContains(code, "AIAgent<MyAgentInput, MyAgentOutput>")
        assertContains(code, "fun chat(@RequestBody request: MyAgentInput): MyAgentOutput")
        assertContains(code, "agent.run(request)")
        assertFalse(code.contains("ChatRequest"))
        assertFalse(code.contains("ChatResponse"))
    }

    @Test
    fun `generates typed controller with only input`() {
        val code = generator.generate(inputClassName = "MyInput")
        assertContains(code, "AIAgent<MyInput, String>")
        assertContains(code, "fun chat(@RequestBody request: MyInput): ChatResponse")
        assertContains(code, "agent.run(request)")
    }

    @Test
    fun `generates typed controller with only output`() {
        val code = generator.generate(outputClassName = "MyOutput")
        assertContains(code, "AIAgent<String, MyOutput>")
        assertContains(code, "fun chat(@RequestBody request: ChatRequest): MyOutput")
        assertContains(code, "agent.run(request.message)")
    }

    @Test
    fun `generates default String String controller when no IO`() {
        val code = generator.generate()
        assertContains(code, "AIAgent<String, String>")
        assertContains(code, "ChatRequest")
        assertContains(code, "ChatResponse")
    }
}
