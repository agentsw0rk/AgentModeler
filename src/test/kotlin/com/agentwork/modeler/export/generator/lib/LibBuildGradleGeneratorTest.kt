package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LibBuildGradleGeneratorTest {

    private val generator = LibBuildGradleGenerator()

    private val simpleAgent = KoogAgent(
        name = "TestAgent",
        systemPrompt = "Help",
        modelProvider = ModelProvider.OPENAI,
        modelId = null,
        temperature = null,
        tools = emptyList()
    )

    @Test
    fun `generates KMP build with JVM target only`() {
        val result = generator.generate(simpleAgent, listOf("jvm"))
        assertContains(result, "kotlin(\"multiplatform\")")
        assertContains(result, "id(\"maven-publish\")")
        assertContains(result, "jvm()")
        assertContains(result, "koog-agents")
        assertContains(result, "group = \"com.example\"")
        assertContains(result, "version = \"0.1.0\"")
        assertTrue(!result.contains("wasmJs"))
        assertTrue(!result.contains("iosArm64"))
    }

    @Test
    fun `generates KMP build with all targets`() {
        val result = generator.generate(simpleAgent, listOf("jvm", "wasmJs", "js", "ios"))
        assertContains(result, "jvm()")
        assertContains(result, "wasmJs {")
        assertContains(result, "js(IR) {")
        assertContains(result, "iosArm64()")
        assertContains(result, "iosSimulatorArm64()")
    }

    @Test
    fun `includes MCP dependency when agent has MCP servers`() {
        val agent = simpleAgent.copy(
            mcpServers = listOf(KoogMcpServer("test", "sse", "http://localhost", null, emptyList(), emptyList()))
        )
        val result = generator.generate(agent, listOf("jvm"))
        assertContains(result, "agents-ext-mcp")
    }

    @Test
    fun `includes test dependencies`() {
        val result = generator.generate(simpleAgent, listOf("jvm"))
        assertContains(result, "kotlin(\"test\")")
    }
}
