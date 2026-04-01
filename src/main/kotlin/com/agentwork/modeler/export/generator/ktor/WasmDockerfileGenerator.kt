package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmDockerfileGenerator {
    fun generate(): String = buildString {
        appendLine("FROM node:22-alpine")
        appendLine("WORKDIR /app")
        appendLine("COPY build/js/packages/agent/kotlin/ .")
        appendLine("EXPOSE 8080")
        appendLine("""CMD ["node", "agent.mjs"]""")
    }
}
