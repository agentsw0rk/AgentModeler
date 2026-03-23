package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ToolGeneratorTest {

    private val generator = ToolGenerator()

    @Test
    fun `generates tool class with parameters`() {
        val tool = KoogTool("searchTool", "Searches the web for information",
            listOf(
                KoogToolParameter("query", KoogParamType.STRING, "The search query", true),
                KoogToolParameter("maxResults", KoogParamType.INT, "Maximum results", false)
            ), null)
        val code = generator.generate(tool)
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "@LLMDescription")
        assertContains(code, "query: String")
        assertContains(code, "maxResults: Int")
        assertContains(code, "Searches the web for information")
    }

    @Test
    fun `generates tool with no parameters`() {
        val tool = KoogTool("pingTool", "Pings the server", emptyList(), null)
        val code = generator.generate(tool)
        assertContains(code, "PingToolToolSet")
        assertContains(code, "@Tool")
    }
}
