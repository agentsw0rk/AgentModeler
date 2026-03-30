package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmSettingsGradleGenerator {
    fun generate(projectName: String): String = "rootProject.name = \"$projectName\""
}
