package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class A2AServerGeneratorTest {

    private val generator = A2AServerGenerator()

    private fun config(
        streaming: Boolean = false,
        skills: List<KoogA2ASkill> = listOf(
            KoogA2ASkill("s1", "search", "Search the web", listOf("web"))
        )
    ) = KoogA2AConfig(
        enabled = true,
        agentCard = KoogAgentCard(
            name = "TestBot",
            description = "A test bot",
            url = "http://localhost:9000/a2a",
            version = "1.0.0",
            capabilities = KoogA2ACapabilities(
                streaming = streaming,
                pushNotifications = false,
                stateTransitionHistory = false
            ),
            skills = skills,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text")
        ),
        server = KoogA2AServerConfig(
            transportType = "http-jsonrpc",
            port = 9000,
            basePath = "/a2a"
        )
    )

    @Test
    fun `generates AgentCard data object`() {
        val files = generator.generate(config())
        val code = files["A2AAgentCard.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "object A2AAgentCard")
        assertContains(code, "\"TestBot\"")
        assertContains(code, "\"A test bot\"")
        assertContains(code, "\"http://localhost:9000/a2a\"")
        assertContains(code, "\"1.0.0\"")
        assertContains(code, "\"search\"")
    }

    @Test
    fun `generates A2AServer configuration`() {
        val files = generator.generate(config())
        val code = files["A2AServer.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "9000")
        assertContains(code, "/a2a")
        assertContains(code, "A2AAgentCard")
    }

    @Test
    fun `generates streaming handler when streaming enabled`() {
        val files = generator.generate(config(streaming = true))
        val code = files["A2AServer.kt"]!!
        assertContains(code, "streaming")
    }

    @Test
    fun `generates executor skeleton`() {
        val files = generator.generate(config())
        val code = files["A2AExecutor.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "class A2AExecutor")
        assertContains(code, "handleMessage")
        assertContains(code, "handleTask")
    }
}
