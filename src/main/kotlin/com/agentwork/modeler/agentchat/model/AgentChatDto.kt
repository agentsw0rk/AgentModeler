package com.agentwork.modeler.agentchat.model

data class AgentChatRequest(
    val message: String
)

data class AgentChatEvent(
    val type: String,
    val content: String? = null,
    val tool: String? = null,
    val args: Map<String, Any?>? = null,
    val result: String? = null,
    val message: String? = null
) {
    companion object {
        fun toolCall(tool: String, args: Map<String, Any?>, result: String) =
            AgentChatEvent(type = "tool-call", tool = tool, args = args, result = result)

        fun agentMessage(content: String) =
            AgentChatEvent(type = "agent-message", content = content)

        fun streamComplete() =
            AgentChatEvent(type = "stream-complete")

        fun error(message: String) =
            AgentChatEvent(type = "error", message = message)
    }
}
