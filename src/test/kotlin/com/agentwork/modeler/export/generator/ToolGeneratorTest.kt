package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ToolGeneratorTest {

    private val generator = ToolGenerator()

    @Test
    fun `generates toolset with multiple functions`() {
        val tool = KoogTool(
            "MoneyTransferTools",
            "Tools for money transfer operations.",
            listOf(
                KoogToolFunction("getBalance", "Returns the current balance.", listOf(
                    KoogToolParameter("userId", KoogParamType.INT, "The unique identifier of the user.", true, null)
                )),
                KoogToolFunction("sendMoney", "Sends money from the user to a contact.", listOf(
                    KoogToolParameter("senderId", KoogParamType.INT, "Sender user id.", true, null),
                    KoogToolParameter("amount", KoogParamType.DOUBLE, "Amount in sender's default currency.", true, null),
                    KoogToolParameter("confirmed", KoogParamType.BOOLEAN, "Whether the user already confirmed.", false, "false")
                ))
            )
        )
        val code = generator.generate(tool)
        assertContains(code, """@LLMDescription("Tools for money transfer operations.")""")
        assertContains(code, "class MoneyTransferTools : ToolSet {")
        assertContains(code, "@Tool")
        assertContains(code, """@LLMDescription("Returns the current balance.")""")
        assertContains(code, "fun getBalance(")
        assertContains(code, """@LLMDescription("The unique identifier of the user.") userId: Int""")
        assertContains(code, """@LLMDescription("Sends money from the user to a contact.")""")
        assertContains(code, "fun sendMoney(")
        assertContains(code, """@LLMDescription("Amount in sender's default currency.") amount: Double""")
        assertContains(code, "confirmed: Boolean = false")
    }

    @Test
    fun `generates toolset with no functions`() {
        val tool = KoogTool("EmptyTools", "Empty toolset.", emptyList())
        val code = generator.generate(tool)
        assertContains(code, "class EmptyTools : ToolSet {")
        assertFalse(code.contains("@Tool"))
    }

    @Test
    fun `generates function with no parameters`() {
        val tool = KoogTool("PingTools", "Ping toolset.", listOf(
            KoogToolFunction("ping", "Pings the server.", emptyList())
        ))
        val code = generator.generate(tool)
        assertContains(code, "fun ping(): String {")
    }

    @Test
    fun `omits LLMDescription for empty param description`() {
        val tool = KoogTool("Tools", "Some tools.", listOf(
            KoogToolFunction("doStuff", "Does stuff.", listOf(
                KoogToolParameter("input", KoogParamType.STRING, "", true, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, "input: String")
        assertFalse(code.contains("""@LLMDescription("") input"""))
    }

    @Test
    fun `maps all parameter types correctly`() {
        val tool = KoogTool("TypeTools", "Type test.", listOf(
            KoogToolFunction("allTypes", "All types.", listOf(
                KoogToolParameter("a", KoogParamType.STRING, "", true, null),
                KoogToolParameter("b", KoogParamType.INT, "", true, null),
                KoogToolParameter("c", KoogParamType.DOUBLE, "", true, null),
                KoogToolParameter("d", KoogParamType.FLOAT, "", true, null),
                KoogToolParameter("e", KoogParamType.BOOLEAN, "", true, null),
                KoogToolParameter("f", KoogParamType.LIST_STRING, "", true, null),
                KoogToolParameter("g", KoogParamType.MAP_STRING_STRING, "", true, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, "a: String")
        assertContains(code, "b: Int")
        assertContains(code, "c: Double")
        assertContains(code, "d: Float")
        assertContains(code, "e: Boolean")
        assertContains(code, "f: List<String>")
        assertContains(code, "g: Map<String, String>")
    }

    @Test
    fun `optional params with defaults come after required params`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("fn", "Test fn.", listOf(
                KoogToolParameter("opt", KoogParamType.BOOLEAN, "", false, "false"),
                KoogToolParameter("req", KoogParamType.STRING, "Desc", true, null)
            ))
        ))
        val code = generator.generate(tool)
        val reqIdx = code.indexOf("req: String")
        val optIdx = code.indexOf("opt: Boolean = false")
        assert(reqIdx < optIdx) { "Required params should come before optional" }
    }

    @Test
    fun `generates function with Unit return type`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("logAction", "Logs an action.", listOf(
                KoogToolParameter("action", KoogParamType.STRING, "Action name.", true, null)
            ), returnType = "Unit")
        ))
        val code = generator.generate(tool)
        assertContains(code, "fun logAction(")
        assertContains(code, ") {")
        assertFalse(code.contains("): String"))
        assertContains(code, "// TODO: Implement tool logic")
        assertFalse(code.contains("return"))
    }

    @Test
    fun `generates function with custom body`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("getBalance", "Returns balance.", listOf(
                KoogToolParameter("userId", KoogParamType.INT, "User id.", true, null)
            ), body = "return \"Balance: 200.00 EUR\"")
        ))
        val code = generator.generate(tool)
        assertContains(code, "): String {")
        assertContains(code, "return \"Balance: 200.00 EUR\"")
        assertFalse(code.contains("TODO"))
    }

    @Test
    fun `generates Unit function with custom body`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("log", "Logs.", emptyList(), returnType = "Unit", body = "println(\"done\")")
        ))
        val code = generator.generate(tool)
        assertContains(code, "fun log() {")
        assertFalse(code.contains(": String"))
        assertContains(code, "println(\"done\")")
        assertFalse(code.contains("TODO"))
    }

    @Test
    fun `default value formatting for different types`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("fn", "Test fn.", listOf(
                KoogToolParameter("s", KoogParamType.STRING, "", false, "hello"),
                KoogToolParameter("n", KoogParamType.INT, "", false, "42"),
                KoogToolParameter("b", KoogParamType.BOOLEAN, "", false, "true"),
                KoogToolParameter("d", KoogParamType.DOUBLE, "", false, "3.14"),
                KoogToolParameter("l", KoogParamType.LIST_STRING, "", false, null),
                KoogToolParameter("m", KoogParamType.MAP_STRING_STRING, "", false, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, """s: String = "hello"""")
        assertContains(code, "n: Int = 42")
        assertContains(code, "b: Boolean = true")
        assertContains(code, "d: Double = 3.14")
        assertContains(code, "l: List<String> = emptyList()")
        assertContains(code, "m: Map<String, String> = emptyMap()")
    }
}
