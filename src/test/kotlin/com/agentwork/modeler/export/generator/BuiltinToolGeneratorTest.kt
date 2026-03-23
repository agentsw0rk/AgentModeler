package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class BuiltinToolGeneratorTest {
    private val generator = BuiltinToolGenerator()

    @Test
    fun `returns null for THINK`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.THINK)))
    }

    @Test
    fun `returns null for USER_PROMPT`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.USER_PROMPT)))
    }

    @Test
    fun `returns null for FILESYSTEM`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.FILESYSTEM)))
    }

    @Test
    fun `generates ShellToolSet with workingDir config`() {
        val tool = KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app"))
        val code = generator.generate(tool)!!
        assertContains(code, "class ShellToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "ProcessBuilder")
        assertContains(code, "workingDir")
        assertContains(code, "waitFor")
    }

    @Test
    fun `generates FetchToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.FETCH))!!
        assertContains(code, "class FetchToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "HttpClient")
    }

    @Test
    fun `generates MemoryToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.MEMORY))!!
        assertContains(code, "class MemoryToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "store")
        assertContains(code, "retrieve")
        assertContains(code, "listKeys")
    }

    @Test
    fun `generates TodoToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.TODO))!!
        assertContains(code, "class TodoToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "addTask")
        assertContains(code, "completeTask")
        assertContains(code, "listTasks")
    }
}
