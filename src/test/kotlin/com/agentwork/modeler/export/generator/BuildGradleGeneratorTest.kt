package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogMcpServer
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class BuildGradleGeneratorTest {
    private val generator = BuildGradleGenerator()

    @Test
    fun `generates build file with Spring Boot and Koog plugins`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "kotlin(\"jvm\")")
        assertContains(code, "kotlin(\"plugin.spring\")")
        assertContains(code, "org.springframework.boot")
        assertContains(code, "io.spring.dependency-management")
    }

    @Test
    fun `includes Koog repository`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "mavenCentral()")
        assertContains(code, "packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    }

    @Test
    fun `includes core dependencies`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "spring-boot-starter-webflux")
        assertContains(code, "kotlin-reflect")
        assertContains(code, "kotlinx-coroutines-reactor")
        assertContains(code, "koog-spring-boot-starter")
        assertContains(code, "koog-agents")
    }

    @Test
    fun `includes SQLite dependency when Memory tool present`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            listOf(KoogBuiltinTool(BuiltinToolType.MEMORY)))
        val code = generator.generate(agent)
        assertContains(code, "sqlite-jdbc")
    }

    @Test
    fun `omits SQLite dependency when no Memory tool`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            listOf(KoogBuiltinTool(BuiltinToolType.SHELL)))
        val code = generator.generate(agent)
        assertFalse(code.contains("sqlite-jdbc"))
    }

    @Test
    fun `adds MCP dependency when MCP servers present`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            mcpServers = listOf(KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())))
        val code = generator.generate(agent)
        assertContains(code, "agents-ext-mcp")
    }

    @Test
    fun `no MCP dependency without MCP servers`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("agents-ext-mcp"))
    }

    @Test
    fun `includes OTel dependencies when otelConfig enabled with HTTP exporter`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = true, exporterType = "http"))
        val code = generator.generate(agent)
        assertContains(code, "opentelemetry-bom")
        assertContains(code, "koog-opentelemetry")
        assertContains(code, "opentelemetry-sdk")
        assertContains(code, "opentelemetry-exporter-otlp")
    }

    @Test
    fun `includes logging exporter when otelConfig logging`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = true, exporterType = "logging"))
        val code = generator.generate(agent)
        assertContains(code, "opentelemetry-exporter-logging")
        assertFalse(code.contains("opentelemetry-exporter-otlp"))
    }

    @Test
    fun `omits OTel dependencies when otelConfig disabled`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = false))
        val code = generator.generate(agent)
        assertFalse(code.contains("opentelemetry"))
        assertFalse(code.contains("koog-opentelemetry"))
    }

    @Test
    fun `omits OTel dependencies when otelConfig null`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("opentelemetry"))
    }
}
