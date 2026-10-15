package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import com.agentwork.modeler.export.model.OtelConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ApplicationPropertiesGeneratorTest {
    private val generator = ApplicationPropertiesGenerator()

    @Test
    fun `generates application properties with dynamic port`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "server.port=\${SERVER_PORT:8080}")
    }

    @Test
    fun `generates api key property for provider`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "openai.api-key=\${OPENAI_API_KEY:}")
    }

    @Test
    fun `generates api key property for anthropic provider`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.ANTHROPIC, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "anthropic.api-key=\${ANTHROPIC_API_KEY:}")
    }

    @Test
    fun `generates OTel properties when enabled`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces",
                serviceName = "my-svc", serviceVersion = "1.0.0", verbose = true))
        val code = generator.generate(agent)
        assertContains(code, "otel.endpoint=http://localhost:4318/v1/traces")
        assertContains(code, "otel.service-name=my-svc")
        assertContains(code, "otel.service-version=1.0.0")
        assertContains(code, "otel.verbose=true")
    }

    @Test
    fun `generates OTel properties with defaults when values are null`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = true))
        val code = generator.generate(agent)
        assertContains(code, "otel.endpoint=http://localhost:4318/v1/traces")
        assertContains(code, "otel.service-name=ai.koog")
        assertContains(code, "otel.service-version=1.0.0")
    }

    @Test
    fun `omits OTel properties when disabled`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            otelConfig = OtelConfig(enabled = false))
        val code = generator.generate(agent)
        assertFalse(code.contains("otel."))
    }

    @Test
    fun `omits OTel properties when otelConfig null`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("otel."))
    }

    @Test
    fun `generates ollama base url property without api key`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OLLAMA, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "ollama.base-url=\${OLLAMA_BASE_URL:http://localhost:11434}")
        assertFalse(code.contains("api-key"))
    }
}
