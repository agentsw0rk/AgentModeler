package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.ModelProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DockerComposeGeneratorTest {

    private val generator = DockerComposeGenerator()

    @Test
    fun `passes provider api key to the app service`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val compose = generator.generate(agent)
        assertContains(compose, "OPENAI_API_KEY: \${OPENAI_API_KEY}")
    }

    @Test
    fun `points ollama agents to ollama on the docker host without api key`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OLLAMA, null, null, emptyList())
        val compose = generator.generate(agent)
        assertContains(compose, "OLLAMA_BASE_URL: \${OLLAMA_BASE_URL:-http://host.docker.internal:11434}")
        assertContains(compose, "extra_hosts:\n      - \"host.docker.internal:host-gateway\"")
        assertFalse(compose.contains("API_KEY"))
    }

    @Test
    fun `passes atlassian credentials when agent uses jira`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
            builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net"))))
        val compose = generator.generate(agent)
        assertContains(compose, "ATLASSIAN_EMAIL: \${ATLASSIAN_EMAIL}")
        assertContains(compose, "ATLASSIAN_API_TOKEN: \${ATLASSIAN_API_TOKEN}")
    }

    @Test
    fun `omits atlassian credentials without atlassian tools`() {
        val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        assertFalse(generator.generate(agent).contains("ATLASSIAN"))
    }
}
