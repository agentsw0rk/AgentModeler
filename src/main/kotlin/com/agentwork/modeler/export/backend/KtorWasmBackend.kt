package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.RagCodeGenerator
import com.agentwork.modeler.export.generator.sanitizeClassName
import com.agentwork.modeler.export.generator.sanitizeVarPrefix
import com.agentwork.modeler.export.generator.ktor.*
import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class KtorWasmBackend(
    private val wasmBuildGradleGenerator: WasmBuildGradleGenerator,
    private val wasmSettingsGradleGenerator: WasmSettingsGradleGenerator,
    private val wasmGradlePropertiesGenerator: WasmGradlePropertiesGenerator,
    private val wasmDockerfileGenerator: WasmDockerfileGenerator,
    private val ktorMainGenerator: KtorMainGenerator,
    private val ktorControllerGenerator: KtorControllerGenerator,
    private val ktorRegistryGenerator: KtorRegistryGenerator,
    private val wasmToolGenerator: WasmToolGenerator,
    private val ragCodeGenerator: RagCodeGenerator
) : GeneratorBackend {

    override val id = "ktor-wasm"
    override val displayName = "Ktor (WasmJS)"
    override val supportedPlatforms = listOf("wasm")
    override val remapsSharedFiles = true

    private val jvmPrefix = "src/main/kotlin/generated/"
    private val wasmPrefix = "src/wasmJsMain/kotlin/generated/"

    private fun remapSharedFiles(sharedFiles: Map<String, String>): Map<String, String> =
        sharedFiles
            .filterKeys { path ->
                // Exclude JVM ToolSet-based tool files, Spring ToolRegistry, and JVM RAG files — we generate Wasm versions
                !path.matches(Regex(".*/tools/[^/]+\\.kt$")) && !path.endsWith("ToolRegistry.kt") && !path.contains("/rag/")
            }
            .mapKeys { (path, _) ->
                if (path.startsWith(jvmPrefix)) {
                    wasmPrefix + path.removePrefix(jvmPrefix)
                } else {
                    path
                }
            }

    /** Generate Wasm-compatible tool files using SimpleTool instead of ToolSet */
    private fun generateWasmToolFiles(tools: List<KoogTool>): Map<String, String> {
        val files = mutableMapOf<String, String>()
        for (tool in tools) {
            files["${wasmPrefix}tools/${tool.name}.kt"] = wasmToolGenerator.generate(tool)
        }
        // Add wasmFormat utility for String.format() replacement
        if (tools.any { tool -> tool.functions.any { it.body.contains(".format(") } }) {
            files["${wasmPrefix}tools/WasmFormat.kt"] = generateWasmFormat()
        }
        return files
    }

    private fun generateWasmFormat(): String = buildString {
        appendLine("package generated.tools")
        appendLine()
        appendLine("/** WasmJS-compatible replacement for String.format() */")
        appendLine("fun String.wasmFormat(vararg args: Any?): String {")
        appendLine("    var result = this")
        appendLine("    for (arg in args) {")
        appendLine("        val replacement = when (arg) {")
        appendLine("            is Double -> arg.toString()")
        appendLine("            is Float -> arg.toString()")
        appendLine("            null -> \"\"")
        appendLine("            else -> arg.toString()")
        appendLine("        }")
        appendLine("        result = result.replaceFirst(Regex(\"\"\"%[\\\\d.]*[sdfb]\"\"\"), replacement)")
        appendLine("    }")
        appendLine("    return result")
        appendLine("}")
    }

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = wasmBuildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = wasmSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = wasmGradlePropertiesGenerator.generate()
        files["Dockerfile"] = wasmDockerfileGenerator.generate()

        // Kotlin sources
        files["${wasmPrefix}Main.kt"] = ktorMainGenerator.generate(agent)
        files["${wasmPrefix}ChatRoutes.kt"] = ktorControllerGenerator.generate(
            inputClassName = inputClassName,
            outputClassName = outputClassName
        )
        if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
            files["${wasmPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = agent.tools,
                builtinTools = agent.builtinTools,
                subAgents = agent.subAgents,
                mcpServers = agent.mcpServers
            )
        }

        // Remapped shared files (excluding JVM tool files)
        files.putAll(remapSharedFiles(sharedFiles))

        // Wasm-compatible tool files (override shared ToolSet-based ones)
        files.putAll(generateWasmToolFiles(agent.tools))

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Create a KoogAgent facade for infrastructure generators
        val infraAgent = KoogAgent(
            name = agent.name,
            systemPrompt = agent.systemPrompt,
            modelProvider = agent.modelProvider,
            modelId = agent.modelId,
            temperature = agent.temperature,
            tools = agent.tools,
            builtinTools = agent.builtinTools,
            subAgents = agent.subAgents,
            mcpServers = agent.mcpServers,
            inputs = agent.inputs,
            outputs = agent.outputs
        )

        // Infrastructure
        var buildGradle = wasmBuildGradleGenerator.generate(infraAgent)
        // koog-agents already includes vector-storage and embeddings-llm transitively
        files["build.gradle.kts"] = buildGradle
        files["settings.gradle.kts"] = wasmSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = wasmGradlePropertiesGenerator.generate()
        files["Dockerfile"] = wasmDockerfileGenerator.generate()

        // Kotlin sources
        files["${wasmPrefix}Main.kt"] = ktorMainGenerator.generate(infraAgent, strategyCall)
        files["${wasmPrefix}ChatRoutes.kt"] = ktorControllerGenerator.generate(
            inputClassName = inputClassName,
            outputClassName = outputClassName
        )

        // Tool registry (including RAG tools)
        val hasRagTools = agent.ragNodes.any { it.provideAsTool }
        if (infraAgent.tools.isNotEmpty() || infraAgent.builtinTools.isNotEmpty() || infraAgent.subAgents.isNotEmpty() || infraAgent.mcpServers.isNotEmpty() || hasRagTools) {
            var registry = ktorRegistryGenerator.generate(
                tools = infraAgent.tools,
                builtinTools = infraAgent.builtinTools,
                subAgents = infraAgent.subAgents,
                mcpServers = infraAgent.mcpServers
            )
            if (hasRagTools) {
                // Add RAG tool imports and registrations
                registry = registry.replace(
                    "import ai.koog.agents.core.tools.ToolRegistry",
                    "import ai.koog.agents.core.tools.ToolRegistry\nimport generated.rag.*"
                )
                val ragToolLines = agent.ragNodes.filter { it.provideAsTool }.joinToString("\n") { rag ->
                    val varPrefix = rag.name.sanitizeVarPrefix()
                    "        for (t in ${varPrefix}RagTools) tool(t)"
                }
                registry = registry.replace("    }\n}", "$ragToolLines\n    }\n}")
            }
            files["${wasmPrefix}ToolRegistry.kt"] = registry
        }

        // Remapped shared files (excluding JVM tool files and RAG files)
        files.putAll(remapSharedFiles(sharedFiles))

        // Wasm-compatible tool files
        files.putAll(generateWasmToolFiles(agent.tools))

        // Wasm-compatible RAG files
        for (rag in agent.ragNodes) {
            val className = rag.name.sanitizeClassName() + "Rag"
            files["${wasmPrefix}rag/$className.kt"] = ragCodeGenerator.generateWasmRagFile(rag)
        }

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String> {
        throw UnsupportedOperationException("Planner agents are not supported on the Ktor/WasmJS backend")
    }
}
