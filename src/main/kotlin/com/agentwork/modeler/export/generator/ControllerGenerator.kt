package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ControllerGenerator {
    fun generate(inputClassName: String? = null, outputClassName: String? = null): String {
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"
        val hasCustomInput = inputClassName != null
        val hasCustomOutput = outputClassName != null

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
            appendLine("class ChatController(private val agent: AIAgent<$inputType, $outputType>) {")
            appendLine()
            appendLine("""    @PostMapping("/api/chat")""")

            val requestType = if (hasCustomInput) inputType else "ChatRequest"
            val responseType = if (hasCustomOutput) outputType else "ChatResponse"
            val runArg = if (hasCustomInput) "request" else "request.message"
            val resultWrap = if (hasCustomOutput) "result" else "ChatResponse(response = result)"

            appendLine("    fun chat(@RequestBody request: $requestType): $responseType {")
            appendLine("        val result = runBlocking { agent.run($runArg) }")
            appendLine("        return $resultWrap")
            appendLine("    }")
            appendLine("}")

            if (!hasCustomInput) {
                appendLine()
                appendLine("data class ChatRequest(val message: String)")
            }
            if (!hasCustomOutput) {
                appendLine()
                appendLine("data class ChatResponse(val response: String)")
            }
        }
    }
}
