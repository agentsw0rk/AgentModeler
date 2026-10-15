package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class KtorRegistryGeneratorTest {

    private val generator = KtorRegistryGenerator()
    private val shell = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/data")))

    @Test
    fun `registers the Wasm shell tool with its working directory`() {
        val code = generator.generate(emptyList(), builtinTools = shell, includeShellTool = true)
        assertContains(code, "tool(ShellTool(workingDir = \"/data\"))")
        assertContains(code, "import generated.tools.*")
    }

    @Test
    fun `leaves the shell tool out unless the backend provides it`() {
        // The KMP library backend shares this generator but has no Node shell tool
        val code = generator.generate(emptyList(), builtinTools = shell)
        assertFalse(code.contains("ShellTool"))
    }
}
