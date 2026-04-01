package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class KtorControllerGenerator {
    fun generate(inputClassName: String? = null, outputClassName: String? = null): String {
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"
        val hasCustomInput = inputClassName != null
        val hasCustomOutput = outputClassName != null

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import io.ktor.server.application.*")
            appendLine("import io.ktor.server.routing.*")
            if (hasCustomInput || hasCustomOutput) {
                appendLine("import io.ktor.server.plugins.contentnegotiation.*")
                appendLine("import io.ktor.serialization.kotlinx.json.*")
            }
            appendLine("import io.ktor.server.request.*")
            appendLine("import io.ktor.server.response.*")
            if (!hasCustomInput || !hasCustomOutput) {
                appendLine("import kotlinx.serialization.Serializable")
            }
            appendLine()
            // Ktor route handlers are already suspend — no runBlocking needed
            appendLine("fun Application.configureChatRoutes(agent: AIAgent<$inputType, $outputType>) {")

            if (hasCustomInput || hasCustomOutput) {
                appendLine("    install(ContentNegotiation) {")
                appendLine("        json()")
                appendLine("    }")
                appendLine()
            }

            appendLine("    routing {")
            appendLine("""        post("/api/chat") {""")

            if (hasCustomInput) {
                appendLine("            val request = call.receive<$inputType>()")
                appendLine("            val result = agent.run(request)")
            } else {
                appendLine("            val request = call.receive<ChatRequest>()")
                appendLine("            val result = agent.run(request.message)")
            }

            if (hasCustomOutput) {
                appendLine("            call.respond(result)")
            } else {
                appendLine("            call.respond(ChatResponse(response = result))")
            }

            appendLine("        }")
            appendLine("    }")
            appendLine("}")

            if (!hasCustomInput) {
                appendLine()
                appendLine("@Serializable")
                appendLine("data class ChatRequest(val message: String)")
            }
            if (!hasCustomOutput) {
                appendLine()
                appendLine("@Serializable")
                appendLine("data class ChatResponse(val response: String)")
            }
        }
    }
}
