package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class MainGeneratorTest {
    private val generator = MainGenerator()

    @Test
    fun `generates runnable main function`() {
        val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "fun main()")
        assertContains(code, "runBlocking")
        assertContains(code, "MyAgent")
    }
}
