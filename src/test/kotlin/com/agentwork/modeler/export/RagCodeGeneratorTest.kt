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
        embedderModel: String = "text-embedding-ada-002",
        apiKeyVariable: String = "OPENAI_API_KEY",
        storageType: String = "InMemory",
        storagePath: String? = null,
        customStorageClass: String? = null,
        similarityThreshold: Double = 0.7,
        filePaths: List<String> = emptyList(),
        urlSources: List<String> = emptyList(),
        maxDocuments: Int = 5,
        injectAsContext: Boolean = true,
        provideAsTool: Boolean = false,
        toolDescription: String? = null
    ) = KoogRagNode(
        id = "rag-1",
        name = name,
        embedderProvider = embedderProvider,
        embedderModel = embedderModel,
        apiKeyVariable = apiKeyVariable,
        storageType = storageType,
        storagePath = storagePath,
        customStorageClass = customStorageClass,
        similarityThreshold = similarityThreshold,
        filePaths = filePaths,
        urlSources = urlSources,
        maxDocuments = maxDocuments,
        injectAsContext = injectAsContext,
        provideAsTool = provideAsTool,
        toolDescription = toolDescription
    )

    @Test
    fun `generates OpenAI embedder with InMemory storage`() {
        val rag = ragNode(
            name = "ProductDocs",
            embedderProvider = "OpenAI",
            embedderModel = "text-embedding-ada-002",
            apiKeyVariable = "OPENAI_API_KEY",
            storageType = "InMemory",
            filePaths = listOf("./docs/product.txt", "./docs/faq.txt")
        )
        val code = generator.generateSetup(rag)

        assertContains(code, "LLMEmbedder")
        assertContains(code, "OpenAILLMClient")
        assertContains(code, "OPENAI_API_KEY")
        assertContains(code, "text-embedding-ada-002")
        assertContains(code, "JVMTextDocumentEmbedder")
        assertContains(code, "EmbeddingBasedDocumentStorage")
        assertContains(code, "InMemoryVectorStorage()")
        assertContains(code, "productDocsStorage")
        assertContains(code, """store(Path.of("./docs/product.txt"))""")
        assertContains(code, """store(Path.of("./docs/faq.txt"))""")
    }

    @Test
    fun `generates Ollama embedder with File storage`() {
        val rag = ragNode(
            name = "KnowledgeBase",
            embedderProvider = "Ollama",
            embedderModel = "nomic-embed-text",
            storageType = "File",
            storagePath = "./vector-store"
        )
        val code = generator.generateSetup(rag)

        assertContains(code, "OllamaClient()")
        assertContains(code, "nomic-embed-text")
        assertContains(code, "JVMFileVectorStorage")
        assertContains(code, """Path.of("./vector-store")""")
        assertFalse(code.contains("OpenAILLMClient"))
        assertFalse(code.contains("InMemoryVectorStorage"))
    }

    @Test
    fun `generates context injection code`() {
        val rag = ragNode(
            name = "SupportDocs",
            maxDocuments = 3,
            similarityThreshold = 0.8,
            injectAsContext = true
        )
        val code = generator.generateContextInjection(rag)

        assertContains(code, "mostRelevantDocuments")
        assertContains(code, "count = 3")
        assertContains(code, "similarityThreshold = 0.8")
        assertContains(code, """prompt("rag-context-supportDocs")""")
        assertContains(code, "user {")
        assertContains(code, "+\"Relevant context:\"")
        assertContains(code, "supportDocsContext")
        assertContains(code, "supportDocsStorage")
    }

    @Test
    fun `generates tool registration code`() {
        val rag = ragNode(
            name = "ProductDocs",
            maxDocuments = 5,
            similarityThreshold = 0.75,
            provideAsTool = true,
            toolDescription = "Search product documentation"
        )
        val code = generator.generateToolRegistration(rag)

        assertContains(code, "@Tool")
        assertContains(code, "@LLMDescription")
        assertContains(code, "Search product documentation")
        assertContains(code, "suspend fun searchProductDocs(")
        assertContains(code, "query: String")
        assertContains(code, "count: Int")
        assertContains(code, "mostRelevantDocuments")
        assertContains(code, "similarityThreshold = 0.75")
        assertContains(code, "productDocsStorage")
    }

    @Test
    fun `generates imports based on configuration`() {
        val nodes = listOf(
            ragNode(
                embedderProvider = "OpenAI",
                storageType = "File",
                storagePath = "./store",
                provideAsTool = true,
                urlSources = listOf("https://example.com/docs")
            )
        )
        val code = generator.generateImports(nodes)

        assertContains(code, "import ai.koog.rag.base.LLMEmbedder")
        assertContains(code, "import ai.koog.rag.base.files.JVMTextDocumentEmbedder")
        assertContains(code, "import ai.koog.rag.base.storage.EmbeddingBasedDocumentStorage")
        assertContains(code, "import ai.koog.prompt.executor.clients.openai.OpenAILLMClient")
        assertContains(code, "import ai.koog.prompt.executor.model.OpenAIModels")
        assertContains(code, "import ai.koog.rag.base.files.JVMFileVectorStorage")
        assertContains(code, "import ai.koog.agents.core.tools.annotations.Tool")
        assertContains(code, "import ai.koog.agents.core.tools.annotations.LLMDescription")
        assertContains(code, "import ai.koog.rag.base.files.downloadToTempFile")
        assertFalse(code.contains("OllamaClient"))
    }
}
