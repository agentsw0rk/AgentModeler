package com.agentwork.modeler.mcpserver

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpServerConfig {
    @Bean
    fun editorToolCallbackProvider(editorMcpTools: EditorMcpTools): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(editorMcpTools)
            .build()
    }
}
