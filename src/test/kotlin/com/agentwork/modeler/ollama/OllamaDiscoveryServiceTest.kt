package com.agentwork.modeler.ollama

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OllamaDiscoveryServiceTest {

    @Test
    fun `lists installed models with label and tool support`() {
        val service = OllamaDiscoveryService("http://ollama:11434") {
            listOf(
                OllamaModelInfo("llama3.2:latest", 3_200_000_000L, "Q4_K_M", supportsTools = true),
                OllamaModelInfo("gemma:2b", null, null, supportsTools = false),
            )
        }

        val response = runBlocking { service.listModels() }

        assertNull(response.error)
        assertEquals(
            listOf(
                OllamaModelDto("llama3.2:latest", "llama3.2:latest (3.2B, Q4_K_M)", tools = true),
                OllamaModelDto("gemma:2b", "gemma:2b", tools = false),
            ),
            response.models
        )
    }

    @Test
    fun `returns empty list with hint when ollama is not reachable`() {
        val service = OllamaDiscoveryService("http://localhost:19999") { error("Connection refused") }

        val response = runBlocking { service.listModels() }

        assertTrue(response.models.isEmpty())
        val error = assertNotNull(response.error)
        assertTrue(error.contains("http://localhost:19999"), error)
    }
}
