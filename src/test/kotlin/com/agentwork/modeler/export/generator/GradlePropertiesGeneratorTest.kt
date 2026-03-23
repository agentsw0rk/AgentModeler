package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class GradlePropertiesGeneratorTest {
    private val generator = GradlePropertiesGenerator()

    @Test
    fun `generates gradle properties`() {
        val code = generator.generate()
        assertContains(code, "kotlin.code.style=official")
        assertContains(code, "org.gradle.jvmargs=-Xmx512m")
    }
}
