package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class GradlePropertiesGenerator {
    fun generate(): String {
        return buildString {
            appendLine("kotlin.code.style=official")
            appendLine("org.gradle.jvmargs=-Xmx512m")
        }
    }
}
