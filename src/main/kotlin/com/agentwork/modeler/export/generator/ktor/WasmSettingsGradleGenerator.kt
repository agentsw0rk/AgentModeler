package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmSettingsGradleGenerator {
    fun generate(projectName: String): String {
        val npmName = projectName.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        return "rootProject.name = \"$npmName\""
    }
}
