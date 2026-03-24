package com.agentwork.modeler.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class McpDiscoveryControllerTest {

    private val service = McpDiscoveryService()

    @Test
    fun `returns error when SSE server is not reachable`() {
        val request = McpDiscoveryRequest("sse", "http://localhost:19999", null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
        assertNull(response.tools)
    }

    @Test
    fun `returns error when STDIO command does not exist`() {
        val request = McpDiscoveryRequest("stdio", null, "nonexistent-mcp-binary-xyz", null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
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
