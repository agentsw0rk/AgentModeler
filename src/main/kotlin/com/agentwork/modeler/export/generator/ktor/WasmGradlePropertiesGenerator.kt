package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmGradlePropertiesGenerator {
    fun generate(): String = buildString {
        appendLine("kotlin.code.style=official")
        appendLine("org.gradle.jvmargs=-Xmx512m")
    }
}
