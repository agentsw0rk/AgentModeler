package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class LibGradlePropertiesGenerator {
    fun generate(): String = buildString {
        appendLine("kotlin.code.style=official")
        appendLine("org.gradle.jvmargs=-Xmx512m")
    }
}
