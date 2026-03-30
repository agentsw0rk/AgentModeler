package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.GeneratorBackend
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
    private val ktorRegistryGenerator: KtorRegistryGenerator
) : GeneratorBackend {

    override val id = "ktor-wasm"
    override val displayName = "Ktor (WasmJS)"
    override val supportedPlatforms = listOf("wasm")

    private val jvmPrefix = "src/main/kotlin/generated/"
    private val wasmPrefix = "src/wasmJsMain/kotlin/generated/"

    private fun remapSharedFiles(sharedFiles: Map<String, String>): Map<String, String> =
        sharedFiles.mapKeys { (path, _) ->
            if (path.startsWith(jvmPrefix)) {
                wasmPrefix + path.removePrefix(jvmPrefix)
            } else {
                path
            }
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

        // Remapped shared files
        files.putAll(remapSharedFiles(sharedFiles))

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
        files["build.gradle.kts"] = wasmBuildGradleGenerator.generate(infraAgent)
        files["settings.gradle.kts"] = wasmSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = wasmGradlePropertiesGenerator.generate()
        files["Dockerfile"] = wasmDockerfileGenerator.generate()

        // Kotlin sources
        files["${wasmPrefix}Main.kt"] = ktorMainGenerator.generate(infraAgent, strategyCall)
        files["${wasmPrefix}ChatRoutes.kt"] = ktorControllerGenerator.generate(
            inputClassName = inputClassName,
            outputClassName = outputClassName
        )
        if (infraAgent.tools.isNotEmpty() || infraAgent.builtinTools.isNotEmpty() || infraAgent.subAgents.isNotEmpty() || infraAgent.mcpServers.isNotEmpty()) {
            files["${wasmPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = infraAgent.tools,
                builtinTools = infraAgent.builtinTools,
                subAgents = infraAgent.subAgents,
                mcpServers = infraAgent.mcpServers
            )
        }

        // Remapped shared files
        files.putAll(remapSharedFiles(sharedFiles))

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
