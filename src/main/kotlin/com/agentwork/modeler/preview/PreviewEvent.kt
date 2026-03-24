package com.agentwork.modeler.preview

sealed class PreviewEvent(val type: String) {
    companion object {
        private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    }

    data class SessionCreated(val sessionId: String) : PreviewEvent("session-created")
    data class AgentInitialized(val durationMs: Long) : PreviewEvent("agent-initialized")
    data class LlmCallStarted(val model: String) : PreviewEvent("llm-call-started")
    data class LlmCallCompleted(val durationMs: Long, val tokens: Int) : PreviewEvent("llm-call-completed")
    data class ToolCallStarted(val tool: String, val args: Map<String, Any?>) : PreviewEvent("tool-call-started")
    data class ToolCallCompleted(val tool: String, val durationMs: Long, val result: String?) : PreviewEvent("tool-call-completed")
    data class AgentMessage(val content: String) : PreviewEvent("agent-message")
    data class Error(val message: String) : PreviewEvent("error")
    data object StreamComplete : PreviewEvent("stream-complete")

    fun toJson(): String {
        val data = when (this) {
            is SessionCreated -> mapOf("sessionId" to sessionId)
            is AgentInitialized -> mapOf("durationMs" to durationMs)
            is LlmCallStarted -> mapOf("model" to model)
            is LlmCallCompleted -> mapOf("durationMs" to durationMs, "tokens" to tokens)
            is ToolCallStarted -> mapOf("tool" to tool, "args" to args)
            is ToolCallCompleted -> mapOf("tool" to tool, "durationMs" to durationMs, "result" to result)
            is AgentMessage -> mapOf("content" to content)
            is Error -> mapOf("message" to message)
            is StreamComplete -> emptyMap()
        }
        return objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
    }
}
