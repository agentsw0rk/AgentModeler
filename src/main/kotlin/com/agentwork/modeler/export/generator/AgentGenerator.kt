package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.springframework.stereotype.Component

@Component
class AgentGenerator {

    fun generate(agent: KoogAgent, strategyCall: String? = null, inputClassName: String? = null, outputClassName: String? = null): String {
        val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
        val effectivePrompt = if (hasThink) {
            agent.systemPrompt + "\nThink step by step before answering."
        } else {
            agent.systemPrompt
        }
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"

        val inputTypeName = if (inputClassName != null) ClassName("generated", inputClassName) else ClassName("kotlin", "String")
        val outputTypeName = if (outputClassName != null) ClassName("generated", outputClassName) else ClassName("kotlin", "String")
        val agentReturnType = KoogTypes.AI_AGENT.parameterizedBy(inputTypeName, outputTypeName)

        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val hasMcp = agent.mcpServers.isNotEmpty()
        val hasOtel = agent.otelConfig?.enabled == true

        // --- Spring annotations ---
        val configAnnotation = AnnotationSpec.builder(ClassName("org.springframework.context.annotation", "Configuration")).build()
        val beanAnnotation = AnnotationSpec.builder(ClassName("org.springframework.context.annotation", "Bean")).build()
        val primaryAnnotation = AnnotationSpec.builder(ClassName("org.springframework.context.annotation", "Primary")).build()
        val postConstructAnnotation = AnnotationSpec.builder(ClassName("jakarta.annotation", "PostConstruct")).build()
        val valueClass = ClassName("org.springframework.beans.factory.annotation", "Value")

        // --- Build the class ---
        val classBuilder = TypeSpec.classBuilder("AgentConfig")
            .addAnnotation(configAnnotation)

        // Constructor parameters as properties
        val providerKey = agent.modelProvider.name.lowercase()
        classBuilder.primaryConstructor(buildConstructor(agent, valueClass, providerKey, hasOtel))

        val isBedrock = agent.modelProvider == ModelProvider.BEDROCK
        if (isBedrock) {
            classBuilder.addProperty(
                PropertySpec.builder("accessKey", String::class)
                    .initializer("accessKey")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            classBuilder.addProperty(
                PropertySpec.builder("secretKey", String::class)
                    .initializer("secretKey")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
        } else {
            classBuilder.addProperty(
                PropertySpec.builder("apiKey", String::class)
                    .initializer("apiKey")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
        }

        // OTel properties
        if (hasOtel) {
            if (agent.otelConfig?.exporterType != "logging") {
                classBuilder.addProperty(PropertySpec.builder("otelEndpoint", String::class).initializer("otelEndpoint").addModifiers(KModifier.PRIVATE).build())
            }
            classBuilder.addProperty(PropertySpec.builder("otelServiceName", String::class).initializer("otelServiceName").addModifiers(KModifier.PRIVATE).build())
            classBuilder.addProperty(PropertySpec.builder("otelServiceVersion", String::class).initializer("otelServiceVersion").addModifiers(KModifier.PRIVATE).build())
            if (agent.otelConfig?.authorizationHeader != null) {
                classBuilder.addProperty(PropertySpec.builder("otelAuthHeader", String::class).initializer("otelAuthHeader").addModifiers(KModifier.PRIVATE).build())
            }
            classBuilder.addProperty(PropertySpec.builder("otelVerbose", Boolean::class).initializer("otelVerbose").addModifiers(KModifier.PRIVATE).build())
        }

        // @PostConstruct validate credentials
        if (isBedrock) {
            classBuilder.addFunction(
                FunSpec.builder("validateCredentials")
                    .addAnnotation(postConstructAnnotation)
                    .addStatement("require(accessKey.isNotBlank()) { %S }", "AWS_BEDROCK_ACCESS_KEY environment variable must be set")
                    .addStatement("require(secretKey.isNotBlank()) { %S }", "AWS_BEDROCK_SECRET_ACCESS_KEY environment variable must be set")
                    .build()
            )
        } else {
            classBuilder.addFunction(
                FunSpec.builder("validateApiKey")
                    .addAnnotation(postConstructAnnotation)
                    .addStatement("require(apiKey.isNotBlank()) { %S }", "${agent.modelProvider.name}_API_KEY environment variable must be set")
                    .build()
            )
        }

        // @PostConstruct initializeRag (if RAG nodes exist)
        if (agent.ragNodes.isNotEmpty()) {
            val runBlockingType = ClassName("kotlinx.coroutines", "runBlocking")
            val initRagBuilder = FunSpec.builder("initializeRag")
                .addAnnotation(postConstructAnnotation)
                .beginControlFlow("%T", runBlockingType)
            for (rag in agent.ragNodes) {
                val ragClassName = ClassName("generated.rag", rag.name.replaceFirstChar { it.uppercase() } + "Rag")
                initRagBuilder.addStatement("%T.initialize()", ragClassName)
            }
            initRagBuilder.endControlFlow()
            classBuilder.addFunction(initRagBuilder.build())
        }

        // agentScope for MCP
        if (hasMcp) {
            val coroutineScopeType = ClassName("kotlinx.coroutines", "CoroutineScope")
            val dispatchersType = ClassName("kotlinx.coroutines", "Dispatchers")
            val supervisorJobType = ClassName("kotlinx.coroutines", "SupervisorJob")
            classBuilder.addProperty(
                PropertySpec.builder("agentScope", coroutineScopeType)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T(%T.IO + %T())", coroutineScopeType, dispatchersType, supervisorJobType)
                    .build()
            )
        }

        // @Bean @Primary fun agent()
        val agentFunBuilder = FunSpec.builder("agent")
            .addAnnotation(beanAnnotation)
            .addAnnotation(primaryAnnotation)
            .returns(agentReturnType)

        // Build AIAgent constructor call params
        val aiAgentBody = buildAgentBody(agent, effectivePrompt, modelRef, hasTools, strategyCall, hasMcp, hasOtel)

        if (hasMcp) {
            val runBlockingType = ClassName("kotlinx.coroutines", "runBlocking")
            agentFunBuilder.addCode(CodeBlock.builder()
                .beginControlFlow("return %T(agentScope.coroutineContext)", runBlockingType)
                .add(aiAgentBody)
                .endControlFlow()
                .build())
        } else {
            agentFunBuilder.addCode(aiAgentBody)
        }

        classBuilder.addFunction(agentFunBuilder.build())

        // --- Build the FileSpec ---
        val fileBuilder = FileSpec.builder("generated", "AgentConfig")
            .addType(classBuilder.build())

        // Add explicit imports that KotlinPoet can't auto-resolve from code literals
        fileBuilder.addImport("ai.koog.prompt.executor.llms.all", agent.modelProvider.executorFactory)
        fileBuilder.addImport(agent.modelProvider.importPackage.substringBeforeLast('.'), agent.modelProvider.importPackage.substringAfterLast('.'))

        if (agent.modelProvider == ModelProvider.BEDROCK) {
            fileBuilder.addImport("ai.koog.prompt.executor.clients.bedrock", "BedrockClientSettings")
        }

        if (hasOtel) {
            fileBuilder.addImport("ai.koog.agents.features.otel", "OpenTelemetry")
            when (agent.otelConfig?.exporterType) {
                "http" -> {
                    fileBuilder.addImport("io.opentelemetry.exporter.otlp.http.trace", "OtlpHttpSpanExporter")
                    fileBuilder.addImport("java.util.concurrent", "TimeUnit")
                }
                "grpc" -> {
                    fileBuilder.addImport("io.opentelemetry.exporter.otlp.trace", "OtlpGrpcSpanExporter")
                    fileBuilder.addImport("java.util.concurrent", "TimeUnit")
                }
                "logging" -> fileBuilder.addImport("io.opentelemetry.exporter.logging", "LoggingSpanExporter")
            }
        }

        var result = fileBuilder.build().toString()

        // Post-process KotlinPoet output:
        // 1. Replace ${'$'} with \$ in @Value annotations (KotlinPoet uses Kotlin's dollar escape,
        //    but Spring @Value conventionally uses \$ in generated code)
        result = result.replace("\${'$'}", "\\$")
        // 2. Remove backticks from package names (KotlinPoet escapes 'annotation' as a keyword)
        result = result.replace("`", "")

        return result
    }

    private fun buildConstructor(
        agent: KoogAgent,
        valueClass: ClassName,
        providerKey: String,
        hasOtel: Boolean
    ): com.squareup.kotlinpoet.FunSpec {
        val ctorBuilder = FunSpec.constructorBuilder()

        val isBedrock = agent.modelProvider == ModelProvider.BEDROCK
        if (isBedrock) {
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("accessKey", String::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${bedrock.access-key}").build())
                    .build()
            )
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("secretKey", String::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${bedrock.secret-key}").build())
                    .build()
            )
        } else {
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("apiKey", String::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${$providerKey.api-key}").build())
                    .build()
            )
        }

        if (hasOtel) {
            if (agent.otelConfig?.exporterType != "logging") {
                ctorBuilder.addParameter(
                    com.squareup.kotlinpoet.ParameterSpec.builder("otelEndpoint", String::class)
                        .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${otel.endpoint}").build())
                        .build()
                )
            }
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("otelServiceName", String::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${otel.service-name}").build())
                    .build()
            )
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("otelServiceVersion", String::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${otel.service-version}").build())
                    .build()
            )
            if (agent.otelConfig?.authorizationHeader != null) {
                ctorBuilder.addParameter(
                    com.squareup.kotlinpoet.ParameterSpec.builder("otelAuthHeader", String::class)
                        .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${otel.authorization-header}").build())
                        .build()
                )
            }
            ctorBuilder.addParameter(
                com.squareup.kotlinpoet.ParameterSpec.builder("otelVerbose", Boolean::class)
                    .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${otel.verbose}").build())
                    .build()
            )
        }

        return ctorBuilder.build()
    }

    private fun buildAgentBody(
        agent: KoogAgent,
        effectivePrompt: String,
        modelRef: String,
        hasTools: Boolean,
        strategyCall: String?,
        hasMcp: Boolean,
        hasOtel: Boolean
    ): CodeBlock {
        val builder = CodeBlock.builder()

        // Build the AIAgent(...) call with named parameters
        val aiAgentType = KoogTypes.AI_AGENT
        builder.add("return %T(\n", aiAgentType) // no beginControlFlow — raw parens
        if (agent.modelProvider == ModelProvider.BEDROCK) {
            builder.add("    promptExecutor = %L(\n", agent.modelProvider.executorFactory)
            builder.add("        awsAccessKeyId = accessKey,\n")
            builder.add("        awsSecretAccessKey = secretKey,\n")
            builder.add("        settings = BedrockClientSettings(\n")
            builder.add("            region = System.getenv(\"AWS_BEDROCK_REGION\") ?: \"us-west-2\"\n")
            builder.add("        )\n")
            builder.add("    ),\n")
        } else {
            builder.add("    promptExecutor = %L(apiKey),\n", agent.modelProvider.executorFactory)
        }
        // Manually escape the prompt to produce a single-line string literal with \n escapes
        // (KotlinPoet's %S converts multi-line strings to trimMargin blocks)
        val escapedPrompt = effectivePrompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        builder.add("    systemPrompt = %L,\n", "\"$escapedPrompt\"")
        builder.add("    llmModel = %L", modelRef)

        agent.temperature?.let {
            builder.add(",\n    temperature = %L", it)
        }
        if (strategyCall != null) {
            builder.add(",\n    strategy = %L", strategyCall)
        }
        if (hasTools) {
            builder.add(",\n    toolRegistry = createToolRegistry()")
        }
        builder.add("\n)")

        if (hasOtel) {
            builder.beginControlFlow("")
            builder.beginControlFlow("install(OpenTelemetry)")
            builder.addStatement("setServiceInfo(otelServiceName, otelServiceVersion)")
            builder.addStatement("setVerbose(otelVerbose)")
            when (agent.otelConfig?.exporterType) {
                "http" -> {
                    builder.add("addSpanExporter(\n")
                    builder.add("    OtlpHttpSpanExporter.builder()\n")
                    builder.add("        .setEndpoint(otelEndpoint)\n")
                    builder.add("        .setTimeout(30, TimeUnit.SECONDS)\n")
                    if (agent.otelConfig?.authorizationHeader != null) {
                        builder.add("        .addHeader(%S, otelAuthHeader)\n", "Authorization")
                    }
                    builder.add("        .build()\n")
                    builder.addStatement(")")
                }
                "grpc" -> {
                    builder.add("addSpanExporter(\n")
                    builder.add("    OtlpGrpcSpanExporter.builder()\n")
                    builder.add("        .setEndpoint(otelEndpoint)\n")
                    if (agent.otelConfig?.authorizationHeader != null) {
                        builder.add("        .addHeader(%S, otelAuthHeader)\n", "Authorization")
                    }
                    builder.add("        .build()\n")
                    builder.addStatement(")")
                }
                "logging" -> {
                    builder.addStatement("addSpanExporter(LoggingSpanExporter.create())")
                }
            }
            builder.endControlFlow()
            builder.endControlFlow()
        }
        builder.add("\n")

        return builder.build()
    }

    private fun mapModelId(provider: ModelProvider, id: String): String = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
            "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
            "gpt-4-1" -> "OpenAIModels.Chat.GPT4_1"
            else -> provider.defaultModel
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
            "claude-opus-4" -> "AnthropicModels.Opus_4"
            "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
            else -> provider.defaultModel
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
            "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
            else -> provider.defaultModel
        }
        ModelProvider.BEDROCK -> when (id.lowercase()) {
            "claude-4.5-sonnet" -> "BedrockModels.AnthropicClaude4_5Sonnet"
            "claude-4-sonnet" -> "BedrockModels.AnthropicClaude4Sonnet"
            "claude-4-opus" -> "BedrockModels.AnthropicClaude4Opus"
            "nova-pro" -> "BedrockModels.AmazonNovaPro"
            "nova-lite" -> "BedrockModels.AmazonNovaLite"
            "nova-micro" -> "BedrockModels.AmazonNovaMicro"
            "llama-3.3-70b" -> "BedrockModels.MetaLlama3_3_70BInstruct"
            "llama-3.1-405b" -> "BedrockModels.MetaLlama3_1_405BInstruct"
            else -> provider.defaultModel
        }
        ModelProvider.OLLAMA -> provider.defaultModel
    }
}
