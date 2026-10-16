package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.KoogSubAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RegistryGeneratorTest {
    private val generator = RegistryGenerator()

    @Test
    fun `generates registry with custom tools only`() {
        val tools = listOf(
            KoogTool("searchTool", "Searches", emptyList()),
            KoogTool("calcTool", "Calculates", emptyList())
        )
        val code = generator.generate(tools, emptyList())
        assertContains(code, "ToolRegistry")
        assertContains(code, "searchTool()")
        assertContains(code, "calcTool()")
    }

    @Test
    fun `generates registry with builtin shell tool`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app")))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "ShellToolSet")
        assertContains(code, "/app")
    }

    @Test
    fun `generates registry with user_prompt using Koog built-ins`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.USER_PROMPT))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "tool(AskUser)")
        assertContains(code, "tool(SayToUser)")
        assertContains(code, "import ai.koog.agents.ext.tool.AskUser")
        assertContains(code, "import ai.koog.agents.ext.tool.SayToUser")
    }

    @Test
    fun `generates registry with filesystem using Koog built-ins`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.FILESYSTEM))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "ReadFileTool")
        assertContains(code, "WriteFileTool")
        assertContains(code, "ListDirectoryTool")
        assertContains(code, "JVMFileSystemProvider")
    }

    @Test
    fun `skips THINK in registry`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.THINK))
        val code = generator.generate(emptyList(), builtinTools)
        assertFalse(code.contains("Think"))
    }

    @Test
    fun `generates mixed custom and builtin tools`() {
        val tools = listOf(KoogTool("searchTool", "Searches", emptyList()))
        val builtinTools = listOf(
            KoogBuiltinTool(BuiltinToolType.SHELL),
            KoogBuiltinTool(BuiltinToolType.USER_PROMPT)
        )
        val code = generator.generate(tools, builtinTools)
        assertContains(code, "searchTool()")
        assertContains(code, "ShellToolSet")
        assertContains(code, "tool(AskUser)")
    }

    @Test
    fun `generates registry with sub-agent as tool`() {
        val subAgents = listOf(
            KoogSubAgent(
                name = "ResearchAgent", description = "Researches topics",
                systemPrompt = "Research.", modelProvider = ModelProvider.ANTHROPIC,
                modelId = null, temperature = null, delegationMode = "automatic",
                inputDescription = "A topic to research",
                tools = emptyList(), builtinTools = emptyList()
            )
        )
        val code = generator.generate(emptyList(), emptyList(), subAgents)
        assertContains(code, "createResearchAgent()")
        assertContains(code, "createAgentTool")
        assertContains(code, "ResearchAgent")
        assertContains(code, "Researches topics")
        assertContains(code, "A topic to research")
        assertContains(code, "import generated.subagents.*")
        assertContains(code, "@OptIn(kotlin.time.ExperimentalTime::class)")
    }

    @Test
    fun `generates registry with mixed tools and sub-agents`() {
        val tools = listOf(KoogTool("weatherTool", "Weather", emptyList()))
        val subAgents = listOf(
            KoogSubAgent(
                name = "SA", description = "Desc",
                systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
                modelId = null, temperature = null, delegationMode = "automatic",
                inputDescription = "Input",
                tools = emptyList(), builtinTools = emptyList()
            )
        )
        val code = generator.generate(tools, emptyList(), subAgents)
        assertContains(code, "weatherTool()")
        assertContains(code, "createSA()")
    }

    @Test
    fun `generates suspend registry with MCP merge`() {
        val mcpServers = listOf(
            KoogMcpServer("Google Maps", "sse", "http://localhost:8931", null, null, emptyList())
        )
        val code = generator.generate(emptyList(), emptyList(), emptyList(), mcpServers)
        assertContains(code, "suspend fun createToolRegistry()")
        assertContains(code, "import generated.mcp.*")
        assertContains(code, "createGoogleMapsRegistry()")
    }

    @Test
    fun `generates typed sub-agent tool when IO present`() {
        val subAgent = KoogSubAgent(
            name = "Research", description = "Researches topics",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "A topic",
            tools = emptyList(), builtinTools = emptyList(),
            inputs = listOf(KoogInput(null, listOf(KoogField("query", "String", "Q", false)))),
            outputs = listOf(KoogOutput(null, listOf(KoogField("result", "String", "R", false))))
        )
        val code = generator.generate(emptyList(), emptyList(), listOf(subAgent))
        assertContains(code, "GraphAIAgent<ResearchInput, ResearchOutput>")
        assertContains(code, "createAgentTool<ResearchInput, ResearchOutput>")
        assertContains(code, "import generated.subagents.ResearchInput")
        assertContains(code, "import generated.subagents.ResearchOutput")
    }

    @Test
    fun `generates non-suspend registry without MCP`() {
        val tools = listOf(KoogTool("searchTool", "Searches", emptyList()))
        val code = generator.generate(tools)
        assertFalse(code.contains("suspend"))
        assertFalse(code.contains("import generated.mcp"))
    }

    @Test
    fun `generates suspend registry with tools and MCP`() {
        val tools = listOf(KoogTool("weatherTool", "Weather", emptyList()))
        val mcpServers = listOf(
            KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())
        )
        val code = generator.generate(tools, emptyList(), emptyList(), mcpServers)
        assertContains(code, "suspend fun createToolRegistry()")
        assertContains(code, "weatherTool()")
        assertContains(code, "createMapsRegistry()")
    }

    @Test
    fun `tool set registry compiles against Koog 1_x without removed reflect tools import`() {
        // Koog 1.x made tools(ToolSet) a ToolRegistryBuilder member; the old top-level import no longer exists
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app")))
        val code = generator.generate(listOf(KoogTool("searchTool", "Searches", emptyList())), builtinTools)
        assertContains(code, "tools(ShellToolSet(")
        assertFalse(code.contains("import ai.koog.agents.core.tools.reflect.tools"))
    }

    @Test
    fun `generates registry with jira and confluence tool sets`() {
        val builtinTools = listOf(
            KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net")),
            KoogBuiltinTool(BuiltinToolType.CONFLUENCE, mapOf("baseUrl" to "https://x.atlassian.net"))
        )
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "tools(JiraToolSet(baseUrl = \"https://x.atlassian.net\"))")
        assertContains(code, "tools(ConfluenceToolSet(baseUrl = \"https://x.atlassian.net\"))")
    }
}
