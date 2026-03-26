# Input/Output Nodes Feature — Design Spec

## Overview

Add explicit Input and Output nodes to the Koog Agent Modeler. These nodes make the data flow visible: what data an Agent/SubAgent/Planner expects (inputs) and what it produces (outputs). They are standalone graph nodes connected to agents via edges.

## Architecture

### Integration Strategy

- **Input Node:** Standalone node with an output socket. Connects to Agent/SubAgent/Planner (edge direction: input → agent). Represents one named, typed input parameter.
- **Output Node:** Standalone node with an input socket. Connected from Agent/SubAgent/Planner (edge direction: agent → output). Represents one named, typed output field.
- **Visibility:** The graph clearly shows Input → Agent → Output data flow.

### Node Colors

- Input: Cyan (`#06b6d4`, `cyan-500`) — visually represents "data coming in"
- Output: Orange (`#f97316`, `orange-500`) — visually represents "data going out"

## Domain Model

### Frontend Types (`types/io.ts`)

```typescript
export type IODataType = "string" | "number" | "boolean" | "object" | "array";

export const IO_DATA_TYPES: IODataType[] = ["string", "number", "boolean", "object", "array"];

export const IO_DATA_TYPE_LABELS: Record<IODataType, string> = {
  string: "String",
  number: "Number",
  boolean: "Boolean",
  object: "Object",
  array: "Array",
};

export interface InputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
  required: boolean;
  defaultValue?: string;
}

export interface OutputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
}
```

### Backend Model (`export/model/IOModel.kt`)

```kotlin
data class KoogInput(
    val name: String,
    val description: String,
    val dataType: String,
    val required: Boolean,
    val defaultValue: String?
)

data class KoogOutput(
    val name: String,
    val description: String,
    val dataType: String
)
```

### Model Integration

`KoogAgent` gains:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

`KoogSubAgent` gains:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

`KoogPlannerAgent` gains:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

## Graph Integration

### NodeType Extension

Add `"input"` and `"output"` to the `NodeType` union (frontend) and recognized types (backend).

### Connection Rules

| Source | Target | Allowed |
|--------|--------|---------|
| `input` | `agent` | yes |
| `input` | `sub-agent` | yes |
| `input` | `planner-agent` | yes |
| `agent` | `output` | yes (reverse direction — agent produces output) |
| `sub-agent` | `output` | yes |
| `planner-agent` | `output` | yes |
| `input` | `output` | no |
| `input` | `input` | no |
| `output` | `output` | no |
| `output` | `agent` | no |

Note on direction: For output nodes, the connection direction is reversed compared to tools. An agent's output socket connects to the output node's input socket. This means the Output Rete node has an input socket, and the existing agent nodes need an additional output socket for output connections.

### Rete Node Classes

- `InputReteNode`: Has one **output** socket labeled "Agent" (same pattern as ToolReteNode — connects to agent input)
- `OutputReteNode`: Has one **input** socket labeled "Agent" (receives connection from agent — agent needs output socket)

For agent nodes to connect TO output nodes, agent/sub-agent/planner nodes need an additional output socket. Currently they only have an input socket ("Tools"). Add an output socket ("Outputs") to `AgentReteNode`, `SubAgentReteNode`, and `PlannerAgentReteNode`.

### GraphNode.data

`GraphNode.data` union includes `InputNodeConfig` when `type === "input"` and `OutputNodeConfig` when `type === "output"`.

## UI Components

### InputProperties (`components/panels/InputProperties.tsx`)

Properties panel for `input` node:
- Name (text input)
- Description (textarea, 2 rows)
- Data Type (select: String/Number/Boolean/Object/Array)
- Required (switch)
- Default Value (text input, shown only when required is false)

### OutputProperties (`components/panels/OutputProperties.tsx`)

Properties panel for `output` node:
- Name (text input)
- Description (textarea, 2 rows)
- Data Type (select: String/Number/Boolean/Object/Array)

Both integrated into `PropertiesPanel.tsx` following existing patterns.

## Validation Rules

| # | Rule | Severity | Location |
|---|------|----------|----------|
| 1 | Input node: name empty | error | input node |
| 2 | Output node: name empty | error | output node |
| 3 | Input node: not connected to any agent/sub-agent/planner | error | input node |
| 4 | Output node: not connected to any agent/sub-agent/planner | error | output node |
| 5 | Input node: connected to multiple agents | error | input node |
| 6 | Output node: connected to multiple agents | error | output node |
| 7 | Duplicate input name on same agent | error | input node |
| 8 | Duplicate output name on same agent | error | output node |
| 9 | Input required but no default value | warning | input node |

Implemented in both frontend (`lib/validation.ts`) and backend (`GraphValidator.kt`).

## Code Generation

### InputOutputGenerator

For each agent with inputs/outputs, generates:

**Input handling:**
- Input parameters as typed function parameters in the agent invocation
- Default values for optional inputs
- Type mapping: `string` → `String`, `number` → `Double`, `boolean` → `Boolean`, `object` → `Map<String, Any>`, `array` → `List<Any>`

**Output handling:**
- A typed response data class per agent with output fields
- Agent function return type set to the response class

Example generated code for an agent with inputs `query: String (required)`, `limit: Number (optional, default "10")` and output `result: String`:

```kotlin
data class MyAgentInput(
    val query: String,
    val limit: Double = 10.0
)

data class MyAgentOutput(
    val result: String
)
```

### Adapter Changes

`GraphToKoogAdapter` extended:
- Parse `input` nodes, find connected agent via edges, add to agent's `inputs` list
- Parse `output` nodes, find connected agent via edges (reverse direction), add to agent's `outputs` list

## Test Plan

### Backend Tests

- `GraphToKoogAdapterIOTest.kt` — Adapter converts input/output nodes connected to agents, sub-agents, planners
- `GraphValidatorIOTest.kt` — All 9 validation rules
- `InputOutputGeneratorTest.kt` — Generated input class, output class, type mapping, defaults

### Frontend

- Frontend validation mirrors backend rules (existing pattern)

## File Changes Summary

### New Files

| File | Purpose |
|------|---------|
| `frontend/types/io.ts` | Input/Output TypeScript types |
| `frontend/components/panels/InputProperties.tsx` | Input node properties panel |
| `frontend/components/panels/OutputProperties.tsx` | Output node properties panel |
| `src/.../export/model/IOModel.kt` | Backend IO data classes |
| `src/.../export/generator/InputOutputGenerator.kt` | IO code generator |
| Tests (3 files) | See test plan |

### Modified Files

| File | Change |
|------|--------|
| `frontend/types/graph.ts` | Add `"input"` and `"output"` to NodeType, configs to data union |
| `frontend/store/editorStore.ts` | Handle input/output node creation defaults |
| `frontend/lib/rete-nodes.ts` | Add InputReteNode, OutputReteNode, add output socket to agent nodes |
| `frontend/lib/rete-setup.ts` | Add connection rules for input/output |
| `frontend/lib/validation.ts` | Add IO validation rules |
| `frontend/components/editor/Canvas.tsx` | Handle input/output node types |
| `frontend/components/editor/CustomNode.tsx` | Add input/output colors/icon/subtitle |
| `frontend/components/panels/PropertiesPanel.tsx` | Route to InputProperties/OutputProperties |
| `src/.../export/model/KoogModel.kt` | Add inputs/outputs to KoogAgent, KoogSubAgent |
| `src/.../export/model/PlannerModel.kt` | Add inputs/outputs to KoogPlannerAgent |
| `src/.../export/adapter/GraphToKoogAdapter.kt` | Parse input/output nodes |
| `src/.../export/validation/GraphValidator.kt` | Add IO validation rules |
| `src/.../export/ExportService.kt` | Register InputOutputGenerator |
