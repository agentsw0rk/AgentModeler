package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateServiceTest {

    private lateinit var service: EditorStateService
    private val events = mutableListOf<EditorEvent>()

    @BeforeEach
    fun setUp() {
        service = EditorStateService()
        events.clear()
        service.subscribe { events.add(it) }
    }

    @Test
    fun `createNode adds node to graph and emits event`() {
        val nodeId = service.createNode("agent", 100.0, 200.0, mapOf("name" to "MyAgent"))

        val graph = service.getGraph()
        assertEquals(1, graph.nodes.size)
        assertEquals(nodeId, graph.nodes[0].id)
        assertEquals("agent", graph.nodes[0].type)
        assertEquals(100.0, graph.nodes[0].position.x)
        assertEquals(200.0, graph.nodes[0].position.y)
        assertEquals("MyAgent", graph.nodes[0].data["name"])

        assertEquals(1, events.size)
        assertTrue(events[0] is EditorEvent.NodeCreated)
    }

    @Test
    fun `createEdge connects two nodes and emits event`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        events.clear()

        val edgeId = service.createEdge(toolId, agentId)

        val graph = service.getGraph()
        assertEquals(1, graph.edges.size)
        assertEquals(edgeId, graph.edges[0].id)
        assertEquals(toolId, graph.edges[0].source)
        assertEquals(agentId, graph.edges[0].target)

        assertEquals(1, events.size)
        assertTrue(events[0] is EditorEvent.EdgeCreated)
    }

    @Test
    fun `updateNode merges data and emits event`() {
        val nodeId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "model" to "gpt-4"))
        events.clear()

        service.updateNode(nodeId, mapOf("name" to "Updated", "temperature" to 0.5))

        val node = service.getNode(nodeId)
        assertNotNull(node)
        assertEquals("Updated", node.data["name"])
        assertEquals("gpt-4", node.data["model"])
        assertEquals(0.5, node.data["temperature"])

        assertEquals(1, events.size)
        assertTrue(events[0] is EditorEvent.NodeUpdated)
    }

    @Test
    fun `deleteNode removes node and connected edges`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        service.createEdge(toolId, agentId)
        events.clear()

        service.deleteNode(toolId)

        val graph = service.getGraph()
        assertEquals(1, graph.nodes.size)
        assertEquals(agentId, graph.nodes[0].id)
        assertEquals(0, graph.edges.size)

        assertTrue(events.any { it is EditorEvent.NodeDeleted })
    }

    @Test
    fun `deleteEdge removes edge and emits event`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        val edgeId = service.createEdge(toolId, agentId)
        events.clear()

        service.deleteEdge(edgeId)

        val graph = service.getGraph()
        assertEquals(0, graph.edges.size)

        assertEquals(1, events.size)
        assertTrue(events[0] is EditorEvent.EdgeDeleted)
    }

    @Test
    fun `resetGraph clears all state and emits event`() {
        service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        events.clear()

        service.resetGraph()

        val graph = service.getGraph()
        assertEquals(0, graph.nodes.size)
        assertEquals(0, graph.edges.size)

        assertEquals(1, events.size)
        assertTrue(events[0] is EditorEvent.GraphReset)
    }

    @Test
    fun `setWorkspaceName updates name and emits event`() {
        service.setWorkspaceName("My Workspace")

        val graph = service.getGraph()
        assertEquals("My Workspace", graph.name)

        assertTrue(events.any { it is EditorEvent.WorkspaceRenamed })
    }

    @Test
    fun `syncGraph replaces entire state`() {
        service.createNode("agent", 0.0, 0.0, mapOf("name" to "Old"))
        events.clear()

        val newGraph = WorkspaceGraph(
            version = 1,
            name = "Synced Workspace",
            nodes = listOf(
                GraphNode("node-10", "agent", Position(50.0, 50.0), mapOf("name" to "SyncedAgent")),
                GraphNode("node-20", "tool", Position(150.0, 50.0), mapOf("name" to "SyncedTool"))
            ),
            edges = listOf(
                GraphEdge("edge-5", "node-20", "node-10")
            )
        )

        service.syncGraph(newGraph)

        val graph = service.getGraph()
        assertEquals("Synced Workspace", graph.name)
        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)

        // After sync, new node IDs should continue from max existing
        val nextNodeId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Next"))
        assertEquals("node-21", nextNodeId)
    }

    @Test
    fun `getNode returns node by id`() {
        val nodeId = service.createNode("agent", 10.0, 20.0, mapOf("name" to "TestAgent"))

        val node = service.getNode(nodeId)
        assertNotNull(node)
        assertEquals(nodeId, node.id)
        assertEquals("agent", node.type)
    }

    @Test
    fun `getNode returns null for unknown id`() {
        val node = service.getNode("node-999")
        assertNull(node)
    }

    @Test
    fun `createEdge fails for invalid source`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))

        assertThrows<IllegalArgumentException> {
            service.createEdge(agentId, agentId)
        }
    }
}
