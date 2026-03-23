# Built-in Tools Frontend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add visual editor support for 7 built-in tool types — new node type, dropdown menu, tool-specific properties panel, canvas sync, and validation.

**Architecture:** Extend the TypeScript type system with `BuiltinToolType` and `BuiltinToolConfig`. Add a new `BuiltinToolReteNode` for the visual editor. Replace the "+Tool" button with a dropdown offering Custom and 7 Built-in options. Add a `BuiltinToolProperties` panel with tool-specific fields. Extend validation and canvas sync for the new node type.

**Tech Stack:** TypeScript, React 19, Next.js 16, Rete.js v2, Zustand, Tailwind CSS, shadcn/ui

**Spec:** `docs/superpowers/specs/2026-03-23-builtin-tools-design.md` (Frontend Changes section)

---

## File Map

### New Files
- `frontend/components/panels/BuiltinToolProperties.tsx`

### Modified Files
- `frontend/types/graph.ts`
- `frontend/store/editorStore.ts`
- `frontend/lib/rete-nodes.ts`
- `frontend/lib/rete-setup.ts`
- `frontend/lib/validation.ts`
- `frontend/components/panels/Toolbar.tsx`
- `frontend/components/panels/PropertiesPanel.tsx`
- `frontend/components/editor/Canvas.tsx`

---

## Task 1: Extend type system with BuiltinToolType and BuiltinToolConfig

**Files:**
- Modify: `frontend/types/graph.ts`

Both `BuiltinToolType` and `BuiltinToolConfig` live in `graph.ts` per spec (graph-level concepts, not Koog-specific). No changes to `koog.ts`.

- [ ] **Step 1: Replace `frontend/types/graph.ts` content**

```typescript
import { AgentConfig, ToolConfig } from "./koog";

export type BuiltinToolType = "think" | "shell" | "filesystem" | "fetch" | "memory" | "todo" | "user_prompt";

export const BUILTIN_TOOL_TYPES: BuiltinToolType[] = ["think", "shell", "filesystem", "fetch", "memory", "todo", "user_prompt"];

export interface BuiltinToolConfig {
  toolType: BuiltinToolType;
  workingDir?: string;
  rootDir?: string;
  baseUrl?: string;
  path?: string;
}

export type NodeType = "agent" | "tool" | "builtin-tool";

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig | BuiltinToolConfig;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
}

export interface ProjectGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}
```

Note: `BUILTIN_TOOL_TYPES` is a shared constant used by both validation and the Toolbar dropdown, avoiding duplication.

Also add a shared label map after `BUILTIN_TOOL_TYPES`:

```typescript
export const BUILTIN_TOOL_LABELS: Record<BuiltinToolType, string> = {
  think: "Think",
  shell: "Shell",
  filesystem: "Filesystem",
  fetch: "Fetch",
  memory: "Memory",
  todo: "Todo",
  user_prompt: "User Prompt",
};
```

- [ ] **Step 2: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/types/graph.ts
git commit -m "feat(frontend): add BuiltinToolType, BuiltinToolConfig to type system"
```

---

## Task 2: Add BuiltinToolReteNode and update Rete setup

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`
- Modify: `frontend/lib/rete-setup.ts`

- [ ] **Step 1: Add BuiltinToolReteNode to `frontend/lib/rete-nodes.ts`**

Add after the `ToolReteNode` class, before the `AppNode` type:

```typescript
export class BuiltinToolReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Built-in Tool");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}
```

Update the `AppNode` type:

```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode;
```

- [ ] **Step 2: Update connection validation in `frontend/lib/rete-setup.ts`**

Add import for `BuiltinToolReteNode`:

```typescript
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
} from "./rete-nodes";
```

Update the connection validation pipe (the `instanceof` check) from:

```typescript
if (
  !(sourceNode instanceof ToolReteNode) ||
  !(targetNode instanceof AgentReteNode)
) {
  return undefined;
}
```

to:

```typescript
const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
if (!isToolSource || !(targetNode instanceof AgentReteNode)) {
  return undefined;
}
```

Also add `BuiltinToolReteNode` to the context menu items array:

After the `["Tool", () => new ToolReteNode("temp", "Tool")]` line, the array stays as-is (built-in tools are added via the Toolbar dropdown, not the context menu).

- [ ] **Step 3: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/lib/rete-setup.ts
git commit -m "feat(frontend): add BuiltinToolReteNode and update connection validation"
```

---

## Task 3: Extend EditorStore for builtin-tool nodes

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Update imports and add defaults**

Add imports at top:

```typescript
import { BuiltinToolType, BuiltinToolConfig } from "@/types/graph";
```

Add after `defaultToolData`:

```typescript
const builtinToolDefaults: Record<BuiltinToolType, BuiltinToolConfig> = {
  think:       { toolType: "think" },
  shell:       { toolType: "shell" },
  filesystem:  { toolType: "filesystem" },
  fetch:       { toolType: "fetch" },
  memory:      { toolType: "memory" },
  todo:        { toolType: "todo" },
  user_prompt: { toolType: "user_prompt" },
};
```

- [ ] **Step 2: Update EditorState interface**

Change `addNode` signature from:

```typescript
addNode: (type: NodeType, position: { x: number; y: number }) => void;
```

to:

```typescript
addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType) => void;
```

Change `updateNodeData` from:

```typescript
updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig>) => void;
```

to:

```typescript
updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig>) => void;
```

- [ ] **Step 3: Update addNode implementation**

Change the `addNode` implementation from:

```typescript
addNode: (type, position) =>
  set((state) => ({
    nodes: [
      ...state.nodes,
      {
        id: `node-${nextId++}`,
        type,
        position,
        data: type === "agent" ? { ...defaultAgentData } : { ...defaultToolData },
      },
    ],
  })),
```

to:

```typescript
addNode: (type, position, toolType?) =>
  set((state) => {
    let data: AgentConfig | ToolConfig | BuiltinToolConfig;
    if (type === "agent") {
      data = { ...defaultAgentData };
    } else if (type === "builtin-tool" && toolType) {
      data = { ...builtinToolDefaults[toolType] };
    } else {
      data = { ...defaultToolData };
    }
    return {
      nodes: [
        ...state.nodes,
        { id: `node-${nextId++}`, type, position, data },
      ],
    };
  }),
```

- [ ] **Step 4: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 5: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat(frontend): extend editorStore for builtin-tool nodes with defaults"
```

---

## Task 4: Toolbar dropdown for built-in tools

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Replace Toolbar.tsx content**

```typescript
"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
import { ExportDialog } from "@/components/dialogs/ExportDialog";
import { SaveLoadDialog } from "@/components/dialogs/SaveLoadDialog";
import { ProjectGraph } from "@/types/graph";
import type { BuiltinToolType } from "@/types/graph";

const builtinToolOptions: { type: BuiltinToolType; label: string }[] = [
  { type: "think", label: "Think" },
  { type: "shell", label: "Shell" },
  { type: "filesystem", label: "Filesystem" },
  { type: "fetch", label: "Fetch" },
  { type: "memory", label: "Memory" },
  { type: "todo", label: "Todo" },
  { type: "user_prompt", label: "User Prompt" },
];

export function Toolbar() {
  const addNode = useEditorStore((s) => s.addNode);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const setErrors = useValidationStore((s) => s.setErrors);
  const [toolMenuOpen, setToolMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setToolMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleValidate = () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    const errors = validateGraph(graph);
    setErrors(errors);
  };

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={() => addNode("agent", { x: 100, y: 100 })}>
        + Agent
      </Button>
      <div className="relative" ref={menuRef}>
        <Button variant="outline" size="sm" onClick={() => setToolMenuOpen(!toolMenuOpen)}>
          + Tool ▾
        </Button>
        {toolMenuOpen && (
          <div className="absolute top-full left-0 mt-1 w-44 bg-popover border rounded-md shadow-md z-50 py-1">
            <button
              className="w-full text-left px-3 py-1.5 text-sm hover:bg-accent"
              onClick={() => { addNode("tool", { x: 300, y: 100 }); setToolMenuOpen(false); }}
            >
              Custom Tool
            </button>
            <div className="border-t my-1" />
            {builtinToolOptions.map((opt) => (
              <button
                key={opt.type}
                className="w-full text-left px-3 py-1.5 text-sm hover:bg-accent"
                onClick={() => {
                  addNode("builtin-tool", { x: 300, y: 100 }, opt.type);
                  setToolMenuOpen(false);
                }}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="flex-1" />
      <Button variant="outline" size="sm" onClick={handleValidate}>
        Validate
      </Button>
      <SaveLoadDialog />
      <ExportDialog />
    </div>
  );
}
```

- [ ] **Step 2: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx
git commit -m "feat(frontend): replace +Tool button with dropdown for Custom and Built-in tools"
```

---

## Task 5: BuiltinToolProperties panel

**Files:**
- Create: `frontend/components/panels/BuiltinToolProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Create BuiltinToolProperties.tsx**

```typescript
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useEditorStore } from "@/store/editorStore";
import { BuiltinToolConfig, BUILTIN_TOOL_LABELS } from "@/types/graph";
import type { BuiltinToolType } from "@/types/graph";

const toolDescriptions: Record<BuiltinToolType, string> = {
  think: "Adds a step-by-step reasoning instruction to the agent's system prompt.",
  shell: "Execute shell commands in the agent's environment.",
  filesystem: "Read, write, and list files and directories using Koog built-in file tools.",
  fetch: "Make HTTP requests to external APIs and web services.",
  memory: "Persistent key-value storage backed by SQLite.",
  todo: "In-memory task list management for complex workflows.",
  user_prompt: "Ask users questions and collect interactive input using Koog built-in tools.",
};

export function BuiltinToolProperties({ nodeId, data }: { nodeId: string; data: BuiltinToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<BuiltinToolConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label>Type</Label>
        <div className="text-sm font-medium mt-1">{BUILTIN_TOOL_LABELS[data.toolType]}</div>
      </div>
      <div>
        <Label>Description</Label>
        <p className="text-sm text-muted-foreground mt-1">{toolDescriptions[data.toolType]}</p>
      </div>

      {data.toolType === "shell" && (
        <div>
          <Label htmlFor="bt-workingDir">Working Directory</Label>
          <Input
            id="bt-workingDir"
            value={data.workingDir ?? ""}
            onChange={(e) => update({ workingDir: e.target.value || undefined })}
            placeholder="."
          />
        </div>
      )}

      {data.toolType === "filesystem" && (
        <div>
          <Label htmlFor="bt-rootDir">Root Directory</Label>
          <Input
            id="bt-rootDir"
            value={data.rootDir ?? ""}
            onChange={(e) => update({ rootDir: e.target.value || undefined })}
            placeholder="."
          />
        </div>
      )}

      {data.toolType === "fetch" && (
        <div>
          <Label htmlFor="bt-baseUrl">Base URL</Label>
          <Input
            id="bt-baseUrl"
            value={data.baseUrl ?? ""}
            onChange={(e) => update({ baseUrl: e.target.value || undefined })}
            placeholder="https://api.example.com"
          />
        </div>
      )}

      {data.toolType === "memory" && (
        <div>
          <Label htmlFor="bt-path">Database Path</Label>
          <Input
            id="bt-path"
            value={data.path ?? ""}
            onChange={(e) => update({ path: e.target.value || undefined })}
            placeholder="memory.db"
          />
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Update PropertiesPanel.tsx**

Add import at top:

```typescript
import { BuiltinToolProperties } from "./BuiltinToolProperties";
import { BuiltinToolConfig } from "@/types/graph";
```

Update the heading to show a nicer label for builtin-tool:

```typescript
<h3 className="font-semibold mb-4 capitalize">
  {selectedNode.type === "builtin-tool" ? "Built-in Tool" : selectedNode.type} Properties
</h3>
```

Add the third case after the `tool` case:

```typescript
{selectedNode.type === "builtin-tool" && (
  <BuiltinToolProperties nodeId={selectedNode.id} data={selectedNode.data as BuiltinToolConfig} />
)}
```

- [ ] **Step 3: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/BuiltinToolProperties.tsx frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat(frontend): add BuiltinToolProperties panel with tool-specific config fields"
```

---

## Task 6: Extend Canvas sync for builtin-tool nodes

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`

- [ ] **Step 1: Update imports**

Add `BuiltinToolReteNode` to the Rete import:

```typescript
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode } from "@/lib/rete-nodes";
```

Add graph type imports:

```typescript
import { BuiltinToolType, BUILTIN_TOOL_LABELS } from "@/types/graph";
```

- [ ] **Step 2: Update Zustand→Rete node creation**

Replace the ternary in the subscribe callback (around line 96):

```typescript
const reteNode = node.type === "agent"
  ? new AgentReteNode(node.id, (node.data as any).name || "Agent")
  : new ToolReteNode(node.id, (node.data as any).name || "Tool");
```

with:

```typescript
let reteNode;
if (node.type === "agent") {
  reteNode = new AgentReteNode(node.id, (node.data as any).name || "Agent");
} else if (node.type === "builtin-tool") {
  const toolType = (node.data as any).toolType || "tool";
  const label = BUILTIN_TOOL_LABELS[toolType as BuiltinToolType] || toolType;
  reteNode = new BuiltinToolReteNode(node.id, label);
} else {
  reteNode = new ToolReteNode(node.id, (node.data as any).name || "Tool");
}
```

- [ ] **Step 3: Update Rete→Zustand connection handlers**

Update BOTH `connectioncreated` and `connectionremoved` handlers. In each, replace:

```typescript
if (sourceNode instanceof ToolReteNode && targetNode instanceof AgentReteNode) {
    syncing = true;
    useEditorStore.getState().addEdge(sourceNode.storeNodeId, targetNode.storeNodeId);
```

with:

```typescript
const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
if (isToolSource && targetNode instanceof AgentReteNode) {
    syncing = true;
    const sourceStoreId = (sourceNode as ToolReteNode | BuiltinToolReteNode).storeNodeId;
    useEditorStore.getState().addEdge(sourceStoreId, targetNode.storeNodeId);
```

For the `connectionremoved` handler, same pattern — replace `sourceNode.storeNodeId` with the cast version:

```typescript
const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
if (isToolSource && targetNode instanceof AgentReteNode) {
    syncing = true;
    const sourceStoreId = (sourceNode as ToolReteNode | BuiltinToolReteNode).storeNodeId;
    const edges = useEditorStore.getState().edges;
    const edge = edges.find(
      (e) => e.source === sourceStoreId && e.target === targetNode.storeNodeId
    );
    if (edge) useEditorStore.getState().removeEdge(edge.id);
```

- [ ] **Step 4: Update label sync for builtin-tool nodes**

In the label update section of the subscribe callback, the current code uses `(node.data as any).name`. For `builtin-tool` nodes, the label comes from `toolType`, not `name`. Update:

Replace:

```typescript
const newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
```

with:

```typescript
let newLabel: string;
if (node.type === "builtin-tool") {
  const toolType = (node.data as any).toolType || "tool";
  newLabel = toolType.charAt(0).toUpperCase() + toolType.slice(1).replace(/_/g, " ");
} else {
  newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
}
```

- [ ] **Step 5: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 6: Commit**

```bash
git add frontend/components/editor/Canvas.tsx
git commit -m "feat(frontend): extend Canvas sync for builtin-tool nodes (create, connect, label)"
```

---

## Task 7: Extend validation for builtin-tool nodes

**Files:**
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Update validation.ts**

Add imports at top:

```typescript
import { BuiltinToolConfig, BUILTIN_TOOL_TYPES } from "@/types/graph";
```

Add a new block after the `if (node.type === "tool")` block:

```typescript
if (node.type === "builtin-tool") {
  const data = node.data as BuiltinToolConfig;
  if (!data.toolType || !BUILTIN_TOOL_TYPES.includes(data.toolType)) {
    errors.push({ nodeId: node.id, severity: "error", message: "Built-in tool has invalid type" });
  }
  const isConnected = graph.edges.some((e) => e.source === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "Built-in tool must be connected to an Agent" });
  }
}
```

- [ ] **Step 2: Verify project compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/validation.ts
git commit -m "feat(frontend): add builtin-tool validation (toolType check, connection check)"
```

---

## Task 8: Manual verification

- [ ] **Step 1: Start the frontend dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Verify the following manually**

1. Click "+Tool ▾" — dropdown shows "Custom Tool" and 7 built-in options
2. Click "Shell" — a node appears on the canvas labeled "Shell"
3. Select the Shell node — properties panel shows "Built-in Tool Properties" with "Shell" type, description, and "Working Directory" input
4. Type "/app" in the Working Directory field
5. Click "+Agent" — agent node appears
6. Draw a connection from Shell output to Agent input — connection succeeds
7. Click "Validate" — no errors (Shell is connected)
8. Disconnect the Shell node — click Validate again — error: "Built-in tool must be connected to an Agent"
9. Repeat steps 2-6 with "Think" (no config fields), "Memory" (has "Database Path"), "Fetch" (has "Base URL"), "Filesystem" (has "Root Directory"), "Todo" (no config), "User Prompt" (no config)
10. Save the project — load it back — all builtin-tool nodes persist correctly
11. Export — verify the export succeeds and the ZIP contains built-in tool files

- [ ] **Step 3: Commit any fixes**

If anything needed fixing during manual testing, commit the fixes.

- [ ] **Step 4: Final full test suite**

Run: `./gradlew test` — verify ALL backend tests still pass.

- [ ] **Step 5: Final commit**

```bash
git commit -m "feat(frontend): builtin tools verified working end-to-end" --allow-empty
```
