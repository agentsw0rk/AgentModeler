package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogRagNode
import org.springframework.stereotype.Component

@Component
class RagCodeGenerator {

    private fun sanitizeVarPrefix(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9]"), "")
        if (cleaned.isEmpty()) return "rag"
        return cleaned.replaceFirstChar { it.lowercase() }
    }

    fun generateSetup(rag: KoogRagNode): String {
        val varPrefix = sanitizeVarPrefix(rag.name)
        return buildString {
            // Embedder client
            val clientExpr = when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> """OpenAILLMClient(System.getenv("${rag.apiKeyVariable}"))"""
                "OLLAMA" -> "OllamaClient()"
                else -> """OpenAILLMClient(System.getenv("${rag.apiKeyVariable}"))"""
            }
            appendLine("val ${varPrefix}Embedder = LLMEmbedder($clientExpr, \"${rag.embedderModel}\")")

            // Document embedder
            appendLine("val ${varPrefix}DocumentEmbedder = JVMTextDocumentEmbedder(${varPrefix}Embedder)")

            // Storage
            val storageExpr = when (rag.storageType.uppercase()) {
                "INMEMORY" -> "InMemoryVectorStorage()"
                "FILE" -> {
                    val path = rag.storagePath ?: "./rag-storage"
                    """JVMFileVectorStorage(root = Path.of("$path"))"""
                }
                "CUSTOM" -> {
                    val cls = rag.customStorageClass ?: "CustomVectorStorage"
                    "$cls()"
                }
                else -> "InMemoryVectorStorage()"
            }
            appendLine("val ${varPrefix}Storage = EmbeddingBasedDocumentStorage(${varPrefix}DocumentEmbedder, $storageExpr)")

            // Store documents
            for (filePath in rag.filePaths) {
                appendLine("""${varPrefix}Storage.store(Path.of("$filePath"))""")
            }
            for (url in rag.urlSources) {
                appendLine("""${varPrefix}Storage.store(downloadToTempFile("$url"))""")
            }
        }.trimEnd()
    }

    fun generateContextInjection(rag: KoogRagNode): String {
        val varPrefix = sanitizeVarPrefix(rag.name)
        return buildString {
            appendLine("val ${varPrefix}Docs = ${varPrefix}Storage.mostRelevantDocuments(query, count = ${rag.maxDocuments}, similarityThreshold = ${rag.similarityThreshold})")
            appendLine("""val ${varPrefix}ContextPrompt = prompt("rag-context-$varPrefix") {""")
            appendLine("    user {")
            appendLine("        ${varPrefix}Docs.forEach { file(it.pathString, \"text/plain\") }")
            appendLine("    }")
            append("}")
        }
    }

    fun generateToolRegistration(rag: KoogRagNode): String {
        val varPrefix = sanitizeVarPrefix(rag.name)
        val safeName = rag.name.replace(Regex("[^a-zA-Z0-9]"), "").replaceFirstChar { it.uppercase() }
        val funcName = "search$safeName"
        val toolDesc = rag.toolDescription?.replace("\"", "\\\"")
            ?: "Search for relevant documents in ${rag.name}"
        return buildString {
            appendLine("@Tool")
            appendLine("""@LLMDescription("$toolDesc")""")
            appendLine("suspend fun $funcName(")
            appendLine("""    @LLMDescription("Query to search relevant documents about")""")
            appendLine("    query: String,")
            appendLine("""    @LLMDescription("Maximum number of documents")""")
            appendLine("    count: Int")
            appendLine("): String {")
            appendLine("    val relevantDocuments = ${varPrefix}Storage.mostRelevantDocuments(query, count = count, similarityThreshold = ${rag.similarityThreshold}).toList()")
            appendLine("    if (relevantDocuments.isEmpty()) return \"No relevant documents found for the query: \$query\"")
            appendLine("    val result = StringBuilder(\"Found \${relevantDocuments.size} relevant documents:\\n\\n\")")
            appendLine("    relevantDocuments.forEachIndexed { index, document ->")
            appendLine("        val content = java.nio.file.Files.readString(document)")
            appendLine("        result.append(\"Document \${index + 1}: \${document.fileName}\\n\")")
            appendLine("        result.append(\"Content: \$content\\n\\n\")")
            appendLine("    }")
            append("    return result.toString()")
            appendLine()
            append("}")
        }
    }

    fun generateImports(ragNodes: List<KoogRagNode>): String {
        if (ragNodes.isEmpty()) return ""

        val hasOpenAI = ragNodes.any { it.embedderProvider.uppercase() == "OPENAI" }
        val hasOllama = ragNodes.any { it.embedderProvider.uppercase() == "OLLAMA" }
        val hasFile = ragNodes.any { it.storageType.uppercase() == "FILE" }
        val hasTool = ragNodes.any { it.provideAsTool }
        val hasUrls = ragNodes.any { it.urlSources.isNotEmpty() }

        return buildString {
            appendLine("import ai.koog.rag.base.LLMEmbedder")
            appendLine("import ai.koog.rag.base.files.JVMTextDocumentEmbedder")
            appendLine("import ai.koog.rag.base.storage.EmbeddingBasedDocumentStorage")
            appendLine("import ai.koog.rag.base.storage.InMemoryVectorStorage")
            if (hasOpenAI) {
                appendLine("import ai.koog.prompt.executor.clients.openai.OpenAILLMClient")
            }
            if (hasOllama) {
                appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaClient")
            }
            val needsPath = hasFile || ragNodes.any { it.filePaths.isNotEmpty() }
            if (hasFile) {
                appendLine("import ai.koog.rag.base.files.JVMFileVectorStorage")
            }
            if (needsPath) {
                appendLine("import java.nio.file.Path")
            }
            if (hasTool) {
                appendLine("import ai.koog.agents.core.tools.annotations.Tool")
                appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            }
            if (hasUrls) {
                appendLine("import ai.koog.rag.base.files.downloadToTempFile")
            }
        }.trimEnd()
    }
}
