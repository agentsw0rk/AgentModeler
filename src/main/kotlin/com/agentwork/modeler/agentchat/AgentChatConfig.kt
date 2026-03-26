package com.agentwork.modeler.agentchat

import com.agentwork.modeler.mcpserver.EditorMcpTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentChatConfig {

    @Bean
    fun agentChatToolCallbacks(editorMcpTools: EditorMcpTools): List<ToolCallback> {
        val provider = MethodToolCallbackProvider.builder()
            .toolObjects(editorMcpTools)
            .build()
        return provider.toolCallbacks.toList()
    }

    @Bean
    fun agentChatService(
        chatClientBuilder: ChatClient.Builder,
        agentChatToolCallbacks: List<ToolCallback>
    ): AgentChatService {
        val chatClient = chatClientBuilder.build()
        return AgentChatService(chatClient, agentChatToolCallbacks)
    }
}
