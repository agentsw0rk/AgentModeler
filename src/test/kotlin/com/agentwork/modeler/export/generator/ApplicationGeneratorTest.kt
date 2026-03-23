package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ApplicationGeneratorTest {
    private val generator = ApplicationGenerator()

    @Test
    fun `generates SpringBootApplication entry point`() {
        val code = generator.generate()
        assertContains(code, "package generated")
        assertContains(code, "@SpringBootApplication")
        assertContains(code, "class Application")
        assertContains(code, "fun main(args: Array<String>)")
        assertContains(code, "runApplication<Application>(*args)")
    }
}
