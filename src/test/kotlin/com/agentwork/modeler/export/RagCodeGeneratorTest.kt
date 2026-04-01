package com.agentwork.modeler.export

import com.agentwork.modeler.export.generator.RagCodeGenerator
import com.agentwork.modeler.export.model.KoogRagNode
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RagCodeGeneratorTest {
    private val generator = RagCodeGenerator()

    private fun ragNode(
        name: String = "ProductDocs",
        embedderProvider: String = "OpenAI",
        embedderModel: String = "TextEmbeddingAda3Large",
        apiKeyVariable: String = "OPENAI_API_KEY",
        storageType: String = "InMemory",
        storagePath: String? = null,
        customStorageClass: String? = null,
        similarityThreshold: Double = 0.7,
        filePaths: List<String> = emptyList(),
        urlSources: List<String> = emptyList(),
        maxDocuments: Int = 5,
        injectAsContext: Boolean = false,
        provideAsTool: Boolean = false,
        toolDescription: String? = null
    ) = KoogRagNode(
        id = "rag-1", name = name,
        embedderProvider = embedderProvider, embedderModel = embedderModel,
        apiKeyVariable = apiKeyVariable,
        storageType = storageType, storagePath = storagePath,
        customStorageClass = customStorageClass,
        similarityThreshold = similarityThreshold,
        filePaths = filePaths, urlSources = urlSources,
        maxDocuments = maxDocuments,
        injectAsContext = injectAsContext, provideAsTool = provideAsTool,
        toolDescription = toolDescription
    )

    @Test
    fun `generates object with OpenAI embedder and InMemory storage`() {
        val code = generator.generateRagFile(ragNode(
            filePaths = listOf("./docs/products", "./docs/faq.txt")
        ))

        assertContains(code, "object ProductDocsRag")
        assertContains(code, "OpenAILLMClient(System.getenv(\"OPENAI_API_KEY\"))")
        assertContains(code, "OpenAIModels.Embeddings.TextEmbeddingAda3Large")
        assertContains(code, "JVMTextDocumentEmbedder(embedder)")
        assertContains(code, "InMemoryVectorStorage()")
        assertContains(code, "suspend fun initialize()")
        assertContains(code, "storage.store(Path.of(\"./docs/products\"))")
        assertContains(code, "storage.store(Path.of(\"./docs/faq.txt\"))")
    }

    @Test
    fun `generates object with Ollama embedder and File storage`() {
        val code = generator.generateRagFile(ragNode(
            name = "FaqDocs",
            embedderProvider = "Ollama", embedderModel = "NOMIC_EMBED_TEXT",
            storageType = "File", storagePath = "./vector-db",
            filePaths = listOf("./faq.txt")
        ))

        assertContains(code, "object FaqDocsRag")
        assertContains(code, "OllamaClient()")
        assertContains(code, "OllamaModels.Embeddings.NOMIC_EMBED_TEXT")
        assertContains(code, "JVMFileVectorStorage(root = Path.of(\"./vector-db\"))")
    }

    @Test
    fun `generates context injection helper when enabled`() {
        val code = generator.generateRagFile(ragNode(
            injectAsContext = true, maxDocuments = 3, similarityThreshold = 0.8
        ))

        assertContains(code, "suspend fun getRelevantContext(query: String): List<Path>")
        assertContains(code, "mostRelevantDocuments(query, count = 3, similarityThreshold = 0.8)")
    }

    @Test
    fun `does not generate context helper when disabled`() {
        val code = generator.generateRagFile(ragNode(injectAsContext = false))
        assertFalse(code.contains("getRelevantContext"))
    }

    @Test
    fun `generates ToolSet class when provideAsTool is enabled`() {
        val code = generator.generateRagFile(ragNode(
            provideAsTool = true,
            toolDescription = "Search product docs"
        ))

        assertContains(code, "class ProductDocsSearchTools")
        assertContains(code, ": ToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "@LLMDescription(\"Search product docs\")")
        assertContains(code, "suspend fun searchProductDocs(")
    }

    @Test
    fun `does not generate ToolSet when provideAsTool is disabled`() {
        val code = generator.generateRagFile(ragNode(provideAsTool = false))
        assertFalse(code.contains("@Tool"))
        assertFalse(code.contains("SearchTools"))
    }
}
