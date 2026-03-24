# Beta Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the Koog Agent Editor for beta: empty states, node validation indicators, node readability subtitles, keyboard shortcuts dialog, and toolbar cleanup.

**Architecture:** Small targeted changes across existing components. No new stores or major refactors. Validation runs reactively via editorStore subscription. CustomNode reads store data for subtitles and validation status.

**Tech Stack:** React, Zustand, shadcn Dialog, Lucide icons, Tailwind CSS

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `frontend/components/dialogs/KeyboardShortcutsDialog.tsx` | Shortcuts overview dialog |

### Modified Files
| File | Change |
|------|--------|
| `frontend/components/editor/Canvas.tsx` | Empty state overlay |
| `frontend/components/preview/ChatArea.tsx` | Empty message placeholder |
| `frontend/components/validation/ValidationPanel.tsx` | Success state with auto-dismiss |
| `frontend/store/validationStore.ts` | Add `lastValidatedAt` for success state + reactive validation |
| `frontend/components/editor/CustomNode.tsx` | Validation badges + subtitle lines |
| `frontend/components/panels/Toolbar.tsx` | Shortcuts button |
| `frontend/app/page.tsx` | Auto-validation subscription |

---

### Task 1: Empty States — Canvas, ChatArea, ValidationPanel

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`
- Modify: `frontend/components/preview/ChatArea.tsx`
- Modify: `frontend/components/validation/ValidationPanel.tsx`
- Modify: `frontend/store/validationStore.ts`

- [ ] **Step 1: Add empty canvas overlay**

In `frontend/components/editor/Canvas.tsx`, add an import and overlay. Read the file first. The component returns a `<div ref={containerRef}>`. Add a conditional overlay inside it.

Add import:
```typescript
import { useEditorStore } from "@/store/editorStore";
```

The component already imports `useEditorStore`. Inside the `Canvas` function, add:
```typescript
const nodes = useEditorStore((s) => s.nodes);
```

Change the return from:
```tsx
return (
  <div ref={containerRef} className="flex-1 h-full" style={{...}} />
);
```
to:
```tsx
return (
  <div ref={containerRef} className="flex-1 h-full relative" style={{...}}>
    {nodes.length === 0 && (
      <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-10">
        <p className="text-muted-foreground text-sm">Add an Agent to get started</p>
      </div>
    )}
  </div>
);
```

Note: Change the self-closing `<div ... />` to `<div ...>...</div>` to allow children.

- [ ] **Step 2: Add empty ChatArea placeholder**

In `frontend/components/preview/ChatArea.tsx`, read the file. Inside the scroll area div (the one with `ref={scrollRef}`), add a conditional before the messages map:

```tsx
{messages.length === 0 && !error && (
  <div className="flex-1 flex items-center justify-center">
    <p className="text-muted-foreground text-xs">Send a message to start the conversation</p>
  </div>
)}
```

- [ ] **Step 3: Add validation success state**

In `frontend/store/validationStore.ts`, add `showSuccess: boolean` and `setShowSuccess`:

```typescript
import { create } from "zustand";
import { ValidationError } from "@/lib/validation";

interface ValidationState {
  errors: ValidationError[];
  showSuccess: boolean;
  setErrors: (errors: ValidationError[]) => void;
  clearErrors: () => void;
  setShowSuccess: (show: boolean) => void;
  errorsForNode: (nodeId: string) => ValidationError[];
}

export const useValidationStore = create<ValidationState>((set, get) => ({
  errors: [],
  showSuccess: false,
  setErrors: (errors) => set({ errors, showSuccess: errors.length === 0 }),
  clearErrors: () => set({ errors: [], showSuccess: false }),
  setShowSuccess: (show) => set({ showSuccess: show }),
  errorsForNode: (nodeId) => get().errors.filter((e) => e.nodeId === nodeId),
}));
```

In `frontend/components/validation/ValidationPanel.tsx`, update to show success:

```tsx
"use client";

import { useEffect } from "react";
import { useValidationStore } from "@/store/validationStore";
import { Badge } from "@/components/ui/badge";

export function ValidationPanel() {
  const errors = useValidationStore((s) => s.errors);
  const showSuccess = useValidationStore((s) => s.showSuccess);
  const setShowSuccess = useValidationStore((s) => s.setShowSuccess);

  useEffect(() => {
    if (showSuccess) {
      const timer = setTimeout(() => setShowSuccess(false), 3000);
      return () => clearTimeout(timer);
    }
  }, [showSuccess, setShowSuccess]);

  if (errors.length === 0 && !showSuccess) return null;

  if (errors.length === 0 && showSuccess) {
    return (
      <div className="border-t p-2 bg-background">
        <span className="text-sm text-green-500">No issues found ✓</span>
      </div>
    );
  }

  return (
    <div className="border-t p-2 bg-background space-y-1 max-h-40 overflow-auto">
      {errors.map((err, i) => (
        <div key={i} className="flex items-center gap-2 text-sm">
          <Badge variant={err.severity === "error" ? "destructive" : "secondary"}>
            {err.severity}
          </Badge>
          <span className="text-muted-foreground">[{err.nodeId}]</span>
          <span>{err.message}</span>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/components/editor/Canvas.tsx frontend/components/preview/ChatArea.tsx frontend/components/validation/ValidationPanel.tsx frontend/store/validationStore.ts
git commit -m "feat(polish): add empty states — canvas hint, chat placeholder, validation success"
```

---

### Task 2: Auto-validation on store changes

**Files:**
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Add reactive validation subscription**

Read `frontend/app/page.tsx`. Add import:
```typescript
import { useValidationStore } from "@/store/validationStore";
import { validateGraph } from "@/lib/validation";
```

Add a `useEffect` inside `EditorPage()` that subscribes to editorStore changes and runs validation:

```typescript
useEffect(() => {
  // Debounced auto-validation on store changes
  let timeout: ReturnType<typeof setTimeout>;
  const unsubscribe = useEditorStore.subscribe((state) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => {
      const graph = { version: 1 as const, name: state.projectName, nodes: state.nodes, edges: state.edges };
      const errors = validateGraph(graph);
      useValidationStore.getState().setErrors(errors);
    }, 500);
  });
  return () => { unsubscribe(); clearTimeout(timeout); };
}, []);
```

Note: Remove the `setErrors` import from Toolbar's `handleValidate` if it duplicates — actually keep the manual Validate button working too, it's fine to have both.

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 3: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat(polish): add reactive auto-validation on store changes"
```

---

### Task 3: Node validation status on canvas

**Files:**
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add validation status to nodes**

Read `frontend/components/editor/CustomNode.tsx`. Add import:
```typescript
import { useValidationStore } from "@/store/validationStore";
```

Inside the `CustomNode` function, after getting `nodeType` and `colors`, add:

```typescript
const storeNodeId = "storeNodeId" in data ? (data as any).storeNodeId : null;
const nodeErrors = storeNodeId
  ? useValidationStore.getState().errorsForNode(storeNodeId)
  : [];
const hasErrors = nodeErrors.some((e) => e.severity === "error");
const hasWarnings = nodeErrors.some((e) => e.severity === "warning");
```

To determine "valid" (configured) vs "new", read from editorStore:
```typescript
const storeNode = storeNodeId
  ? require("@/store/editorStore").useEditorStore.getState().nodes.find((n: any) => n.id === storeNodeId)
  : null;
const isConfigured = storeNode && (storeNode.data as any).name?.trim();
```

Override border color based on validation:
```typescript
const borderColor = hasErrors
  ? "rgba(239, 68, 68, 0.5)"
  : hasWarnings
  ? "rgba(251, 191, 36, 0.5)"
  : colors.border;

const glowColor = hasErrors
  ? "rgba(239, 68, 68, 0.15)"
  : hasWarnings
  ? "rgba(251, 191, 36, 0.12)"
  : colors.glow;
```

Replace `colors.border` and `colors.glow` usages in the component's style with `borderColor` and `glowColor`.

Add a status badge in the node header (top-right, absolute positioned). Wrap the header in `position: relative` and add:

```tsx
{/* Validation badge */}
{hasErrors && (
  <span style={{ position: "absolute", top: 4, right: 8, fontSize: 10, color: "#ef4444" }}>✗</span>
)}
{!hasErrors && hasWarnings && (
  <span style={{ position: "absolute", top: 4, right: 8, fontSize: 10, color: "#fbbf24" }}>⚠</span>
)}
{!hasErrors && !hasWarnings && isConfigured && (
  <span style={{ position: "absolute", top: 4, right: 8, fontSize: 8, color: "#22c55e" }}>●</span>
)}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/editor/CustomNode.tsx
git commit -m "feat(polish): add validation status badges to canvas nodes"
```

---

### Task 4: Node readability — subtitle lines

**Files:**
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add subtitles to nodes**

In the same `CustomNode` function, after reading `storeNode` (from Task 3), compute a subtitle:

```typescript
let subtitle = "";
if (storeNode) {
  const d = storeNode.data as any;
  switch (storeNode.type) {
    case "agent":
      if (d.modelProvider) subtitle = `${d.modelProvider}${d.modelId ? " · " + d.modelId : ""}`;
      break;
    case "sub-agent":
      if (d.delegationMode) subtitle = d.delegationMode;
      break;
    case "tool":
      if (d.parameters?.length) subtitle = `${d.parameters.length} parameter${d.parameters.length !== 1 ? "s" : ""}`;
      break;
    case "mcp-server":
      if (d.transportType) subtitle = `${d.transportType.toUpperCase()}${d.url ? " · " + d.url.slice(0, 30) : ""}`;
      break;
  }
}
```

Add the subtitle below the label in the header section:

```tsx
<div style={{ fontSize: 13, fontWeight: 500, color: "#e2e8f0" }}>
  {data.label}
</div>
{subtitle && (
  <div style={{ fontSize: 10, color: "#64748b", marginTop: 2 }}>
    {subtitle}
  </div>
)}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/editor/CustomNode.tsx
git commit -m "feat(polish): add contextual subtitles to canvas nodes"
```

---

### Task 5: Keyboard Shortcuts Dialog + Toolbar button

**Files:**
- Create: `frontend/components/dialogs/KeyboardShortcutsDialog.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Create KeyboardShortcutsDialog**

```tsx
// frontend/components/dialogs/KeyboardShortcutsDialog.tsx
"use client";

import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { KeyboardIcon } from "lucide-react";

const isMac = typeof navigator !== "undefined" && /Mac/.test(navigator.platform);
const mod = isMac ? "⌘" : "Ctrl";

const shortcuts = [
  { keys: `${mod}+Z`, action: "Undo" },
  { keys: `${mod}+Shift+Z`, action: "Redo" },
  { keys: `${mod}+C`, action: "Copy nodes" },
  { keys: `${mod}+V`, action: "Paste nodes" },
  { keys: "Delete / Backspace", action: "Delete selected" },
];

export function KeyboardShortcutsDialog() {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <KeyboardIcon className="size-4" />
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Keyboard Shortcuts</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {shortcuts.map((s) => (
            <div key={s.keys} className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{s.action}</span>
              <kbd className="px-2 py-0.5 rounded bg-muted text-xs font-mono">{s.keys}</kbd>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Add to Toolbar**

In `frontend/components/panels/Toolbar.tsx`, add import:
```typescript
import { KeyboardShortcutsDialog } from "@/components/dialogs/KeyboardShortcutsDialog";
```

Add `<KeyboardShortcutsDialog />` after `<ExportDialog />` (at the far right of the toolbar).

- [ ] **Step 3: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 4: Commit**

```bash
git add frontend/components/dialogs/KeyboardShortcutsDialog.tsx frontend/components/panels/Toolbar.tsx
git commit -m "feat(polish): add keyboard shortcuts dialog and toolbar button"
```

---

### Task 6: Manual integration test

- [ ] **Step 1: Start dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Test empty states**

1. Create new project → empty canvas shows "Add an Agent to get started"
2. Open Preview panel → chat shows "Send a message to start the conversation"
3. Click Validate with no nodes → "No issues found ✓" appears briefly then fades

- [ ] **Step 3: Test node validation status**

1. Add an agent (no name) → red border + ✗ badge on node
2. Fill in agent name + prompt → green ● dot appears
3. Add a tool, don't configure → ✗ badge
4. Configure tool name → green ● dot

- [ ] **Step 4: Test node subtitles**

1. Agent shows "openai" or "openai · gpt-4o" under name
2. Sub-agent shows "automatic" under name
3. Tool with 2 params shows "2 parameters"
4. MCP server shows "SSE · http://..."

- [ ] **Step 5: Test keyboard shortcuts dialog**

1. Click ⌨ button → dialog opens with shortcut list
2. Shows ⌘ on Mac, Ctrl on Windows/Linux

- [ ] **Step 6: Commit fixes if any**

```bash
git add -A
git commit -m "fix: adjustments from beta polish integration testing"
```
