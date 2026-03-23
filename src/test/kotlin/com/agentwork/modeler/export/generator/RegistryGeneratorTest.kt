package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogSubAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RegistryGeneratorTest {
    private val generator = RegistryGenerator()

    @Test
    fun `generates registry with custom tools only`() {
        val tools = listOf(
            KoogTool("searchTool", "Searches", emptyList(), null),
            KoogTool("calcTool", "Calculates", emptyList(), null)
        )
        val code = generator.generate(tools, emptyList())
        assertContains(code, "ToolRegistry")
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "CalcToolToolSet")
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
        val tools = listOf(KoogTool("searchTool", "Searches", emptyList(), null))
        val builtinTools = listOf(
            KoogBuiltinTool(BuiltinToolType.SHELL),
            KoogBuiltinTool(BuiltinToolType.USER_PROMPT)
        )
        val code = generator.generate(tools, builtinTools)
        assertContains(code, "SearchToolToolSet")
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
    }

    @Test
    fun `generates registry with mixed tools and sub-agents`() {
        val tools = listOf(KoogTool("weatherTool", "Weather", emptyList(), null))
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
        assertContains(code, "WeatherToolToolSet")
        assertContains(code, "createSA()")
    }
}
