package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ApplicationGenerator {
    fun generate(): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import org.springframework.boot.autoconfigure.SpringBootApplication")
            appendLine("import org.springframework.boot.runApplication")
            appendLine()
            appendLine("@SpringBootApplication")
            appendLine("class Application")
            appendLine()
            appendLine("fun main(args: Array<String>) {")
            appendLine("    runApplication<Application>(*args)")
            appendLine("}")
        }
    }
}
