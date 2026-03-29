# Subgraphs in Graph Editor

**Date:** 2026-03-29
**Feature:** 06 — Subgraphs
**Status:** Design approved

## Problem

The Banking Tutorial uses subgraphs to isolate different intent paths (Transfer vs. Analytics). Each subgraph has its own nodes, edges, and optionally its own tool set. The modeler only supports flat graphs.

## Solution

New node type `node-subgraph` with a nested strategy graph. Double-click opens a nested overlay editor. One level deep only (no subgraphs within subgraphs).

## Data Model

New fields in `GraphStrategyNodeData`:
- `subgraphName?: string` — Subgraph name (Kotlin variable)
- `subgraphInputType?: string` — Input type (String, Message.Response, etc.)
- `subgraphOutputType?: string` — Output type
- `subgraphToolSelection?: "ALL" | "SPECIFIC" | "NONE"`
- `subgraphTools?: string[]` — Tool names when SPECIFIC
- `subgraphNodes?: GraphStrategyNode[]` — Inner strategy nodes
- `subgraphEdges?: GraphStrategyEdge[]` — Inner strategy edges

Default inner graph: Start + Finish nodes (same as empty Graph Agent template).

## Visual

- Rete class: `SubgraphReteNode` (220x80, input + output)
- Color: Orange (`rgba(249, 115, 22, 0.5)`)
- Label: `SUBGRAPH`
- Subtitle: subgraphName
- Toolbar button: "Subgraph" with `text-orange-400`
- Double-click on canvas node → opens nested editor

## Nested Editor

- **Nested overlay** rendered on top of the main strategy editor (higher z-index)
- Reuses `GraphStrategyCanvas` and `GraphStrategyToolbar` components
- Own Start/Finish nodes, same node types as main graph
- **No subgraph nodes inside subgraphs** (1 level only) — toolbar hides the Subgraph button
- Breadcrumb: `Strategy > Subgraph: {name}`
- Close button returns to main graph
- Changes saved to the parent `node-subgraph`'s data (subgraphNodes/subgraphEdges)

### Implementation Approach

The `GraphStrategyEditor` component manages a stack:
- `editingSubgraphId: string | null` state
- When set, renders a second overlay with the subgraph's inner nodes/edges
- The inner overlay uses the same `GraphStrategyCanvas` but with `subgraphNodes`/`subgraphEdges` as data
- Node/edge changes update the parent subgraph node via `updateNodeData`

## Properties Panel

| Field | Component | Description |
|-------|-----------|-------------|
| Name | Input | Subgraph name → Kotlin variable |
| Input Type | TypeSelect | Same component as Custom Node (predefined + Custom...) |
| Output Type | TypeSelect | Same options |
| Tool Selection | Select | ALL / SPECIFIC / NONE |
| Tools | Checkbox list | Only shown when SPECIFIC — lists connected tool names |
| Edit Subgraph | Button | Opens nested editor (alternative to double-click) |

## autoTypeEdge

Same logic as Custom Node:
- `node-llm-request` → `node-subgraph` (subgraphInputType="String") → `onAssistantMessage`
- All other combinations → `onCondition`

## Backend Code Generation

### Model

New fields in `KoogStrategyNode`:
- `subgraphName: String?`
- `subgraphInputType: String?`
- `subgraphOutputType: String?`
- `subgraphToolSelection: String?` — "ALL", "SPECIFIC", "NONE"
- `subgraphTools: List<String>?`
- `subgraphNodes: List<KoogStrategyNode>?`
- `subgraphEdges: List<KoogStrategyEdge>?`

### Generated Code

```kotlin
val transfer by subgraph<String, String>(
    name = "transfer",
    toolSelectionStrategy = ToolSelectionStrategy.Tools(listOf(sendMoneyTool.descriptor))
) {
    val llm by nodeLLMRequest()
    val exec by nodeExecuteTool()
    val send by nodeLLMSendToolResult()

    edge(nodeStart forwardTo llm)
    edge(llm forwardTo exec onToolCall { true })
    edge(llm forwardTo nodeFinish onAssistantMessage { true })
    edge(exec forwardTo send)
    edge(send forwardTo llm)
}
```

- Import: `import ai.koog.agents.core.dsl.builder.subgraph`
- Tool selection strategy:
  - ALL → `ToolSelectionStrategy.All` (or omit parameter)
  - SPECIFIC → `ToolSelectionStrategy.Tools(listOf(toolName.descriptor, ...))`
  - NONE → `ToolSelectionStrategy.None`
- Inner nodes/edges use same generation logic as main strategy (reuse existing `generateNodeDeclarations` and `generateEdges` logic)

### Adapter

Extract nested `subgraphNodes` and `subgraphEdges` from node data, recursively converting inner strategy nodes the same way as main strategy nodes.

## Validation

- Subgraph without `subgraphName` → Error
- Inner graph: same rules as main graph (exactly 1 Start, 1 Finish, reachability check)
- SPECIFIC tool selection with empty tools list → Warning

## Files Changed

### Frontend

| File | Change |
|------|--------|
| `frontend/types/graph-agent.ts` | Add `"node-subgraph"` to NodeType, add subgraph data fields |
| `frontend/lib/strategy-rete-nodes.ts` | Add `SubgraphReteNode` class |
| `frontend/components/graph-agent/StrategyCustomNode.tsx` | Orange color, SUBGRAPH label, double-click handler |
| `frontend/components/graph-agent/GraphStrategyToolbar.tsx` | Subgraph button, optional hide when in subgraph |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | `createReteNode()`, `autoTypeEdge()`, double-click event |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | `editingSubgraphId` state, nested overlay, breadcrumb |
| `frontend/components/graph-agent/StrategyNodeProperties.tsx` | Subgraph properties (name, types, tool selection, edit button) |
| `frontend/lib/graph-agent-validation.ts` | Validate inner subgraph |

### Backend

| File | Change |
|------|--------|
| `export/model/GraphAgentModel.kt` | Add subgraph fields to `KoogStrategyNode` |
| `export/adapter/GraphToKoogAdapter.kt` | Extract nested subgraph data |
| `export/generator/GraphAgentGenerator.kt` | Generate `subgraph<I, O>(...) { ... }` with inner nodes/edges |

## Acceptance Criteria

- [ ] Subgraph node placeable in graph editor
- [ ] Double-click opens nested graph editor overlay
- [ ] Breadcrumb navigation between main graph and subgraph
- [ ] Input/output type and tool selection configurable
- [ ] Inner graph has own Start/Finish and supports all node types except subgraph
- [ ] Generated code produces `subgraph<I, O>(...) { ... }` with inner nodes/edges
- [ ] Tool selection strategy correctly generated (ALL/SPECIFIC/NONE)
- [ ] Edges between subgraph and other nodes work correctly
- [ ] Banking Tutorial intent routing reproducible
