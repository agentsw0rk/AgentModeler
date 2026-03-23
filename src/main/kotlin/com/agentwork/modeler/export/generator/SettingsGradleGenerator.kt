package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class SettingsGradleGenerator {
    fun generate(projectName: String): String {
        return """rootProject.name = "$projectName"
"""
    }
}
