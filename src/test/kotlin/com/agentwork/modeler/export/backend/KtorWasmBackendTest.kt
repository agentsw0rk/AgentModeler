package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.BackendConfig
import com.agentwork.modeler.export.generator.RagCodeGenerator
import com.agentwork.modeler.export.generator.ktor.*
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class KtorWasmBackendTest {

    private val backend = KtorWasmBackend(
        wasmBuildGradleGenerator = WasmBuildGradleGenerator(),
        wasmSettingsGradleGenerator = WasmSettingsGradleGenerator(),
        wasmGradlePropertiesGenerator = WasmGradlePropertiesGenerator(),
        wasmDockerfileGenerator = WasmDockerfileGenerator(),
        ktorMainGenerator = KtorMainGenerator(),
        ktorControllerGenerator = KtorControllerGenerator(),
        ktorRegistryGenerator = KtorRegistryGenerator(),
        wasmToolGenerator = WasmToolGenerator(),
        ragCodeGenerator = RagCodeGenerator()
    )

    @Test
    fun `exports the shell tool for an agent with a shell builtin`() {
        val agent = KoogAgent(
            name = "FileAgent", systemPrompt = "Help", modelProvider = ModelProvider.OLLAMA,
            modelId = null, temperature = null, tools = emptyList(),
            builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/data")))
        )
        val files = backend.generateAgentFiles(agent, "files", emptyMap(), null, null, BackendConfig())

        assertNotNull(files["src/wasmJsMain/kotlin/generated/tools/ShellTool.kt"])
        assertContains(files.getValue("src/wasmJsMain/kotlin/generated/ToolRegistry.kt"), "tool(ShellTool(workingDir = \"/data\"))")
        assertContains(files.getValue("src/wasmJsMain/kotlin/generated/Main.kt"), "toolRegistry = createToolRegistry()")
    }
}
