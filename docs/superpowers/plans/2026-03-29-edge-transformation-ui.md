# Edge-Transformation UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make edges selectable in the Graph Strategy Editor and allow editing transformation on all edge types.

**Architecture:** Change edge click from delete to select (custom event), wire up existing selection state and properties panel, move transformation field outside the `onCondition` guard, add delete button to edge properties.

**Tech Stack:** React, Rete.js, CustomEvent API, existing CodeEditor component

---

### Task 1: Change edge click from delete to select

**Files:**
- Modify: `frontend/components/graph-agent/StrategyCustomNode.tsx:123-143`

- [ ] **Step 1: Change the click handler and hover color**

In `frontend/components/graph-agent/StrategyCustomNode.tsx`, replace the entire `StrategyCustomConnection` function (lines 123-143):

Find:
```tsx
export function StrategyCustomConnection(props: { data: { id: string; source: string; target: string } }) {
  const { path } = Presets.classic.useConnection();
  const [hovered, setHovered] = React.useState(false);

  if (!path) return null;

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    const event = new CustomEvent("strategy-edge-delete", {
      detail: { source: props.data.source, target: props.data.target },
    });
    window.dispatchEvent(event);
  };

  return (
    <svg data-testid="connection" style={{ overflow: "visible", position: "absolute", pointerEvents: "none", width: "100%", height: "100%" }}>
      <path d={path} stroke="transparent" strokeWidth={14} fill="none" style={{ pointerEvents: "stroke", cursor: "pointer" }} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onClick={handleClick} />
      <path d={path} stroke={hovered ? "rgba(239, 68, 68, 0.8)" : "rgba(99, 102, 241, 0.5)"} strokeWidth={hovered ? 3 : 2} fill="none" style={{ pointerEvents: "none", transition: "stroke 0.15s, stroke-width 0.15s" }} />
    </svg>
  );
}
```

Replace with:
```tsx
export function StrategyCustomConnection(props: { data: { id: string; source: string; target: string } }) {
  const { path } = Presets.classic.useConnection();
  const [hovered, setHovered] = React.useState(false);

  if (!path) return null;

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    const event = new CustomEvent("strategy-edge-select", {
      detail: { source: props.data.source, target: props.data.target },
    });
    window.dispatchEvent(event);
  };

  return (
    <svg data-testid="connection" style={{ overflow: "visible", position: "absolute", pointerEvents: "none", width: "100%", height: "100%" }}>
      <path d={path} stroke="transparent" strokeWidth={14} fill="none" style={{ pointerEvents: "stroke", cursor: "pointer" }} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onClick={handleClick} />
      <path d={path} stroke={hovered ? "rgba(129, 140, 248, 0.9)" : "rgba(99, 102, 241, 0.5)"} strokeWidth={hovered ? 3 : 2} fill="none" style={{ pointerEvents: "none", transition: "stroke 0.15s, stroke-width 0.15s" }} />
    </svg>
  );
}
```

Changes:
- Event name: `strategy-edge-delete` → `strategy-edge-select`
- Hover color: red `rgba(239, 68, 68, 0.8)` → bright indigo `rgba(129, 140, 248, 0.9)`

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/StrategyCustomNode.tsx
git commit -m "feat: change edge click from delete to select"
```

---

### Task 2: Wire up edge selection in Canvas

**Files:**
- Modify: `frontend/components/graph-agent/GraphStrategyCanvas.tsx:111-124`

- [ ] **Step 1: Replace the edge-delete listener with edge-select listener**

In `frontend/components/graph-agent/GraphStrategyCanvas.tsx`, find the `handleEdgeDelete` function and its event listener (lines 111-124):

Find:
```typescript
      const handleEdgeDelete = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        const sourceRete = editor.getNodes().find((n: any) => n.id === detail.source);
        const targetRete = editor.getNodes().find((n: any) => n.id === detail.target);
        if (sourceRete && targetRete && "strategyNodeId" in sourceRete && "strategyNodeId" in targetRete) {
          const srcId = (sourceRete as any).strategyNodeId;
          const tgtId = (targetRete as any).strategyNodeId;
          const newEdges = propsRef.current.strategyEdges.filter(
            (se) => !(se.source === srcId && se.target === tgtId)
          );
          propsRef.current.onEdgesChanged(newEdges);
        }
      };
      window.addEventListener("strategy-edge-delete", handleEdgeDelete);
```

Replace with:
```typescript
      const handleEdgeSelect = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        const sourceRete = editor.getNodes().find((n: any) => n.id === detail.source);
        const targetRete = editor.getNodes().find((n: any) => n.id === detail.target);
        if (sourceRete && targetRete && "strategyNodeId" in sourceRete && "strategyNodeId" in targetRete) {
          const srcId = (sourceRete as any).strategyNodeId;
          const tgtId = (targetRete as any).strategyNodeId;
          const edge = propsRef.current.strategyEdges.find(
            (se) => se.source === srcId && se.target === tgtId
          );
          if (edge) {
            propsRef.current.onEdgeSelected(edge.id);
          }
        }
      };
      window.addEventListener("strategy-edge-select", handleEdgeSelect);
```

Also find the cleanup `return` block (around line 134-138) where `strategy-edge-delete` is removed and update it:

Find:
```typescript
      window.removeEventListener("strategy-edge-delete", handleEdgeDelete);
```

Replace with:
```typescript
      window.removeEventListener("strategy-edge-select", handleEdgeSelect);
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/GraphStrategyCanvas.tsx
git commit -m "feat: wire up edge selection in strategy canvas"
```

---

### Task 3: Update StrategyEdgeProperties — transformation for all types + delete button

**Files:**
- Modify: `frontend/components/graph-agent/StrategyEdgeProperties.tsx`

- [ ] **Step 1: Move transformation outside onCondition block and add delete button**

Replace the entire content of `frontend/components/graph-agent/StrategyEdgeProperties.tsx`:

Find:
```tsx
"use client";

import CodeEditor from "@/components/shared/CodeEditor";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyEdge, GraphEdgeType } from "@/types/graph-agent";

export function StrategyEdgeProperties({
  edge,
  onUpdate,
}: {
  edge: GraphStrategyEdge;
  onUpdate: (data: Partial<GraphStrategyEdge>) => void;
}) {
  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Edge Type</Label>
        <Select value={edge.edgeType} onValueChange={(v) => onUpdate({ edgeType: v as GraphEdgeType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {edge.edgeType === "onCondition" && (
        <>
          <div>
            <Label>Condition</Label>
            <CodeEditor
              value={edge.condition ?? ""}
              onChange={(v) => onUpdate({ condition: v })}
              height="80px"
            />
          </div>
          <div>
            <Label>Transformation (optional)</Label>
            <CodeEditor
              value={edge.transformation ?? ""}
              onChange={(v) => onUpdate({ transformation: v || undefined })}
              height="80px"
            />
          </div>
        </>
      )}
    </div>
  );
}
```

Replace with:
```tsx
"use client";

import CodeEditor from "@/components/shared/CodeEditor";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyEdge, GraphEdgeType } from "@/types/graph-agent";

export function StrategyEdgeProperties({
  edge,
  onUpdate,
  onDelete,
}: {
  edge: GraphStrategyEdge;
  onUpdate: (data: Partial<GraphStrategyEdge>) => void;
  onDelete: () => void;
}) {
  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Edge Type</Label>
        <Select value={edge.edgeType} onValueChange={(v) => onUpdate({ edgeType: v as GraphEdgeType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {edge.edgeType === "onCondition" && (
        <div>
          <Label>Condition</Label>
          <CodeEditor
            value={edge.condition ?? ""}
            onChange={(v) => onUpdate({ condition: v })}
            height="80px"
          />
        </div>
      )}

      <div>
        <Label>Transformation (optional)</Label>
        <CodeEditor
          value={edge.transformation ?? ""}
          onChange={(v) => onUpdate({ transformation: v || undefined })}
          height="80px"
        />
      </div>

      <Button variant="destructive" size="sm" className="w-full" onClick={onDelete}>
        Delete Edge
      </Button>
    </div>
  );
}
```

Changes:
- Added `Button` import, removed unused `Input` import
- Added `onDelete` prop
- Moved transformation CodeEditor outside `onCondition` block (shown for all edge types)
- Condition CodeEditor stays inside `onCondition` block
- Added "Delete Edge" button at the bottom

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

Expected: May show a type error because `GraphStrategyEditor` doesn't pass `onDelete` yet. That's OK — we fix it in Task 4.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/StrategyEdgeProperties.tsx
git commit -m "feat: show transformation for all edge types, add delete button"
```

---

### Task 4: Wire up delete handler in GraphStrategyEditor

**Files:**
- Modify: `frontend/components/graph-agent/GraphStrategyEditor.tsx:145-153`

- [ ] **Step 1: Add handleDeleteEdge function and pass onDelete to StrategyEdgeProperties**

In `frontend/components/graph-agent/GraphStrategyEditor.tsx`, add a `handleDeleteEdge` function. Find the `handleDeleteStrategyNode` function (around line 88-95) and add after it:

```typescript
  const handleDeleteEdge = (edgeId: string) => {
    const updatedEdges = data.strategyEdges.filter((e) => e.id !== edgeId);
    update({ strategyEdges: updatedEdges });
    setSelectedStrategyEdgeId(null);
  };
```

Then find the `StrategyEdgeProperties` usage (around line 148-151):

Find:
```tsx
              <StrategyEdgeProperties
                edge={selectedEdge}
                onUpdate={(edgeUpdate) => handleUpdateEdge(selectedEdge.id, edgeUpdate)}
              />
```

Replace with:
```tsx
              <StrategyEdgeProperties
                edge={selectedEdge}
                onUpdate={(edgeUpdate) => handleUpdateEdge(selectedEdge.id, edgeUpdate)}
                onDelete={() => handleDeleteEdge(selectedEdge.id)}
              />
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/GraphStrategyEditor.tsx
git commit -m "feat: wire up edge delete handler from properties panel"
```

---

### Task 5: Build verification

**Files:**
- No new files

- [ ] **Step 1: Run production build**

```bash
cd frontend && npx next build
```

Expected: Build succeeds.

- [ ] **Step 2: Manual verification**

```bash
cd frontend && npm run dev
```

Verify:
1. Open a Graph Agent strategy editor
2. Click on an edge → edge is selected (not deleted)
3. Properties Panel shows Edge Properties with type, condition (if onCondition), and transformation
4. Edit transformation on a non-onCondition edge → value persists
5. Click "Delete Edge" → edge is removed
6. Click canvas background → edge is deselected
7. Hover over edge → indigo highlight (not red)

- [ ] **Step 3: Final commit if adjustments needed**

```bash
git add -A
git commit -m "feat: complete edge-transformation UI"
```
