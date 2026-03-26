package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AConfig
import org.springframework.stereotype.Component

@Component
class A2AServerGenerator {

    fun generate(config: KoogA2AConfig): Map<String, String> {
        val files = mutableMapOf<String, String>()
        files["A2AAgentCard.kt"] = generateAgentCard(config)
        files["A2AExecutor.kt"] = generateExecutor(config)
        files["A2AServer.kt"] = generateServer(config)
        return files
    }

    private fun generateAgentCard(config: KoogA2AConfig): String = buildString {
        val card = config.agentCard
        appendLine("package generated.a2a")
        appendLine()
        appendLine("object A2AAgentCard {")
        appendLine("    val name = \"${card.name}\"")
        appendLine("    val description = \"${card.description}\"")
        appendLine("    val url = \"${card.url}\"")
        appendLine("    val version = \"${card.version}\"")
        appendLine()
        appendLine("    val capabilities = mapOf(")
        appendLine("        \"streaming\" to ${card.capabilities.streaming},")
        appendLine("        \"pushNotifications\" to ${card.capabilities.pushNotifications},")
        appendLine("        \"stateTransitionHistory\" to ${card.capabilities.stateTransitionHistory}")
        appendLine("    )")
        appendLine()
        appendLine("    val skills = listOf(")
        for ((i, skill) in card.skills.withIndex()) {
            val comma = if (i < card.skills.size - 1) "," else ""
            appendLine("        mapOf(\"id\" to \"${skill.id}\", \"name\" to \"${skill.name}\", \"description\" to \"${skill.description}\", \"tags\" to listOf(${skill.tags.joinToString(", ") { "\"$it\"" }}))$comma")
        }
        appendLine("    )")
        appendLine()
        appendLine("    val defaultInputModes = listOf(${card.defaultInputModes.joinToString(", ") { "\"$it\"" }})")
        appendLine("    val defaultOutputModes = listOf(${card.defaultOutputModes.joinToString(", ") { "\"$it\"" }})")
        appendLine()
        appendLine("    fun toJson(): String = \"\"\"{")
        appendLine("        |  \"name\": \"${'$'}{name}\",")
        appendLine("        |  \"description\": \"${'$'}{description}\",")
        appendLine("        |  \"url\": \"${'$'}{url}\",")
        appendLine("        |  \"version\": \"${'$'}{version}\"")
        appendLine("        |}\"\"\".trimMargin()")
        appendLine("}")
    }

    private fun generateExecutor(config: KoogA2AConfig): String = buildString {
        appendLine("package generated.a2a")
        appendLine()
        appendLine("/**")
        appendLine(" * A2A Executor — handles incoming A2A requests.")
        appendLine(" * Wire this to your agent's invoke logic.")
        appendLine(" */")
        appendLine("class A2AExecutor {")
        appendLine()
        appendLine("    suspend fun handleMessage(message: String): String {")
        appendLine("        // TODO: Delegate to generated agent")
        appendLine("        return \"Response from \${A2AAgentCard.name}\"")
        appendLine("    }")
        appendLine()
        appendLine("    suspend fun handleTask(taskId: String, message: String): TaskStatus {")
        appendLine("        // TODO: Delegate to generated agent with task tracking")
        appendLine("        return TaskStatus(taskId, \"completed\", \"Task processed by \${A2AAgentCard.name}\")")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("data class TaskStatus(val taskId: String, val status: String, val message: String)")
    }

    private fun generateServer(config: KoogA2AConfig): String = buildString {
        val server = config.server
        appendLine("package generated.a2a")
        appendLine()
        appendLine("import org.springframework.web.bind.annotation.*")
        appendLine("import org.springframework.http.MediaType")
        if (config.agentCard.capabilities.streaming) {
            appendLine("import org.springframework.http.codec.ServerSentEvent")
            appendLine("import reactor.core.publisher.Flux")
        }
        appendLine()
        appendLine("/**")
        appendLine(" * A2A Server — HTTP JSON-RPC transport on port ${server.port}, basePath ${server.basePath}")
        appendLine(" */")
        appendLine("@RestController")
        appendLine("@RequestMapping(\"${server.basePath}\")")
        appendLine("class A2AServer(private val executor: A2AExecutor) {")
        appendLine()
        appendLine("    @GetMapping(\"/.well-known/agent.json\", produces = [MediaType.APPLICATION_JSON_VALUE])")
        appendLine("    fun agentCard(): String = A2AAgentCard.toJson()")
        appendLine()
        appendLine("    @PostMapping(\"/message\")")
        appendLine("    suspend fun sendMessage(@RequestBody request: A2AMessageRequest): A2AMessageResponse {")
        appendLine("        val result = executor.handleMessage(request.message)")
        appendLine("        return A2AMessageResponse(result = result)")
        appendLine("    }")
        appendLine()
        appendLine("    @PostMapping(\"/task\")")
        appendLine("    suspend fun sendTask(@RequestBody request: A2ATaskRequest): TaskStatus {")
        appendLine("        return executor.handleTask(request.taskId, request.message)")
        appendLine("    }")
        if (config.agentCard.capabilities.streaming) {
            appendLine()
            appendLine("    @GetMapping(\"/task/{taskId}/events\", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])")
            appendLine("    fun streamTaskEvents(@PathVariable taskId: String): Flux<ServerSentEvent<String>> {")
            appendLine("        // TODO: Implement streaming task status updates")
            appendLine("        return Flux.just(ServerSentEvent.builder(\"{ \\\"taskId\\\": \\\"${'$'}taskId\\\", \\\"status\\\": \\\"completed\\\" }\").build())")
            appendLine("    }")
        }
        appendLine("}")
        appendLine()
        appendLine("data class A2AMessageRequest(val message: String)")
        appendLine("data class A2AMessageResponse(val result: String)")
        appendLine("data class A2ATaskRequest(val taskId: String, val message: String)")
    }
}
