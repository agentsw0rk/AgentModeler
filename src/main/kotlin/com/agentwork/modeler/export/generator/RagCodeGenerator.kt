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
            appendLine("import ai.koog.embeddings.local.LLMEmbedder")
            appendLine("import ai.koog.rag.vector.JVMTextDocumentEmbedder")
            appendLine("import ai.koog.rag.vector.EmbeddingBasedDocumentStorage")
            appendLine("import ai.koog.rag.vector.InMemoryVectorStorage")
            appendLine("import ai.koog.rag.base.mostRelevantDocuments")
            when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> {
                    appendLine("import ai.koog.prompt.executor.clients.openai.OpenAILLMClient")
                    appendLine("import ai.koog.prompt.executor.clients.openai.OpenAIModels")
                }
                "OLLAMA" -> {
                    appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaClient")
                    appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaModels")
                }
            }
            if (rag.storageType.uppercase() == "FILE") {
                appendLine("import ai.koog.rag.vector.JVMFileVectorStorage")
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

    /**
     * Generates a WasmJS-compatible RAG file. Key differences from JVM:
     * - No java.nio.file.Path — uses String paths
     * - No System.getenv — uses js() interop or hardcoded env var name
     * - No JVMTextDocumentEmbedder — uses TextDocumentEmbedder
     * - No JVMFileVectorStorage — only InMemory or Custom
     * - Search tool as SimpleTool instead of ToolSet (no reflection on Wasm)
     */
    fun generateWasmRagFile(rag: KoogRagNode): String {
        val className = sanitizeClassName(rag.name) + "Rag"
        val safeName = sanitizeClassName(rag.name)

        return buildString {
            appendLine("package generated.rag")
            appendLine()
            appendLine("import ai.koog.embeddings.base.Embedder")
            appendLine("import ai.koog.embeddings.base.Vector")
            appendLine("import ai.koog.embeddings.local.LLMEmbedder")
            appendLine("import ai.koog.rag.vector.DocumentEmbedder")
            appendLine("import ai.koog.rag.vector.InMemoryDocumentEmbeddingStorage")
            appendLine("import ai.koog.rag.base.mostRelevantDocuments")
            when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> {
                    appendLine("import ai.koog.prompt.executor.clients.openai.OpenAILLMClient")
                    appendLine("import ai.koog.prompt.executor.clients.openai.OpenAIModels")
                }
                "OLLAMA" -> {
                    appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaClient")
                    appendLine("import ai.koog.prompt.executor.clients.ollama.OllamaModels")
                }
            }
            if (rag.provideAsTool) {
                appendLine("import ai.koog.agents.core.tools.SimpleTool")
                appendLine("import kotlinx.serialization.Serializable")
            }
            appendLine()

            // Env var helper for WasmJS
            appendLine("private fun getEnv(key: String): String = js(\"process.env[key] || ''\")")
            appendLine()

            // DocumentEmbedder adapter for String documents
            appendLine("private class StringDocumentEmbedder(private val embedder: Embedder) : DocumentEmbedder<String> {")
            appendLine("    override suspend fun embed(document: String): Vector = embedder.embed(document)")
            appendLine("    override fun diff(embedding1: Vector, embedding2: Vector): Double = embedder.diff(embedding1, embedding2)")
            appendLine("}")
            appendLine()

            // Object
            appendLine("object $className {")
            appendLine()

            val clientExpr = when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> """OpenAILLMClient(getEnv("${rag.apiKeyVariable}"))"""
                "OLLAMA" -> "OllamaClient()"
                else -> """OpenAILLMClient(getEnv("${rag.apiKeyVariable}"))"""
            }
            val modelRef = when (rag.embedderProvider.uppercase()) {
                "OPENAI" -> "OpenAIModels.Embeddings.${rag.embedderModel}"
                "OLLAMA" -> "OllamaModels.Embeddings.${rag.embedderModel}"
                else -> "\"${rag.embedderModel}\""
            }
            appendLine("    private val embedder = LLMEmbedder($clientExpr, $modelRef)")
            appendLine("    private val documentEmbedder = StringDocumentEmbedder(embedder)")
            appendLine("    val storage = InMemoryDocumentEmbeddingStorage<String>(documentEmbedder)")
            appendLine()

            // Initialize — no file system access on Wasm, document paths are placeholders
            appendLine("    suspend fun initialize() {")
            appendLine("        // Note: File system access is limited on WasmJS.")
            appendLine("        // Documents should be loaded via HTTP or embedded at build time.")
            for (url in rag.urlSources) {
                appendLine("""        // TODO: Fetch and store document from: $url""")
            }
            for (filePath in rag.filePaths) {
                appendLine("""        // TODO: Load document: $filePath""")
            }
            appendLine("    }")

            // Context injection helper
            if (rag.injectAsContext) {
                appendLine()
                appendLine("    suspend fun getRelevantContext(query: String): List<String> {")
                appendLine("        return storage.mostRelevantDocuments(query, count = ${rag.maxDocuments}, similarityThreshold = ${rag.similarityThreshold}).toList().map { it.toString() }")
                appendLine("    }")
            }

            appendLine("}")

            // Search Tool as SimpleTool (Wasm — no reflection)
            if (rag.provideAsTool) {
                val toolDesc = rag.toolDescription?.replace("\"", "\\\"")
                    ?: "Search for relevant documents in ${rag.name}"
                appendLine()
                appendLine("@Serializable")
                appendLine("data class Search${safeName}Args(val query: String, val count: Int = ${rag.maxDocuments})")
                appendLine()
                appendLine("val search${safeName}Tool = object : SimpleTool<Search${safeName}Args>(")
                appendLine("    argsSerializer = Search${safeName}Args.serializer(),")
                appendLine("    name = \"search$safeName\",")
                appendLine("    description = \"$toolDesc\"")
                appendLine(") {")
                appendLine("    override suspend fun execute(args: Search${safeName}Args): String {")
                appendLine("        val docs = $className.storage.mostRelevantDocuments(args.query, count = args.count, similarityThreshold = ${rag.similarityThreshold}).toList()")
                appendLine("        if (docs.isEmpty()) return \"No relevant documents found for: \${args.query}\"")
                appendLine("        return docs.mapIndexed { i, doc -> \"Document \${i + 1}: \$doc\" }.joinToString(\"\\n\")")
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("val ${sanitizeVarPrefix(rag.name)}RagTools = listOf(search${safeName}Tool)")
            }
        }
    }
}
