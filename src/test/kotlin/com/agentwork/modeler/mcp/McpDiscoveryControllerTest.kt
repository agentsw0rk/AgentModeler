package com.agentwork.modeler.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class McpDiscoveryControllerTest {

    private val service = McpDiscoveryService()

    @Test
    fun `discovers tools via SSE`() {
        val request = McpDiscoveryRequest("sse", "http://localhost:8931", null, null, null)
        val response = service.discover(request)
        assertNull(response.error)
        assertNotNull(response.tools)
        assertEquals(2, response.tools!!.size)
    }

    @Test
    fun `discovers tools via STDIO`() {
        val request = McpDiscoveryRequest("stdio", null, "npx", listOf("@playwright/mcp"), null)
        val response = service.discover(request)
        assertNull(response.error)
        assertNotNull(response.tools)
    }

    @Test
    fun `returns error for SSE without URL`() {
        val request = McpDiscoveryRequest("sse", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
        assertNull(response.tools)
    }

    @Test
    fun `returns error for STDIO without command`() {
        val request = McpDiscoveryRequest("stdio", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
    }

    @Test
    fun `returns error for unsupported transport`() {
        val request = McpDiscoveryRequest("websocket", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
    }
}
