package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class WasmToolGeneratorTest {

    @Test
    fun `builds SimpleTool with a type token as required by Koog 1_x`() {
        val tool = KoogTool(
            "weather", "Weather tools",
            listOf(
                KoogToolFunction(
                    "forecast", "Returns the forecast",
                    listOf(KoogToolParameter("city", KoogParamType.STRING, "City", true, null)),
                    body = "return \"sunny in \$city\""
                )
            )
        )
        val code = WasmToolGenerator().generate(tool)
        assertContains(code, "object : SimpleTool<ForecastArgs>(")
        assertContains(code, "argsType = typeToken<ForecastArgs>(),")
        assertContains(code, "import ai.koog.serialization.typeToken")
        assertFalse(code.contains("argsSerializer"))
    }

    @Test
    fun `shell tool runs commands through node child_process in the configured directory`() {
        val code = WasmToolGenerator().generateShellTool()
        assertContains(code, "process.getBuiltinModule('child_process')")
        assertContains(code, "execSync(command, { cwd")
        assertContains(code, "class ShellTool(private val workingDir: String) : SimpleTool<ExecuteCommandArgs>(")
        assertContains(code, "argsType = typeToken<ExecuteCommandArgs>(),")
        assertContains(code, "name = \"executeCommand\"")
    }
}
