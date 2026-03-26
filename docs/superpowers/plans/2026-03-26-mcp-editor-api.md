# MCP Editor API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the Agent Studio editor as an MCP Server with Streamable HTTP transport, allowing LLM clients to create, modify, and inspect agent graphs via well-described tools, with real-time SSE updates to the frontend.

**Architecture:** Spring AI MCP Server Starter provides the MCP protocol layer. `EditorStateService` holds the authoritative graph state. `EditorMcpTools` exposes 13 `@Tool`-annotated methods. Frontend syncs on connect and receives all updates via SSE. All mutations flow through the backend.

**Tech Stack:** Spring Boot 4, Spring AI MCP Server (Streamable HTTP), Kotlin, Zustand, SSE

**Spec:** `docs/superpowers/specs/2026-03-26-mcp-editor-api-design.md`

---

### Task 1: Build Configuration and Spring AI MCP Server Setup

**Files:**
- Modify: `build.gradle.kts:24-36` (dependencies)
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt`

- [ ] **Step 1: Add Spring AI MCP Server dependency to build.gradle.kts**

In `build.gradle.kts`, add to the dependencies block (after the existing spring-ai-starter-model-openai line):

```kotlin
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
```

- [ ] **Step 2: Add MCP server configuration to application.yaml**

Append to `src/main/resources/application.yaml`:

```yaml
    mcp:
      server:
        protocol: STREAMABLE
        name: agent-studio-editor
        version: 1.0.0
        type: SYNC
        instructions: "MCP server for the Agent Studio visual editor. Provides tools to create, modify, and inspect AI agent graphs consisting of agents, tools, sub-agents, MCP servers, and planner agents."
        streamable-http:
          mcp-endpoint: /mcp
```

This goes under the `spring.ai` section, at the same level as the existing `openai` and `model` keys.

- [ ] **Step 3: Extend CORS config to allow /mcp endpoint**

In `src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt`, add a second mapping for the MCP endpoint. After the existing `registry.addMapping("/api/**")` block, add:

```kotlin
registry.addMapping("/mcp/**")
    .allowedOrigins("*")
    .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
    .allowedHeaders("*")
    .exposedHeaders("Mcp-Session-Id")
```

- [ ] **Step 4: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/main/resources/application.yaml src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt
git commit -m "feat: add Spring AI MCP Server dependency and configuration"
```

---

### Task 2: Editor Event Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcpserver/model/EditorEvent.kt`

- [ ] **Step 1: Create EditorEvent.kt**

```kotlin
package com.agentwork.modeler.mcpserver.model

import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.WorkspaceGraph
import tools.jackson.module.kotlin.jacksonObjectMapper

sealed class EditorEvent(val type: String) {
    data class NodeCreated(val node: GraphNode) : EditorEvent("node-created")
    data class NodeUpdated(val nodeId: String, val data: Map<String, Any?>) : EditorEvent("node-updated")
    data class NodeDeleted(val nodeId: String) : EditorEvent("node-deleted")
    data class EdgeCreated(val edge: GraphEdge) : EditorEvent("edge-created")
    data class EdgeDeleted(val edgeId: String) : EditorEvent("edge-deleted")
    data class GraphReset(val graph: WorkspaceGraph) : EditorEvent("graph-reset")
    data class WorkspaceRenamed(val name: String) : EditorEvent("workspace-renamed")
    data class ValidationUpdated(val errors: List<ValidationError>) : EditorEvent("validation-updated")

    fun toJson(): String {
        val mapper = jacksonObjectMapper()
        val dataMap = when (this) {
            is NodeCreated -> mapOf("node" to node)
            is NodeUpdated -> mapOf("nodeId" to nodeId, "data" to data)
            is NodeDeleted -> mapOf("nodeId" to nodeId)
            is EdgeCreated -> mapOf("edge" to edge)
            is EdgeDeleted -> mapOf("edgeId" to edgeId)
            is GraphReset -> mapOf("graph" to graph)
            is WorkspaceRenamed -> mapOf("name" to name)
            is ValidationUpdated -> mapOf("errors" to errors)
        }
        return mapper.writeValueAsString(mapOf("type" to type) + dataMap)
    }
}

data class ValidationError(
    val nodeId: String,
    val severity: String,
    val message: String
)
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcpserver/model/EditorEvent.kt
git commit -m "feat: add EditorEvent sealed class for SSE event types"
```

---

### Task 3: Editor State Service

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorStateService.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/mcpserver/EditorStateServiceTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateServiceTest {
    private lateinit var service: EditorStateService
    private val collectedEvents = mutableListOf<EditorEvent>()

    @BeforeEach
    fun setUp() {
        service = EditorStateService()
        collectedEvents.clear()
        service.subscribe { collectedEvents.add(it) }
    }

    @Test
    fun `createNode adds node to graph and emits event`() {
        val nodeId = service.createNode("agent", 100.0, 200.0, mapOf("name" to "TestAgent", "systemPrompt" to "Help"))
        assertNotNull(nodeId)
        val graph = service.getGraph()
        assertEquals(1, graph.nodes.size)
        assertEquals("agent", graph.nodes[0].type)
        assertEquals(100.0, graph.nodes[0].position.x)
        assertEquals("TestAgent", graph.nodes[0].data["name"])
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.NodeCreated)
    }

    @Test
    fun `createEdge connects two nodes and emits event`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "Help"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool", "description" to "Does stuff"))
        collectedEvents.clear()
        val edgeId = service.createEdge(toolId, agentId)
        assertNotNull(edgeId)
        val graph = service.getGraph()
        assertEquals(1, graph.edges.size)
        assertEquals(toolId, graph.edges[0].source)
        assertEquals(agentId, graph.edges[0].target)
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.EdgeCreated)
    }

    @Test
    fun `updateNode merges data and emits event`() {
        val nodeId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Old"))
        collectedEvents.clear()
        service.updateNode(nodeId, mapOf("name" to "New", "systemPrompt" to "Help"))
        val node = service.getGraph().nodes[0]
        assertEquals("New", node.data["name"])
        assertEquals("Help", node.data["systemPrompt"])
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.NodeUpdated)
    }

    @Test
    fun `deleteNode removes node and connected edges`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "x"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        service.createEdge(toolId, agentId)
        collectedEvents.clear()
        service.deleteNode(toolId)
        val graph = service.getGraph()
        assertEquals(1, graph.nodes.size)
        assertEquals(0, graph.edges.size)
        assertTrue(collectedEvents.any { it is EditorEvent.NodeDeleted })
    }

    @Test
    fun `deleteEdge removes edge and emits event`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "x"))
        val toolId = service.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool"))
        val edgeId = service.createEdge(toolId, agentId)
        collectedEvents.clear()
        service.deleteEdge(edgeId)
        assertEquals(0, service.getGraph().edges.size)
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.EdgeDeleted)
    }

    @Test
    fun `resetGraph clears all state and emits event`() {
        service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "x"))
        collectedEvents.clear()
        service.resetGraph()
        val graph = service.getGraph()
        assertEquals(0, graph.nodes.size)
        assertEquals(0, graph.edges.size)
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.GraphReset)
    }

    @Test
    fun `setWorkspaceName updates name and emits event`() {
        collectedEvents.clear()
        service.setWorkspaceName("MyProject")
        assertEquals("MyProject", service.getGraph().name)
        assertEquals(1, collectedEvents.size)
        assertTrue(collectedEvents[0] is EditorEvent.WorkspaceRenamed)
    }

    @Test
    fun `syncGraph replaces entire state`() {
        service.createNode("agent", 0.0, 0.0, mapOf("name" to "Old"))
        val newGraph = WorkspaceGraph(
            version = 1,
            name = "Synced",
            nodes = listOf(GraphNode("ext-1", "tool", Position(50.0, 50.0), mapOf("name" to "ExtTool"))),
            edges = emptyList()
        )
        service.syncGraph(newGraph)
        val graph = service.getGraph()
        assertEquals("Synced", graph.name)
        assertEquals(1, graph.nodes.size)
        assertEquals("ext-1", graph.nodes[0].id)
    }

    @Test
    fun `getNode returns node by id`() {
        val nodeId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val node = service.getNode(nodeId)
        assertNotNull(node)
        assertEquals("Agent", node.data["name"])
    }

    @Test
    fun `getNode returns null for unknown id`() {
        assertNull(service.getNode("nonexistent"))
    }

    @Test
    fun `createEdge fails for invalid source`() {
        val agentId = service.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val result = runCatching { service.createEdge("nonexistent", agentId) }
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.mcpserver.EditorStateServiceTest"`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Implement EditorStateService**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class EditorStateService {

    private val lock = ReentrantReadWriteLock()
    private var nodes = mutableListOf<GraphNode>()
    private var edges = mutableListOf<GraphEdge>()
    private var workspaceName = "Untitled Workspace"
    private val nodeCounter = AtomicInteger(1)
    private val edgeCounter = AtomicInteger(1)
    private val subscribers = CopyOnWriteArrayList<(EditorEvent) -> Unit>()

    private val validNodeTypes = setOf("agent", "tool", "builtin-tool", "sub-agent", "mcp-server", "planner-agent")
    private val sourceTypes = setOf("tool", "builtin-tool", "sub-agent", "mcp-server")
    private val targetTypes = setOf("agent", "sub-agent", "planner-agent")

    fun subscribe(listener: (EditorEvent) -> Unit) {
        subscribers.add(listener)
    }

    fun unsubscribe(listener: (EditorEvent) -> Unit) {
        subscribers.remove(listener)
    }

    private fun emit(event: EditorEvent) {
        subscribers.forEach { it(event) }
    }

    fun getGraph(): WorkspaceGraph = lock.read {
        WorkspaceGraph(
            version = 1,
            name = workspaceName,
            nodes = nodes.toList(),
            edges = edges.toList()
        )
    }

    fun getNode(nodeId: String): GraphNode? = lock.read {
        nodes.find { it.id == nodeId }
    }

    fun createNode(type: String, x: Double, y: Double, data: Map<String, Any?>): String {
        require(type in validNodeTypes) { "Unknown node type: $type. Valid types: ${validNodeTypes.joinToString()}" }
        val nodeId = "node-${nodeCounter.getAndIncrement()}"
        val node = GraphNode(id = nodeId, type = type, position = Position(x, y), data = data)
        lock.write { nodes.add(node) }
        emit(EditorEvent.NodeCreated(node))
        return nodeId
    }

    fun createEdge(sourceId: String, targetId: String): String {
        lock.write {
            val source = nodes.find { it.id == sourceId }
                ?: throw IllegalArgumentException("Source node not found: $sourceId")
            val target = nodes.find { it.id == targetId }
                ?: throw IllegalArgumentException("Target node not found: $targetId")
            require(source.type in sourceTypes) {
                "Cannot use ${source.type} as edge source. Source must be: ${sourceTypes.joinToString()}"
            }
            require(target.type in targetTypes) {
                "Cannot use ${target.type} as edge target. Target must be: ${targetTypes.joinToString()}"
            }
            val edgeId = "edge-${edgeCounter.getAndIncrement()}"
            val edge = GraphEdge(id = edgeId, source = sourceId, target = targetId)
            edges.add(edge)
            emit(EditorEvent.EdgeCreated(edge))
            return edgeId
        }
    }

    fun updateNode(nodeId: String, data: Map<String, Any?>) {
        lock.write {
            val index = nodes.indexOfFirst { it.id == nodeId }
            require(index >= 0) { "Node not found: $nodeId" }
            val existing = nodes[index]
            val merged = existing.data + data
            nodes[index] = existing.copy(data = merged)
        }
        emit(EditorEvent.NodeUpdated(nodeId, data))
    }

    fun deleteNode(nodeId: String) {
        lock.write {
            val removed = nodes.removeAll { it.id == nodeId }
            require(removed) { "Node not found: $nodeId" }
            val removedEdges = edges.filter { it.source == nodeId || it.target == nodeId }
            edges.removeAll { it.source == nodeId || it.target == nodeId }
            removedEdges.forEach { emit(EditorEvent.EdgeDeleted(it.id)) }
        }
        emit(EditorEvent.NodeDeleted(nodeId))
    }

    fun deleteEdge(edgeId: String) {
        lock.write {
            val removed = edges.removeAll { it.id == edgeId }
            require(removed) { "Edge not found: $edgeId" }
        }
        emit(EditorEvent.EdgeDeleted(edgeId))
    }

    fun setWorkspaceName(name: String) {
        lock.write { workspaceName = name }
        emit(EditorEvent.WorkspaceRenamed(name))
    }

    fun resetGraph() {
        lock.write {
            nodes.clear()
            edges.clear()
            workspaceName = "Untitled Workspace"
            nodeCounter.set(1)
            edgeCounter.set(1)
        }
        emit(EditorEvent.GraphReset(getGraph()))
    }

    fun syncGraph(graph: WorkspaceGraph) {
        lock.write {
            nodes = graph.nodes.toMutableList()
            edges = graph.edges.toMutableList()
            workspaceName = graph.name
            val maxNodeId = graph.nodes.mapNotNull { it.id.removePrefix("node-").toIntOrNull() }.maxOrNull() ?: 0
            val maxEdgeId = graph.edges.mapNotNull { it.id.removePrefix("edge-").toIntOrNull() }.maxOrNull() ?: 0
            nodeCounter.set(maxNodeId + 1)
            edgeCounter.set(maxEdgeId + 1)
        }
        emit(EditorEvent.GraphReset(getGraph()))
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.mcpserver.EditorStateServiceTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcpserver/EditorStateService.kt src/test/kotlin/com/agentwork/modeler/mcpserver/EditorStateServiceTest.kt
git commit -m "feat: add EditorStateService for graph state management"
```

---

### Task 4: Editor MCP Tools

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorMcpTools.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/mcpserver/EditorMcpToolsTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.validation.GraphValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertContains
import kotlin.test.assertFalse

class EditorMcpToolsTest {
    private lateinit var stateService: EditorStateService
    private lateinit var tools: EditorMcpTools

    @BeforeEach
    fun setUp() {
        stateService = EditorStateService()
        val validator = GraphValidator()
        val exportService = mock(ExportService::class.java)
        tools = EditorMcpTools(stateService, validator, exportService)
    }

    @Test
    fun `listNodeTypes returns all types`() {
        val result = tools.listNodeTypes()
        assertContains(result, "agent")
        assertContains(result, "tool")
        assertContains(result, "planner-agent")
        assertContains(result, "mcp-server")
    }

    @Test
    fun `createNode returns nodeId`() {
        val result = tools.createNode("agent", 100.0, 200.0, mapOf("name" to "Test", "systemPrompt" to "Hi"))
        assertContains(result, "node-")
        assertContains(result, "Node created successfully")
    }

    @Test
    fun `createNode fails for invalid type`() {
        val result = tools.createNode("invalid", 0.0, 0.0, emptyMap())
        assertContains(result, "Error")
    }

    @Test
    fun `getGraph returns graph JSON`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "x"))
        val result = tools.getGraph()
        assertContains(result, "Agent")
        assertContains(result, "nodes")
    }

    @Test
    fun `getNode returns node data`() {
        val createResult = tools.createNode("agent", 0.0, 0.0, mapOf("name" to "MyAgent", "systemPrompt" to "x"))
        val nodeId = extractNodeId(createResult)
        val result = tools.getNode(nodeId)
        assertContains(result, "MyAgent")
    }

    @Test
    fun `getNode fails for unknown id`() {
        val result = tools.getNode("nonexistent")
        assertContains(result, "not found")
    }

    @Test
    fun `createEdge connects nodes`() {
        val agentResult = tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "x"))
        val toolResult = tools.createNode("tool", 100.0, 0.0, mapOf("name" to "Tool", "description" to "d"))
        val agentId = extractNodeId(agentResult)
        val toolId = extractNodeId(toolResult)
        val result = tools.createEdge(toolId, agentId)
        assertContains(result, "edge-")
        assertContains(result, "Edge created successfully")
    }

    @Test
    fun `updateNode updates properties`() {
        val createResult = tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Old"))
        val nodeId = extractNodeId(createResult)
        val result = tools.updateNode(nodeId, mapOf("name" to "New"))
        assertContains(result, "updated")
        val nodeResult = tools.getNode(nodeId)
        assertContains(nodeResult, "New")
    }

    @Test
    fun `deleteNode removes node`() {
        val createResult = tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val nodeId = extractNodeId(createResult)
        val result = tools.deleteNode(nodeId)
        assertContains(result, "deleted")
        val graphResult = tools.getGraph()
        assertFalse(graphResult.contains(nodeId))
    }

    @Test
    fun `validateGraph returns validation results`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent", "systemPrompt" to "Help"))
        val result = tools.validateGraph()
        assertContains(result, "valid")
    }

    @Test
    fun `setWorkspaceName updates name`() {
        val result = tools.setWorkspaceName("NewName")
        assertContains(result, "NewName")
        val graph = tools.getGraph()
        assertContains(graph, "NewName")
    }

    @Test
    fun `resetGraph clears state`() {
        tools.createNode("agent", 0.0, 0.0, mapOf("name" to "Agent"))
        val result = tools.resetGraph()
        assertContains(result, "reset")
        val graph = tools.getGraph()
        assertFalse(graph.contains("Agent"))
    }

    @Test
    fun `applyTemplate creates basic-agent structure`() {
        val result = tools.applyTemplate("basic-agent")
        assertContains(result, "applied")
        val graph = tools.getGraph()
        assertContains(graph, "agent")
    }

    @Test
    fun `applyTemplate fails for unknown template`() {
        val result = tools.applyTemplate("nonexistent")
        assertContains(result, "Unknown template")
    }

    private fun extractNodeId(createResult: String): String {
        val regex = """"nodeId"\s*:\s*"(node-\d+)"""".toRegex()
        return regex.find(createResult)?.groupValues?.get(1) ?: error("No nodeId in: $createResult")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.mcpserver.EditorMcpToolsTest"`
Expected: FAIL

- [ ] **Step 3: Implement EditorMcpTools**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.validation.GraphValidator
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper

@Service
class EditorMcpTools(
    private val stateService: EditorStateService,
    private val validator: GraphValidator,
    private val exportService: ExportService
) {
    private val mapper = jacksonObjectMapper()

    @Tool(description = "Lists all available node types (agent, tool, builtin-tool, sub-agent, mcp-server, planner-agent) with their required and optional configuration fields")
    fun listNodeTypes(): String {
        val types = listOf(
            mapOf(
                "type" to "agent",
                "requiredFields" to listOf("name", "systemPrompt", "modelProvider"),
                "optionalFields" to listOf("modelId", "temperature"),
                "description" to "Main AI agent with a system prompt and model configuration"
            ),
            mapOf(
                "type" to "tool",
                "requiredFields" to listOf("name", "description"),
                "optionalFields" to listOf("parameters"),
                "description" to "Custom tool that an agent can use"
            ),
            mapOf(
                "type" to "builtin-tool",
                "requiredFields" to listOf("toolType"),
                "optionalFields" to emptyList<String>(),
                "description" to "Built-in tool. toolType must be one of: think, shell, filesystem, fetch, memory, todo, user_prompt"
            ),
            mapOf(
                "type" to "sub-agent",
                "requiredFields" to listOf("name", "description", "systemPrompt", "modelProvider", "delegationMode", "inputDescription"),
                "optionalFields" to listOf("modelId", "temperature"),
                "description" to "Delegable sub-agent. delegationMode: automatic, manual, fallback, or specialized"
            ),
            mapOf(
                "type" to "mcp-server",
                "requiredFields" to listOf("name", "transportType"),
                "optionalFields" to listOf("url", "command", "args", "envVars"),
                "description" to "External MCP server. transportType: sse or stdio. SSE requires url, STDIO requires command"
            ),
            mapOf(
                "type" to "planner-agent",
                "requiredFields" to listOf("name", "description", "plannerType"),
                "optionalFields" to listOf("modelProvider", "modelId", "temperature", "maxReplanAttempts", "goals", "actions", "initialState"),
                "description" to "Planner agent for multi-step tasks. plannerType: llm or goap"
            )
        )
        return mapper.writeValueAsString(mapOf("types" to types))
    }

    @Tool(description = "Returns the complete workspace graph including all nodes, edges, workspace name, and metadata")
    fun getGraph(): String {
        val graph = stateService.getGraph()
        return mapper.writeValueAsString(mapOf(
            "name" to graph.name,
            "nodes" to graph.nodes,
            "edges" to graph.edges,
            "nodeCount" to graph.nodes.size,
            "edgeCount" to graph.edges.size
        ))
    }

    @Tool(description = "Returns a specific node by ID with all its properties and configuration")
    fun getNode(@ToolParam(description = "The node ID (e.g. node-1)") nodeId: String): String {
        val node = stateService.getNode(nodeId)
            ?: return mapper.writeValueAsString(mapOf("error" to "Node not found: $nodeId"))
        return mapper.writeValueAsString(node)
    }

    @Tool(description = "Creates a new node of the given type at the specified canvas position with configuration data. Returns the assigned node ID")
    fun createNode(
        @ToolParam(description = "Node type: agent, tool, builtin-tool, sub-agent, mcp-server, or planner-agent") type: String,
        @ToolParam(description = "X position on the canvas") x: Double,
        @ToolParam(description = "Y position on the canvas") y: Double,
        @ToolParam(description = "Configuration data as key-value pairs (e.g. name, systemPrompt, modelProvider)") data: Map<String, Any>
    ): String {
        return try {
            val nodeId = stateService.createNode(type, x, y, data)
            mapper.writeValueAsString(mapOf("nodeId" to nodeId, "type" to type, "message" to "Node created successfully"))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to "Error: ${e.message}"))
        }
    }

    @Tool(description = "Creates a directed edge from source node to target node. Source must be a tool, builtin-tool, sub-agent, or mcp-server. Target must be an agent, sub-agent, or planner-agent")
    fun createEdge(
        @ToolParam(description = "Source node ID (must be tool, builtin-tool, sub-agent, or mcp-server)") sourceId: String,
        @ToolParam(description = "Target node ID (must be agent, sub-agent, or planner-agent)") targetId: String
    ): String {
        return try {
            val edgeId = stateService.createEdge(sourceId, targetId)
            mapper.writeValueAsString(mapOf("edgeId" to edgeId, "message" to "Edge created successfully"))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to "Error: ${e.message}"))
        }
    }

    @Tool(description = "Updates specific properties of an existing node. Merges provided data with existing configuration (partial update)")
    fun updateNode(
        @ToolParam(description = "The node ID to update") nodeId: String,
        @ToolParam(description = "Properties to update as key-value pairs") data: Map<String, Any>
    ): String {
        return try {
            stateService.updateNode(nodeId, data)
            mapper.writeValueAsString(mapOf("nodeId" to nodeId, "message" to "Node updated successfully"))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to "Error: ${e.message}"))
        }
    }

    @Tool(description = "Deletes a node and automatically removes all edges connected to it")
    fun deleteNode(@ToolParam(description = "The node ID to delete") nodeId: String): String {
        return try {
            stateService.deleteNode(nodeId)
            mapper.writeValueAsString(mapOf("nodeId" to nodeId, "message" to "Node deleted successfully"))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to "Error: ${e.message}"))
        }
    }

    @Tool(description = "Deletes a specific edge by its ID")
    fun deleteEdge(@ToolParam(description = "The edge ID to delete") edgeId: String): String {
        return try {
            stateService.deleteEdge(edgeId)
            mapper.writeValueAsString(mapOf("edgeId" to edgeId, "message" to "Edge deleted successfully"))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to "Error: ${e.message}"))
        }
    }

    @Tool(description = "Sets the workspace name displayed in the editor")
    fun setWorkspaceName(@ToolParam(description = "The new workspace name") name: String): String {
        stateService.setWorkspaceName(name)
        return mapper.writeValueAsString(mapOf("name" to name, "message" to "Workspace name set to '$name'"))
    }

    @Tool(description = "Removes all nodes and edges, resets to an empty workspace")
    fun resetGraph(): String {
        stateService.resetGraph()
        return mapper.writeValueAsString(mapOf("message" to "Graph reset successfully"))
    }

    @Tool(description = "Applies a predefined structure template. Available templates: basic-agent, agent-with-tools, agent-with-mcp, planner-agent")
    fun applyTemplate(@ToolParam(description = "Template name: basic-agent, agent-with-tools, agent-with-mcp, or planner-agent") template: String): String {
        return try {
            when (template) {
                "basic-agent" -> {
                    stateService.resetGraph()
                    stateService.createNode("agent", 200.0, 200.0, mapOf(
                        "name" to "MyAgent",
                        "systemPrompt" to "You are a helpful assistant.",
                        "modelProvider" to "anthropic"
                    ))
                }
                "agent-with-tools" -> {
                    stateService.resetGraph()
                    val agentId = stateService.createNode("agent", 200.0, 200.0, mapOf(
                        "name" to "MyAgent",
                        "systemPrompt" to "You are a helpful assistant.",
                        "modelProvider" to "anthropic"
                    ))
                    val tool1Id = stateService.createNode("tool", 400.0, 100.0, mapOf(
                        "name" to "SearchTool",
                        "description" to "Searches for information"
                    ))
                    val tool2Id = stateService.createNode("tool", 400.0, 300.0, mapOf(
                        "name" to "CalculatorTool",
                        "description" to "Performs calculations"
                    ))
                    stateService.createEdge(tool1Id, agentId)
                    stateService.createEdge(tool2Id, agentId)
                }
                "agent-with-mcp" -> {
                    stateService.resetGraph()
                    val agentId = stateService.createNode("agent", 200.0, 200.0, mapOf(
                        "name" to "MyAgent",
                        "systemPrompt" to "You are a helpful assistant.",
                        "modelProvider" to "anthropic"
                    ))
                    val mcpId = stateService.createNode("mcp-server", 400.0, 200.0, mapOf(
                        "name" to "ExternalServer",
                        "transportType" to "sse",
                        "url" to "http://localhost:3001/sse"
                    ))
                    stateService.createEdge(mcpId, agentId)
                }
                "planner-agent" -> {
                    stateService.resetGraph()
                    stateService.createNode("planner-agent", 200.0, 200.0, mapOf(
                        "name" to "MyPlanner",
                        "description" to "Plans and executes multi-step tasks",
                        "plannerType" to "llm",
                        "modelProvider" to "anthropic",
                        "maxReplanAttempts" to 3,
                        "goals" to listOf(mapOf(
                            "id" to "goal-1",
                            "name" to "task_complete",
                            "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)),
                            "priority" to 1
                        )),
                        "actions" to listOf(mapOf(
                            "id" to "action-1",
                            "name" to "execute_step",
                            "description" to "Execute a single step",
                            "preconditions" to listOf(mapOf("key" to "ready", "operator" to "equals", "value" to true)),
                            "effects" to listOf(mapOf("key" to "done", "value" to true)),
                            "cost" to 1
                        )),
                        "initialState" to listOf(
                            mapOf("key" to "ready", "value" to true),
                            mapOf("key" to "done", "value" to false)
                        )
                    ))
                }
                else -> return mapper.writeValueAsString(mapOf(
                    "error" to "Unknown template: $template. Available: basic-agent, agent-with-tools, agent-with-mcp, planner-agent"
                ))
            }
            mapper.writeValueAsString(mapOf("template" to template, "message" to "Template '$template' applied successfully"))
        } catch (e: Exception) {
            mapper.writeValueAsString(mapOf("error" to "Error applying template: ${e.message}"))
        }
    }

    @Tool(description = "Runs validation on the current graph and returns all errors and warnings with affected node IDs")
    fun validateGraph(): String {
        val graph = stateService.getGraph()
        val errors = validator.validate(graph)
        return mapper.writeValueAsString(mapOf(
            "valid" to errors.isEmpty(),
            "errors" to errors.map { mapOf("message" to it) }
        ))
    }

    @Tool(description = "Generates Kotlin code for the current graph configuration and returns a listing of generated files with their content")
    fun exportCode(): String {
        return try {
            val graph = stateService.getGraph()
            val (_, files) = exportService.exportWithFiles(graph)
            mapper.writeValueAsString(mapOf(
                "fileCount" to files.size,
                "files" to files.map { (path, content) -> mapOf("path" to path, "content" to content) }
            ))
        } catch (e: Exception) {
            mapper.writeValueAsString(mapOf("error" to "Export failed: ${e.message}"))
        }
    }
}
```

- [ ] **Step 4: Register tools as ToolCallbackProvider bean**

Create `src/main/kotlin/com/agentwork/modeler/mcpserver/McpServerConfig.kt`:

```kotlin
package com.agentwork.modeler.mcpserver

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpServerConfig {
    @Bean
    fun editorToolCallbackProvider(editorMcpTools: EditorMcpTools): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(editorMcpTools)
            .build()
    }
}
```

- [ ] **Step 5: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.mcpserver.EditorMcpToolsTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcpserver/EditorMcpTools.kt src/main/kotlin/com/agentwork/modeler/mcpserver/McpServerConfig.kt src/test/kotlin/com/agentwork/modeler/mcpserver/EditorMcpToolsTest.kt
git commit -m "feat: add EditorMcpTools with 13 MCP tools and ToolCallbackProvider config"
```

---

### Task 5: Editor SSE Controller

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSseController.kt`

- [ ] **Step 1: Create EditorSseController**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.mcpserver.model.EditorEvent
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/editor")
class EditorSseController(
    private val stateService: EditorStateService
) {
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): SseEmitter {
        val emitter = SseEmitter(0L)
        val listener: (EditorEvent) -> Unit = { event ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(event.type)
                        .data(event.toJson())
                )
            } catch (_: Exception) {
                stateService.unsubscribe(listener)
            }
        }
        // Store reference for cleanup
        val listenerRef = listener
        stateService.subscribe(listenerRef)

        emitter.onCompletion { stateService.unsubscribe(listenerRef) }
        emitter.onTimeout { stateService.unsubscribe(listenerRef) }
        emitter.onError { stateService.unsubscribe(listenerRef) }

        return emitter
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSseController.kt
git commit -m "feat: add EditorSseController for frontend SSE event stream"
```

---

### Task 6: Editor Sync Controller

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSyncController.kt`

- [ ] **Step 1: Create EditorSyncController**

```kotlin
package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/editor")
class EditorSyncController(
    private val stateService: EditorStateService
) {
    @PostMapping("/sync")
    fun syncGraph(@RequestBody graph: WorkspaceGraph): ResponseEntity<Map<String, Any>> {
        stateService.syncGraph(graph)
        return ResponseEntity.ok(mapOf(
            "message" to "Graph synced successfully",
            "nodeCount" to graph.nodes.size,
            "edgeCount" to graph.edges.size
        ))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSyncController.kt
git commit -m "feat: add EditorSyncController for frontend graph sync"
```

---

### Task 7: Frontend Sync Client

**Files:**
- Create: `frontend/lib/editor-sync-client.ts`

- [ ] **Step 1: Create editor-sync-client.ts**

```typescript
import { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function syncGraph(graph: WorkspaceGraph): Promise<void> {
  const response = await fetch(`${API_BASE}/api/editor/sync`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(graph),
  });
  if (!response.ok) {
    throw new Error(`Sync failed: ${response.statusText}`);
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/editor-sync-client.ts
git commit -m "feat: add editor-sync-client for frontend→backend graph sync"
```

---

### Task 8: Frontend SSE Client

**Files:**
- Create: `frontend/lib/editor-sse-client.ts`

- [ ] **Step 1: Create editor-sse-client.ts**

```typescript
import { GraphNode, GraphEdge, WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface EditorSseEvent {
  type: string;
  node?: GraphNode;
  nodeId?: string;
  data?: Record<string, unknown>;
  edge?: GraphEdge;
  edgeId?: string;
  graph?: WorkspaceGraph;
  name?: string;
  errors?: Array<{ nodeId: string; severity: string; message: string }>;
}

type EventHandler = (event: EditorSseEvent) => void;

let eventSource: EventSource | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

export function connectEditorSse(onEvent: EventHandler): () => void {
  const eventTypes = [
    "node-created",
    "node-updated",
    "node-deleted",
    "edge-created",
    "edge-deleted",
    "graph-reset",
    "workspace-renamed",
    "validation-updated",
  ];

  function connect() {
    if (eventSource) {
      eventSource.close();
    }

    eventSource = new EventSource(`${API_BASE}/api/editor/events`);

    for (const eventType of eventTypes) {
      eventSource.addEventListener(eventType, (e: MessageEvent) => {
        try {
          const parsed = JSON.parse(e.data) as EditorSseEvent;
          onEvent(parsed);
        } catch {
          console.error("Failed to parse SSE event:", e.data);
        }
      });
    }

    eventSource.onerror = () => {
      eventSource?.close();
      eventSource = null;
      // Reconnect after 3 seconds
      if (reconnectTimer) clearTimeout(reconnectTimer);
      reconnectTimer = setTimeout(connect, 3000);
    };
  }

  connect();

  return () => {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    eventSource?.close();
    eventSource = null;
  };
}

export function isEditorSseConnected(): boolean {
  return eventSource !== null && eventSource.readyState === EventSource.OPEN;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/editor-sse-client.ts
git commit -m "feat: add editor-sse-client for receiving backend events via SSE"
```

---

### Task 9: EditorStore Extensions

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Add applyRemote methods to EditorState interface**

Read `frontend/store/editorStore.ts` first. In the `EditorState` interface (around lines 10-30), add these methods after the existing ones:

```typescript
applyRemoteNodeCreated: (node: GraphNode) => void;
applyRemoteNodeUpdated: (nodeId: string, data: Record<string, unknown>) => void;
applyRemoteNodeDeleted: (nodeId: string) => void;
applyRemoteEdgeCreated: (edge: GraphEdge) => void;
applyRemoteEdgeDeleted: (edgeId: string) => void;
applyRemoteGraphReset: (graph: { name: string; nodes: GraphNode[]; edges: GraphEdge[] }) => void;
applyRemoteWorkspaceRenamed: (name: string) => void;
```

- [ ] **Step 2: Implement applyRemote methods in the store**

In the `useEditorStore` create block, add the implementations after the existing `reset` method:

```typescript
applyRemoteNodeCreated: (node) => {
  set((state) => ({ nodes: [...state.nodes, node] }));
},
applyRemoteNodeUpdated: (nodeId, data) => {
  set((state) => ({
    nodes: state.nodes.map((n) =>
      n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n
    ),
  }));
},
applyRemoteNodeDeleted: (nodeId) => {
  set((state) => ({
    nodes: state.nodes.filter((n) => n.id !== nodeId),
    edges: state.edges.filter((e) => e.source !== nodeId && e.target !== nodeId),
    selectedNodeIds: state.selectedNodeIds.filter((id) => id !== nodeId),
  }));
},
applyRemoteEdgeCreated: (edge) => {
  set((state) => ({ edges: [...state.edges, edge] }));
},
applyRemoteEdgeDeleted: (edgeId) => {
  set((state) => ({
    edges: state.edges.filter((e) => e.id !== edgeId),
  }));
},
applyRemoteGraphReset: (graph) => {
  set({
    nodes: graph.nodes,
    edges: graph.edges,
    workspaceName: graph.name,
    selectedNodeIds: [],
  });
},
applyRemoteWorkspaceRenamed: (name) => {
  set({ workspaceName: name });
},
```

Note: These methods do NOT call `pushHistory()` — they are backend-authoritative updates, not user actions.

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat: add applyRemote methods to editorStore for SSE event handling"
```

---

### Task 10: Page SSE Connection and Event Dispatch

**Files:**
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Add SSE connection setup**

Read `frontend/app/page.tsx` first. Add imports:

```typescript
import { connectEditorSse, EditorSseEvent } from "@/lib/editor-sse-client";
import { syncGraph } from "@/lib/editor-sync-client";
import { buildWorkspaceGraph } from "@/store/editorStore";
```

Add a new `useEffect` hook after the existing ones (around line 82) for SSE connection:

```typescript
// SSE connection to backend
const [mcpConnected, setMcpConnected] = useState(false);

useEffect(() => {
  const store = useEditorStore.getState();

  const handleEvent = (event: EditorSseEvent) => {
    const s = useEditorStore.getState();
    switch (event.type) {
      case "node-created":
        if (event.node) s.applyRemoteNodeCreated(event.node);
        break;
      case "node-updated":
        if (event.nodeId && event.data) s.applyRemoteNodeUpdated(event.nodeId, event.data);
        break;
      case "node-deleted":
        if (event.nodeId) s.applyRemoteNodeDeleted(event.nodeId);
        break;
      case "edge-created":
        if (event.edge) s.applyRemoteEdgeCreated(event.edge);
        break;
      case "edge-deleted":
        if (event.edgeId) s.applyRemoteEdgeDeleted(event.edgeId);
        break;
      case "graph-reset":
        if (event.graph) s.applyRemoteGraphReset(event.graph);
        break;
      case "workspace-renamed":
        if (event.name) s.applyRemoteWorkspaceRenamed(event.name);
        break;
    }
  };

  // Initial sync + connect SSE
  const graph = buildWorkspaceGraph();
  syncGraph(graph)
    .then(() => {
      setMcpConnected(true);
    })
    .catch(() => {
      setMcpConnected(false);
    });

  const disconnect = connectEditorSse((event) => {
    setMcpConnected(true);
    handleEvent(event);
  });

  return () => {
    disconnect();
    setMcpConnected(false);
  };
}, []);
```

Pass `mcpConnected` as a prop to the Toolbar component:

```tsx
<Toolbar onNewWorkspace={handleNewWorkspaceRequest} mcpConnected={mcpConnected} />
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: Will fail until Toolbar is updated (Task 11). Move on.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat: add SSE connection and event dispatch to editor page"
```

---

### Task 11: Toolbar MCP Connection Indicator

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Update Toolbar props and add indicator**

Read `frontend/components/panels/Toolbar.tsx` first. Update the component signature to accept `mcpConnected`:

```typescript
export function Toolbar({ onNewWorkspace, mcpConnected = false }: { onNewWorkspace: () => void; mcpConnected?: boolean }) {
```

At the end of the toolbar (before the closing container div), add the MCP connection indicator:

```tsx
<div className="flex items-center gap-1.5 ml-2 text-xs text-muted-foreground">
  <span className={`inline-block h-2 w-2 rounded-full ${mcpConnected ? "bg-green-400" : "bg-gray-500"}`} />
  {mcpConnected ? "MCP" : "MCP Off"}
</div>
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx
git commit -m "feat: add MCP connection indicator to Toolbar"
```

---

### Task 12: Backend Full Test Run and Frontend Build

**Files:** None (verification only)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test`
Expected: All tests pass (except the 2 pre-existing MCP export failures)

- [ ] **Step 2: Verify frontend TypeScript compilation**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Verify frontend builds**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 4: Start backend and verify MCP endpoint**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew bootRun`
Then in another terminal: `curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'`
Expected: JSON-RPC response with server info and capabilities
