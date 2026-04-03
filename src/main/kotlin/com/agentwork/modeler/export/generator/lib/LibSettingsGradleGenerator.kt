package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class LibSettingsGradleGenerator {
    fun generate(projectName: String): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        return "rootProject.name = \"$safeName\""
    }
}
