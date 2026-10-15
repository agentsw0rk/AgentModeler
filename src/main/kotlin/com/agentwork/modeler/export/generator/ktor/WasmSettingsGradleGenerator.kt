package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmSettingsGradleGenerator {
    fun generate(projectName: String): String = "rootProject.name = \"${npmName(projectName)}\""

    companion object {
        /** Kotlin/Wasm names the output folder and `.mjs` after this, so the Dockerfile must use it too. */
        fun npmName(projectName: String): String = projectName.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    }
}
