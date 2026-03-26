package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AAgent
import org.springframework.stereotype.Component

@Component
class A2AClientGenerator {

    fun generate(agent: KoogA2AAgent): String {
        val className = "${agent.name.replace(" ", "").replaceFirstChar { it.uppercase() }}A2AClient"
        val baseUrl = agent.url.trimEnd('/')

        return buildString {
            appendLine("package generated.a2a")
            appendLine()
            appendLine("import java.net.URI")
            appendLine("import java.net.http.HttpClient")
            appendLine("import java.net.http.HttpRequest")
            appendLine("import java.net.http.HttpResponse")
            appendLine("import java.time.Duration")
            appendLine()
            appendLine("/**")
            appendLine(" * A2A Client for ${agent.name} — ${agent.description}")
            appendLine(" * Target: $baseUrl")
            appendLine(" */")
            appendLine("class $className {")
            appendLine()
            appendLine("    private val baseUrl = \"$baseUrl\"")
            appendLine("    private val timeout = Duration.ofMillis(${agent.timeout}.toLong())")
            appendLine("    private val client = HttpClient.newBuilder().connectTimeout(timeout).build()")
            appendLine()

            when (agent.authType) {
                "bearer" -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder =")
                    appendLine("        builder.header(\"Authorization\", \"Bearer ${agent.authToken}\")")
                }
                "api-key" -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder =")
                    appendLine("        builder.header(\"x-api-key\", \"${agent.authToken}\")")
                }
                else -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder = builder")
                }
            }
            appendLine()

            appendLine("    fun sendMessage(message: String): String {")
            appendLine("        val body = \"\"\"{\"message\": \"${'$'}message\"}\"\"\".trimIndent()")
            appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
            appendLine("            .uri(URI.create(\"${'$'}baseUrl/message\"))")
            appendLine("            .header(\"Content-Type\", \"application/json\")")
            appendLine("            .timeout(timeout)")
            appendLine("            .POST(HttpRequest.BodyPublishers.ofString(body)))")
            appendLine("            .build()")
            appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
            appendLine("        return response.body()")
            appendLine("    }")

            if (agent.communicationMode == "task") {
                appendLine()
                appendLine("    fun sendTask(taskId: String, message: String): String {")
                appendLine("        val body = \"\"\"{\"taskId\": \"${'$'}taskId\", \"message\": \"${'$'}message\"}\"\"\".trimIndent()")
                appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
                appendLine("            .uri(URI.create(\"${'$'}baseUrl/task\"))")
                appendLine("            .header(\"Content-Type\", \"application/json\")")
                appendLine("            .timeout(timeout)")
                appendLine("            .POST(HttpRequest.BodyPublishers.ofString(body)))")
                appendLine("            .build()")
                appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
                appendLine("        return response.body()")
                appendLine("    }")
                appendLine()
                appendLine("    fun getTaskStatus(taskId: String): String {")
                appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
                appendLine("            .uri(URI.create(\"${'$'}baseUrl/task/${'$'}taskId\"))")
                appendLine("            .timeout(timeout)")
                appendLine("            .GET())")
                appendLine("            .build()")
                appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
                appendLine("        return response.body()")
                appendLine("    }")
            }

            appendLine("}")
        }
    }
}
