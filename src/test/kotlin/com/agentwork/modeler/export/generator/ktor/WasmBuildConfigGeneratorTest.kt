package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class WasmBuildConfigGeneratorTest {

    private val agent = KoogAgent("Agent", "Help", ModelProvider.OLLAMA, null, null, emptyList())

    @Test
    fun `uses the Kotlin version Koog is built with because Wasm klibs must match exactly`() {
        val build = WasmBuildGradleGenerator().generate(agent)
        assertContains(build, """kotlin("multiplatform") version "2.3.10"""")
        assertContains(build, """kotlin("plugin.serialization") version "2.3.10"""")
    }

    @Test
    fun `aligns Ktor server with Koog's Ktor and adds the CIO engine and executors`() {
        val build = WasmBuildGradleGenerator().generate(agent)
        assertContains(build, """implementation("io.ktor:ktor-server-core:3.3.3")""")
        assertContains(build, """implementation("io.ktor:ktor-server-cio:3.3.3")""")
        assertContains(build, """implementation("ai.koog:prompt-executor-llms-all:1.3.0-beta")""")
    }

    @Test
    fun `gives the Kotlin Wasm production linker enough heap`() {
        val properties = WasmGradlePropertiesGenerator().generate()
        assertContains(properties, "org.gradle.jvmargs=-Xmx2g")
        assertContains(properties, "kotlin.daemon.jvmargs=-Xmx2g")
    }
}
