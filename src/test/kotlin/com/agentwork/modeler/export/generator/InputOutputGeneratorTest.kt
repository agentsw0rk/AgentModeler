package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogField
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class InputOutputGeneratorTest {

    private val generator = InputOutputGenerator()

    @Test
    fun `generates input class with multiple fields and LLMDescription`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("successful", "Boolean", "Was the order successful?", false),
                KoogField("comment", "String", "Optional comment", true),
                KoogField("totalPrice", "Int", "Total price in USD", false)
            )
        )
        val code = generator.generateInputClass("OrderAgent", input)!!
        assertContains(code, "data class OrderAgentInput(")
        assertContains(code, "@Serializable")
        assertContains(code, "import kotlinx.serialization.Serializable")
        assertContains(code, "import ai.koog.agents.core.tools.annotations.LLMDescription")
        assertContains(code, "@property:LLMDescription(\"Was the order successful?\")")
        assertContains(code, "val successful: Boolean,")
        assertContains(code, "val totalPrice: Int,")
        assertContains(code, "@property:LLMDescription(\"Optional comment\")")
        assertContains(code, "val comment: String? = null")
    }

    @Test
    fun `uses custom className when provided`() {
        val input = KoogInput(
            className = "FinalOrderResult",
            fields = listOf(KoogField("ok", "Boolean", "", false))
        )
        val code = generator.generateInputClass("OrderAgent", input)!!
        assertContains(code, "data class FinalOrderResult(")
    }

    @Test
    fun `omits LLMDescription when description is empty`() {
        val input = KoogInput(
            className = null,
            fields = listOf(KoogField("query", "String", "", false))
        )
        val code = generator.generateInputClass("Test", input)!!
        assert(!code.contains("LLMDescription"))
        assertContains(code, "val query: String")
    }

    @Test
    fun `maps all field types correctly`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("a", "String", "", false),
                KoogField("b", "Int", "", false),
                KoogField("c", "Double", "", false),
                KoogField("d", "Boolean", "", false),
                KoogField("e", "List", "", false),
                KoogField("f", "Map", "", false)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        assertContains(code, "val a: String")
        assertContains(code, "val b: Int")
        assertContains(code, "val c: Double")
        assertContains(code, "val d: Boolean")
        assertContains(code, "val e: List<Any>")
        assertContains(code, "val f: Map<String, Any>")
    }

    @Test
    fun `optional fields get nullable type with null default`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("name", "String", "", false),
                KoogField("age", "Int", "", true)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        assertContains(code, "val name: String,")
        assertContains(code, "val age: Int? = null")
    }

    @Test
    fun `returns null for empty fields`() {
        assertNull(generator.generateInputClass("Test", KoogInput(null, emptyList())))
        assertNull(generator.generateOutputClass("Test", KoogOutput(null, emptyList())))
    }

    @Test
    fun `generates output class with fields and LLMDescription`() {
        val output = KoogOutput(
            className = "OrderResult",
            fields = listOf(
                KoogField("status", "String", "Order status", false),
                KoogField("details", "String", "Extra info", true)
            )
        )
        val code = generator.generateOutputClass("OrderAgent", output)!!
        assertContains(code, "data class OrderResult(")
        assertContains(code, "@property:LLMDescription(\"Order status\")")
        assertContains(code, "val status: String,")
        assertContains(code, "val details: String? = null")
    }

    @Test
    fun `required fields come before optional fields`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("opt1", "String", "", true),
                KoogField("req1", "String", "", false),
                KoogField("opt2", "Int", "", true),
                KoogField("req2", "Boolean", "", false)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        val req1Idx = code.indexOf("val req1: String")
        val req2Idx = code.indexOf("val req2: Boolean")
        val opt1Idx = code.indexOf("val opt1: String?")
        val opt2Idx = code.indexOf("val opt2: Int?")
        assert(req1Idx < opt1Idx) { "Required fields should come before optional" }
        assert(req2Idx < opt1Idx) { "Required fields should come before optional" }
        assert(opt1Idx < opt2Idx) { "Optional fields should maintain relative order" }
    }
}
