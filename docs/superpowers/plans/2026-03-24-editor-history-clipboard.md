# Editor History & Clipboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add undo/redo and copy/paste to the Koog Agent Editor with snapshot-based history, system clipboard integration, and keyboard shortcuts.

**Architecture:** A `historyStore` captures state snapshots before each mutation. Undo/redo apply snapshots via `useEditorStore.setState()` directly. A `clipboardStore` uses `navigator.clipboard` for cross-tab copy/paste with UUID-based ID generation. `selectedNodeId` is migrated to `selectedNodeIds[]` for multi-selection.

**Tech Stack:** React, Zustand, `navigator.clipboard` API, `crypto.randomUUID()`

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `frontend/store/historyStore.ts` | Past/future snapshot stacks, undo/redo logic |
| `frontend/store/clipboardStore.ts` | Copy/paste with system clipboard, ID remapping |

### Modified Files

| File | Change |
|------|--------|
| `frontend/store/editorStore.ts` | `selectedNodeIds[]`, `commitPosition()`, `addNodes()`, history integration |
| `frontend/components/panels/PropertiesPanel.tsx` | Use `selectedNodeIds`, multi-select placeholder |
| `frontend/components/editor/Canvas.tsx` | Multi-select sync, drag-end detection, edge sync |
| `frontend/components/panels/Toolbar.tsx` | Undo/Redo buttons |
| `frontend/app/page.tsx` | Keyboard shortcuts |

---

### Task 1: History Store

**Files:**
- Create: `frontend/store/historyStore.ts`

- [ ] **Step 1: Create the history store**

```typescript
// frontend/store/historyStore.ts
import { create } from "zustand";
import { GraphNode, GraphEdge } from "@/types/graph";
import { useEditorStore } from "./editorStore";

const MAX_HISTORY = 100;

export interface Snapshot {
  nodes: GraphNode[];
  edges: GraphEdge[];
  projectName: string;
}

interface HistoryState {
  past: Snapshot[];
  future: Snapshot[];
  pushState: (snapshot: Snapshot) => void;
  undo: () => void;
  redo: () => void;
  clear: () => void;
}

export const useHistoryStore = create<HistoryState>((set, get) => ({
  past: [],
  future: [],

  pushState: (snapshot) =>
    set((s) => ({
      past: [...s.past.slice(-(MAX_HISTORY - 1)), snapshot],
      future: [],
    })),

  undo: () => {
    const { past } = get();
    if (past.length === 0) return;

    const previous = past[past.length - 1];
    const { nodes, edges, projectName } = useEditorStore.getState();
    const current: Snapshot = {
      nodes: structuredClone(nodes),
      edges: structuredClone(edges),
      projectName,
    };

    set({ past: past.slice(0, -1), future: [...get().future, current] });
    useEditorStore.setState({
      nodes: previous.nodes,
      edges: previous.edges,
      projectName: previous.projectName,
    });
  },

  redo: () => {
    const { future } = get();
    if (future.length === 0) return;

    const next = future[future.length - 1];
    const { nodes, edges, projectName } = useEditorStore.getState();
    const current: Snapshot = {
      nodes: structuredClone(nodes),
      edges: structuredClone(edges),
      projectName,
    };

    set({ future: future.slice(0, -1), past: [...get().past, current] });
    useEditorStore.setState({
      nodes: next.nodes,
      edges: next.edges,
      projectName: next.projectName,
    });
  },

  clear: () => set({ past: [], future: [] }),
}));
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/store/historyStore.ts
git commit -m "feat(history): add historyStore with snapshot-based undo/redo"
```

---

### Task 2: Migrate editorStore — selectedNodeIds + history integration

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Read editorStore.ts and apply these changes**

1. Replace `selectedNodeId: string | null` with `selectedNodeIds: string[]` in the interface and initial state.

2. Replace `setSelectedNodeId: (id: string | null) => void` with:
   - `setSelectedNodeIds: (ids: string[]) => void`
   - `toggleNodeSelection: (id: string) => void` (for Ctrl+Click)

3. Add new actions to the interface:
   - `commitPosition: () => void`
   - `addNodes: (nodes: GraphNode[], edges: GraphEdge[]) => void`

4. Update `removeNode` to filter `selectedNodeIds` instead of checking singular `selectedNodeId`.

5. Wrap mutating actions (`addNode`, `removeNode`, `updateNodeData`, `addEdge`, `removeEdge`, `setProjectName`) to call `useHistoryStore.getState().pushState()` with a snapshot of the current state BEFORE the mutation.

6. `updateNodePosition` does NOT push history (called every drag pixel).

7. `commitPosition` pushes current state to history (called on drag-end).

8. `addNodes` adds batch nodes+edges in one set() call and pushes one history entry.

9. `reset()` also calls `useHistoryStore.getState().clear()`.

Here's the complete updated file:

```typescript
import { create } from "zustand";
import { GraphNode, GraphEdge, NodeType, BuiltinToolType, BuiltinToolConfig } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerConfig } from "@/types/mcp";

export const DEFAULT_PROJECT_NAME = "Untitled Project";

interface EditorState {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedNodeIds: string[];
  projectName: string;

  addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig>) => void;
  updateNodePosition: (id: string, position: { x: number; y: number }) => void;
  addEdge: (source: string, target: string) => void;
  removeEdge: (id: string) => void;
  setSelectedNodeIds: (ids: string[]) => void;
  toggleNodeSelection: (id: string) => void;
  setProjectName: (name: string) => void;
  commitPosition: () => void;
  addNodes: (nodes: GraphNode[], edges: GraphEdge[]) => void;
  reset: () => void;
}

const defaultAgentData: AgentConfig = { name: "", systemPrompt: "", modelProvider: "openai" };
const defaultToolData: ToolConfig = { name: "", description: "", parameters: [] };
const defaultSubAgentData: SubAgentConfig = { name: "", description: "", systemPrompt: "", modelProvider: "openai", delegationMode: "automatic", inputDescription: "" };
const defaultMcpServerData: McpServerConfig = { name: "", transportType: "sse", envVars: [], discoveredTools: [] };
const builtinToolDefaults: Record<BuiltinToolType, BuiltinToolConfig> = {
  think: { toolType: "think" }, shell: { toolType: "shell" }, filesystem: { toolType: "filesystem" },
  fetch: { toolType: "fetch" }, memory: { toolType: "memory" }, todo: { toolType: "todo" }, user_prompt: { toolType: "user_prompt" },
};

let nextId = 1;

function pushHistory() {
  // Lazy import to avoid circular dependency at module load time
  const { useHistoryStore } = require("./historyStore");
  const { nodes, edges, projectName } = useEditorStore.getState();
  useHistoryStore.getState().pushState({
    nodes: structuredClone(nodes),
    edges: structuredClone(edges),
    projectName,
  });
}

export const useEditorStore = create<EditorState>((set) => ({
  nodes: [],
  edges: [],
  selectedNodeIds: [],
  projectName: DEFAULT_PROJECT_NAME,

  addNode: (type, position, toolType?) => {
    pushHistory();
    set((state) => {
      let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig;
      if (type === "agent") data = { ...defaultAgentData };
      else if (type === "sub-agent") data = { ...defaultSubAgentData };
      else if (type === "builtin-tool" && toolType) data = { ...builtinToolDefaults[toolType] };
      else if (type === "mcp-server") data = { ...defaultMcpServerData };
      else data = { ...defaultToolData };
      return { nodes: [...state.nodes, { id: `node-${nextId++}`, type, position, data }] };
    });
  },

  removeNode: (id) => {
    pushHistory();
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeIds: state.selectedNodeIds.filter((nid) => nid !== id),
    }));
  },

  updateNodeData: (id, data) => {
    pushHistory();
    set((state) => ({
      nodes: state.nodes.map((n) => n.id === id ? { ...n, data: { ...n.data, ...data } } : n),
    }));
  },

  updateNodePosition: (id, position) =>
    set((state) => ({
      nodes: state.nodes.map((n) => n.id === id ? { ...n, position } : n),
    })),

  addEdge: (source, target) => {
    pushHistory();
    set((state) => ({
      edges: [...state.edges, { id: `edge-${nextId++}`, source, target }],
    }));
  },

  removeEdge: (id) => {
    pushHistory();
    set((state) => ({
      edges: state.edges.filter((e) => e.id !== id),
    }));
  },

  setSelectedNodeIds: (ids) => set({ selectedNodeIds: ids }),

  toggleNodeSelection: (id) =>
    set((state) => ({
      selectedNodeIds: state.selectedNodeIds.includes(id)
        ? state.selectedNodeIds.filter((nid) => nid !== id)
        : [...state.selectedNodeIds, id],
    })),

  setProjectName: (name) => {
    pushHistory();
    set({ projectName: name });
  },

  commitPosition: () => {
    pushHistory();
  },

  addNodes: (newNodes, newEdges) => {
    pushHistory();
    set((state) => ({
      nodes: [...state.nodes, ...newNodes],
      edges: [...state.edges, ...newEdges],
      selectedNodeIds: newNodes.map((n) => n.id),
    }));
  },

  reset: () => {
    nextId = 1;
    const { useHistoryStore } = require("./historyStore");
    useHistoryStore.getState().clear();
    return set({
      nodes: [],
      edges: [],
      selectedNodeIds: [],
      projectName: DEFAULT_PROJECT_NAME,
    });
  },
}));
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: May show errors in PropertiesPanel/Canvas (they still reference `selectedNodeId`) — that's OK, fixed in next tasks.

- [ ] **Step 3: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat(history): migrate editorStore — selectedNodeIds, history integration, addNodes"
```

---

### Task 3: Update PropertiesPanel for selectedNodeIds

**Files:**
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Update to use selectedNodeIds**

Replace `selectedNodeId` usage with `selectedNodeIds`. When multiple nodes are selected, show a placeholder.

```tsx
"use client";

import { useEditorStore } from "@/store/editorStore";
import { AgentProperties } from "./AgentProperties";
import { ToolProperties } from "./ToolProperties";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { BuiltinToolProperties } from "./BuiltinToolProperties";
import { BuiltinToolConfig } from "@/types/graph";
import { SubAgentProperties } from "./SubAgentProperties";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerProperties } from "./McpServerProperties";
import { McpServerConfig } from "@/types/mcp";

export function PropertiesPanel() {
  const selectedNodeIds = useEditorStore((s) => s.selectedNodeIds);
  const nodes = useEditorStore((s) => s.nodes);

  if (selectedNodeIds.length === 0) {
    return (
      <div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">
        Select a node to edit its properties.
      </div>
    );
  }

  if (selectedNodeIds.length > 1) {
    return (
      <div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">
        {selectedNodeIds.length} nodes selected
      </div>
    );
  }

  const selectedNode = nodes.find((n) => n.id === selectedNodeIds[0]);
  if (!selectedNode) {
    return (
      <div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">
        Select a node to edit its properties.
      </div>
    );
  }

  return (
    <ScrollArea className="w-80 border-l bg-background">
      <div className="p-4">
        <h3 className="font-semibold mb-4 capitalize">
          {selectedNode.type === "mcp-server" ? "MCP Server" :
           selectedNode.type === "builtin-tool" ? "Built-in Tool" :
           selectedNode.type === "sub-agent" ? "Sub-Agent" :
           selectedNode.type} Properties
        </h3>
        {selectedNode.type === "agent" && (
          <AgentProperties nodeId={selectedNode.id} data={selectedNode.data as AgentConfig} />
        )}
        {selectedNode.type === "tool" && (
          <ToolProperties nodeId={selectedNode.id} data={selectedNode.data as ToolConfig} />
        )}
        {selectedNode.type === "builtin-tool" && (
          <BuiltinToolProperties nodeId={selectedNode.id} data={selectedNode.data as BuiltinToolConfig} />
        )}
        {selectedNode.type === "sub-agent" && (
          <SubAgentProperties nodeId={selectedNode.id} data={selectedNode.data as SubAgentConfig} />
        )}
        {selectedNode.type === "mcp-server" && (
          <McpServerProperties nodeId={selectedNode.id} data={selectedNode.data as McpServerConfig} />
        )}
      </div>
    </ScrollArea>
  );
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat(history): update PropertiesPanel for multi-selection"
```

---

### Task 4: Update Canvas for selectedNodeIds, drag-end, and edge sync

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`

- [ ] **Step 1: Read Canvas.tsx and apply these changes**

1. Replace `setSelectedNodeId` calls with `setSelectedNodeIds([storeNodeId])`.

2. Add drag-end detection: On `nodepicked`, set a `dragging` flag. Listen for `pointerup` on document to call `commitPosition()` and clear the flag.

3. Add edge sync to the Zustand→Rete subscriber: After syncing nodes, diff Rete connections against editorStore edges and add/remove connections as needed.

Key changes to the `nodepicked` handler:

```typescript
// Rete → Zustand: node selection + drag-end tracking
let dragging = false;

const handlePointerUp = () => {
  if (dragging) {
    dragging = false;
    useEditorStore.getState().commitPosition();
    document.removeEventListener("pointerup", handlePointerUp);
  }
};

area.addPipe((context) => {
  if (context.type === "nodepicked") {
    const reteNode = editor.getNode(context.data.id);
    if (reteNode && "storeNodeId" in reteNode) {
      useEditorStore.getState().setSelectedNodeIds([(reteNode as any).storeNodeId]);
      dragging = true;
      document.addEventListener("pointerup", handlePointerUp);
    }
  }
  return context;
});
```

Edge sync addition at the end of the Zustand→Rete subscriber (after the existing node sync code):

```typescript
// Edge sync: add missing connections, remove stale ones
const reteConnections = editor.getConnections();
const storeEdgeKeys = new Set(state.edges.map((e) => `${e.source}→${e.target}`));

// Remove stale Rete connections
for (const conn of reteConnections) {
  const sourceNode = editor.getNode(conn.source);
  const targetNode = editor.getNode(conn.target);
  if (!sourceNode || !targetNode) continue;
  const sourceStoreId = "storeNodeId" in sourceNode ? (sourceNode as any).storeNodeId : null;
  const targetStoreId = "storeNodeId" in targetNode ? (targetNode as any).storeNodeId : null;
  if (sourceStoreId && targetStoreId && !storeEdgeKeys.has(`${sourceStoreId}→${targetStoreId}`)) {
    syncing = true;
    await editor.removeConnection(conn.id);
    syncing = false;
  }
}

// Add missing connections
const reteEdgeKeys = new Set(reteConnections.map((c) => {
  const sn = editor.getNode(c.source);
  const tn = editor.getNode(c.target);
  const sid = sn && "storeNodeId" in sn ? (sn as any).storeNodeId : null;
  const tid = tn && "storeNodeId" in tn ? (tn as any).storeNodeId : null;
  return `${sid}→${tid}`;
}));

for (const edge of state.edges) {
  if (!reteEdgeKeys.has(`${edge.source}→${edge.target}`)) {
    const sourceRete = findReteNode(edge.source);
    const targetRete = findReteNode(edge.target);
    if (sourceRete && targetRete) {
      const outputKey = Object.keys(sourceRete.outputs || {})[0];
      const inputKey = Object.keys(targetRete.inputs || {})[0];
      if (outputKey && inputKey) {
        syncing = true;
        const conn = new (await import("rete")).ClassicPreset.Connection(sourceRete, outputKey, targetRete, inputKey);
        await editor.addConnection(conn);
        syncing = false;
      }
    }
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/editor/Canvas.tsx
git commit -m "feat(history): update Canvas — selectedNodeIds, drag-end, edge sync"
```

---

### Task 5: Clipboard Store

**Files:**
- Create: `frontend/store/clipboardStore.ts`

- [ ] **Step 1: Create the clipboard store**

```typescript
// frontend/store/clipboardStore.ts
import { GraphNode, GraphEdge } from "@/types/graph";
import { useEditorStore } from "./editorStore";

interface ClipboardData {
  type: "koog-clipboard";
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export async function copySelectedNodes(): Promise<void> {
  const { nodes, edges, selectedNodeIds } = useEditorStore.getState();
  if (selectedNodeIds.length === 0) return;

  const selectedSet = new Set(selectedNodeIds);
  const copiedNodes = structuredClone(nodes.filter((n) => selectedSet.has(n.id)));
  const copiedEdges = structuredClone(
    edges.filter((e) => selectedSet.has(e.source) && selectedSet.has(e.target))
  );

  const data: ClipboardData = { type: "koog-clipboard", nodes: copiedNodes, edges: copiedEdges };

  try {
    await navigator.clipboard.writeText(JSON.stringify(data));
  } catch {
    // Permission denied or no focus — silent no-op
  }
}

export async function pasteNodes(): Promise<void> {
  let text: string;
  try {
    text = await navigator.clipboard.readText();
  } catch {
    return; // Permission denied — silent no-op
  }

  let data: ClipboardData;
  try {
    data = JSON.parse(text);
    if (data.type !== "koog-clipboard" || !Array.isArray(data.nodes)) return;
  } catch {
    return; // Invalid JSON — silent no-op
  }

  // Generate new IDs
  const idMap = new Map<string, string>();
  const newNodes = data.nodes.map((node) => {
    const newId = crypto.randomUUID();
    idMap.set(node.id, newId);
    return {
      ...node,
      id: newId,
      position: { x: node.position.x + 20, y: node.position.y + 20 },
    };
  });

  const newEdges = (data.edges || [])
    .filter((e) => idMap.has(e.source) && idMap.has(e.target))
    .map((edge) => ({
      ...edge,
      id: crypto.randomUUID(),
      source: idMap.get(edge.source)!,
      target: idMap.get(edge.target)!,
    }));

  useEditorStore.getState().addNodes(newNodes, newEdges);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/store/clipboardStore.ts
git commit -m "feat(clipboard): add clipboard store with copy/paste and UUID ID generation"
```

---

### Task 6: Keyboard shortcuts in page.tsx

**Files:**
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Add keyboard shortcut handler**

Add imports:
```typescript
import { useHistoryStore } from "@/store/historyStore";
import { copySelectedNodes, pasteNodes } from "@/store/clipboardStore";
```

Add a `useEffect` for keyboard shortcuts inside `EditorPage()`:

```typescript
useEffect(() => {
  const handler = (e: KeyboardEvent) => {
    const target = e.target as HTMLElement;
    if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) return;

    const mod = e.metaKey || e.ctrlKey;

    if (mod && e.key === "z" && !e.shiftKey) {
      e.preventDefault();
      useHistoryStore.getState().undo();
    } else if (mod && e.key === "z" && e.shiftKey) {
      e.preventDefault();
      useHistoryStore.getState().redo();
    } else if (mod && e.key === "c") {
      e.preventDefault();
      copySelectedNodes();
    } else if (mod && e.key === "v") {
      e.preventDefault();
      pasteNodes();
    } else if (e.key === "Delete" || e.key === "Backspace") {
      e.preventDefault();
      const { selectedNodeIds, removeNode } = useEditorStore.getState();
      for (const id of selectedNodeIds) {
        removeNode(id);
      }
    }
  };

  document.addEventListener("keydown", handler);
  return () => document.removeEventListener("keydown", handler);
}, []);
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat(history): add keyboard shortcuts — undo/redo/copy/paste/delete"
```

---

### Task 7: Toolbar undo/redo buttons

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Add undo/redo buttons**

Add import:
```typescript
import { useHistoryStore } from "@/store/historyStore";
```

Add selectors inside the `Toolbar` function:
```typescript
const past = useHistoryStore((s) => s.past);
const future = useHistoryStore((s) => s.future);
const undo = useHistoryStore((s) => s.undo);
const redo = useHistoryStore((s) => s.redo);
```

Add buttons after the "New Project" separator (`<div className="w-px h-6 bg-border" />`) and before the "+ Agent" button:

```tsx
<Button variant="outline" size="sm" onClick={undo} disabled={past.length === 0}>
  Undo
</Button>
<Button variant="outline" size="sm" onClick={redo} disabled={future.length === 0}>
  Redo
</Button>
<div className="w-px h-6 bg-border" />
```

- [ ] **Step 2: Verify full build succeeds**

Run: `cd frontend && npx next build`
Expected: Build succeeds — all pieces connected

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx
git commit -m "feat(history): add undo/redo buttons to toolbar"
```

---

### Task 8: Fix remaining selectedNodeId references

**Files:**
- Multiple files may reference the old `selectedNodeId`

- [ ] **Step 1: Search for remaining references**

Run: `grep -r "selectedNodeId" frontend/` — check if any files still reference the old singular field. Common places: `SaveLoadDialog.tsx`, `ExportDialog.tsx`, `validation.ts`, any properties panels.

Fix each reference:
- If it reads `selectedNodeId`, change to `selectedNodeIds[0]` or `selectedNodeIds` as appropriate
- If it calls `setSelectedNodeId(id)`, change to `setSelectedNodeIds(id ? [id] : [])`

- [ ] **Step 2: Verify build succeeds**

Run: `cd frontend && npx next build`
Expected: Build succeeds with no TypeScript errors

- [ ] **Step 3: Commit**

```bash
git add -u frontend/
git commit -m "fix: update remaining selectedNodeId references to selectedNodeIds"
```

---

### Task 9: Manual integration test

- [ ] **Step 1: Start dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Test undo/redo**

1. Add an agent node → click Undo → node disappears → click Redo → node reappears
2. Add a tool node, connect it to agent → Undo → connection removed → Undo → tool removed
3. Edit agent name → Undo → name reverts
4. Drag a node → release → Undo → node jumps back to previous position

- [ ] **Step 3: Test copy/paste**

1. Select a node → Cmd/Ctrl+C → Cmd/Ctrl+V → cloned node appears offset by (20,20)
2. Select agent + connected tool → Cmd/Ctrl+C → Cmd/Ctrl+V → both nodes pasted with connection preserved
3. Open a second browser tab → paste → nodes appear (cross-tab)

- [ ] **Step 4: Test keyboard shortcuts**

1. Cmd/Ctrl+Z → undo
2. Cmd/Ctrl+Shift+Z → redo
3. Delete/Backspace → removes selected node(s)
4. Verify shortcuts don't fire while typing in text inputs

- [ ] **Step 5: Test toolbar buttons**

1. Undo button disabled when no history
2. Redo button disabled when no future
3. After undo, redo button enables

- [ ] **Step 6: Commit fixes if any**

```bash
git add -A
git commit -m "fix: adjustments from history/clipboard integration testing"
```
