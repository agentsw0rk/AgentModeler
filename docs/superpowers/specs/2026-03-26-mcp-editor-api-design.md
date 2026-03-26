# MCP Editor API Design

## Overview

The Agent Studio backend exposes itself as an MCP Server using Streamable HTTP transport. External MCP clients (Claude Code, Cursor, etc.) can connect and use tools to create, modify, inspect, and export AI agent graphs. The backend becomes the source-of-truth for graph state. The frontend syncs on connect and receives all updates (both its own and external) via an SSE stream.

## Key Decisions

- **Architecture:** MCP Server via Spring AI Starter (`spring-ai-starter-mcp-server-webmvc`)
- **Transport:** Streamable HTTP (single `/mcp` endpoint, JSON-RPC 2.0)
- **Tools:** 13 tools — full CRUD for nodes/edges, templates, validation, export, introspection
- **Session:** Single-session — one MCP server = one workspace
- **State:** Backend is source-of-truth (in-memory, 60min TTL)
- **Sync:** Frontend syncs state on-connect, then receives SSE events
- **Tool descriptions:** Written so LLMs can understand and use them automatically

## Backend Architecture

### New Package: `mcpserver/`

```
src/main/kotlin/com/agentwork/modeler/mcpserver/
├── EditorMcpTools.kt        # @Tool-annotated methods (MCP interface)
├── EditorStateService.kt    # Graph state management (source-of-truth)
├── EditorSseController.kt   # SSE endpoint for frontend updates
├── EditorSyncController.kt  # REST endpoint for frontend→backend sync
└── model/
    ├── EditorCommand.kt     # DTOs for MCP tool parameters
    └── EditorEvent.kt       # SSE event types
```

### Spring AI MCP Server Configuration

Add dependency:
```gradle
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
```

Configuration in `application.yaml`:
```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: agent-studio-editor
        version: 1.0.0
        instructions: "MCP server for the Agent Studio visual editor. Provides tools to create, modify, and inspect AI agent graphs consisting of agents, tools, sub-agents, MCP servers, and planner agents."
        streamable-http:
          mcp-endpoint: /mcp
```

Tools are registered via a `ToolCallbackProvider` bean that wraps `EditorMcpTools`.

### EditorStateService

- Holds a `WorkspaceGraph` in memory
- Thread-safe via `ReentrantReadWriteLock`
- Every mutation produces an `EditorEvent` and pushes it to all SSE subscribers
- Node IDs assigned by backend: `node-{counter}`
- Edge IDs assigned by backend: `edge-{counter}`
- 60-minute inactivity TTL, scheduled cleanup

### EditorSseController

- `GET /api/editor/events` → `SseEmitter(0L)` (no timeout)
- Follows existing Preview SSE pattern
- Events sent as: `emitter.send(SseEmitter.event().name(event.type).data(event.toJson()))`

### EditorSyncController

- `POST /api/editor/sync` — receives `WorkspaceGraph` from frontend, replaces backend state
- Returns `200 OK` with confirmation
- Triggers `graph-reset` event to all other SSE subscribers

### CORS

Extend existing CORS config to allow `/mcp` endpoint access.

## MCP Tools

### Tool Catalog

| Tool | Parameters | LLM Description |
|------|------------|-----------------|
| `list_node_types` | — | "Lists all available node types (agent, tool, builtin-tool, sub-agent, mcp-server, planner-agent) with their required and optional configuration fields" |
| `get_graph` | — | "Returns the complete workspace graph including all nodes, edges, workspace name, and metadata" |
| `get_node` | `nodeId: String` | "Returns a specific node by ID with all its properties and configuration" |
| `create_node` | `type: String, x: Double, y: Double, data: Map<String, Any>` | "Creates a new node of the given type at the specified canvas position with configuration data. Returns the assigned node ID" |
| `create_edge` | `sourceId: String, targetId: String` | "Creates a directed edge from source node to target node. Source must be a tool, builtin-tool, sub-agent, or mcp-server. Target must be an agent, sub-agent, or planner-agent" |
| `update_node` | `nodeId: String, data: Map<String, Any>` | "Updates specific properties of an existing node. Merges provided data with existing configuration (partial update)" |
| `delete_node` | `nodeId: String` | "Deletes a node and automatically removes all edges connected to it" |
| `delete_edge` | `edgeId: String` | "Deletes a specific edge by its ID" |
| `set_workspace_name` | `name: String` | "Sets the workspace name displayed in the editor" |
| `reset_graph` | — | "Removes all nodes and edges, resets to an empty workspace" |
| `apply_template` | `template: String` | "Applies a predefined structure template. Available templates: basic-agent, agent-with-tools, agent-with-mcp, planner-agent" |
| `validate_graph` | — | "Runs validation on the current graph and returns all errors and warnings with affected node IDs" |
| `export_code` | — | "Generates Kotlin code for the current graph configuration and returns a listing of generated files with their content" |

### Templates

- `basic-agent` — One agent node with default config
- `agent-with-tools` — Agent + 2 custom tools, connected
- `agent-with-mcp` — Agent + MCP server, connected
- `planner-agent` — Planner agent with example goals and actions

### Return Format

All tools return structured JSON strings:

```json
// create_node
{ "nodeId": "node-5", "type": "agent", "message": "Node created successfully" }

// validate_graph
{ "valid": false, "errors": [{"nodeId": "node-1", "severity": "error", "message": "..."}], "warnings": [...] }

// get_graph
{ "name": "MyWorkspace", "nodes": [...], "edges": [...] }

// list_node_types
{ "types": [{"type": "agent", "requiredFields": ["name", "systemPrompt", "modelProvider"], ...}] }
```

### Tool Validation

- `create_node`: Type must be valid, position required
- `create_edge`: Source and target must exist, connection type must be allowed
- `update_node`: Node must exist
- `delete_node`/`delete_edge`: ID must exist
- `apply_template`: Template name must be known
- All validation errors returned as structured JSON with clear error messages

## SSE Events

### Event Types

```kotlin
sealed class EditorEvent(val type: String) {
    data class NodeCreated(val node: GraphNode) : EditorEvent("node-created")
    data class NodeUpdated(val nodeId: String, val data: Map<String, Any?>) : EditorEvent("node-updated")
    data class NodeDeleted(val nodeId: String) : EditorEvent("node-deleted")
    data class EdgeCreated(val edge: GraphEdge) : EditorEvent("edge-created")
    data class EdgeDeleted(val edgeId: String) : EditorEvent("edge-deleted")
    data class GraphReset(val graph: WorkspaceGraph) : EditorEvent("graph-reset")
    data class WorkspaceRenamed(val name: String) : EditorEvent("workspace-renamed")
    data class ValidationUpdated(val errors: List<ValidationError>) : EditorEvent("validation-updated")
}
```

### Event Format (SSE)

```
event: node-created
data: {"type":"node-created","node":{"id":"node-5","type":"agent","position":{"x":100,"y":200},"data":{...}}}

event: validation-updated
data: {"type":"validation-updated","errors":[...]}
```

## Frontend Integration

### New Files

- `frontend/lib/editor-sse-client.ts` — SSE stream connection + event dispatch
- `frontend/lib/editor-sync-client.ts` — Sync graph state to backend

### EditorStore Extensions

New methods for incoming SSE events (no history push, no backend re-sync):
- `applyRemoteNodeCreated(node: GraphNode)`
- `applyRemoteNodeUpdated(nodeId: string, data: Partial<...>)`
- `applyRemoteNodeDeleted(nodeId: string)`
- `applyRemoteEdgeCreated(edge: GraphEdge)`
- `applyRemoteEdgeDeleted(edgeId: string)`
- `applyRemoteGraphReset(graph: WorkspaceGraph)`
- `applyRemoteWorkspaceRenamed(name: string)`

Existing mutations (`addNode`, `updateNodeData`, etc.) are extended to also push changes to the backend via `POST /api/editor/sync` or granular endpoints.

### Connection Flow

1. Frontend opens SSE stream to `GET /api/editor/events`
2. Frontend sends current graph via `POST /api/editor/sync`
3. Backend takes over as source-of-truth
4. All changes (frontend or MCP) flow through backend → SSE → frontend
5. Auto-reconnect on connection loss with re-sync

### Visual Indicators

- Toolbar indicator: "MCP Connected" (green dot) / "MCP Disconnected" (grey)
- Subtle toast or node pulse when external MCP changes arrive

## Error Handling

- Invalid node IDs → `"Node not found: node-42"`
- Invalid connections → `"Cannot connect agent to agent. Source must be tool, builtin-tool, sub-agent, or mcp-server"`
- Invalid node types → `"Unknown node type: foo. Valid types: agent, tool, builtin-tool, sub-agent, mcp-server, planner-agent"`
- Unknown templates → `"Unknown template: xyz. Available: basic-agent, agent-with-tools, agent-with-mcp, planner-agent"`
- All errors as structured JSON strings that LLMs can parse

## Tests

### Backend Tests

| Test Class | Coverage |
|------------|----------|
| `EditorStateServiceTest` | Graph mutations (create/update/delete node/edge), event generation, thread safety, reset |
| `EditorMcpToolsTest` | Each tool: correct return values, error handling for invalid inputs |
| `EditorSseControllerTest` | SSE stream delivers events after mutations |
| `EditorSyncControllerTest` | Sync accepts and applies graph state |

### Test Pattern

Direct instantiation, `assertEquals`/`assertContains` on JSON responses. Follows existing `AgentGeneratorTest` pattern.

## Files to Create

### Backend
1. `src/main/kotlin/com/agentwork/modeler/mcpserver/model/EditorEvent.kt`
2. `src/main/kotlin/com/agentwork/modeler/mcpserver/model/EditorCommand.kt`
3. `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorStateService.kt`
4. `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorMcpTools.kt`
5. `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSseController.kt`
6. `src/main/kotlin/com/agentwork/modeler/mcpserver/EditorSyncController.kt`
7. `src/test/kotlin/com/agentwork/modeler/mcpserver/EditorStateServiceTest.kt`
8. `src/test/kotlin/com/agentwork/modeler/mcpserver/EditorMcpToolsTest.kt`

### Backend (modify)
1. `build.gradle.kts` — Add MCP server dependency
2. `src/main/resources/application.yaml` — Add MCP server config
3. `src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt` — Allow `/mcp`

### Frontend
1. `frontend/lib/editor-sse-client.ts`
2. `frontend/lib/editor-sync-client.ts`

### Frontend (modify)
1. `frontend/store/editorStore.ts` — Add applyRemote* methods, extend mutations
2. `frontend/app/page.tsx` — Initialize SSE connection
3. `frontend/components/panels/Toolbar.tsx` — MCP connection indicator
