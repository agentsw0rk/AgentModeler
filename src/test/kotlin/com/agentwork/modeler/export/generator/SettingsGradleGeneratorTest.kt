package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class SettingsGradleGeneratorTest {
    private val generator = SettingsGradleGenerator()

    @Test
    fun `generates settings with project name`() {
        val code = generator.generate("my-cool-agent")
        assertContains(code, """rootProject.name = "my-cool-agent"""")
    }
}
