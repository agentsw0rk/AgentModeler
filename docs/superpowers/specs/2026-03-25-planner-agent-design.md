# Planner Agent Feature Design

## Overview

Planner Agents are a new agent type for the Agent Editor that supports multi-step task execution through iterative planning cycles. Unlike Graph Agents where all edges and paths are manually defined, Planner Agents let users define available actions, states, and goals — the planner determines the step sequence and adapts when needed.

## Key Decisions

- **Integration:** Hybrid — Planner Agent as a node in the existing graph canvas with a sliding panel for detailed configuration
- **Action Model:** Key-Value based (Preconditions/Effects as structured conditions)
- **Node Color:** Pink (#ec4899)
- **Plan Inspection:** In the existing PreviewPanel (bottom)
- **Connectivity:** Full compatibility — Planner node accepts Tools, Sub-Agents, and MCP Servers
- **Implementation:** Standalone NodeType `"planner-agent"`, separate from all existing agent types

## Domain Model

### Frontend Types (`frontend/types/planner.ts`)

```typescript
interface PlannerAgentConfig {
  name: string;
  description: string;
  plannerType: "llm" | "goap";
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  maxReplanAttempts: number;     // default 3
  goals: PlannerGoal[];
  actions: PlannerAction[];
  initialState: StateEntry[];
}

interface PlannerGoal {
  id: string;
  name: string;
  conditions: StateCondition[];
  priority: number;
}

interface PlannerAction {
  id: string;
  name: string;
  description: string;
  preconditions: StateCondition[];
  effects: StateEffect[];
  cost: number;                  // default 1
}

interface StateCondition {
  key: string;
  operator: "equals" | "notEquals" | "greaterThan" | "lessThan";
  value: string | number | boolean;
}

interface StateEffect {
  key: string;
  value: string | number | boolean;
}

interface StateEntry {
  key: string;
  value: string | number | boolean;
}
```

### Backend Types (Kotlin)

```kotlin
data class KoogPlannerAgent(
  val name: String,
  val description: String,
  val plannerType: PlannerType,
  val modelProvider: ModelProvider?,
  val modelId: String?,
  val temperature: Double?,
  val maxReplanAttempts: Int,
  val goals: List<PlannerGoal>,
  val actions: List<PlannerAction>,
  val initialState: Map<String, Any>,
  val tools: List<KoogTool>,
  val builtinTools: List<KoogBuiltinTool>,
  val subAgents: List<KoogSubAgent>,
  val mcpServers: List<KoogMcpServer>
)

enum class PlannerType { LLM, GOAP }

data class PlannerGoal(
  val name: String,
  val conditions: List<StateCondition>,
  val priority: Int
)

data class PlannerAction(
  val name: String,
  val description: String,
  val preconditions: List<StateCondition>,
  val effects: List<StateEffect>,
  val cost: Int
)

data class StateCondition(
  val key: String,
  val operator: ConditionOperator,
  val value: Any
)

data class StateEffect(val key: String, val value: Any)

enum class ConditionOperator { EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN }
```

### Graph Model

- New `NodeType`: `"planner-agent"`
- Input sockets identical to Agent node (receives Tool, Sub-Agent, MCP)
- No output sockets

## UI Components

### Toolbar
- New "Planner Agent" button with pink icon
- Creates planner-agent node with default values

### Canvas / CustomNode
- Pink (#ec4899) node color, distinct icon
- Subtitle shows planner type ("LLM Planner" / "GOAP Planner")
- Input sockets identical to Agent node

### Properties Panel
- Shows basic info (Name, Description, Planner Type) when planner node selected
- "Configure Planner" button opens the Sliding Panel

### Sliding Panel (`PlannerConfigPanel.tsx`)
- Slides in from right, covers ~55% of canvas
- Close button returns to normal editor view
- 4 tabs:

**Goals Tab:**
- List of goals with name, priority, conditions
- Add/edit/delete goals
- Each condition: key + operator dropdown + value input

**Actions Tab:**
- List of actions with name, description, cost
- Expandable: preconditions and effects as key-value lists
- Add/edit/delete for actions and their conditions/effects

**State Tab:**
- Initial state as key-value table
- Add/edit/delete entries

**Settings Tab:**
- Planner type toggle (LLM / GOAP)
- Model provider + model ID (LLM only)
- Temperature slider (LLM only)
- Max replan attempts input

### PreviewPanel — Plan Inspection
- When planner node is selected, PreviewPanel shows plan inspection instead of chat:
  - Current plan as step list with status icons (pending/running/done/failed)
  - Current state as key-value table
  - Completion status indicator
- Static inspection in V1 based on example/mock data

## Validation

### Frontend Rules (`validation.ts`)

| Rule | Severity | Message |
|------|----------|---------|
| Name empty | error | "Planner agent must have a name" |
| No goals | error | "Planner agent must have at least one goal" |
| No actions | error | "Planner agent must have at least one action" |
| Goal without conditions | warning | "Goal '{name}' has no conditions" |
| Action without preconditions | warning | "Action '{name}' has no preconditions" |
| Action without effects | warning | "Action '{name}' has no effects" |
| LLM planner without model provider | error | "LLM planner requires a model provider" |
| Empty condition/effect key | error | "Condition/Effect key must not be empty" |
| Duplicate goal names | error | "Duplicate goal name: '{name}'" |
| Duplicate action names | error | "Duplicate action name: '{name}'" |
| Tool/MCP connected to multiple agents | error | (existing rule, extended to planner-agent) |

### Backend Rules (`GraphValidator.kt`)
- Same rules as frontend
- Max one planner-agent per workspace
- Planner-agent and regular agent cannot coexist in same workspace

## Code Generation (Export)

### Adapter (`GraphToKoogAdapter.kt`)
- New `convertPlannerAgent()` method converts `GraphNode` (type: planner-agent) to `KoogPlannerAgent`
- Collects connected tools, sub-agents, MCP servers using existing edge-traversal logic

### Generator (`PlannerAgentGenerator.kt`)

**LLM Planner generates:**
- Spring `@Configuration` class
- Goals and actions as data structures
- LLM-based planner that checks state, generates plan, executes steps
- Replanning support (maxReplanAttempts)
- Tool registry integration

**GOAP Planner generates:**
- Goals, actions, preconditions, effects, costs as graph structures
- A*-based GOAP solver for optimal action-path search
- Initial state as starting point
- Sequential step execution with state updates

### Generated File Structure
```
generated/
├── PlannerConfig.kt          # Goals, actions, state definitions
├── PlannerAgent.kt           # Spring @Configuration + @Bean
├── planner/
│   ├── PlannerGoals.kt       # Goal data classes
│   ├── PlannerActions.kt     # Action data classes with pre/effects
│   └── PlannerExecutor.kt    # Plan execution logic
├── tools/                    # (unchanged)
├── subagents/                # (unchanged)
└── Application.kt            # Spring Boot main
```

### ExportService Integration
- `ExportService.generateFiles()` checks for planner-agent node
- Uses `PlannerAgentGenerator` instead of `AgentGenerator`
- All other generators (Tool, SubAgent, MCP, Build, Dockerfile) remain unchanged

### Controller Generation
- Generates `/api/plan` endpoint instead of `/api/chat`
- Accepts a goal, runs planning + execution, returns plan status

## Persistence

- No schema migration needed — `version: 1` stays
- `planner-agent` is a new `NodeType` value
- `GraphNode.data` contains `PlannerAgentConfig` when `type === "planner-agent"`
- `serializeWorkspace()` / `deserializeWorkspace()` work without changes

## Tests

### Backend Tests

| Test Class | Coverage |
|------------|----------|
| `PlannerAgentGeneratorTest` | LLM planner code generation (@Configuration, @Bean, goals, actions) |
| `PlannerAgentGeneratorTest` | GOAP planner code generation (solver, cost optimization) |
| `PlannerAgentGeneratorTest` | Planner with tools/sub-agents/MCP generates correct registry |
| `GraphToKoogAdapterTest` | GraphNode → KoogPlannerAgent conversion |
| `GraphValidatorTest` | Planner-specific validation rules |

### Frontend Tests
- Validation rules for planner nodes
- EditorStore: `addNode("planner-agent")` creates correct default state

### Test Pattern
Follows existing pattern: direct generator instantiation, `assertContains` on generated code.

## Files to Create

### Frontend (new files)
1. `frontend/types/planner.ts` — PlannerAgentConfig and related interfaces
2. `frontend/components/panels/PlannerProperties.tsx` — Basic properties + "Configure" button
3. `frontend/components/planner/PlannerConfigPanel.tsx` — Sliding panel with 4 tabs
4. `frontend/components/planner/GoalsTab.tsx` — Goals editor
5. `frontend/components/planner/ActionsTab.tsx` — Actions editor with preconditions/effects
6. `frontend/components/planner/StateTab.tsx` — Initial state editor
7. `frontend/components/planner/SettingsTab.tsx` — Planner settings
8. `frontend/components/planner/PlanInspector.tsx` — Plan inspection view for PreviewPanel

### Frontend (files to modify)
1. `frontend/types/graph.ts` — Add "planner-agent" to NodeType
2. `frontend/store/editorStore.ts` — Add default planner data, update addNode()
3. `frontend/lib/rete-nodes.ts` — Add PlannerAgentReteNode class
4. `frontend/components/editor/Canvas.tsx` — Handle planner node sync
5. `frontend/components/editor/CustomNode.tsx` — Add pink color + icon
6. `frontend/components/panels/Toolbar.tsx` — Add planner button
7. `frontend/components/panels/PropertiesPanel.tsx` — Route planner to PlannerProperties
8. `frontend/lib/validation.ts` — Add planner validation rules
9. `frontend/components/preview/PreviewPanel.tsx` — Add plan inspection mode

### Backend (new files)
1. `src/main/kotlin/export/model/PlannerModel.kt` — KoogPlannerAgent and related data classes
2. `src/main/kotlin/export/generator/PlannerAgentGenerator.kt` — Code generator
3. `src/test/kotlin/export/generator/PlannerAgentGeneratorTest.kt` — Generator tests

### Backend (files to modify)
1. `src/main/kotlin/export/model/GraphModel.kt` — Add planner-agent node type handling
2. `src/main/kotlin/export/adapter/GraphToKoogAdapter.kt` — Add planner conversion
3. `src/main/kotlin/export/validation/GraphValidator.kt` — Add planner rules
4. `src/main/kotlin/export/ExportService.kt` — Integrate planner generator
