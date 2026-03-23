package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ControllerGenerator {
    fun generate(): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import kotlinx.coroutines.runBlocking")
            appendLine("import org.springframework.web.bind.annotation.PostMapping")
            appendLine("import org.springframework.web.bind.annotation.RequestBody")
            appendLine("import org.springframework.web.bind.annotation.RestController")
            appendLine()
            appendLine("@RestController")
            appendLine("class ChatController(private val agent: AIAgent<String, String>) {")
            appendLine()
            appendLine("""    @PostMapping("/api/chat")""")
            appendLine("    fun chat(@RequestBody request: ChatRequest): ChatResponse {")
            appendLine("        val result = runBlocking { agent.run(request.message) }")
            appendLine("        return ChatResponse(response = result)")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("data class ChatRequest(val message: String)")
            appendLine("data class ChatResponse(val response: String)")
        }
    }
}
