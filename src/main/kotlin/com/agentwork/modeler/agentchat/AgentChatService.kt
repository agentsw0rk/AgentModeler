package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatEvent
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.tool.ToolCallback

class AgentChatService(
    private val chatClient: ChatClient? = null,
    private val toolCallbacks: List<ToolCallback> = emptyList()
) {

    data class ChatMessage(val role: String, val content: String)

    private val history = mutableListOf<ChatMessage>()

    private val systemPrompt = """
        You are a graph editor assistant for building AI agent workflows.
        You help users create, modify, and understand agent graphs.

        You know about the following node types:
        - LLM nodes: process text with language models
        - Tool nodes: execute external tools or functions
        - Condition nodes: branch the workflow based on conditions
        - Input/Output nodes: define the entry and exit points of the graph
        - Subgraph nodes: reference other graphs as reusable components

        Connection rules:
        - Nodes connect via typed ports (input/output)
        - Each output port can connect to multiple input ports
        - Each input port accepts only one connection
        - Connections must match compatible port types

        Help users design effective agent workflows by suggesting node arrangements,
        explaining connection patterns, and validating graph structures.
    """.trimIndent()

    fun getHistory(): List<ChatMessage> = history.toList()

    fun addUserMessage(content: String) {
        history.add(ChatMessage(role = "user", content = content))
    }

    fun addAssistantMessage(content: String) {
        history.add(ChatMessage(role = "assistant", content = content))
    }

    fun resetHistory() {
        history.clear()
    }

    fun chat(userMessage: String, onEvent: (AgentChatEvent) -> Unit) {
        addUserMessage(userMessage)

        if (chatClient == null) {
            onEvent(AgentChatEvent.error("Chat client is not configured"))
            onEvent(AgentChatEvent.streamComplete())
            return
        }

        try {
            val messages = mutableListOf<org.springframework.ai.chat.messages.Message>()
            messages.add(SystemMessage(systemPrompt))
            for (msg in history) {
                when (msg.role) {
                    "user" -> messages.add(UserMessage(msg.content))
                    "assistant" -> messages.add(AssistantMessage(msg.content))
                }
            }

            val response = chatClient.prompt()
                .messages(messages)
                .toolCallbacks(*toolCallbacks.toTypedArray())
                .call()

            val chatResponse = response.chatResponse()
            val output = chatResponse?.result?.output

            // Emit tool call events if present
            output?.toolCalls?.forEach { toolCall ->
                onEvent(
                    AgentChatEvent.toolCall(
                        tool = toolCall.name(),
                        args = mapOf("arguments" to toolCall.arguments()),
                        result = ""
                    )
                )
            }

            // Emit agent message
            val text = output?.text ?: ""
            if (text.isNotEmpty()) {
                addAssistantMessage(text)
                onEvent(AgentChatEvent.agentMessage(text))
            }

            onEvent(AgentChatEvent.streamComplete())
        } catch (e: Exception) {
            onEvent(AgentChatEvent.error(e.message ?: "Unknown error"))
            onEvent(AgentChatEvent.streamComplete())
        }
    }
}
