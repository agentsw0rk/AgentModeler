package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ApplicationPropertiesGeneratorTest {
    private val generator = ApplicationPropertiesGenerator()

    @Test
    fun `generates application properties with dynamic port`() {
        val code = generator.generate()
        assertContains(code, "server.port=\${SERVER_PORT:8080}")
    }
}
