# Graph Agents Design Spec

**Date:** 2026-03-28
**Status:** Approved

## Overview

Graph Agents are a new first-class agent type in the Koog Agent Modeler. They enable visual state-machine modeling of Koog strategies with typed nodes, conditional edges, and deterministic Kotlin code generation.

Graph Agents coexist with existing agent types (Basic, Planner) in the same workspace. They are modeled directly on the main canvas — no sub-canvas or separate editor mode.

## Architecture Decision

**Approach C: Extend existing infrastructure + domain mapping at export boundaries.**

- Strategy nodes are new `NodeType` values in the existing type system
- `editorStore` remains single source of truth
- Domain model (`KoogGraphAgent`) is derived at validation/export time via adapter
- No sync layer, no dual state

## Node Types

### New NodeType Values

```typescript
"graph-agent" | "strategy-start" | "strategy-finish" | "strategy-llm-request" |
"strategy-execute-tool" | "strategy-send-tool-result" | "strategy-condition"
```

### Config Types (frontend/types/graph-agent.ts)

**GraphAgentConfig:**
- `name: string`
- `description: string`
- `modelProvider: ModelProvider`
- `modelId: string`
- `temperature: number`

**StrategyStartConfig:**
- `label: string` (default "Start")

**StrategyFinishConfig:**
- `label: string` (default "Finish")

**StrategyLLMRequestConfig:**
- `label: string`
- `systemPrompt: string` (optional)
- `maxTokens: number` (optional)

**StrategyExecuteToolConfig:**
- `label: string`

**StrategySendToolResultConfig:**
- `label: string`

**StrategyConditionConfig:**
- `label: string`
- `expression: string`

### parentGraphAgentId

All strategy nodes carry `parentGraphAgentId: string` in their config data, linking them to their owning Graph Agent node. Visual grouping in the UI (colored zone or label), no explicit Rete edge to the container.

### Visual Distinction

| Node Type | Color | Icon |
|---|---|---|
| Graph Agent (Container) | Cyan (#06b6d4) | Network |
| Start | Green (#22c55e) | Play |
| Finish | Red (#ef4444) | Square (Stop) |
| LLM Request | Blue (#3b82f6) | Brain |
| Execute Tool | Orange (#f97316) | Wrench |
| Send Tool Result | Violet (#a855f7) | Send |
| Condition | Yellow (#eab308) | GitBranch |

## Edge Model

### Extended GraphEdge

```typescript
interface GraphEdge {
  id: string;
  source: string;
  target: string;
  data?: GraphEdgeConfig;
}

interface GraphEdgeConfig {
  edgeType: "standard" | "onToolCall" | "onAssistantMessage" | "onCondition";
  conditionExpression?: string;
  transformationExpression?: string;
  label?: string;
}
```

### Edge Properties Panel

Clicking a strategy edge opens a properties panel with:
- Edge Type dropdown
- Condition Expression (visible for `onCondition`)
- Transformation Expression (optional, all types)
- Label text input

### Edge Selection

New `selectedEdgeId: string | null` in editorStore. Edge and node selection are mutually exclusive.

## Canvas Integration

### Rete Node Classes

7 new classes in `rete-nodes.ts`:
- `GraphAgentReteNode`
- `StrategyStartReteNode`
- `StrategyFinishReteNode`
- `StrategyLLMRequestReteNode`
- `StrategyExecuteToolReteNode`
- `StrategySendToolResultReteNode`
- `StrategyConditionReteNode`

### Socket System

- **StrategySocket** — new socket type for strategy-to-strategy connections
- **ToolSocket** — existing socket, used on GraphAgentReteNode for tool/MCP/sub-agent connections

This prevents cross-wiring between tool nodes and strategy nodes.

### Connection Rules

**Strategy (StrategySocket):**
- Start can connect to: LLM Request, Execute Tool, Condition, Send Tool Result
- LLM Request can connect to: Execute Tool, Finish, Condition, Send Tool Result
- Execute Tool can connect to: Send Tool Result, Finish, Condition
- Send Tool Result can connect to: LLM Request, Execute Tool, Finish, Condition
- Condition can connect to: anything except Start
- No incoming edges to Start
- No outgoing edges from Finish

**Graph Agent container (ToolSocket):**
- Tool, Builtin Tool, MCP Server, Sub-Agent, Input → Graph Agent: allowed
- Graph Agent → Output: allowed

**Forbidden:**
- Tool/MCP directly to strategy nodes
- Strategy nodes to Basic/Planner Agent
- Cross-socket-type connections

### Toolbar

New "Strategy Nodes" section appears when at least one Graph Agent node exists. Buttons for: Start, Finish, LLM Request, Execute Tool, Send Tool Result, Condition.

New strategy nodes auto-set `parentGraphAgentId` to the existing Graph Agent. Multiple Graph Agents: dropdown to choose.

## Validation

### Graph-Agent-Specific Rules

**Structural errors (severity: error):**
- No Start node for a Graph Agent
- No Finish node for a Graph Agent
- More than one Start node per Graph Agent
- Start node without outgoing edge
- Finish not reachable from Start
- Orphan strategy nodes (unreachable, not Start)
- Strategy node with missing or invalid `parentGraphAgentId`

**Configuration errors (severity: error):**
- Graph Agent without name
- Condition node without expression
- `onCondition` edge without condition expression

**Warnings (severity: warning):**
- Cycles without a path to Finish (potential infinite loops)
- Execute Tool node present but no tools connected to Graph Agent
- Multiple edges from same node without conditions (non-deterministic)
- Not all paths end at Finish

### Coexistence

- Graph Agent + Basic Agent: allowed
- Graph Agent + Planner Agent: allowed
- Strategy nodes without a Graph Agent: error

### Implementation

Frontend: extend `validateGraph()` in `validation.ts`
Backend: extend `GraphValidator.kt`

## Persistence

No format changes needed. `WorkspaceGraph` already stores `nodes[]` and `edges[]`. New node types and the optional `data` field on edges serialize as JSON automatically. Save/load works via existing localStorage mechanism.

## Export & Code Generation

### Backend Model (GraphAgentModel.kt)

```kotlin
data class KoogGraphAgent(
    val name: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val strategyNodes: List<KoogStrategyNode>,
    val strategyEdges: List<KoogStrategyEdge>,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>,
    val mcpServers: List<KoogMcpServer>,
    val subAgents: List<KoogSubAgent>
)

data class KoogStrategyNode(
    val id: String,
    val type: StrategyNodeType,
    val label: String,
    val config: Map<String, String>
)

data class KoogStrategyEdge(
    val sourceNodeId: String,
    val targetNodeId: String,
    val edgeType: StrategyEdgeType,
    val conditionExpression: String?,
    val transformationExpression: String?,
    val label: String?
)

enum class StrategyNodeType {
    START, FINISH, LLM_REQUEST, EXECUTE_TOOL, SEND_TOOL_RESULT, CONDITION
}

enum class StrategyEdgeType {
    STANDARD, ON_TOOL_CALL, ON_ASSISTANT_MESSAGE, ON_CONDITION
}
```

### Adapter (GraphToKoogAdapter)

New method `convertGraphAgents()`:
1. Find all nodes with `type == "graph-agent"`
2. Per Graph Agent: collect strategy nodes via `parentGraphAgentId`
3. Collect edges where both source and target belong to same Graph Agent
4. Collect connected tools/MCP/sub-agents via existing edge logic
5. Map to `KoogGraphAgent`

### Generator (GraphAgentGenerator.kt)

Generates Kotlin strategy DSL per Graph Agent:

```kotlin
val simpleLoop = strategy("simpleLoop") {
    val nodeCallLLM by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}
```

**Rules:**
- Node declarations topologically sorted (Start first)
- `nodeStart` and `nodeFinish` are Koog built-ins — not declared
- Labels sanitized to valid Kotlin identifiers
- Edges sorted deterministically: by source node order, then target
- `onCondition`/`transformed` expressions inserted as lambda strings
- Validation errors → no code generated, clean error returned

### ExportService Integration

`ExportService.export()` calls `convertGraphAgents()` and delegates to `GraphAgentGenerator`. Generated files added to ZIP as `GraphAgent_{Name}.kt`.

## Templates

Three templates offered when creating a Graph Agent:

### 1. Empty
Only the Graph Agent container node. User builds from scratch.

### 2. Tool-Calling Agent
```
Start → LLM Request → Execute Tool → Send Tool Result
LLM Request --onAssistantMessage--> Finish
Send Tool Result --onToolCall--> Execute Tool
Send Tool Result --onAssistantMessage--> Finish
```

### 3. ReAct Loop
```
Start → LLM Request (Think) → Execute Tool
Execute Tool → Send Tool Result (Observe)
Send Tool Result → LLM Request (Think)
LLM Request --onAssistantMessage--> Finish
```

Templates auto-layout nodes vertically with spacing and set `parentGraphAgentId`. Implemented as `createGraphAgentFromTemplate()` in editorStore.

## Inspection

No separate inspection view. The existing Export Dialog preview with Shiki code highlighting serves as inspection — users see their graph rendered as Koog DSL.

## Tests

### GraphToKoogAdapterGraphAgentTest.kt
- Linear graph (Start → LLM → Finish) correctly mapped
- Tool-calling loop fully mapped
- Tools/MCP/Sub-Agents on Graph Agent correctly associated
- Strategy nodes without Graph Agent ignored
- Multiple Graph Agents in same workspace

### GraphAgentGeneratorTest.kt
- Simple linear graph produces valid Kotlin
- Tool-calling loop with onToolCall/onAssistantMessage edges
- onCondition + transformed edges correctly generated
- Labels sanitized to valid Kotlin identifiers
- Deterministic output (same input → same output)
- nodeStart/nodeFinish not declared (Koog built-ins)

### GraphValidatorGraphAgentTest.kt
- Missing Start node → error
- Missing Finish node → error
- Start without outgoing edge → error
- Finish not reachable → error
- Orphan node → error
- Cycle without Finish path → warning
- Execute Tool without connected tools → warning
- Multiple edges without conditions → warning
- Valid tool-calling loop → no errors

~20-25 test cases total across 3 files.

## Extension Points

The architecture supports future additions without structural changes:
- **New strategy node types** (subgraph, memory, MCP) → add NodeType value + config + Rete class + generator case
- **New edge types** → add to StrategyEdgeType enum + generator mapping
- **Loops/branches** → already supported via graph structure and cycle detection
- **Debug/tracing** → overlay on existing canvas nodes
- **Checkpointing** → extend GraphAgentConfig with persistence settings
