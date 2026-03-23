package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class RegistryGeneratorTest {
    private val generator = RegistryGenerator()

    @Test
    fun `generates registry with multiple tools`() {
        val tools = listOf(
            KoogTool("searchTool", "Searches", emptyList(), null),
            KoogTool("calcTool", "Calculates", emptyList(), null)
        )
        val code = generator.generate(tools)
        assertContains(code, "ToolRegistry")
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "CalcToolToolSet")
        assertContains(code, "tools(")
    }
}
