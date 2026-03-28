# Graph Agents Design Spec

## Overview

Graph Agents are a new agent type in Agent Studio that models execution logic as an explicit state machine. Unlike Basic Agents (prompt + tools) or Planner Agents (goal-oriented planning), Graph Agents define their workflow as a directed graph of nodes and edges, mapping directly to the Koog `strategy { }` DSL.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Canvas approach | Graph-in-Graph (Sub-Canvas) | Clean separation, main canvas stays uncluttered, follows PlannerConfigPanel pattern |
| Tool access | Hybrid | Tools connected on main canvas, selected per `nodeExecuteTool` in sub-canvas |
| Initial node types | Core + Condition | Covers tool-calling loops and branching without custom code complexity |
| Edge configuration | Auto-type + Override | Reduces configuration effort; most edges are predictable from node types |
| Templates | Yes, in V1 | Lowers barrier to entry; technically simple (predefined node/edge arrays) |
| Code preview | Existing Code Explorer | Reuses VFS infrastructure, keeps sub-canvas clean |

## Data Model

### Frontend Types (`types/graph-agent.ts`)

```typescript
type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition";

interface GraphStrategyNode {
  id: string;
  type: GraphStrategyNodeType;
  position: { x: number; y: number };
  data: GraphStrategyNodeData;
}

interface GraphStrategyNodeData {
  label?: string;
  toolName?: string;          // node-execute-tool: which connected tool
  condition?: string;         // node-condition: condition expression
  temperature?: number;       // node-llm-request: optional override
}

type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition";

interface GraphStrategyEdge {
  id: string;
  source: string;
  target: string;
  edgeType: GraphEdgeType;
  condition?: string;         // for onCondition
  transformation?: string;    // optional transformation
}

type GraphAgentTemplate = "empty" | "tool-calling" | "react-loop" | "tool-loop-finish";

interface GraphAgentConfig {
  name: string;
  description: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  strategyNodes: GraphStrategyNode[];
  strategyEdges: GraphStrategyEdge[];
}
```

### Backend Models (`GraphAgentModel.kt`)

```kotlin
data class KoogGraphAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val strategyNodes: List<KoogStrategyNode>,
    val strategyEdges: List<KoogStrategyEdge>,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>,
    val subAgents: List<KoogSubAgent>,
    val mcpServers: List<KoogMcpServer>,
    val inputs: List<KoogInput>,
    val outputs: List<KoogOutput>
)

data class KoogStrategyNode(
    val id: String,
    val type: String,
    val label: String?,
    val toolName: String?,
    val condition: String?,
    val temperature: Double?
)

data class KoogStrategyEdge(
    val source: String,
    val target: String,
    val edgeType: String,
    val condition: String?,
    val transformation: String?
)
```

## Architecture

### Main Canvas Integration

The Graph Agent appears as a regular node on the main canvas with:
- **Input sockets**: `inputs` (for Input nodes), `tools` (for Tools, Sub-Agents, MCP Servers)
- **Output socket**: `outputs` (for Output nodes)
- Same connection rules as Agent/SubAgent/PlannerAgent
- **Double-click** opens the sub-canvas

### Sub-Canvas (GraphStrategyEditor)

A fullscreen panel (same pattern as PlannerConfigPanel) overlaying the main canvas:

- **Mini-toolbar** at top: buttons for LLM Request, Execute Tool, Send Tool Result, Condition
- Start + Finish are auto-placed on creation and cannot be deleted
- **Independent Rete editor** for the strategy graph
- **Right-side properties panel** for selected graph node or edge
- **Breadcrumb** top-left: `Workspace > {GraphAgentName} > Strategy`
- **Back button** to close sub-canvas

### Data Flow

Strategy data lives inside `GraphAgentConfig.strategyNodes/strategyEdges` in the main `editorStore`. The sub-canvas Rete editor syncs bidirectionally with these arrays via `updateNodeData(graphAgentNodeId, { strategyNodes, strategyEdges })`. No separate store needed.

### Template Selection

When clicking the Graph Agent toolbar button, a dialog appears with four options:

- **Empty** -- Start + Finish only
- **Tool-Calling Agent** -- Start -> LLMRequest -> ExecuteTool -> SendToolResult -> LLMRequest (loop), LLMRequest -> Finish (onAssistantMessage)
- **ReAct Loop** -- Start -> LLMRequest -> Condition -> ExecuteTool/Finish, ExecuteTool -> SendToolResult -> LLMRequest
- **Tool Loop with Finish** -- Start -> LLMRequest -> ExecuteTool -> SendToolResult -> LLMRequest, LLMRequest -> Finish (onAssistantMessage)

### Edge Configuration

- Edges are drawn via drag like on the main canvas
- **Auto-typing** based on source/target node types:
  - `node-llm-request` -> `node-execute-tool` = `onToolCall`
  - `node-llm-request` -> `node-finish` or `node-condition` = `onAssistantMessage`
  - All others = `onCondition`
- **Click on edge** -> right panel shows Edge Properties: type dropdown, condition, transformation
- **Click to delete** works the same as on the main canvas (hover = red, click = delete)

## Validation

### Errors (block export)

| Rule | Description |
|------|-------------|
| Name required | Graph Agent must have a name |
| Exactly 1 Start | `node-start` must exist exactly once |
| Exactly 1 Finish | `node-finish` must exist exactly once |
| Start has outgoing edge | `node-start` must have at least one outgoing edge |
| Finish reachable | A path from `node-start` to `node-finish` must exist (BFS) |
| No orphan nodes | Every node (except Start) must have at least one incoming edge |
| Execute Tool needs tool | `node-execute-tool` must reference a `toolName` matching a connected tool on the main canvas |
| Condition needs expression | `node-condition` must have a non-empty `condition` |
| Edge type required | Every edge must have a valid `edgeType` |
| onCondition needs condition | Edges of type `onCondition` must have a `condition` expression |

### Warnings

| Rule | Description |
|------|-------------|
| Unreachable nodes | Nodes not reachable from Start |
| Non-standard cycles | Cycles not following the ExecuteTool -> SendToolResult -> LLMRequest pattern |
| Empty graph | Only Start + Finish without further nodes |

### Not validated (by design)

- Cycles are allowed (tool loops are the normal case)
- Type compatibility between nodes is not checked (Koog DSL is flexible here)

## Code Generation

### GraphAgentGenerator

Generates `{AgentName}Strategy.kt` containing the Koog `strategy { }` DSL.

#### Mapping Rules

| Graph Node Type | Kotlin DSL |
|-----------------|-----------|
| `node-start` | `nodeStart` (implicit, no `val`) |
| `node-finish` | `nodeFinish` (implicit, no `val`) |
| `node-llm-request` | `val {label} by nodeLLMRequest()` |
| `node-execute-tool` | `val {label} by nodeExecuteTool()` |
| `node-llm-send-tool-result` | `val {label} by nodeLLMSendToolResult()` |
| `node-condition` | `val {label} by nodeCondition("{condition}")` |

| Edge Type | Kotlin DSL |
|-----------|-----------|
| `onToolCall` | `edge(A forwardTo B onToolCall { true })` |
| `onAssistantMessage` | `edge(A forwardTo B onAssistantMessage { true })` |
| `onCondition` | `edge(A forwardTo B onCondition { {condition} })` |
| `onCondition` + transformation | `edge(A forwardTo B onCondition { {condition} } transformed { {transformation} })` |

#### Example Output

```kotlin
package generated

import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.forwardTo

fun createSearchAgentStrategy() = strategy<String, String>("searchAgent") {
    val llmRequest by nodeLLMRequest()
    val executeTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo llmRequest)
    edge(llmRequest forwardTo executeTool onToolCall { true })
    edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })
    edge(executeTool forwardTo sendToolResult)
    edge(sendToolResult forwardTo llmRequest)
}
```

### ExportService Integration

Third path alongside Agent and Planner:
- `GraphToKoogAdapter.convertGraphAgents(graph)` -> `List<KoogGraphAgent>`
- `GraphAgentGenerator.generate(graphAgent)` -> Strategy file
- `AgentGenerator` extended to include `strategy = createXxxStrategy()` parameter
- Connected tools/sub-agents/MCP generated via existing generators

### Generated File Structure

```
src/main/kotlin/generated/
  Application.kt
  AgentConfig.kt              <- with strategy = createXxxStrategy()
  ChatController.kt
  {AgentName}Strategy.kt      <- NEW
  tools/
    SearchToolSet.kt
  {AgentName}Input.kt
  {AgentName}Output.kt
  build.gradle.kts
  ...
```

## File Structure

### New Files

**Frontend:**
```
frontend/types/graph-agent.ts
frontend/components/graph-agent/GraphStrategyEditor.tsx
frontend/components/graph-agent/GraphStrategyToolbar.tsx
frontend/components/graph-agent/GraphStrategyCanvas.tsx
frontend/components/graph-agent/StrategyNodeProperties.tsx
frontend/components/graph-agent/StrategyEdgeProperties.tsx
frontend/components/graph-agent/StrategyCustomNode.tsx
frontend/components/graph-agent/templates.ts
frontend/components/panels/GraphAgentProperties.tsx
frontend/components/dialogs/GraphAgentTemplateDialog.tsx
frontend/lib/strategy-rete-nodes.ts
frontend/lib/strategy-rete-setup.ts
frontend/lib/graph-agent-validation.ts
```

**Backend:**
```
src/main/kotlin/.../export/model/GraphAgentModel.kt
src/main/kotlin/.../export/generator/GraphAgentGenerator.kt
```

**Tests:**
```
src/test/kotlin/.../export/GraphToKoogAdapterGraphAgentTest.kt
src/test/kotlin/.../export/GraphValidatorGraphAgentTest.kt
src/test/kotlin/.../export/GraphAgentGeneratorTest.kt
```

### Modified Files

**Frontend:**
- `types/graph.ts` -- add `"graph-agent"` to NodeType union
- `store/editorStore.ts` -- defaults, addNode case, selectedStrategyEdgeId state
- `lib/rete-nodes.ts` -- GraphAgentReteNode class
- `lib/rete-setup.ts` -- connection validation rules
- `lib/validation.ts` -- graph-agent case delegating to graph-agent-validation.ts
- `components/editor/Canvas.tsx` -- sync logic for GraphAgentReteNode
- `components/editor/CustomNode.tsx` -- color, icon, subtitle for graph-agent
- `components/panels/PropertiesPanel.tsx` -- add graph-agent case
- `components/panels/Toolbar.tsx` -- add Graph Agent button

**Backend:**
- `GraphToKoogAdapter.kt` -- add convertGraphAgents() method
- `GraphValidator.kt` -- add graph-agent validation block
- `ExportService.kt` -- integrate graph-agent generation path
- `AgentGenerator.kt` -- support strategy parameter in generated code
