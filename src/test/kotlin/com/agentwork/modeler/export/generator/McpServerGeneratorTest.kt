package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.McpEnvVarName
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class McpServerGeneratorTest {

    private val generator = McpServerGenerator()

    @Test
    fun `generates SSE MCP server code`() {
        val mcp = KoogMcpServer(
            name = "Google Maps", transportType = "sse",
            url = "http://localhost:8931", command = null, args = null,
            envVars = emptyList()
        )
        val code = generator.generate(mcp)
        assertContains(code, "package generated.mcp")
        assertContains(code, "suspend fun createGoogleMapsRegistry()")
        assertContains(code, "defaultSseTransport")
        assertContains(code, "http://localhost:8931")
        assertContains(code, "McpToolRegistryProvider")
    }

    @Test
    fun `generates STDIO MCP server code with command and args`() {
        val mcp = KoogMcpServer(
            name = "Playwright", transportType = "stdio",
            url = null, command = "npx", args = listOf("@playwright/mcp", "--headless"),
            envVars = listOf(McpEnvVarName("PLAYWRIGHT_KEY"))
        )
        val code = generator.generate(mcp)
        assertContains(code, "suspend fun createPlaywrightRegistry()")
        assertContains(code, "ProcessBuilder")
        assertContains(code, "\"npx\"")
        assertContains(code, "\"@playwright/mcp\"")
        assertContains(code, "\"--headless\"")
        assertContains(code, "PLAYWRIGHT_KEY")
        assertContains(code, "System.getenv")
        assertContains(code, "defaultStdioTransport")
    }

    @Test
    fun `generates env var comment when env vars present`() {
        val mcp = KoogMcpServer(
            name = "Test", transportType = "sse",
            url = "http://localhost", command = null, args = null,
            envVars = listOf(McpEnvVarName("API_KEY"), McpEnvVarName("SECRET"))
        )
        val code = generator.generate(mcp)
        assertContains(code, "// Required environment variables: API_KEY, SECRET")
    }

    @Test
    fun `no env var comment when no env vars`() {
        val mcp = KoogMcpServer(
            name = "Test", transportType = "sse",
            url = "http://localhost", command = null, args = null,
            envVars = emptyList()
        )
        val code = generator.generate(mcp)
        assertFalse(code.contains("Required environment variables"))
    }
}
