# Project Name Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "New Project" dialog so users can name their project at creation time, with auto-show on first load and a toolbar button.

**Architecture:** A controlled `NewProjectDialog` component receives `open`/`onOpenChange` props from `page.tsx`. The store gets a `DEFAULT_PROJECT_NAME` constant and a `slugifyName()` utility sanitizes names for Gradle compatibility. The Toolbar gets a "New Project" button with `window.confirm()` guard.

**Tech Stack:** React, Zustand, base-ui Dialog, Next.js

---

### Task 1: Extract DEFAULT_PROJECT_NAME constant and add slugify utility

**Files:**
- Modify: `frontend/store/editorStore.ts` (replace both `"Untitled Project"` strings with constant)
- Create: `frontend/lib/slugify.ts`

- [ ] **Step 1: Create `slugify.ts`**

```typescript
// frontend/lib/slugify.ts
export function slugifyProjectName(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[/\\:<>"?*|]+/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 64);
}
```

- [ ] **Step 2: Extract constant in editorStore**

In `frontend/store/editorStore.ts`, add the export before the interface:

```typescript
export const DEFAULT_PROJECT_NAME = "Untitled Project";
```

Search for both occurrences of `"Untitled Project"` in the file and replace with `DEFAULT_PROJECT_NAME`.

- [ ] **Step 3: Verify the app still compiles**

Run: `cd frontend && npx next build --no-lint`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/slugify.ts frontend/store/editorStore.ts
git commit -m "refactor: extract DEFAULT_PROJECT_NAME constant and add slugify utility"
```

---

### Task 2: Create NewProjectDialog component

**Files:**
- Create: `frontend/components/dialogs/NewProjectDialog.tsx`

- [ ] **Step 1: Create the dialog component**

Note: base-ui's `Dialog.Root` `onOpenChange` accepts `(open: boolean, event: Event, reason: string)`. Our component's `onOpenChange` prop is `(open: boolean) => void`, so we must wrap the call to `Dialog` to match the signature.

```tsx
// frontend/components/dialogs/NewProjectDialog.tsx
"use client";

import { useState, useEffect } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { slugifyProjectName } from "@/lib/slugify";

interface NewProjectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  isInitial?: boolean;
  onCreateProject: (name: string) => void;
}

export function NewProjectDialog({ open, onOpenChange, isInitial = false, onCreateProject }: NewProjectDialogProps) {
  const [name, setName] = useState("");

  useEffect(() => {
    if (open) setName("");
  }, [open]);

  const trimmed = name.trim();
  const slug = slugifyProjectName(trimmed);
  const isValid = slug.length > 0;

  const handleCreate = () => {
    if (!isValid) return;
    onCreateProject(slug);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(value) => onOpenChange(value)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New Project</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div>
            <Input
              placeholder="my-awesome-agent"
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={64}
              onKeyDown={(e) => { if (e.key === "Enter" && isValid) handleCreate(); }}
              autoFocus
            />
          </div>
          {trimmed && slug && trimmed !== slug && (
            <p className="text-xs text-muted-foreground">
              Project name: <span className="font-mono">{slug}</span>
            </p>
          )}
        </div>
        <DialogFooter>
          {!isInitial && (
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
          )}
          <Button onClick={handleCreate} disabled={!isValid}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build --no-lint`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/dialogs/NewProjectDialog.tsx
git commit -m "feat: add NewProjectDialog component"
```

---

### Task 3: Wire dialog into page.tsx and add Toolbar button

**Files:**
- Modify: `frontend/app/page.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Add `onNewProject` prop and button to Toolbar**

Change the function signature in `frontend/components/panels/Toolbar.tsx`:

```tsx
export function Toolbar({ onNewProject }: { onNewProject: () => void }) {
```

Add the "New Project" button at the beginning of the toolbar's button row, before the "+ Agent" button:

```tsx
<Button variant="outline" size="sm" onClick={onNewProject}>
  New Project
</Button>
<div className="w-px h-6 bg-border" />
```

- [ ] **Step 2: Update page.tsx with state, auto-show, and dialog wiring**

Replace the entire `frontend/app/page.tsx` content:

```tsx
"use client";

import { useState, useEffect } from "react";
import { Toolbar } from "@/components/panels/Toolbar";
import { Canvas } from "@/components/editor/Canvas";
import { PropertiesPanel } from "@/components/panels/PropertiesPanel";
import { ValidationPanel } from "@/components/validation/ValidationPanel";
import { NewProjectDialog } from "@/components/dialogs/NewProjectDialog";
import { useEditorStore, DEFAULT_PROJECT_NAME } from "@/store/editorStore";

export default function EditorPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isInitial, setIsInitial] = useState(false);
  const nodes = useEditorStore((s) => s.nodes);
  const projectName = useEditorStore((s) => s.projectName);
  const reset = useEditorStore((s) => s.reset);
  const setProjectName = useEditorStore((s) => s.setProjectName);

  useEffect(() => {
    if (nodes.length === 0 && projectName === DEFAULT_PROJECT_NAME) {
      setIsInitial(true);
      setDialogOpen(true);
    }
  }, []);

  const handleCreateProject = (name: string) => {
    reset();
    setProjectName(name);
  };

  const handleNewProjectRequest = () => {
    if (nodes.length > 0) {
      if (!window.confirm("Unsaved changes will be lost. Continue?")) return;
    }
    setIsInitial(false);
    setDialogOpen(true);
  };

  return (
    <div className="flex flex-col h-screen">
      <Toolbar onNewProject={handleNewProjectRequest} />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
        <PropertiesPanel />
      </div>
      <ValidationPanel />
      <NewProjectDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        isInitial={isInitial}
        onCreateProject={handleCreateProject}
      />
    </div>
  );
}
```

- [ ] **Step 3: Verify full build succeeds**

Run: `cd frontend && npx next build --no-lint`
Expected: Build succeeds — all pieces connected

- [ ] **Step 4: Commit**

```bash
git add frontend/app/page.tsx frontend/components/panels/Toolbar.tsx
git commit -m "feat: wire NewProjectDialog into page and add toolbar button"
```

---

### Task 4: Manual integration test

- [ ] **Step 1: Start dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Verify auto-show on fresh load**

Open `http://localhost:3000`. The "New Project" dialog should appear automatically. Enter a name like "My Test Agent" — the slug preview should show `my-test-agent`. Click "Create" — canvas should be empty with the name set.

- [ ] **Step 3: Verify "New Project" button from Toolbar**

Add some nodes to the canvas. Click "New Project" in toolbar. A `window.confirm()` should appear. Click OK. The dialog opens. Enter a new name and create — canvas resets.

- [ ] **Step 4: Verify export uses the project name**

Add an agent node, fill in required fields. Click "Export Kotlin". The downloaded ZIP should be named `my-test-agent.zip` (matching the project name).

- [ ] **Step 5: Verify dismiss on auto-show**

Refresh the page. Dialog appears. Press Escape or click X. Dialog closes, canvas works with "Untitled Project".

- [ ] **Step 6: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: adjustments from integration testing"
```
