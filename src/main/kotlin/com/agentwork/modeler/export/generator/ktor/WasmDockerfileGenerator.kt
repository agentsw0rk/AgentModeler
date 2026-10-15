package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmDockerfileGenerator {
    /** Packages the output of `./gradlew build` (run it before `docker build`). */
    fun generate(projectName: String): String = buildString {
        val name = WasmSettingsGradleGenerator.npmName(projectName)
        appendLine("FROM node:22-alpine")
        appendLine("WORKDIR /app")
        appendLine("COPY build/wasm/node_modules ./node_modules")
        appendLine("COPY build/wasm/packages/$name/kotlin/ ./")
        appendLine("EXPOSE 8080")
        appendLine("""CMD ["node", "$name.mjs"]""")
    }
}
