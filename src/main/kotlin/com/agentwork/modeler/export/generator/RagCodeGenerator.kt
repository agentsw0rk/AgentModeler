package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogRagNode
import org.springframework.stereotype.Component

@Component
class RagCodeGenerator {

    fun sanitizeVarPrefix(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9]"), "")
        if (cleaned.isEmpty()) return "rag"
        return cleaned.replaceFirstChar { it.lowercase() }
    }

    fun sanitizeClassName(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9]"), "")
        if (cleaned.isEmpty()) return "Rag"
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    /**
     * Generates a complete Kotlin file for a RAG node as an object with:
     * - Embedder + storage setup as lazy vals
     * - suspend fun initialize() for document loading
     * - Optional: suspend fun getContext(query) for context injection
     * - Optional: @Tool search function as inner ToolSet class
     */
    fun generateRagFile(rag: KoogRagNode): String {
        val varPrefix = sanitizeVarPrefix(rag.name)
        val className = sanitizeClassName(rag.name) + "Rag"
        val safeName = sanitizeClassName(rag.name)

        return buildString {
            appendLine("package generated.rag")
            appendLine()
            // Imports
            appendLine("import ai.koog.prompt.embedding.LLMEmbedder")
            appendLine("import ai.koog.rag.vectorstore.embedding.JVMTextDocumentEmbedder")
            appendLine("import ai.koog.rag.vectorstore.EmbeddingBasedDocumentStorage")
            appendLine("import ai.koog.rag.vectorstore.InMemoryVectorStorage")
            when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> {
                    appendLine("import ai.koog.prompt.executor.clients.openai.OpenAILLMClient")
                    appendLine("import ai.koog.prompt.executor.model.OpenAIModels")
                }
                "OLLAMA" -> {
                    appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaClient")
                    appendLine("import ai.koog.prompt.executor.model.OllamaModels")
                }
            }
            if (rag.storageType.uppercase() == "FILE") {
                appendLine("import ai.koog.rag.vectorstore.JVMFileVectorStorage")
            }
            appendLine("import java.nio.file.Path")
            if (rag.provideAsTool) {
                appendLine("import ai.koog.agents.core.tools.annotations.Tool")
                appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
                appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            }
            appendLine()

            // Object
            appendLine("object $className {")
            appendLine()

            // Embedder
            val clientExpr = when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> """OpenAILLMClient(System.getenv("${rag.apiKeyVariable}"))"""
                "OLLAMA" -> "OllamaClient()"
                else -> """OpenAILLMClient(System.getenv("${rag.apiKeyVariable}"))"""
            }
            val modelRef = when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> "OpenAIModels.Embeddings.${rag.embedderModel}"
                "OLLAMA" -> "OllamaModels.Embeddings.${rag.embedderModel}"
                else -> "\"${rag.embedderModel}\""
            }
            appendLine("    private val embedder = LLMEmbedder($clientExpr, $modelRef)")
            appendLine("    private val documentEmbedder = JVMTextDocumentEmbedder(embedder)")

            // Storage
            val storageExpr = when (rag.storageType.uppercase()) {
                "INMEMORY" -> "InMemoryVectorStorage()"
                "FILE" -> {
                    val path = rag.storagePath ?: "./rag-storage"
                    """JVMFileVectorStorage(root = Path.of("$path"))"""
                }
                "CUSTOM" -> {
                    val cls = rag.customStorageClass ?: "InMemoryVectorStorage"
                    "$cls()"
                }
                else -> "InMemoryVectorStorage()"
            }
            appendLine("    val storage = EmbeddingBasedDocumentStorage(documentEmbedder, $storageExpr)")
            appendLine()

            // Initialize function
            appendLine("    suspend fun initialize() {")
            for (filePath in rag.filePaths) {
                appendLine("""        storage.store(Path.of("$filePath"))""")
            }
            for (url in rag.urlSources) {
                appendLine("""        // TODO: Download and store URL: $url""")
            }
            appendLine("    }")

            // Context injection helper
            if (rag.injectAsContext) {
                appendLine()
                appendLine("    suspend fun getRelevantContext(query: String): List<Path> {")
                appendLine("        return storage.mostRelevantDocuments(query, count = ${rag.maxDocuments}, similarityThreshold = ${rag.similarityThreshold}).toList()")
                appendLine("    }")
            }

            appendLine("}")

            // Search Tool as ToolSet class
            if (rag.provideAsTool) {
                val toolDesc = rag.toolDescription?.replace("\"", "\\\"")
                    ?: "Search for relevant documents in ${rag.name}"
                appendLine()
                appendLine("""@LLMDescription("$toolDesc")""")
                appendLine("class ${safeName}SearchTools(private val rag: $className = $className) : ToolSet {")
                appendLine()
                appendLine("    @Tool")
                appendLine("""    @LLMDescription("$toolDesc")""")
                appendLine("    suspend fun search$safeName(")
                appendLine("""        @LLMDescription("Query to search relevant documents about") query: String,""")
                appendLine("""        @LLMDescription("Maximum number of documents") count: Int""")
                appendLine("    ): String {")
                appendLine("        val docs = rag.storage.mostRelevantDocuments(query, count = count, similarityThreshold = ${rag.similarityThreshold}).toList()")
                appendLine("        if (docs.isEmpty()) return \"No relevant documents found for: \$query\"")
                appendLine("        return buildString {")
                appendLine("            docs.forEachIndexed { i, doc ->")
                appendLine("                append(\"Document \${i + 1}: \${doc.fileName}\\n\")")
                appendLine("                append(\"Content: \${java.nio.file.Files.readString(doc)}\\n\\n\")")
                appendLine("            }")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            }
        }
    }
}
