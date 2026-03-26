package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InputOutputGeneratorTest {

    private val generator = InputOutputGenerator()

    @Test
    fun `generates input data class with required field`() {
        val inputs = listOf(KoogInput("query", "Search query", "string", true, null))
        val code = generator.generateInputClass("MyAgent", inputs)!!
        assertContains(code, "package generated")
        assertContains(code, "data class MyAgentInput")
        assertContains(code, "val query: String")
        assertFalse(code.contains("="))
    }

    @Test
    fun `generates input data class with optional field and default`() {
        val inputs = listOf(
            KoogInput("query", "Search query", "string", true, null),
            KoogInput("limit", "Max results", "number", false, "10")
        )
        val code = generator.generateInputClass("MyAgent", inputs)!!
        assertContains(code, "val query: String")
        assertContains(code, "val limit: Double = 10.0")
    }

    @Test
    fun `generates output data class`() {
        val outputs = listOf(
            KoogOutput("result", "The result", "string"),
            KoogOutput("count", "Number of results", "number")
        )
        val code = generator.generateOutputClass("MyAgent", outputs)!!
        assertContains(code, "data class MyAgentOutput")
        assertContains(code, "val result: String")
        assertContains(code, "val count: Double")
    }

    @Test
    fun `maps all data types correctly`() {
        val inputs = listOf(
            KoogInput("a", "", "string", true, null),
            KoogInput("b", "", "number", true, null),
            KoogInput("c", "", "boolean", true, null),
            KoogInput("d", "", "object", true, null),
            KoogInput("e", "", "array", true, null)
        )
        val code = generator.generateInputClass("Test", inputs)!!
        assertContains(code, "val a: String")
        assertContains(code, "val b: Double")
        assertContains(code, "val c: Boolean")
        assertContains(code, "val d: Map<String, Any>")
        assertContains(code, "val e: List<Any>")
    }

    @Test
    fun `returns null for empty inputs`() {
        assertNull(generator.generateInputClass("MyAgent", emptyList()))
    }

    @Test
    fun `returns null for empty outputs`() {
        assertNull(generator.generateOutputClass("MyAgent", emptyList()))
    }
}
