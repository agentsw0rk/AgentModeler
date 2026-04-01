package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogRagNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.springframework.stereotype.Component

@Component
class RagCodeGenerator {

    // --- Shared type aliases ---
    private val STRING = ClassName("kotlin", "String")
    private val INT = ClassName("kotlin", "Int")
    private val DOUBLE = ClassName("kotlin", "Double")
    private val LIST = ClassName("kotlin.collections", "List")

    // --- JVM type references ---
    private val jvmTextDocEmbedder = ClassName("ai.koog.rag.vector", "JVMTextDocumentEmbedder")
    private val embeddingBasedStorage = ClassName("ai.koog.rag.vector", "EmbeddingBasedDocumentStorage")
    private val inMemoryVectorStorage = ClassName("ai.koog.rag.vector", "InMemoryVectorStorage")
    private val jvmFileVectorStorage = ClassName("ai.koog.rag.vector", "JVMFileVectorStorage")
    private val pathClass = ClassName("java.nio.file", "Path")
    private val filesClass = ClassName("java.nio.file", "Files")

    // --- Wasm type references ---
    private val embedderInterface = ClassName("ai.koog.embeddings.base", "Embedder")
    private val vectorClass = ClassName("ai.koog.embeddings.base", "Vector")
    private val documentEmbedderInterface = ClassName("ai.koog.rag.vector", "DocumentEmbedder")
    private val inMemoryDocEmbeddingStorage = ClassName("ai.koog.rag.vector", "InMemoryDocumentEmbeddingStorage")

    /**
     * Generates a complete Kotlin file for a RAG node as an object with:
     * - Embedder + storage setup as lazy vals
     * - suspend fun initialize() for document loading
     * - Optional: suspend fun getContext(query) for context injection
     * - Optional: @Tool search function as inner ToolSet class
     */
    fun generateRagFile(rag: KoogRagNode): String {
        val className = rag.name.sanitizeClassName() + "Rag"
        val safeName = rag.name.sanitizeClassName()

        val clientClass = KoogModels.clientClassName(rag.embedderProvider)
        val modelsClass = KoogModels.modelsClassName(rag.embedderProvider)

        val fileBuilder = FileSpec.builder("generated.rag", className)

        // --- Build the object ---
        val objectBuilder = TypeSpec.objectBuilder(className)

        // Embedder property
        val clientInitializer = when (rag.embedderProvider.uppercase()) {
            "OLLAMA" -> CodeBlock.of("%T()", clientClass)
            else -> CodeBlock.of("%T(System.getenv(%S))", clientClass, rag.apiKeyVariable)
        }
        val modelRef = CodeBlock.of("%T.Embeddings.%L", modelsClass, rag.embedderModel)

        objectBuilder.addProperty(
            PropertySpec.builder("embedder", KoogTypes.LLM_EMBEDDER)
                .addModifiers(KModifier.PRIVATE)
                .initializer("%T(%L, %L)", KoogTypes.LLM_EMBEDDER, clientInitializer, modelRef)
                .build()
        )

        // Document embedder
        objectBuilder.addProperty(
            PropertySpec.builder("documentEmbedder", jvmTextDocEmbedder)
                .addModifiers(KModifier.PRIVATE)
                .initializer("%T(embedder)", jvmTextDocEmbedder)
                .build()
        )

        // Storage
        val storageInitializer = when (rag.storageType.uppercase()) {
            "FILE" -> {
                val path = rag.storagePath ?: "./rag-storage"
                CodeBlock.of(
                    "%T(documentEmbedder, %T(root = %T.of(%S)))",
                    embeddingBasedStorage, jvmFileVectorStorage, pathClass, path
                )
            }
            "CUSTOM" -> {
                val cls = rag.customStorageClass ?: "InMemoryVectorStorage"
                CodeBlock.of(
                    "%T(documentEmbedder, %L())",
                    embeddingBasedStorage, cls
                )
            }
            else -> CodeBlock.of(
                "%T(documentEmbedder, %T())",
                embeddingBasedStorage, inMemoryVectorStorage
            )
        }
        val storageType = embeddingBasedStorage.parameterizedBy(pathClass)
        objectBuilder.addProperty(
            PropertySpec.builder("storage", storageType)
                .initializer(storageInitializer)
                .build()
        )

        // initialize()
        val initFun = FunSpec.builder("initialize")
            .addModifiers(KModifier.SUSPEND)
        for (filePath in rag.filePaths) {
            initFun.addStatement("storage.store(%T.of(%S))", pathClass, filePath)
        }
        for (url in rag.urlSources) {
            initFun.addComment("TODO: Download and store URL: %L", url)
        }
        objectBuilder.addFunction(initFun.build())

        // getRelevantContext()
        if (rag.injectAsContext) {
            objectBuilder.addFunction(
                FunSpec.builder("getRelevantContext")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("query", String::class)
                    .returns(LIST.parameterizedBy(pathClass))
                    .addStatement(
                        "return storage.mostRelevantDocuments(query, count = %L, similarityThreshold = %L).toList()",
                        rag.maxDocuments, rag.similarityThreshold
                    )
                    .build()
            )
        }

        fileBuilder.addType(objectBuilder.build())

        // --- Optional ToolSet class ---
        if (rag.provideAsTool) {
            val toolDesc = rag.toolDescription ?: "Search for relevant documents in ${rag.name}"

            val ragTypeName = ClassName("generated.rag", className)
            val toolClassBuilder = TypeSpec.classBuilder("${safeName}SearchTools")
                .addAnnotation(KoogAnnotations.llmDescription(toolDesc))
                .addSuperinterface(KoogTypes.TOOL_SET)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter(
                            ParameterSpec.builder("rag", ragTypeName)
                                .defaultValue("%L", className)
                                .build()
                        )
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("rag", ragTypeName)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("rag")
                        .build()
                )

            val searchFun = FunSpec.builder("search$safeName")
                .addModifiers(KModifier.SUSPEND)
                .addAnnotation(KoogAnnotations.tool())
                .addAnnotation(KoogAnnotations.llmDescription(toolDesc))
                .addParameter(
                    ParameterSpec.builder("query", String::class)
                        .addAnnotation(KoogAnnotations.llmDescription("Query to search relevant documents about"))
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("count", Int::class)
                        .addAnnotation(KoogAnnotations.llmDescription("Maximum number of documents"))
                        .build()
                )
                .returns(String::class)
                .addStatement(
                    "val docs = rag.storage.mostRelevantDocuments(query, count = count, similarityThreshold = %L).toList()",
                    rag.similarityThreshold
                )
                .addStatement("if (docs.isEmpty()) return %S", "No relevant documents found for: \$query")
                .beginControlFlow("return buildString")
                .beginControlFlow("docs.forEachIndexed { i, doc ->")
                .addStatement("append(%S)", "Document \${i + 1}: \${doc.fileName}\n")
                .addStatement("append(%S)", "Content: \${%T.readString(doc)}\n\n")
                .endControlFlow()
                .endControlFlow()
                .build()

            toolClassBuilder.addFunction(searchFun)
            fileBuilder.addType(toolClassBuilder.build())
        }

        // Add import for mostRelevantDocuments extension function
        fileBuilder.addImport("ai.koog.rag.base", "mostRelevantDocuments")

        return fileBuilder.build().toString()
    }

    /**
     * Generates a WasmJS-compatible RAG file. Key differences from JVM:
     * - No java.nio.file.Path — uses String paths
     * - No System.getenv — uses js() interop or hardcoded env var name
     * - No JVMTextDocumentEmbedder — uses custom StringDocumentEmbedder
     * - No JVMFileVectorStorage — only InMemory or Custom
     * - Search tool as SimpleTool instead of ToolSet (no reflection on Wasm)
     */
    fun generateWasmRagFile(rag: KoogRagNode): String {
        val className = rag.name.sanitizeClassName() + "Rag"
        val safeName = rag.name.sanitizeClassName()
        val varPrefix = rag.name.sanitizeVarPrefix()

        val clientClass = KoogModels.clientClassName(rag.embedderProvider)
        val modelsClass = KoogModels.modelsClassName(rag.embedderProvider)

        val fileBuilder = FileSpec.builder("generated.rag", className)

        // getEnv top-level function
        fileBuilder.addFunction(
            FunSpec.builder("getEnv")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("key", String::class)
                .returns(String::class)
                .addStatement("return js(%S)", "process.env[key] || ''")
                .build()
        )

        // StringDocumentEmbedder private class
        val stringDocEmbedder = TypeSpec.classBuilder("StringDocumentEmbedder")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("embedder", embedderInterface)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("embedder", embedderInterface)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("embedder")
                    .build()
            )
            .addSuperinterface(documentEmbedderInterface.parameterizedBy(STRING))
            .addFunction(
                FunSpec.builder("embed")
                    .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                    .addParameter("document", String::class)
                    .returns(vectorClass)
                    .addStatement("return embedder.embed(document)")
                    .build()
            )
            .addFunction(
                FunSpec.builder("diff")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("embedding1", vectorClass)
                    .addParameter("embedding2", vectorClass)
                    .returns(Double::class)
                    .addStatement("return embedder.diff(embedding1, embedding2)")
                    .build()
            )
            .build()
        fileBuilder.addType(stringDocEmbedder)

        // --- Build the object ---
        val objectBuilder = TypeSpec.objectBuilder(className)

        // Embedder
        val clientInitializer = when (rag.embedderProvider.uppercase()) {
            "OLLAMA" -> CodeBlock.of("%T()", clientClass)
            else -> CodeBlock.of("%T(getEnv(%S))", clientClass, rag.apiKeyVariable)
        }
        val modelRef = CodeBlock.of("%T.Embeddings.%L", modelsClass, rag.embedderModel)

        objectBuilder.addProperty(
            PropertySpec.builder("embedder", KoogTypes.LLM_EMBEDDER)
                .addModifiers(KModifier.PRIVATE)
                .initializer("%T(%L, %L)", KoogTypes.LLM_EMBEDDER, clientInitializer, modelRef)
                .build()
        )

        // Document embedder
        val stringDocEmbedderClassName = ClassName("generated.rag", "StringDocumentEmbedder")
        objectBuilder.addProperty(
            PropertySpec.builder("documentEmbedder", stringDocEmbedderClassName)
                .addModifiers(KModifier.PRIVATE)
                .initializer("%T(embedder)", stringDocEmbedderClassName)
                .build()
        )

        // Storage
        objectBuilder.addProperty(
            PropertySpec.builder("storage", inMemoryDocEmbeddingStorage.parameterizedBy(STRING))
                .initializer("%T(documentEmbedder)", inMemoryDocEmbeddingStorage)
                .build()
        )

        // initialize()
        val initFun = FunSpec.builder("initialize")
            .addModifiers(KModifier.SUSPEND)
            .addComment("Note: File system access is limited on WasmJS.")
            .addComment("Documents should be loaded via HTTP or embedded at build time.")
        for (url in rag.urlSources) {
            initFun.addComment("TODO: Fetch and store document from: %L", url)
        }
        for (filePath in rag.filePaths) {
            initFun.addComment("TODO: Load document: %L", filePath)
        }
        objectBuilder.addFunction(initFun.build())

        // getRelevantContext()
        if (rag.injectAsContext) {
            objectBuilder.addFunction(
                FunSpec.builder("getRelevantContext")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("query", String::class)
                    .returns(LIST.parameterizedBy(STRING))
                    .addStatement(
                        "return storage.mostRelevantDocuments(query, count = %L, similarityThreshold = %L).toList().map { it.toString() }",
                        rag.maxDocuments, rag.similarityThreshold
                    )
                    .build()
            )
        }

        fileBuilder.addType(objectBuilder.build())

        // --- Optional SimpleTool ---
        if (rag.provideAsTool) {
            val toolDesc = rag.toolDescription ?: "Search for relevant documents in ${rag.name}"

            // @Serializable data class
            val argsClassName = ClassName("generated.rag", "Search${safeName}Args")
            val argsClass = TypeSpec.classBuilder("Search${safeName}Args")
                .addModifiers(KModifier.DATA)
                .addAnnotation(KoogAnnotations.serializable())
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("query", String::class)
                        .addParameter(
                            ParameterSpec.builder("count", Int::class)
                                .defaultValue("%L", rag.maxDocuments)
                                .build()
                        )
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("query", String::class)
                        .initializer("query")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("count", Int::class)
                        .initializer("count")
                        .build()
                )
                .build()
            fileBuilder.addType(argsClass)

            // val search${Name}Tool = object : SimpleTool<Args>
            val simpleToolType = KoogTypes.SIMPLE_TOOL.parameterizedBy(argsClassName)
            val toolProp = PropertySpec.builder("search${safeName}Tool", simpleToolType)
                .initializer(
                    CodeBlock.builder()
                        .beginControlFlow(
                            "object : %T<%T>(%T.serializer(), %S, %S)",
                            KoogTypes.SIMPLE_TOOL,
                            argsClassName,
                            argsClassName,
                            "search$safeName",
                            toolDesc
                        )
                        .beginControlFlow("override suspend fun execute(args: %T): String", argsClassName)
                        .addStatement(
                            "val docs = %L.storage.mostRelevantDocuments(args.query, count = args.count, similarityThreshold = %L).toList()",
                            className, rag.similarityThreshold
                        )
                        .addStatement("if (docs.isEmpty()) return %S", "No relevant documents found for: \${args.query}")
                        .addStatement(
                            "return docs.mapIndexed { i, doc -> %S }.joinToString(%S)",
                            "Document \${i + 1}: \$doc",
                            "\\n"
                        )
                        .endControlFlow()
                        .endControlFlow()
                        .build()
                )
                .build()
            fileBuilder.addProperty(toolProp)

            // val ${varPrefix}RagTools = listOf(...)
            fileBuilder.addProperty(
                PropertySpec.builder("${varPrefix}RagTools", LIST.parameterizedBy(simpleToolType))
                    .initializer("listOf(search${safeName}Tool)")
                    .build()
            )
        }

        // Add import for mostRelevantDocuments extension function
        fileBuilder.addImport("ai.koog.rag.base", "mostRelevantDocuments")

        return fileBuilder.build().toString()
    }
}
