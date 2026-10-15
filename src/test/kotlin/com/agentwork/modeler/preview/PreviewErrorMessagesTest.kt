package com.agentwork.modeler.preview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PreviewErrorMessagesTest {

    @Test
    fun `explains a missing ollama model with the pull command`() {
        val raw = "Error from client: OllamaClient\nError from client: OllamaClient\nStatus code: 404\n" +
            "Error body:\n{\"error\":\"model 'llama3.2:latest' not found\"}\n\n"
        assertEquals(
            "Ollama model 'llama3.2:latest' is not installed. Run: ollama pull llama3.2:latest",
            describePreviewError(RuntimeException(raw))
        )
    }

    @Test
    fun `explains an unreachable ollama`() {
        val raw = "Error from client: OllamaClient\nConnect to http://localhost:11434 " +
            "[localhost/127.0.0.1, localhost/0:0:0:0:0:0:0:1] failed: Connection refused\n"
        assertEquals(
            "Ollama is not reachable at http://localhost:11434. Start it with: ollama serve",
            describePreviewError(RuntimeException(raw))
        )
    }

    @Test
    fun `keeps other errors unchanged`() {
        assertEquals("Missing environment variable: OPENAI_API_KEY", describePreviewError(RuntimeException("Missing environment variable: OPENAI_API_KEY")))
        assertEquals("Unknown error", describePreviewError(RuntimeException()))
    }
}
