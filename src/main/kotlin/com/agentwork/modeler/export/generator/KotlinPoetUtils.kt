package com.agentwork.modeler.export.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

/**
 * Shared KotlinPoet utilities for Koog code generation.
 */

// --- Koog Annotation Factories ---

object KoogAnnotations {
    private val TOOL = ClassName("ai.koog.agents.core.tools.annotations", "Tool")
    private val LLM_DESCRIPTION = ClassName("ai.koog.agents.core.tools.annotations", "LLMDescription")
    private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
    private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

    fun tool(): AnnotationSpec = AnnotationSpec.builder(TOOL).build()

    fun llmDescription(desc: String): AnnotationSpec =
        AnnotationSpec.builder(LLM_DESCRIPTION).addMember("%S", desc).build()

    fun serializable(): AnnotationSpec = AnnotationSpec.builder(SERIALIZABLE).build()

    fun serialName(name: String): AnnotationSpec =
        AnnotationSpec.builder(SERIAL_NAME).addMember("%S", name).build()
}

// --- Koog Model/Provider ClassNames ---

object KoogModels {
    fun clientClassName(provider: String): ClassName = when (provider.uppercase()) {
        "OPENAI" -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAILLMClient")
        "OLLAMA" -> ClassName("ai.koog.prompt.executor.clients.ollama", "OllamaClient")
        "ANTHROPIC" -> ClassName("ai.koog.prompt.executor.clients.anthropic", "AnthropicLLMClient")
        "GOOGLE" -> ClassName("ai.koog.prompt.executor.clients.google", "GoogleLLMClient")
        else -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAILLMClient")
    }

    fun modelsClassName(provider: String): ClassName = when (provider.uppercase()) {
        "OPENAI" -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAIModels")
        "OLLAMA" -> ClassName("ai.koog.prompt.executor.clients.ollama", "OllamaModels")
        "ANTHROPIC" -> ClassName("ai.koog.prompt.executor.clients.anthropic", "AnthropicModels")
        "GOOGLE" -> ClassName("ai.koog.prompt.executor.clients.google", "GoogleModels")
        else -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAIModels")
    }

    fun executorMemberName(provider: String): String = when (provider.uppercase()) {
        "OPENAI" -> "simpleOpenAIExecutor"
        "OLLAMA" -> "simpleOllamaExecutor"
        "ANTHROPIC" -> "simpleAnthropicExecutor"
        "GOOGLE" -> "simpleGoogleExecutor"
        else -> "simpleOpenAIExecutor"
    }
}

// --- Common Koog ClassNames ---

object KoogTypes {
    val TOOL_SET = ClassName("ai.koog.agents.core.tools.reflect", "ToolSet")
    val TOOL_REGISTRY = ClassName("ai.koog.agents.core.tools", "ToolRegistry")
    val TOOLS_REFLECT = ClassName("ai.koog.agents.core.tools.reflect", "tools")
    val SIMPLE_TOOL = ClassName("ai.koog.agents.core.tools", "SimpleTool")
    val AI_AGENT = ClassName("ai.koog.agents.core.agent", "AIAgent")
    val LLM_EMBEDDER = ClassName("ai.koog.embeddings.local", "LLMEmbedder")
}

// --- Identifier Sanitization ---

fun String.sanitizeIdentifier(): String =
    replace(Regex("[^a-zA-Z0-9_]"), "_")
        .replace(Regex("^[0-9]"), "_$0")
        .ifEmpty { "node" }

fun String.sanitizeClassName(): String =
    replace(Regex("[^a-zA-Z0-9]"), "")
        .replaceFirstChar { it.uppercase() }
        .ifEmpty { "Generated" }

fun String.sanitizeVarPrefix(): String {
    val cleaned = replace(Regex("[^a-zA-Z0-9]"), "")
    if (cleaned.isEmpty()) return "generated"
    return cleaned.replaceFirstChar { it.lowercase() }
}
