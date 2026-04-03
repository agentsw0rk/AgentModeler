package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.BackendConfig
import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.lib.*
import com.agentwork.modeler.export.generator.ktor.KtorRegistryGenerator
import com.agentwork.modeler.export.generator.ktor.WasmToolGenerator
import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class KmpLibraryBackend(
    private val libBuildGradleGenerator: LibBuildGradleGenerator,
    private val libSettingsGradleGenerator: LibSettingsGradleGenerator,
    private val libGradlePropertiesGenerator: LibGradlePropertiesGenerator,
    private val agentFactoryGenerator: AgentFactoryGenerator,
    private val smokeTestGenerator: SmokeTestGenerator,
    private val ktorRegistryGenerator: KtorRegistryGenerator,
    private val wasmToolGenerator: WasmToolGenerator
) : GeneratorBackend {

    override val id = "kmp-library"
    override val displayName = "KMP Library"
    override val supportedPlatforms = listOf("jvm", "wasmJs", "js", "ios")
    override val remapsSharedFiles = true

    private val jvmPrefix = "src/main/kotlin/generated/"
    private val commonPrefix = "src/commonMain/kotlin/generated/"
    private val wasmPrefix = "src/wasmJsMain/kotlin/generated/"
    private val testPrefix = "src/commonTest/kotlin/generated/"

    /** Remap non-tool, non-registry shared files from JVM to commonMain layout */
    private fun remapSharedFiles(sharedFiles: Map<String, String>): Map<String, String> =
        sharedFiles
            .filterKeys { path ->
                !path.matches(Regex(".*/tools/[^/]+\\.kt$")) && !path.endsWith("ToolRegistry.kt")
            }
            .mapKeys { (path, _) ->
                if (path.startsWith(jvmPrefix)) commonPrefix + path.removePrefix(jvmPrefix) else path
            }

    /** Remap tool files from JVM to commonMain (ToolSet-based, works on all targets except WasmJS) */
    private fun generateCommonToolFiles(sharedFiles: Map<String, String>): Map<String, String> =
        sharedFiles
            .filterKeys { it.matches(Regex(".*/tools/[^/]+\\.kt$")) }
            .mapKeys { (path, _) ->
                if (path.startsWith(jvmPrefix)) commonPrefix + path.removePrefix(jvmPrefix) else path
            }

    /** Generate WasmJS SimpleTool overrides */
    private fun generateWasmToolFiles(tools: List<KoogTool>): Map<String, String> {
        val files = mutableMapOf<String, String>()
        for (tool in tools) {
            files["${wasmPrefix}tools/${tool.name}.kt"] = wasmToolGenerator.generate(tool)
        }
        return files
    }

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasWasm = "wasmJs" in targets
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        // Infrastructure (no Dockerfile, no Application.kt, no application.properties)
        files["build.gradle.kts"] = libBuildGradleGenerator.generate(agent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()

        // Public API factory
        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generateAgentFactory(agent, inputClassName = inputClassName, outputClassName = outputClassName)

        // Tool registry (plain function, no Spring)
        if (hasTools) {
            files["${commonPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = agent.tools, builtinTools = agent.builtinTools,
                subAgents = agent.subAgents, mcpServers = agent.mcpServers
            )
        }

        // Smoke test
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools)

        // Shared files: remap non-tool files to commonMain
        files.putAll(remapSharedFiles(sharedFiles))
        // Tool files: remap to commonMain
        files.putAll(generateCommonToolFiles(sharedFiles))
        // WasmJS SimpleTool overrides (if wasmJs target)
        if (hasWasm && agent.tools.isNotEmpty()) {
            files.putAll(generateWasmToolFiles(agent.tools))
        }

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasWasm = "wasmJs" in targets
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        val infraAgent = KoogAgent(
            name = agent.name, systemPrompt = agent.systemPrompt,
            modelProvider = agent.modelProvider, modelId = agent.modelId,
            temperature = agent.temperature, tools = agent.tools,
            builtinTools = agent.builtinTools, subAgents = agent.subAgents,
            mcpServers = agent.mcpServers, inputs = agent.inputs,
            outputs = agent.outputs, ragNodes = agent.ragNodes
        )

        files["build.gradle.kts"] = libBuildGradleGenerator.generate(infraAgent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()

        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generateAgentFactory(infraAgent, strategyCall, inputClassName, outputClassName)

        if (hasTools) {
            files["${commonPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = infraAgent.tools, builtinTools = infraAgent.builtinTools,
                subAgents = infraAgent.subAgents, mcpServers = infraAgent.mcpServers
            )
        }

        val strategyFuncName = "create${agent.name.replaceFirstChar { it.uppercase() }}Strategy"
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools, strategyFunctionName = strategyFuncName)

        files.putAll(remapSharedFiles(sharedFiles))
        files.putAll(generateCommonToolFiles(sharedFiles))
        if (hasWasm && infraAgent.tools.isNotEmpty()) {
            files.putAll(generateWasmToolFiles(infraAgent.tools))
        }

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        val infraAgent = KoogAgent(
            name = agent.name, systemPrompt = "",
            modelProvider = agent.modelProvider ?: ModelProvider.OPENAI,
            modelId = agent.modelId, temperature = agent.temperature,
            tools = agent.tools, builtinTools = agent.builtinTools,
            mcpServers = agent.mcpServers
        )

        files["build.gradle.kts"] = libBuildGradleGenerator.generate(infraAgent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()
        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generatePlannerFactory(agent)
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools)
        files.putAll(remapSharedFiles(sharedFiles))

        return files
    }
}
