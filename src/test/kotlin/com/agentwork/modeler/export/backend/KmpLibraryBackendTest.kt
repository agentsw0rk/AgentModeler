package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.BackendConfig
import com.agentwork.modeler.export.generator.lib.*
import com.agentwork.modeler.export.generator.ktor.KtorRegistryGenerator
import com.agentwork.modeler.export.generator.ktor.WasmToolGenerator
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class KmpLibraryBackendTest {

    private val backend = KmpLibraryBackend(
        libBuildGradleGenerator = LibBuildGradleGenerator(),
        libSettingsGradleGenerator = LibSettingsGradleGenerator(),
        libGradlePropertiesGenerator = LibGradlePropertiesGenerator(),
        agentFactoryGenerator = AgentFactoryGenerator(),
        smokeTestGenerator = SmokeTestGenerator(),
        ktorRegistryGenerator = KtorRegistryGenerator(),
        wasmToolGenerator = WasmToolGenerator()
    )

    private val simpleAgent = KoogAgent(
        name = "TestAgent",
        systemPrompt = "Help",
        modelProvider = ModelProvider.OPENAI,
        modelId = null,
        temperature = null,
        tools = listOf(KoogTool("search", "Searches", listOf(
            KoogToolFunction("find", "Finds things", listOf(
                KoogToolParameter("query", KoogParamType.STRING, "query", true, null)
            ))
        )))
    )

    @Test
    fun `has correct id and metadata`() {
        assertTrue(backend.id == "kmp-library")
        assertTrue(backend.displayName == "KMP Library")
        assertTrue(backend.remapsSharedFiles)
    }

    @Test
    fun `generates commonMain layout for JVM-only target`() {
        val sharedFiles = mapOf(
            "src/main/kotlin/generated/tools/search.kt" to "// tool code",
            "src/main/kotlin/generated/ToolRegistry.kt" to "// registry"
        )
        val config = BackendConfig(targets = listOf("jvm"))
        val files = backend.generateAgentFiles(simpleAgent, "TestProject", sharedFiles, null, null, config)

        assertTrue(files.containsKey("build.gradle.kts"))
        assertTrue(files.containsKey("settings.gradle.kts"))
        assertTrue(files.containsKey("gradle.properties"))
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/AgentFactory.kt"))
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/ToolRegistry.kt"))
        assertTrue(files.containsKey("src/commonTest/kotlin/generated/AgentSmokeTest.kt"))

        // Shared files remapped from src/main to src/commonMain
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/tools/search.kt"))
        assertTrue(!files.containsKey("src/main/kotlin/generated/tools/search.kt"))

        // build.gradle contains jvm() but not wasmJs
        assertContains(files["build.gradle.kts"]!!, "jvm()")
        assertTrue(!files["build.gradle.kts"]!!.contains("wasmJs"))
    }

    @Test
    fun `generates wasmJsMain tool overrides when wasmJs target selected`() {
        val sharedFiles = mapOf(
            "src/main/kotlin/generated/tools/search.kt" to "// tool code",
            "src/main/kotlin/generated/ToolRegistry.kt" to "// registry"
        )
        val config = BackendConfig(targets = listOf("jvm", "wasmJs"))
        val files = backend.generateAgentFiles(simpleAgent, "TestProject", sharedFiles, null, null, config)

        // Should have wasmJsMain tool files with SimpleTool
        assertTrue(files.containsKey("src/wasmJsMain/kotlin/generated/tools/search.kt"))
        // wasmJs tool should use SimpleTool, not ToolSet
        assertContains(files["src/wasmJsMain/kotlin/generated/tools/search.kt"]!!, "SimpleTool")
        assertContains(files["build.gradle.kts"]!!, "wasmJs {")
    }
}
