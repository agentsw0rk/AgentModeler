# A2A Communication Feature — Design Spec

## Overview

Add Agent-to-Agent (A2A) communication to the Koog Agent Modeler. Agents can be published as A2A endpoints (incoming) and call remote A2A agents (outgoing). The feature is a communication capability layered on existing agent types, not a new agent category.

## Architecture

### Integration Strategy

- **Incoming A2A (Agent as Server):** Optional `A2AConfig` on Agent and PlannerAgent nodes. Toggle enables A2A, configures AgentCard, server transport.
- **Outgoing A2A (Call Remote Agent):** New `a2a-agent` node type. Connected to an Agent like Tool/MCP nodes. Represents a remote A2A endpoint.
- **Inspection:** A2A Inspector panel in ActivityBar for message/task status visualization.

### Node Color

`a2a-agent` nodes use rose/pink (`rose-500`, hex `#f43f5e`) — distinct from all existing node colors (blue=agent, green=tool, amber=sub-agent, purple=mcp, teal=graph-agent).

## Domain Model

### Frontend Types (`types/a2a.ts`)

```typescript
// --- Incoming: Agent as A2A Server ---

export interface A2AConfig {
  enabled: boolean;
  agentCard: AgentCard;
  server: A2AServerConfig;
}

export interface AgentCard {
  name: string;
  description: string;
  url: string;
  version: string;
  capabilities: A2ACapabilities;
  skills: A2ASkill[];
  defaultInputModes: string[];
  defaultOutputModes: string[];
}

export interface A2ACapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

export interface A2ASkill {
  id: string;
  name: string;
  description: string;
  tags: string[];
}

export interface A2AServerConfig {
  transportType: "http-jsonrpc";
  port: number;
  basePath: string;
}

// --- Outgoing: Remote A2A Agent Node ---

export interface A2AAgentConfig {
  name: string;
  description: string;
  url: string;
  authType: "none" | "bearer" | "api-key";
  authToken?: string;
  timeout: number;
  communicationMode: "message" | "task";
}

// --- Inspection/Events ---

export type A2ATaskStatus = "submitted" | "working" | "input-required" | "completed" | "failed" | "canceled";

export interface A2AMessage {
  id: string;
  role: "user" | "agent";
  parts: A2AMessagePart[];
  timestamp: string;
}

export interface A2AMessagePart {
  type: "text" | "file" | "data";
  content: string;
  mimeType?: string;
}

export interface A2ATaskEvent {
  id: string;
  taskId: string;
  status: A2ATaskStatus;
  message?: A2AMessage;
  timestamp: string;
}
```

### Backend Model (`export/model/A2AModel.kt`)

```kotlin
data class KoogA2AConfig(
    val enabled: Boolean,
    val agentCard: KoogAgentCard,
    val server: KoogA2AServerConfig
)

data class KoogAgentCard(
    val name: String,
    val description: String,
    val url: String,
    val version: String,
    val capabilities: KoogA2ACapabilities,
    val skills: List<KoogA2ASkill>,
    val defaultInputModes: List<String>,
    val defaultOutputModes: List<String>
)

data class KoogA2ACapabilities(
    val streaming: Boolean,
    val pushNotifications: Boolean,
    val stateTransitionHistory: Boolean
)

data class KoogA2ASkill(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>
)

data class KoogA2AServerConfig(
    val transportType: String,
    val port: Int,
    val basePath: String
)

data class KoogA2AAgent(
    val name: String,
    val description: String,
    val url: String,
    val authType: String,
    val authToken: String?,
    val timeout: Int,
    val communicationMode: String
)
```

## Graph Integration

### NodeType Extension

Add `"a2a-agent"` to `NodeType` union (frontend) and recognized types (backend).

### Connection Rules

- `a2a-agent` → `agent` (allowed)
- `a2a-agent` → `planner-agent` (allowed)
- `a2a-agent` → `sub-agent` (not allowed)
- `a2a-agent` → `a2a-agent` (not allowed)

### AgentConfig Extension

Add optional `a2aConfig?: A2AConfig` to `AgentConfig` and `PlannerAgentConfig`.

### GraphNode.data

`GraphNode.data` union includes `A2AAgentConfig` when `type === "a2a-agent"`.

## UI Components

### A2AAgentProperties (`components/a2a/A2AAgentProperties.tsx`)

Properties panel for `a2a-agent` node:
- Name, description (text inputs)
- URL (text input with validation indicator)
- Auth type (select: none/bearer/api-key) + token field
- Timeout (number input, default 30000)
- Communication mode (select: message/task)

### A2AConfigSection (inside AgentProperties/PlannerProperties)

Collapsible section within existing agent property panels:
- Enable/disable toggle
- When enabled, shows:
  - AgentCard fields: name, description, url, version
  - Capabilities toggles: streaming, pushNotifications, stateTransitionHistory
  - Skills list: add/remove skills with name, description, tags
  - Server config: port, basePath
  - Input/output modes: multi-select checkboxes

### A2AInspectorPanel (`components/a2a/A2AInspectorPanel.tsx`)

New ActivityBar tab (icon: arrows-right-left or similar):
- Tab 1: Messages — sent/received messages with role badges and timestamps
- Tab 2: Tasks — task status timeline with status badges (submitted→working→completed/failed)
- Connected agent selector at top
- Clear button
- Simulated data in editor (real data only in preview/runtime)

## Validation Rules

| # | Rule | Severity | Location |
|---|------|----------|----------|
| 1 | A2A enabled, AgentCard.name empty | error | agent node |
| 2 | A2A enabled, AgentCard.url empty | error | agent node |
| 3 | A2A enabled, no skills defined | warning | agent node |
| 4 | a2a-agent node: url empty | error | a2a-agent node |
| 5 | a2a-agent node: url invalid format | error | a2a-agent node |
| 6 | a2a-agent node: not connected to any agent | error | a2a-agent node |
| 7 | a2a-agent node: timeout <= 0 | warning | a2a-agent node |
| 8 | a2a-agent: communicationMode "task" but target agent has no streaming capability | warning | a2a-agent node |

Implemented in both frontend (`lib/validation.ts`) and backend (`GraphValidator.kt`).

## Code Generation

### A2AServerGenerator

For agents with `a2aConfig.enabled == true`, generates:

- `A2AAgentCard.kt` — AgentCard data object with all configured fields
- `A2AExecutor.kt` — AgentExecutor skeleton that wraps the generated agent, handles incoming A2A requests, maps to agent invocations
- `A2AServerConfig.kt` — HTTP JSON-RPC server setup with configured port/basePath
- `A2AStreamingHandler.kt` — SSE endpoint for streaming task updates (if streaming capability enabled)

### A2AClientGenerator

For connected `a2a-agent` nodes, generates:

- `A2AClient_{name}.kt` — Client class per remote agent with:
  - `sendMessage(message)` — direct message-based call
  - `sendTask(message)` — task-based call with status tracking
  - `getTaskStatus(taskId)` — poll task status
  - `subscribeToUpdates(taskId)` — SSE subscription for streaming updates
  - Auth header configuration
  - Timeout handling

Both generators integrate into `ExportService` alongside existing 13 generators.

## Adapter Changes

`GraphToKoogAdapter` extended:
- Parse `a2a-agent` nodes into `KoogA2AAgent` list
- Extract `A2AConfig` from agent/planner-agent node data
- New return fields on conversion result

## Test Plan

### Backend Tests

- `A2AModelTest.kt` — Serialization/deserialization of A2A types
- `GraphToKoogAdapterA2ATest.kt` — Adapter converts a2a-agent nodes and A2AConfig correctly
- `GraphValidatorA2ATest.kt` — All 8 validation rules
- `A2AServerGeneratorTest.kt` — Generated AgentCard, Executor, ServerConfig, StreamingHandler
- `A2AClientGeneratorTest.kt` — Generated client with message/task methods, auth, timeout

### Frontend Tests

- Frontend validation mirrors backend rules (existing pattern)

## File Changes Summary

### New Files

| File | Purpose |
|------|---------|
| `frontend/types/a2a.ts` | A2A TypeScript types |
| `frontend/components/a2a/A2AAgentProperties.tsx` | a2a-agent node properties |
| `frontend/components/a2a/A2AConfigSection.tsx` | A2A config on agent nodes |
| `frontend/components/a2a/A2AInspectorPanel.tsx` | Message/task inspector |
| `src/.../export/model/A2AModel.kt` | Backend A2A data classes |
| `src/.../export/generator/A2AServerGenerator.kt` | Server code generator |
| `src/.../export/generator/A2AClientGenerator.kt` | Client code generator |
| Tests (5 files) | See test plan |

### Modified Files

| File | Change |
|------|--------|
| `frontend/types/graph.ts` | Add `"a2a-agent"` to NodeType, A2AAgentConfig to data union |
| `frontend/types/koog.ts` | Add optional `a2aConfig` to AgentConfig |
| `frontend/types/planner.ts` | Add optional `a2aConfig` to PlannerAgentConfig |
| `frontend/store/editorStore.ts` | Handle a2a-agent node creation defaults |
| `frontend/lib/validation.ts` | Add A2A validation rules |
| `frontend/lib/rete-setup.ts` | Add A2AAgentReteNode, connection rules |
| `frontend/components/panels/PropertiesPanel.tsx` | Route to A2AAgentProperties |
| `frontend/components/editor/AgentProperties.tsx` | Include A2AConfigSection |
| `frontend/app/page.tsx` | Add A2A Inspector to ActivityBar |
| `frontend/app/globals.css` | Add rose/pink node color variables |
| `src/.../export/model/GraphModel.kt` | Add a2a-agent to recognized types |
| `src/.../export/model/KoogModel.kt` | Add a2aConfig to KoogAgent |
| `src/.../export/adapter/GraphToKoogAdapter.kt` | Parse a2a-agent nodes, extract A2AConfig |
| `src/.../export/validation/GraphValidator.kt` | Add A2A validation rules |
| `src/.../export/ExportService.kt` | Register A2AServerGenerator + A2AClientGenerator |
