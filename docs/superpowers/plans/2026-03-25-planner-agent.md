# Planner Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Planner Agent as a new node type in the agent editor with goals, actions (key-value preconditions/effects), two planner variants (LLM/GOAP), a sliding config panel, plan inspection in PreviewPanel, validation, and Kotlin code generation.

**Architecture:** Planner Agent is a standalone NodeType `"planner-agent"` in the hybrid graph editor. It appears as a pink node on the canvas that accepts Tools, Sub-Agents, and MCP Servers. Configuration happens in a sliding panel from the right. Plan inspection lives in the PreviewPanel. Backend adds a new domain model, adapter conversion, generator, and validator rules.

**Tech Stack:** TypeScript/React (Next.js), Zustand, Rete.js, Kotlin/Spring Boot, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-25-planner-agent-design.md`

---

### Task 1: Backend Domain Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt`

- [ ] **Step 1: Create PlannerModel.kt with all data classes**

```kotlin
package com.agentwork.modeler.export.model

data class KoogPlannerAgent(
    val name: String,
    val description: String,
    val plannerType: PlannerType,
    val modelProvider: ModelProvider?,
    val modelId: String?,
    val temperature: Double?,
    val maxReplanAttempts: Int = 3,
    val goals: List<PlannerGoal>,
    val actions: List<PlannerAction>,
    val initialState: Map<String, Any> = emptyMap(),
    val tools: List<KoogTool> = emptyList(),
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList(),
    val mcpServers: List<KoogMcpServer> = emptyList()
)

enum class PlannerType { LLM, GOAP }

data class PlannerGoal(
    val name: String,
    val conditions: List<StateCondition>,
    val priority: Int = 0
)

data class PlannerAction(
    val name: String,
    val description: String,
    val preconditions: List<StateCondition> = emptyList(),
    val effects: List<StateEffect> = emptyList(),
    val cost: Int = 1
)

data class StateCondition(
    val key: String,
    val operator: ConditionOperator,
    val value: Any
)

data class StateEffect(
    val key: String,
    val value: Any
)

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt
git commit -m "feat: add PlannerAgent domain model (PlannerModel.kt)"
```

---

### Task 2: Frontend Types

**Files:**
- Create: `frontend/types/planner.ts`
- Modify: `frontend/types/graph.ts:27` (NodeType union)
- Modify: `frontend/types/graph.ts:29-34` (GraphNode.data union)

- [ ] **Step 1: Create frontend/types/planner.ts**

```typescript
import type { ModelProvider } from "./koog";

export interface PlannerAgentConfig {
  name: string;
  description: string;
  plannerType: "llm" | "goap";
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  maxReplanAttempts: number;
  goals: PlannerGoal[];
  actions: PlannerAction[];
  initialState: StateEntry[];
}

export interface PlannerGoal {
  id: string;
  name: string;
  conditions: StateCondition[];
  priority: number;
}

export interface PlannerAction {
  id: string;
  name: string;
  description: string;
  preconditions: StateCondition[];
  effects: StateEffect[];
  cost: number;
}

export interface StateCondition {
  key: string;
  operator: "equals" | "notEquals" | "greaterThan" | "lessThan";
  value: string | number | boolean;
}

export interface StateEffect {
  key: string;
  value: string | number | boolean;
}

export interface StateEntry {
  key: string;
  value: string | number | boolean;
}
```

- [ ] **Step 2: Add "planner-agent" to NodeType in graph.ts**

In `frontend/types/graph.ts`, change line 27:

```typescript
// OLD:
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server";

// NEW:
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent";
```

- [ ] **Step 3: Add PlannerAgentConfig to GraphNode.data union in graph.ts**

In `frontend/types/graph.ts`, add import at line 4:

```typescript
import { PlannerAgentConfig } from "./planner";
```

Then change the `data` field in `GraphNode` interface (line 32):

```typescript
// OLD:
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig;

// NEW:
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig;
```

- [ ] **Step 4: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/types/planner.ts frontend/types/graph.ts
git commit -m "feat: add PlannerAgentConfig type and planner-agent NodeType"
```

---

### Task 3: Editor Store — Default Data and addNode

**Files:**
- Modify: `frontend/store/editorStore.ts:2` (import)
- Modify: `frontend/store/editorStore.ts:52-57` (add defaultPlannerData after defaultMcpServerData)
- Modify: `frontend/store/editorStore.ts:94-115` (addNode switch)

- [ ] **Step 1: Add PlannerAgentConfig import in editorStore.ts**

In `frontend/store/editorStore.ts`, add at line 5 (after McpServerConfig import):

```typescript
import { PlannerAgentConfig } from "@/types/planner";
```

- [ ] **Step 2: Add defaultPlannerAgentData constant**

After the `defaultMcpServerData` block (after line 57), add:

```typescript
const defaultPlannerAgentData: PlannerAgentConfig = {
  name: "",
  description: "",
  plannerType: "llm",
  modelProvider: "anthropic",
  modelId: undefined,
  temperature: undefined,
  maxReplanAttempts: 3,
  goals: [],
  actions: [],
  initialState: [],
};
```

- [ ] **Step 3: Add planner-agent case to addNode**

In the `addNode` function, find the switch/if-else that assigns `data` based on `type`. Add a case for `"planner-agent"`:

The addNode function (around line 94-115) constructs a node with data based on type. Find where it handles `"mcp-server"` and add after it:

```typescript
} else if (type === "planner-agent") {
  data = { ...defaultPlannerAgentData };
}
```

- [ ] **Step 4: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat: add planner-agent defaults and addNode support in editorStore"
```

---

### Task 4: Rete Node Class and Canvas Sync

**Files:**
- Modify: `frontend/lib/rete-nodes.ts:55-69` (add PlannerAgentReteNode, update AppNode)
- Modify: `frontend/components/editor/Canvas.tsx:4` (import)
- Modify: `frontend/components/editor/Canvas.tsx:127-139` (node creation switch)
- Modify: `frontend/components/editor/Canvas.tsx:73-86` (connection logic)

- [ ] **Step 1: Add PlannerAgentReteNode in rete-nodes.ts**

After the `McpServerReteNode` class (after line 63), add:

```typescript
export class PlannerAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;
  public storeNodeId: string;
  constructor(storeNodeId: string, label: string) {
    super(label);
    this.storeNodeId = storeNodeId;
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
  }
}
```

- [ ] **Step 2: Add PlannerAgentReteNode to AppNode union type**

Change the AppNode type (line 65):

```typescript
// OLD:
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode;

// NEW:
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode;
```

- [ ] **Step 3: Import PlannerAgentReteNode in Canvas.tsx**

In `frontend/components/editor/Canvas.tsx`, add `PlannerAgentReteNode` to the import from `@/lib/rete-nodes` (line 4):

```typescript
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode, McpServerReteNode, PlannerAgentReteNode } from "@/lib/rete-nodes";
```

- [ ] **Step 4: Add planner-agent case to node sync in Canvas.tsx**

In the node creation section (around lines 127-139), add a case for `"planner-agent"`:

```typescript
} else if (node.type === "planner-agent") {
  reteNode = new PlannerAgentReteNode(node.id, (node.data as any).name || "Planner Agent");
}
```

- [ ] **Step 5: Allow connections TO planner-agent node in Canvas.tsx**

In the connection logic (around lines 73-86), where it checks target node type for allowed connections, add `PlannerAgentReteNode` as a valid target alongside `AgentReteNode` and `SubAgentReteNode`:

Find the condition that checks `targetNode instanceof AgentReteNode || targetNode instanceof SubAgentReteNode` and add:

```typescript
|| targetNode instanceof PlannerAgentReteNode
```

- [ ] **Step 6: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 7: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/components/editor/Canvas.tsx
git commit -m "feat: add PlannerAgentReteNode and Canvas sync for planner-agent"
```

---

### Task 5: CustomNode Colors and Icons

**Files:**
- Modify: `frontend/components/editor/CustomNode.tsx:6` (import icon)
- Modify: `frontend/components/editor/CustomNode.tsx:17-23` (nodeIcons)
- Modify: `frontend/components/editor/CustomNode.tsx:25-31` (nodeColors)
- Modify: `frontend/components/editor/CustomNode.tsx:33-39` (nodeTypeLabels)
- Modify: `frontend/components/editor/CustomNode.tsx:41-48` (getNodeType)
- Modify: `frontend/components/editor/CustomNode.tsx:87-104` (subtitle)

- [ ] **Step 1: Add ClipboardList icon import**

In `frontend/components/editor/CustomNode.tsx`, add `ClipboardListIcon` to the lucide-react import (line 6):

```typescript
import { BotIcon, WrenchIcon, UsersIcon, ServerIcon, ClipboardListIcon } from "lucide-react";
```

- [ ] **Step 2: Add PlannerAgentReteNode import**

Add to the rete-nodes import (line 4):

```typescript
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode, McpServerReteNode, PlannerAgentReteNode } from "@/lib/rete-nodes";
```

- [ ] **Step 3: Add planner-agent entries to all mappings**

Add to `nodeIcons` (after mcp-server entry):
```typescript
"planner-agent": ClipboardListIcon,
```

Add to `nodeColors` (after mcp-server entry):
```typescript
"planner-agent": {
  border: "rgba(236, 72, 153, 0.4)",
  glow: "rgba(236, 72, 153, 0.12)",
  badge: "rgba(236, 72, 153, 0.15)",
  badgeText: "#ec4899",
},
```

Add to `nodeTypeLabels` (after mcp-server entry):
```typescript
"planner-agent": "PLANNER",
```

- [ ] **Step 4: Add planner-agent to getNodeType function**

Add before the return fallback:
```typescript
if (data instanceof PlannerAgentReteNode) return "planner-agent";
```

- [ ] **Step 5: Add subtitle for planner-agent**

In the subtitle calculation section (around lines 87-104), add:
```typescript
} else if (type === "planner-agent") {
  const d = storeNode?.data as any;
  subtitle = d?.plannerType === "goap" ? "GOAP Planner" : "LLM Planner";
}
```

- [ ] **Step 6: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 7: Commit**

```bash
git add frontend/components/editor/CustomNode.tsx
git commit -m "feat: add pink color, icon, and subtitle for planner-agent node"
```

---

### Task 6: Toolbar Button

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx:4` (import icon)
- Modify: `frontend/components/panels/Toolbar.tsx:111-122` (add button after MCP Server)

- [ ] **Step 1: Add ClipboardListIcon import**

In `frontend/components/panels/Toolbar.tsx`, add `ClipboardListIcon` to the lucide-react import:

```typescript
import { ClipboardListIcon } from "lucide-react";
```

(Add to existing lucide-react import line if there is one, or add new import.)

- [ ] **Step 2: Add Planner Agent button after MCP Server button**

After the MCP Server button block (around line 122), add:

```tsx
<Button
  variant="ghost"
  size="sm"
  onClick={() => addNode("planner-agent", { x: 100, y: 400 })}
  title="Add Planner Agent"
>
  <ClipboardListIcon className="h-4 w-4 mr-1 text-pink-400" />
  Planner
</Button>
```

- [ ] **Step 3: Verify the app builds**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds (warnings OK)

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx
git commit -m "feat: add Planner Agent button to Toolbar"
```

---

### Task 7: Properties Panel Routing + PlannerProperties

**Files:**
- Create: `frontend/components/panels/PlannerProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx:10-11` (imports)
- Modify: `frontend/components/panels/PropertiesPanel.tsx:58-60` (routing)

- [ ] **Step 1: Create PlannerProperties.tsx**

```typescript
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { PlannerAgentConfig } from "@/types/planner";
import { ModelProvider } from "@/types/koog";
import { useState } from "react";
import { PlannerConfigPanel } from "@/components/planner/PlannerConfigPanel";

export function PlannerProperties({ nodeId, data }: { nodeId: string; data: PlannerAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<PlannerAgentConfig>) => updateNodeData(nodeId, field);
  const [configOpen, setConfigOpen] = useState(false);

  return (
    <>
      <div className="space-y-4 p-4">
        <div>
          <Label>Name</Label>
          <Input value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyPlannerAgent" />
        </div>
        <div>
          <Label>Description</Label>
          <Textarea value={data.description} onChange={(e) => update({ description: e.target.value })} rows={3} placeholder="Describe what this planner agent does..." />
        </div>
        <div>
          <Label>Planner Type</Label>
          <Select value={data.plannerType} onValueChange={(v) => update({ plannerType: v as "llm" | "goap" })}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="llm">LLM Planner</SelectItem>
              <SelectItem value="goap">GOAP Planner</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {data.plannerType === "llm" && (
          <>
            <div>
              <Label>Model Provider</Label>
              <Select value={data.modelProvider} onValueChange={(v) => update({ modelProvider: v as ModelProvider })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="openai">OpenAI</SelectItem>
                  <SelectItem value="anthropic">Anthropic</SelectItem>
                  <SelectItem value="google">Google</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label>Model ID</Label>
              <Input value={data.modelId ?? ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="claude-sonnet-4" />
            </div>
          </>
        )}
        <Button variant="outline" className="w-full" onClick={() => setConfigOpen(true)}>
          Configure Goals, Actions & State
        </Button>
      </div>
      {configOpen && (
        <PlannerConfigPanel nodeId={nodeId} data={data} onClose={() => setConfigOpen(false)} />
      )}
    </>
  );
}
```

- [ ] **Step 2: Add routing in PropertiesPanel.tsx**

Add imports in `frontend/components/panels/PropertiesPanel.tsx`:

```typescript
import { PlannerProperties } from "./PlannerProperties";
import { PlannerAgentConfig } from "@/types/planner";
```

Add routing case after the `mcp-server` block (after line 60):

```tsx
{selectedNode.type === "planner-agent" && (
  <PlannerProperties nodeId={selectedNode.id} data={selectedNode.data as PlannerAgentConfig} />
)}
```

- [ ] **Step 3: Verify TypeScript compiles (will fail — PlannerConfigPanel doesn't exist yet, that's OK)**

Note: This will fail until Task 8 creates PlannerConfigPanel. Move on.

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/PlannerProperties.tsx frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat: add PlannerProperties panel and PropertiesPanel routing"
```

---

### Task 8: Planner Config Sliding Panel

**Files:**
- Create: `frontend/components/planner/PlannerConfigPanel.tsx`
- Create: `frontend/components/planner/GoalsTab.tsx`
- Create: `frontend/components/planner/ActionsTab.tsx`
- Create: `frontend/components/planner/StateTab.tsx`
- Create: `frontend/components/planner/SettingsTab.tsx`

- [ ] **Step 1: Create GoalsTab.tsx**

```typescript
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PlannerGoal, StateCondition } from "@/types/planner";
import { Trash2Icon, PlusIcon, ChevronDownIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";

function ConditionEditor({
  conditions,
  onChange,
  label,
}: {
  conditions: StateCondition[];
  onChange: (conditions: StateCondition[]) => void;
  label: string;
}) {
  const addCondition = () => {
    onChange([...conditions, { key: "", operator: "equals", value: "" }]);
  };

  const updateCondition = (index: number, field: Partial<StateCondition>) => {
    const updated = conditions.map((c, i) => (i === index ? { ...c, ...field } : c));
    onChange(updated);
  };

  const removeCondition = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label className="text-xs text-muted-foreground">{label}</Label>
        <Button variant="ghost" size="sm" onClick={addCondition} className="h-6 px-2">
          <PlusIcon className="h-3 w-3" />
        </Button>
      </div>
      {conditions.map((cond, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={cond.key}
            onChange={(e) => updateCondition(i, { key: e.target.value })}
          />
          <Select value={cond.operator} onValueChange={(v) => updateCondition(i, { operator: v as StateCondition["operator"] })}>
            <SelectTrigger className="h-7 text-xs w-24"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="equals">=</SelectItem>
              <SelectItem value="notEquals">!=</SelectItem>
              <SelectItem value="greaterThan">&gt;</SelectItem>
              <SelectItem value="lessThan">&lt;</SelectItem>
            </SelectContent>
          </Select>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(cond.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateCondition(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeCondition(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
    </div>
  );
}

export { ConditionEditor };

export function GoalsTab({
  goals,
  onChange,
}: {
  goals: PlannerGoal[];
  onChange: (goals: PlannerGoal[]) => void;
}) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const addGoal = () => {
    const id = `goal-${Date.now()}`;
    onChange([...goals, { id, name: "", conditions: [], priority: 0 }]);
    setExpandedId(id);
  };

  const updateGoal = (id: string, field: Partial<PlannerGoal>) => {
    onChange(goals.map((g) => (g.id === id ? { ...g, ...field } : g)));
  };

  const removeGoal = (id: string) => {
    onChange(goals.filter((g) => g.id !== id));
  };

  return (
    <div className="space-y-3">
      {goals.map((goal) => (
        <div key={goal.id} className="border border-border rounded-md p-3 space-y-2">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={() => setExpandedId(expandedId === goal.id ? null : goal.id)}
            >
              {expandedId === goal.id ? <ChevronDownIcon className="h-3 w-3" /> : <ChevronRightIcon className="h-3 w-3" />}
            </Button>
            <Input
              className="h-7 text-sm flex-1"
              placeholder="Goal name"
              value={goal.name}
              onChange={(e) => updateGoal(goal.id, { name: e.target.value })}
            />
            <Input
              className="h-7 text-xs w-16"
              type="number"
              placeholder="Priority"
              value={goal.priority}
              onChange={(e) => updateGoal(goal.id, { priority: parseInt(e.target.value) || 0 })}
            />
            <Button variant="ghost" size="sm" onClick={() => removeGoal(goal.id)} className="h-7 px-1">
              <Trash2Icon className="h-3 w-3" />
            </Button>
          </div>
          {expandedId === goal.id && (
            <ConditionEditor
              label="Conditions (all must be true)"
              conditions={goal.conditions}
              onChange={(conditions) => updateGoal(goal.id, { conditions })}
            />
          )}
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addGoal} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add Goal
      </Button>
    </div>
  );
}
```

- [ ] **Step 2: Create ActionsTab.tsx**

```typescript
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { PlannerAction, StateEffect } from "@/types/planner";
import { ConditionEditor } from "./GoalsTab";
import { Trash2Icon, PlusIcon, ChevronDownIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";

function EffectsEditor({
  effects,
  onChange,
}: {
  effects: StateEffect[];
  onChange: (effects: StateEffect[]) => void;
}) {
  const addEffect = () => {
    onChange([...effects, { key: "", value: "" }]);
  };

  const updateEffect = (index: number, field: Partial<StateEffect>) => {
    const updated = effects.map((e, i) => (i === index ? { ...e, ...field } : e));
    onChange(updated);
  };

  const removeEffect = (index: number) => {
    onChange(effects.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label className="text-xs text-muted-foreground">Effects</Label>
        <Button variant="ghost" size="sm" onClick={addEffect} className="h-6 px-2">
          <PlusIcon className="h-3 w-3" />
        </Button>
      </div>
      {effects.map((effect, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={effect.key}
            onChange={(e) => updateEffect(i, { key: e.target.value })}
          />
          <span className="text-xs text-muted-foreground px-1">=</span>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(effect.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateEffect(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeEffect(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
    </div>
  );
}

export function ActionsTab({
  actions,
  onChange,
}: {
  actions: PlannerAction[];
  onChange: (actions: PlannerAction[]) => void;
}) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const addAction = () => {
    const id = `action-${Date.now()}`;
    onChange([...actions, { id, name: "", description: "", preconditions: [], effects: [], cost: 1 }]);
    setExpandedId(id);
  };

  const updateAction = (id: string, field: Partial<PlannerAction>) => {
    onChange(actions.map((a) => (a.id === id ? { ...a, ...field } : a)));
  };

  const removeAction = (id: string) => {
    onChange(actions.filter((a) => a.id !== id));
  };

  return (
    <div className="space-y-3">
      {actions.map((action) => (
        <div key={action.id} className="border border-border rounded-md p-3 space-y-2">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={() => setExpandedId(expandedId === action.id ? null : action.id)}
            >
              {expandedId === action.id ? <ChevronDownIcon className="h-3 w-3" /> : <ChevronRightIcon className="h-3 w-3" />}
            </Button>
            <Input
              className="h-7 text-sm flex-1"
              placeholder="Action name"
              value={action.name}
              onChange={(e) => updateAction(action.id, { name: e.target.value })}
            />
            <Input
              className="h-7 text-xs w-16"
              type="number"
              placeholder="Cost"
              value={action.cost}
              onChange={(e) => updateAction(action.id, { cost: parseInt(e.target.value) || 1 })}
            />
            <Button variant="ghost" size="sm" onClick={() => removeAction(action.id)} className="h-7 px-1">
              <Trash2Icon className="h-3 w-3" />
            </Button>
          </div>
          {expandedId === action.id && (
            <div className="space-y-3 pl-8">
              <Textarea
                className="text-xs"
                rows={2}
                placeholder="Action description..."
                value={action.description}
                onChange={(e) => updateAction(action.id, { description: e.target.value })}
              />
              <ConditionEditor
                label="Preconditions"
                conditions={action.preconditions}
                onChange={(preconditions) => updateAction(action.id, { preconditions })}
              />
              <EffectsEditor
                effects={action.effects}
                onChange={(effects) => updateAction(action.id, { effects })}
              />
            </div>
          )}
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addAction} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add Action
      </Button>
    </div>
  );
}
```

- [ ] **Step 3: Create StateTab.tsx**

```typescript
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { StateEntry } from "@/types/planner";
import { Trash2Icon, PlusIcon } from "lucide-react";

export function StateTab({
  entries,
  onChange,
}: {
  entries: StateEntry[];
  onChange: (entries: StateEntry[]) => void;
}) {
  const addEntry = () => {
    onChange([...entries, { key: "", value: "" }]);
  };

  const updateEntry = (index: number, field: Partial<StateEntry>) => {
    const updated = entries.map((e, i) => (i === index ? { ...e, ...field } : e));
    onChange(updated);
  };

  const removeEntry = (index: number) => {
    onChange(entries.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground mb-2">
        Define the initial state as key-value pairs. Values can be strings, numbers, or booleans.
      </div>
      {entries.map((entry, i) => (
        <div key={i} className="flex gap-1 items-center">
          <Input
            className="h-7 text-xs flex-1"
            placeholder="key"
            value={entry.key}
            onChange={(e) => updateEntry(i, { key: e.target.value })}
          />
          <span className="text-xs text-muted-foreground px-1">=</span>
          <Input
            className="h-7 text-xs flex-1"
            placeholder="value"
            value={String(entry.value)}
            onChange={(e) => {
              const raw = e.target.value;
              const value = raw === "true" ? true : raw === "false" ? false : isNaN(Number(raw)) ? raw : Number(raw);
              updateEntry(i, { value });
            }}
          />
          <Button variant="ghost" size="sm" onClick={() => removeEntry(i)} className="h-7 px-1">
            <Trash2Icon className="h-3 w-3" />
          </Button>
        </div>
      ))}
      <Button variant="outline" size="sm" onClick={addEntry} className="w-full">
        <PlusIcon className="h-3 w-3 mr-1" /> Add State Entry
      </Button>
    </div>
  );
}
```

- [ ] **Step 4: Create SettingsTab.tsx**

```typescript
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PlannerAgentConfig } from "@/types/planner";
import { ModelProvider } from "@/types/koog";

export function SettingsTab({
  data,
  onUpdate,
}: {
  data: PlannerAgentConfig;
  onUpdate: (field: Partial<PlannerAgentConfig>) => void;
}) {
  return (
    <div className="space-y-4">
      <div>
        <Label>Planner Type</Label>
        <Select value={data.plannerType} onValueChange={(v) => onUpdate({ plannerType: v as "llm" | "goap" })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="llm">LLM Planner</SelectItem>
            <SelectItem value="goap">GOAP Planner</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {data.plannerType === "llm" && (
        <>
          <div>
            <Label>Model Provider</Label>
            <Select value={data.modelProvider} onValueChange={(v) => onUpdate({ modelProvider: v as ModelProvider })}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="openai">OpenAI</SelectItem>
                <SelectItem value="anthropic">Anthropic</SelectItem>
                <SelectItem value="google">Google</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div>
            <Label>Model ID</Label>
            <Input
              value={data.modelId ?? ""}
              onChange={(e) => onUpdate({ modelId: e.target.value || undefined })}
              placeholder="claude-sonnet-4"
            />
          </div>
          <div>
            <Label>Temperature</Label>
            <Input
              type="number"
              step="0.1"
              min="0"
              max="2"
              value={data.temperature ?? ""}
              onChange={(e) => onUpdate({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })}
              placeholder="0.7"
            />
          </div>
        </>
      )}
      <div>
        <Label>Max Replan Attempts</Label>
        <Input
          type="number"
          min="0"
          max="10"
          value={data.maxReplanAttempts}
          onChange={(e) => onUpdate({ maxReplanAttempts: parseInt(e.target.value) || 3 })}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Create PlannerConfigPanel.tsx**

```typescript
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { PlannerAgentConfig } from "@/types/planner";
import { GoalsTab } from "./GoalsTab";
import { ActionsTab } from "./ActionsTab";
import { StateTab } from "./StateTab";
import { SettingsTab } from "./SettingsTab";
import { XIcon } from "lucide-react";

type TabId = "goals" | "actions" | "state" | "settings";

export function PlannerConfigPanel({
  nodeId,
  data,
  onClose,
}: {
  nodeId: string;
  data: PlannerAgentConfig;
  onClose: () => void;
}) {
  const [activeTab, setActiveTab] = useState<TabId>("goals");
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<PlannerAgentConfig>) => updateNodeData(nodeId, field);

  const tabs: { id: TabId; label: string }[] = [
    { id: "goals", label: "Goals" },
    { id: "actions", label: "Actions" },
    { id: "state", label: "State" },
    { id: "settings", label: "Settings" },
  ];

  return (
    <div className="fixed inset-y-0 right-0 w-[55%] bg-background border-l-2 border-pink-500/40 z-50 flex flex-col shadow-2xl">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-sm font-semibold">Planner: {data.name || "Untitled"}</span>
        <Button variant="ghost" size="sm" onClick={onClose} className="h-7 w-7 p-0">
          <XIcon className="h-4 w-4" />
        </Button>
      </div>
      <div className="flex gap-1 px-4 pt-2">
        {tabs.map((tab) => (
          <Button
            key={tab.id}
            variant={activeTab === tab.id ? "default" : "ghost"}
            size="sm"
            onClick={() => setActiveTab(tab.id)}
            className="text-xs"
          >
            {tab.label}
          </Button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === "goals" && (
          <GoalsTab goals={data.goals} onChange={(goals) => update({ goals })} />
        )}
        {activeTab === "actions" && (
          <ActionsTab actions={data.actions} onChange={(actions) => update({ actions })} />
        )}
        {activeTab === "state" && (
          <StateTab entries={data.initialState} onChange={(initialState) => update({ initialState })} />
        )}
        {activeTab === "settings" && (
          <SettingsTab data={data} onUpdate={update} />
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 7: Commit**

```bash
git add frontend/components/planner/
git commit -m "feat: add PlannerConfigPanel with Goals, Actions, State, Settings tabs"
```

---

### Task 9: Frontend Validation

**Files:**
- Modify: `frontend/lib/validation.ts:1` (import)
- Modify: `frontend/lib/validation.ts:126-137` (add planner-agent validation before connection count check)

- [ ] **Step 1: Add PlannerAgentConfig import**

In `frontend/lib/validation.ts`, add:

```typescript
import { PlannerAgentConfig } from "@/types/planner";
```

- [ ] **Step 2: Add planner-agent validation rules**

In `validateGraph()`, before the connection count validation section (around line 129), add a new block for `"planner-agent"`:

```typescript
} else if (node.type === "planner-agent") {
  const d = node.data as PlannerAgentConfig;
  if (!d.name?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have a name" });
  }
  if (!d.goals || d.goals.length === 0) {
    errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have at least one goal" });
  }
  if (!d.actions || d.actions.length === 0) {
    errors.push({ nodeId: node.id, severity: "error", message: "Planner agent must have at least one action" });
  }
  if (d.plannerType === "llm" && !d.modelProvider) {
    errors.push({ nodeId: node.id, severity: "error", message: "LLM planner requires a model provider" });
  }
  // Goal-level checks
  const goalNames = new Set<string>();
  for (const goal of d.goals ?? []) {
    if (goal.name && goalNames.has(goal.name)) {
      errors.push({ nodeId: node.id, severity: "error", message: `Duplicate goal name: '${goal.name}'` });
    }
    goalNames.add(goal.name);
    if (!goal.conditions || goal.conditions.length === 0) {
      errors.push({ nodeId: node.id, severity: "warning", message: `Goal '${goal.name || "unnamed"}' has no conditions` });
    }
    for (const cond of goal.conditions ?? []) {
      if (!cond.key?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Condition key must not be empty" });
      }
    }
  }
  // Action-level checks
  const actionNames = new Set<string>();
  for (const action of d.actions ?? []) {
    if (action.name && actionNames.has(action.name)) {
      errors.push({ nodeId: node.id, severity: "error", message: `Duplicate action name: '${action.name}'` });
    }
    actionNames.add(action.name);
    if (!action.preconditions || action.preconditions.length === 0) {
      errors.push({ nodeId: node.id, severity: "warning", message: `Action '${action.name || "unnamed"}' has no preconditions` });
    }
    if (!action.effects || action.effects.length === 0) {
      errors.push({ nodeId: node.id, severity: "warning", message: `Action '${action.name || "unnamed"}' has no effects` });
    }
    for (const cond of action.preconditions ?? []) {
      if (!cond.key?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Condition key must not be empty" });
      }
    }
    for (const eff of action.effects ?? []) {
      if (!eff.key?.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Effect key must not be empty" });
      }
    }
  }
}
```

- [ ] **Step 3: Update connection count validation to include planner-agent**

In the connection count validation (around line 129-137), find where it checks target node type for the "can only connect to ONE agent/sub-agent" rule. Add `"planner-agent"` as a valid target type alongside `"agent"` and `"sub-agent"`.

- [ ] **Step 4: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/validation.ts
git commit -m "feat: add planner-agent validation rules"
```

---

### Task 10: Plan Inspector in PreviewPanel

**Files:**
- Create: `frontend/components/planner/PlanInspector.tsx`
- Modify: `frontend/components/preview/PreviewPanel.tsx` (add plan inspection mode)

- [ ] **Step 1: Create PlanInspector.tsx**

```typescript
import { Badge } from "@/components/ui/badge";
import { PlannerAgentConfig, StateEntry } from "@/types/planner";

interface PlanStep {
  actionName: string;
  status: "pending" | "running" | "done" | "failed";
}

export function PlanInspector({ data }: { data: PlannerAgentConfig }) {
  // V1: Static inspection showing configured goals, actions, and initial state.
  // No live execution — this displays the planner configuration as a plan overview.
  const mockPlan: PlanStep[] = data.actions.map((a) => ({
    actionName: a.name || "Unnamed Action",
    status: "pending" as const,
  }));

  const statusIcons: Record<PlanStep["status"], string> = {
    pending: "\u25cb",
    running: "\u25d4",
    done: "\u25cf",
    failed: "\u2717",
  };

  const statusColors: Record<PlanStep["status"], string> = {
    pending: "text-muted-foreground",
    running: "text-blue-400",
    done: "text-green-400",
    failed: "text-red-400",
  };

  return (
    <div className="p-4 space-y-4 text-sm">
      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Goals</h4>
        {data.goals.length === 0 ? (
          <p className="text-xs text-muted-foreground">No goals defined</p>
        ) : (
          <div className="space-y-1">
            {data.goals.map((goal) => (
              <div key={goal.id} className="flex items-center gap-2">
                <Badge variant="outline" className="text-xs">{goal.priority}</Badge>
                <span>{goal.name || "Unnamed"}</span>
                <span className="text-xs text-muted-foreground">
                  ({goal.conditions.length} condition{goal.conditions.length !== 1 ? "s" : ""})
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Plan Steps</h4>
        {mockPlan.length === 0 ? (
          <p className="text-xs text-muted-foreground">No actions defined</p>
        ) : (
          <div className="space-y-1">
            {mockPlan.map((step, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className={statusColors[step.status]}>{statusIcons[step.status]}</span>
                <span>{step.actionName}</span>
                <span className="text-xs text-muted-foreground">cost: {data.actions[i]?.cost ?? 1}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Initial State</h4>
        {data.initialState.length === 0 ? (
          <p className="text-xs text-muted-foreground">No initial state defined</p>
        ) : (
          <div className="border border-border rounded-md overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-muted/50">
                  <th className="text-left px-2 py-1">Key</th>
                  <th className="text-left px-2 py-1">Value</th>
                </tr>
              </thead>
              <tbody>
                {data.initialState.map((entry, i) => (
                  <tr key={i} className="border-t border-border">
                    <td className="px-2 py-1 font-mono">{entry.key}</td>
                    <td className="px-2 py-1 font-mono">{String(entry.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div>
        <h4 className="text-xs font-semibold text-muted-foreground uppercase mb-2">Status</h4>
        <Badge variant="secondary">Not Started (V1: Static View)</Badge>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Integrate PlanInspector into PreviewPanel**

In `frontend/components/preview/PreviewPanel.tsx`:

Add imports:
```typescript
import { PlanInspector } from "@/components/planner/PlanInspector";
import { PlannerAgentConfig } from "@/types/planner";
```

In the component body, find the selected node to check if it's a planner-agent. Add after the existing store hooks:

```typescript
const selectedNodeIds = useEditorStore((s) => s.selectedNodeIds);
const nodes = useEditorStore((s) => s.nodes);
const selectedNode = selectedNodeIds.size === 1 ? nodes.find((n) => selectedNodeIds.has(n.id)) : null;
const isPlannerSelected = selectedNode?.type === "planner-agent";
```

In the content area, add a condition: when `isPlannerSelected` and `activeTab === "preview"`, render PlanInspector instead of the normal chat:

```tsx
{activeTab === "preview" && isPlannerSelected ? (
  <PlanInspector data={selectedNode!.data as PlannerAgentConfig} />
) : activeTab === "preview" ? (
  /* existing chat/preview content */
) : (
  /* existing export review content */
)}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/components/planner/PlanInspector.tsx frontend/components/preview/PreviewPanel.tsx
git commit -m "feat: add PlanInspector component and integrate into PreviewPanel"
```

---

### Task 11: Backend Adapter — GraphToKoogAdapter

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Write the test for adapter conversion**

Create test file `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterPlannerTest.kt`:

```kotlin
package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GraphToKoogAdapterPlannerTest {
    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts planner-agent node to KoogPlannerAgent`() {
        val graph = WorkspaceGraph(
            version = 1,
            name = "TestWorkspace",
            nodes = listOf(
                GraphNode(
                    id = "node-1",
                    type = "planner-agent",
                    position = Position(0.0, 0.0),
                    data = mapOf(
                        "name" to "DeliveryPlanner",
                        "description" to "Plans deliveries",
                        "plannerType" to "llm",
                        "modelProvider" to "anthropic",
                        "modelId" to "claude-sonnet-4",
                        "temperature" to 0.7,
                        "maxReplanAttempts" to 5,
                        "goals" to listOf(
                            mapOf(
                                "id" to "g1",
                                "name" to "all_delivered",
                                "conditions" to listOf(
                                    mapOf("key" to "delivered", "operator" to "equals", "value" to true)
                                ),
                                "priority" to 1
                            )
                        ),
                        "actions" to listOf(
                            mapOf(
                                "id" to "a1",
                                "name" to "pickup",
                                "description" to "Pick up item",
                                "preconditions" to listOf(
                                    mapOf("key" to "at_warehouse", "operator" to "equals", "value" to true)
                                ),
                                "effects" to listOf(
                                    mapOf("key" to "has_item", "value" to true)
                                ),
                                "cost" to 2
                            )
                        ),
                        "initialState" to listOf(
                            mapOf("key" to "at_warehouse", "value" to true),
                            mapOf("key" to "has_item", "value" to false)
                        )
                    )
                )
            ),
            edges = emptyList()
        )

        val result = adapter.convertPlannerAgents(graph)
        assertEquals(1, result.size)
        val planner = result[0]
        assertEquals("DeliveryPlanner", planner.name)
        assertEquals("Plans deliveries", planner.description)
        assertEquals(PlannerType.LLM, planner.plannerType)
        assertEquals(ModelProvider.ANTHROPIC, planner.modelProvider)
        assertEquals("claude-sonnet-4", planner.modelId)
        assertEquals(0.7, planner.temperature)
        assertEquals(5, planner.maxReplanAttempts)
        assertEquals(1, planner.goals.size)
        assertEquals("all_delivered", planner.goals[0].name)
        assertEquals(1, planner.goals[0].conditions.size)
        assertEquals(ConditionOperator.EQUALS, planner.goals[0].conditions[0].operator)
        assertEquals(1, planner.actions.size)
        assertEquals("pickup", planner.actions[0].name)
        assertEquals(2, planner.actions[0].cost)
        assertEquals(1, planner.actions[0].preconditions.size)
        assertEquals(1, planner.actions[0].effects.size)
        assertEquals(2, planner.initialState.size)
    }

    @Test
    fun `converts planner-agent with connected tools`() {
        val graph = WorkspaceGraph(
            version = 1,
            name = "TestWorkspace",
            nodes = listOf(
                GraphNode(
                    id = "node-1",
                    type = "planner-agent",
                    position = Position(0.0, 0.0),
                    data = mapOf(
                        "name" to "Planner",
                        "description" to "",
                        "plannerType" to "goap",
                        "maxReplanAttempts" to 3,
                        "goals" to emptyList<Any>(),
                        "actions" to emptyList<Any>(),
                        "initialState" to emptyList<Any>()
                    )
                ),
                GraphNode(
                    id = "node-2",
                    type = "tool",
                    position = Position(100.0, 0.0),
                    data = mapOf(
                        "name" to "SearchTool",
                        "description" to "Searches",
                        "parameters" to emptyList<Any>()
                    )
                )
            ),
            edges = listOf(
                GraphEdge(id = "edge-1", source = "node-2", target = "node-1")
            )
        )

        val result = adapter.convertPlannerAgents(graph)
        assertEquals(1, result.size)
        assertEquals(PlannerType.GOAP, result[0].plannerType)
        assertEquals(1, result[0].tools.size)
        assertEquals("SearchTool", result[0].tools[0].name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterPlannerTest"`
Expected: FAIL — `convertPlannerAgents` method doesn't exist

- [ ] **Step 3: Implement convertPlannerAgents in GraphToKoogAdapter**

In `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`, add this method:

```kotlin
fun convertPlannerAgents(graph: WorkspaceGraph): List<KoogPlannerAgent> {
    val plannerNodes = graph.nodes.filter { it.type == "planner-agent" }
    val toolNodes = graph.nodes.filter { it.type == "tool" }
    val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
    val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
    val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }

    return plannerNodes.map { plannerNode ->
        val connectedIds = graph.edges.filter { it.target == plannerNode.id }.map { it.source }.toSet()

        val tools = toolNodes.filter { it.id in connectedIds }.map { convertTool(it) }
        val builtinTools = builtinToolNodes.filter { it.id in connectedIds }.map { convertBuiltinTool(it) }
        val mcpServers = mcpServerNodes.filter { it.id in connectedIds }.map { convertMcpServer(it) }

        val subAgentIds = subAgentNodes.filter { it.id in connectedIds }.map { it.id }.toSet()
        val subAgents = subAgentNodes.filter { it.id in subAgentIds }.map { convertSubAgent(it, graph, toolNodes, builtinToolNodes) }

        val name = plannerNode.data["name"] as? String ?: ""
        val description = plannerNode.data["description"] as? String ?: ""
        val plannerTypeStr = plannerNode.data["plannerType"] as? String ?: "llm"
        val plannerType = if (plannerTypeStr == "goap") PlannerType.GOAP else PlannerType.LLM

        val providerStr = plannerNode.data["modelProvider"] as? String
        val modelProvider = providerStr?.let { parseProvider(it) }
        val modelId = plannerNode.data["modelId"] as? String
        val temperature = (plannerNode.data["temperature"] as? Number)?.toDouble()
        val maxReplanAttempts = (plannerNode.data["maxReplanAttempts"] as? Number)?.toInt() ?: 3

        val goalsRaw = plannerNode.data["goals"] as? List<Map<String, Any?>> ?: emptyList()
        val goals = goalsRaw.map { goalMap ->
            val conditionsRaw = goalMap["conditions"] as? List<Map<String, Any?>> ?: emptyList()
            PlannerGoal(
                name = goalMap["name"] as? String ?: "",
                conditions = conditionsRaw.map { parseCondition(it) },
                priority = (goalMap["priority"] as? Number)?.toInt() ?: 0
            )
        }

        val actionsRaw = plannerNode.data["actions"] as? List<Map<String, Any?>> ?: emptyList()
        val actions = actionsRaw.map { actionMap ->
            val preRaw = actionMap["preconditions"] as? List<Map<String, Any?>> ?: emptyList()
            val effRaw = actionMap["effects"] as? List<Map<String, Any?>> ?: emptyList()
            PlannerAction(
                name = actionMap["name"] as? String ?: "",
                description = actionMap["description"] as? String ?: "",
                preconditions = preRaw.map { parseCondition(it) },
                effects = effRaw.map { parseEffect(it) },
                cost = (actionMap["cost"] as? Number)?.toInt() ?: 1
            )
        }

        val stateRaw = plannerNode.data["initialState"] as? List<Map<String, Any?>> ?: emptyList()
        val initialState = stateRaw.associate { (it["key"] as? String ?: "") to (it["value"] ?: "") }

        KoogPlannerAgent(
            name = name,
            description = description,
            plannerType = plannerType,
            modelProvider = modelProvider,
            modelId = modelId,
            temperature = temperature,
            maxReplanAttempts = maxReplanAttempts,
            goals = goals,
            actions = actions,
            initialState = initialState,
            tools = tools,
            builtinTools = builtinTools,
            subAgents = subAgents,
            mcpServers = mcpServers
        )
    }
}

private fun parseCondition(map: Map<String, Any?>): StateCondition {
    val operatorStr = map["operator"] as? String ?: "equals"
    val operator = when (operatorStr) {
        "notEquals" -> ConditionOperator.NOT_EQUALS
        "greaterThan" -> ConditionOperator.GREATER_THAN
        "lessThan" -> ConditionOperator.LESS_THAN
        else -> ConditionOperator.EQUALS
    }
    return StateCondition(
        key = map["key"] as? String ?: "",
        operator = operator,
        value = map["value"] ?: ""
    )
}

private fun parseEffect(map: Map<String, Any?>): StateEffect {
    return StateEffect(
        key = map["key"] as? String ?: "",
        value = map["value"] ?: ""
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterPlannerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterPlannerTest.kt
git commit -m "feat: add planner-agent conversion to GraphToKoogAdapter"
```

---

### Task 12: Backend Validator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`

- [ ] **Step 1: Write the validator test**

Create `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorPlannerTest.kt`:

```kotlin
package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphValidatorPlannerTest {
    private val validator = GraphValidator()

    private fun plannerNode(data: Map<String, Any?>) = GraphNode(
        id = "node-1", type = "planner-agent", position = Position(0.0, 0.0), data = data
    )

    private fun validPlannerData() = mapOf<String, Any?>(
        "name" to "MyPlanner",
        "plannerType" to "llm",
        "modelProvider" to "anthropic",
        "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)))),
        "actions" to listOf(mapOf("name" to "a1", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true))))
    )

    @Test
    fun `valid planner agent produces no errors`() {
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(validPlannerData())), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `planner agent without name produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["name"] = "" }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") }, "Expected name error")
    }

    @Test
    fun `planner agent without goals produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["goals"] = emptyList<Any>() }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("goal") }, "Expected goal error")
    }

    @Test
    fun `planner agent without actions produces error`() {
        val data = validPlannerData().toMutableMap().apply { this["actions"] = emptyList<Any>() }
        val graph = WorkspaceGraph(1, "ws", listOf(plannerNode(data)), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("action") }, "Expected action error")
    }

    @Test
    fun `planner and regular agent cannot coexist`() {
        val graph = WorkspaceGraph(1, "ws", listOf(
            plannerNode(validPlannerData()),
            GraphNode("node-2", "agent", Position(0.0, 0.0), mapOf("name" to "Agent", "systemPrompt" to "Hi"))
        ), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("coexist") || it.contains("both") }, "Expected coexistence error")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorPlannerTest"`
Expected: FAIL

- [ ] **Step 3: Add planner-agent validation rules to GraphValidator**

In `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`, modify the `validate` method:

1. At the top (around lines 10-16), replace the "must have exactly one agent" check to also consider planner-agents:

```kotlin
val agentCount = graph.nodes.count { it.type == "agent" }
val plannerCount = graph.nodes.count { it.type == "planner-agent" }
if (agentCount == 0 && plannerCount == 0) {
    errors.add("Workspace must have at least one agent or planner-agent")
}
if (agentCount > 0 && plannerCount > 0) {
    errors.add("Workspace cannot have both a regular agent and a planner-agent")
}
if (agentCount > 1) {
    errors.add("Workspace can have at most one agent")
}
if (plannerCount > 1) {
    errors.add("Workspace can have at most one planner-agent")
}
```

2. In the node-type loop, add a `"planner-agent"` case:

```kotlin
"planner-agent" -> {
    val name = node.data["name"] as? String ?: ""
    if (name.isBlank()) {
        errors.add("[${node.id}] Planner agent requires a name")
    }
    val goals = node.data["goals"] as? List<*> ?: emptyList<Any>()
    if (goals.isEmpty()) {
        errors.add("[${node.id}] Planner agent must have at least one goal")
    }
    val actions = node.data["actions"] as? List<*> ?: emptyList<Any>()
    if (actions.isEmpty()) {
        errors.add("[${node.id}] Planner agent must have at least one action")
    }
    val plannerType = node.data["plannerType"] as? String ?: "llm"
    if (plannerType == "llm") {
        val provider = node.data["modelProvider"] as? String
        if (provider.isNullOrBlank()) {
            errors.add("[${node.id}] LLM planner requires a model provider")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorPlannerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorPlannerTest.kt
git commit -m "feat: add planner-agent validation rules to GraphValidator"
```

---

### Task 13: Backend Generator — PlannerAgentGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/PlannerAgentGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/PlannerAgentGeneratorTest.kt`

- [ ] **Step 1: Write the generator test**

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PlannerAgentGeneratorTest {
    private val generator = PlannerAgentGenerator()

    private fun llmPlanner(
        tools: List<KoogTool> = emptyList(),
        mcpServers: List<KoogMcpServer> = emptyList()
    ) = KoogPlannerAgent(
        name = "DeliveryPlanner",
        description = "Plans deliveries",
        plannerType = PlannerType.LLM,
        modelProvider = ModelProvider.ANTHROPIC,
        modelId = "claude-sonnet-4",
        temperature = 0.7,
        maxReplanAttempts = 5,
        goals = listOf(
            PlannerGoal("all_delivered", listOf(StateCondition("delivered", ConditionOperator.EQUALS, true)), 1)
        ),
        actions = listOf(
            PlannerAction(
                "pickup", "Pick up item",
                listOf(StateCondition("at_warehouse", ConditionOperator.EQUALS, true)),
                listOf(StateEffect("has_item", true)),
                2
            )
        ),
        initialState = mapOf("at_warehouse" to true, "has_item" to false),
        tools = tools,
        mcpServers = mcpServers
    )

    private fun goapPlanner() = llmPlanner().copy(
        plannerType = PlannerType.GOAP,
        modelProvider = null,
        modelId = null,
        temperature = null
    )

    @Test
    fun `generates LLM planner Spring Configuration`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "@Configuration")
        assertContains(config, "@Bean")
        assertContains(config, "AnthropicModels")
        assertContains(config, "simpleAnthropicExecutor")
        assertContains(config, "all_delivered")
        assertContains(config, "pickup")
        assertContains(config, "maxReplanAttempts = 5")
    }

    @Test
    fun `generates GOAP planner with solver`() {
        val files = generator.generate(goapPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "GOAP")
        assertContains(config, "cost")
        assertContains(config, "all_delivered")
        assertContains(config, "pickup")
        assertFalse(config.contains("simpleAnthropicExecutor"))
    }

    @Test
    fun `generates goals and actions data classes`() {
        val files = generator.generate(llmPlanner())
        val goals = files["PlannerGoals.kt"]!!
        assertContains(goals, "all_delivered")
        assertContains(goals, "delivered")
        assertContains(goals, "EQUALS")

        val actions = files["PlannerActions.kt"]!!
        assertContains(actions, "pickup")
        assertContains(actions, "at_warehouse")
        assertContains(actions, "has_item")
        assertContains(actions, "cost = 2")
    }

    @Test
    fun `generates initial state`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "at_warehouse")
        assertContains(config, "has_item")
    }

    @Test
    fun `generates tool registry when tools present`() {
        val tool = KoogTool("SearchTool", "Searches things", emptyList())
        val files = generator.generate(llmPlanner(tools = listOf(tool)))
        val config = files["PlannerAgent.kt"]!!
        assertContains(config, "toolRegistry")
    }

    @Test
    fun `omits tool registry when no tools`() {
        val files = generator.generate(llmPlanner())
        val config = files["PlannerAgent.kt"]!!
        assertFalse(config.contains("toolRegistry"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.PlannerAgentGeneratorTest"`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Create PlannerAgentGenerator.kt**

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class PlannerAgentGenerator {

    fun generate(planner: KoogPlannerAgent): Map<String, String> {
        val files = mutableMapOf<String, String>()
        files["PlannerGoals.kt"] = generateGoals(planner)
        files["PlannerActions.kt"] = generateActions(planner)
        files["PlannerAgent.kt"] = generateConfig(planner)
        return files
    }

    private fun generateGoals(planner: KoogPlannerAgent): String = buildString {
        appendLine("package generated.planner")
        appendLine()
        appendLine("data class PlannerGoalDef(")
        appendLine("    val name: String,")
        appendLine("    val conditions: List<StateConditionDef>,")
        appendLine("    val priority: Int")
        appendLine(")")
        appendLine()
        appendLine("data class StateConditionDef(")
        appendLine("    val key: String,")
        appendLine("    val operator: ConditionOp,")
        appendLine("    val value: Any")
        appendLine(")")
        appendLine()
        appendLine("enum class ConditionOp { EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN }")
        appendLine()
        appendLine("val plannerGoals = listOf(")
        for (goal in planner.goals) {
            appendLine("    PlannerGoalDef(")
            appendLine("        name = \"${goal.name}\",")
            appendLine("        conditions = listOf(")
            for (cond in goal.conditions) {
                val valueStr = if (cond.value is String) "\"${cond.value}\"" else "${cond.value}"
                appendLine("            StateConditionDef(\"${cond.key}\", ConditionOp.${cond.operator.name}, $valueStr),")
            }
            appendLine("        ),")
            appendLine("        priority = ${goal.priority}")
            appendLine("    ),")
        }
        appendLine(")")
    }

    private fun generateActions(planner: KoogPlannerAgent): String = buildString {
        appendLine("package generated.planner")
        appendLine()
        appendLine("data class PlannerActionDef(")
        appendLine("    val name: String,")
        appendLine("    val description: String,")
        appendLine("    val preconditions: List<StateConditionDef>,")
        appendLine("    val effects: List<StateEffectDef>,")
        appendLine("    val cost: Int")
        appendLine(")")
        appendLine()
        appendLine("data class StateEffectDef(")
        appendLine("    val key: String,")
        appendLine("    val value: Any")
        appendLine(")")
        appendLine()
        appendLine("val plannerActions = listOf(")
        for (action in planner.actions) {
            appendLine("    PlannerActionDef(")
            appendLine("        name = \"${action.name}\",")
            appendLine("        description = \"${action.description}\",")
            appendLine("        preconditions = listOf(")
            for (cond in action.preconditions) {
                val valueStr = if (cond.value is String) "\"${cond.value}\"" else "${cond.value}"
                appendLine("            StateConditionDef(\"${cond.key}\", ConditionOp.${cond.operator.name}, $valueStr),")
            }
            appendLine("        ),")
            appendLine("        effects = listOf(")
            for (effect in action.effects) {
                val valueStr = if (effect.value is String) "\"${effect.value}\"" else "${effect.value}"
                appendLine("            StateEffectDef(\"${effect.key}\", $valueStr),")
            }
            appendLine("        ),")
            appendLine("        cost = ${action.cost}")
            appendLine("    ),")
        }
        appendLine(")")
    }

    private fun generateConfig(planner: KoogPlannerAgent): String = buildString {
        val hasTools = planner.tools.isNotEmpty() || planner.builtinTools.isNotEmpty()
        val isLlm = planner.plannerType == PlannerType.LLM

        appendLine("package generated")
        appendLine()
        appendLine("import generated.planner.*")
        if (isLlm && planner.modelProvider != null) {
            appendLine("import ${planner.modelProvider.importPackage}")
            appendLine("import ai.koog.prompt.executor.clients.${planner.modelProvider.name.lowercase()}.${planner.modelProvider.executorFactory}")
        }
        appendLine("import org.springframework.beans.factory.annotation.Value")
        appendLine("import org.springframework.context.annotation.Bean")
        appendLine("import org.springframework.context.annotation.Configuration")
        if (hasTools) {
            appendLine("import generated.createToolRegistry")
        }
        appendLine()
        appendLine("/**")
        if (isLlm) {
            appendLine(" * LLM-based Planner Agent: ${planner.name}")
        } else {
            appendLine(" * GOAP-based Planner Agent: ${planner.name}")
        }
        appendLine(" * ${planner.description}")
        appendLine(" */")
        appendLine("@Configuration")
        appendLine("class PlannerAgentConfig(")
        if (isLlm && planner.modelProvider != null) {
            val envKey = planner.modelProvider.name.lowercase()
            appendLine("    @Value(\"\\\${$envKey.api.key}\") private val apiKey: String,")
        }
        appendLine(") {")
        appendLine()

        // Initial state
        appendLine("    private val initialState = mutableMapOf<String, Any>(")
        for ((key, value) in planner.initialState) {
            val valueStr = if (value is String) "\"$value\"" else "$value"
            appendLine("        \"$key\" to $valueStr,")
        }
        appendLine("    )")
        appendLine()

        // Bean
        appendLine("    @Bean")
        if (isLlm) {
            appendLine("    fun plannerAgent(): LlmPlannerAgent {")
            val modelRef = if (planner.modelId != null) {
                mapModelId(planner.modelProvider!!, planner.modelId)
            } else {
                planner.modelProvider?.defaultModel ?: "AnthropicModels.Sonnet_4"
            }
            appendLine("        val executor = ${planner.modelProvider?.executorFactory}(apiKey)")
            if (planner.temperature != null) {
                appendLine("        // Temperature: ${planner.temperature}")
            }
            appendLine("        return LlmPlannerAgent(")
            appendLine("            executor = executor,")
            appendLine("            model = $modelRef,")
            appendLine("            goals = plannerGoals,")
            appendLine("            actions = plannerActions,")
            appendLine("            initialState = initialState,")
            appendLine("            maxReplanAttempts = ${planner.maxReplanAttempts},")
            if (hasTools) {
                appendLine("            toolRegistry = createToolRegistry(),")
            }
            appendLine("        )")
        } else {
            // GOAP
            appendLine("    fun plannerAgent(): GoapPlannerAgent {")
            appendLine("        // GOAP solver uses A* search to find optimal action sequence")
            appendLine("        // Actions have cost values used for path optimization")
            appendLine("        return GoapPlannerAgent(")
            appendLine("            goals = plannerGoals,")
            appendLine("            actions = plannerActions,")
            appendLine("            initialState = initialState,")
            appendLine("            maxReplanAttempts = ${planner.maxReplanAttempts},")
            if (hasTools) {
                appendLine("            toolRegistry = createToolRegistry(),")
            }
            appendLine("        )")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun mapModelId(provider: ModelProvider, id: String): String {
        return when (provider) {
            ModelProvider.OPENAI -> when (id) {
                "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
                "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
                else -> "OpenAIModels.Chat.GPT4o"
            }
            ModelProvider.ANTHROPIC -> when (id) {
                "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
                "claude-opus-4" -> "AnthropicModels.Opus_4"
                "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
                else -> "AnthropicModels.Sonnet_4"
            }
            ModelProvider.GOOGLE -> when (id) {
                "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
                "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
                else -> "GoogleModels.Gemini2Flash"
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.PlannerAgentGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/PlannerAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/PlannerAgentGeneratorTest.kt
git commit -m "feat: add PlannerAgentGenerator with LLM and GOAP code generation"
```

---

### Task 14: Backend ExportService Integration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add PlannerAgentGenerator to ExportService constructor**

In `ExportService.kt`, add to the constructor parameters (around line 12-30):

```kotlin
private val plannerAgentGenerator: PlannerAgentGenerator,
```

- [ ] **Step 2: Add planner-agent handling to generateFiles**

In the `generateFiles` method, after the adapter conversion (around line 48-49), add planner-agent detection and generation:

After the line that does `val agents = adapter.convert(graph)`, add:

```kotlin
val plannerAgents = adapter.convertPlannerAgents(graph)

if (plannerAgents.isNotEmpty()) {
    val planner = plannerAgents[0]
    val plannerFiles = plannerAgentGenerator.generate(planner)
    for ((fileName, content) in plannerFiles) {
        val subPath = if (fileName.startsWith("Planner") && fileName != "PlannerAgent.kt") "planner" else ""
        val path = if (subPath.isNotEmpty()) "$pkg/$subPath/$fileName" else "$pkg/$fileName"
        files[path] = content
    }
    // Generate controller for planner
    files["$pkg/PlanController.kt"] = generatePlanController()
    // Still generate tools, sub-agents, MCP if connected
    for (tool in planner.tools) {
        val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
        files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
    }
    for (mcpServer in planner.mcpServers) {
        val fileName = mcpServer.name.replaceFirstChar { it.uppercase() } + "McpServer"
        files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
    }
    if (planner.tools.isNotEmpty() || planner.builtinTools.isNotEmpty()) {
        files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(planner.tools, planner.builtinTools)
    }
    return files
}
```

Wrap the existing agent generation code (the part starting from `val agent = agents[0]`) in a condition so it only runs if no planner agents were found:

```kotlin
// existing agent generation code continues here...
val agent = agents[0]
// ... rest of existing code
```

- [ ] **Step 3: Add generatePlanController helper method**

Add to ExportService:

```kotlin
private fun generatePlanController(): String = buildString {
    appendLine("package generated")
    appendLine()
    appendLine("import org.springframework.web.bind.annotation.*")
    appendLine()
    appendLine("@RestController")
    appendLine("@RequestMapping(\"/api\")")
    appendLine("class PlanController {")
    appendLine()
    appendLine("    @PostMapping(\"/plan\")")
    appendLine("    fun executePlan(@RequestBody request: PlanRequest): PlanResponse {")
    appendLine("        // TODO: Integrate with planner agent bean")
    appendLine("        return PlanResponse(status = \"planned\", steps = emptyList())")
    appendLine("    }")
    appendLine("}")
    appendLine()
    appendLine("data class PlanRequest(val goalName: String, val state: Map<String, Any> = emptyMap())")
    appendLine("data class PlanResponse(val status: String, val steps: List<String>)")
}
```

- [ ] **Step 4: Run all tests to verify nothing is broken**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test`
Expected: All tests pass (except the 2 pre-existing MCP export failures)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat: integrate PlannerAgentGenerator into ExportService pipeline"
```

---

### Task 15: Frontend Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Verify frontend TypeScript compilation**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Verify frontend builds**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Verify all backend tests pass**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test`
Expected: All tests pass (except 2 pre-existing MCP export failures)

- [ ] **Step 4: Manual smoke test**

Start the app and verify:
1. Planner Agent button appears in toolbar (pink icon)
2. Clicking creates a pink node on canvas
3. Properties panel shows planner-specific fields
4. "Configure" button opens sliding panel
5. Can add goals, actions, state entries
6. Validation errors appear for empty goals/actions
7. Tools can be connected to planner node
8. Plan inspector shows in PreviewPanel when planner selected

---

### Task 16: Final Commit — All Frontend Files

**Files:** All remaining uncommitted frontend changes

- [ ] **Step 1: Stage and commit all remaining changes**

```bash
git add -A
git commit -m "feat: complete Planner Agent feature — UI, validation, export, tests"
```
