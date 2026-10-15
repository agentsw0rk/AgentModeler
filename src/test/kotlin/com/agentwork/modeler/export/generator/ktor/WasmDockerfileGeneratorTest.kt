package com.agentwork.modeler.export.generator.ktor

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class WasmDockerfileGeneratorTest {

    @Test
    fun `copies the Kotlin Wasm node output named after the project and starts it`() {
        val dockerfile = WasmDockerfileGenerator().generate("Ollama Demo")
        assertContains(dockerfile, "COPY build/wasm/packages/ollama-demo/kotlin/ ./")
        assertContains(dockerfile, """CMD ["node", "ollama-demo.mjs"]""")
    }

    @Test
    fun `ships the npm dependencies the Wasm module imports`() {
        val dockerfile = WasmDockerfileGenerator().generate("Ollama Demo")
        assertContains(dockerfile, "COPY build/wasm/node_modules ./node_modules")
    }
}
