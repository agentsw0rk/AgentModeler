package com.agentwork.modeler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4oMini
import ai.koog.prompt.executor.model.PromptExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class MyAgentService(@Qualifier("openAIExecutor") private val promptExecutor: PromptExecutor) {


    suspend fun askAgent(userMessage: String): String {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = GPT4oMini,
            systemPrompt = "You are a helpful assistant."
        )

        return agent.run(userMessage)
    }
}