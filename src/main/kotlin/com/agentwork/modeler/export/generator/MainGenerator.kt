package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class MainGenerator {
    fun generate(agent: KoogAgent): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import kotlinx.coroutines.runBlocking")
            appendLine()
            appendLine("fun main() = runBlocking {")
            appendLine("    val agent = ${agent.name}()")
            appendLine("    val result = agent.run(\"Hello! How can you help me?\")")
            appendLine("    println(result)")
            appendLine("}")
        }
    }
}
