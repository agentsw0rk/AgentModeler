package com.agentwork.modeler.export.generator.lib

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SmokeTestGeneratorTest {

    private val generator = SmokeTestGenerator()

    @Test
    fun `generates test with toolRegistry check`() {
        val result = generator.generate(hasTools = true)
        assertContains(result, "package generated")
        assertContains(result, "import kotlin.test.Test")
        assertContains(result, "fun toolRegistryIsCreatable()")
        assertContains(result, "createToolRegistry()")
        assertContains(result, "assertNotNull")
    }

    @Test
    fun `generates test with strategy check for graph agents`() {
        val result = generator.generate(hasTools = true, strategyFunctionName = "createWorkflowStrategy")
        assertContains(result, "fun strategyIsCreatable()")
        assertContains(result, "createWorkflowStrategy()")
    }

    @Test
    fun `omits toolRegistry test when no tools`() {
        val result = generator.generate(hasTools = false)
        assertTrue(!result.contains("toolRegistryIsCreatable"))
        assertTrue(!result.contains("createToolRegistry"))
    }
}
