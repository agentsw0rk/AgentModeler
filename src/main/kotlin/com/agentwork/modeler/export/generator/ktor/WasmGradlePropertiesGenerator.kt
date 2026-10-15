package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmGradlePropertiesGenerator {
    fun generate(): String = buildString {
        appendLine("kotlin.code.style=official")
        // Linking a Kotlin/Wasm production executable with Koog runs out of memory with less
        appendLine("org.gradle.jvmargs=-Xmx2g")
        appendLine("kotlin.daemon.jvmargs=-Xmx2g")
    }
}
