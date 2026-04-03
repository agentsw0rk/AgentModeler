package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class SmokeTestGenerator {

    fun generate(hasTools: Boolean, strategyFunctionName: String? = null): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import kotlin.test.Test")
        appendLine("import kotlin.test.assertNotNull")
        appendLine()
        appendLine("class AgentSmokeTest {")

        if (hasTools) {
            appendLine()
            appendLine("    @Test")
            appendLine("    fun toolRegistryIsCreatable() {")
            appendLine("        val registry = createToolRegistry()")
            appendLine("        assertNotNull(registry)")
            appendLine("    }")
        }

        if (strategyFunctionName != null) {
            appendLine()
            appendLine("    @Test")
            appendLine("    fun strategyIsCreatable() {")
            appendLine("        val strategy = $strategyFunctionName()")
            appendLine("        assertNotNull(strategy)")
            appendLine("    }")
        }

        appendLine("}")
    }
}
