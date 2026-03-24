# Project Name Dialog — Design Spec

## Goal

Add a "New Project" dialog that lets users set a project name when creating a new modeler project. The name flows through to ZIP export (filename, `settings.gradle.kts`, folder structure).

## Components

### 1. NewProjectDialog

A modal dialog with:
- **Text field** for project name (required, non-empty, max 64 characters)
- **"Create" button** — disabled when field is empty or invalid
- **"Cancel" button** — only visible when opened from Toolbar (not on initial auto-show)

The component accepts `open: boolean` and `onOpenChange: (open: boolean) => void` props. The parent (`page.tsx`) owns the open state. A `isInitial: boolean` prop controls whether Cancel is shown.

On auto-show (no Cancel button): the user can still dismiss via X button or Escape key — they then continue with "Untitled Project".

**Validation:** Name is trimmed, must be non-empty, and sanitized to a valid Gradle/directory name (lowercase, spaces → hyphens, strip special characters like `/ \ : < > " ? * |`). Show the sanitized slug as preview below the input.

Location: `frontend/components/dialogs/NewProjectDialog.tsx`

### 2. Store Changes (`editorStore.ts`)

Extract `DEFAULT_PROJECT_NAME = "Untitled Project"` as a named constant (exported).

No new reset method — use existing `reset()` (which already clears nodes, edges, selectedNodeId, and resets `nextId`) followed by `setProjectName(name)`.

### 3. Toolbar Changes (`Toolbar.tsx`)

- Add "New Project" button
- On click: if `nodes.length > 0`, show `window.confirm("Unsaved changes will be lost. Continue?")`
- If confirmed or canvas empty: set dialog open state to true

### 4. Auto-Show on App Start (`page.tsx`)

- Owns `useState` for dialog open state
- On mount: if `nodes.length === 0` AND `projectName === DEFAULT_PROJECT_NAME` → set open to true
- Passes `isInitial={true}` to hide Cancel button

## Data Flow

```
NewProjectDialog → reset() + setProjectName(name) → editorStore updated
                                                    → nodes: [], edges: [], nextId: 1

On Export:
editorStore → ProjectGraph { name: projectName, nodes, edges }
           → POST /api/export
           → Backend uses graph.name for:
              - ZIP filename: "${graph.name}.zip"
              - settings.gradle.kts: rootProject.name = "${graph.name}"
```

## What Does NOT Change

- **Backend** — `ProjectGraph.name` is already used correctly for ZIP filename and `settings.gradle.kts`
- **Export flow** — `ExportDialog` already reads `projectName` from store
- **Save/Load flow** — `SaveLoadDialog` already serializes/deserializes `projectName`

## Edge Cases

- Empty name: "Create" button stays disabled, no submission possible
- Whitespace-only name: trim before validation, treated as empty
- Cancel/Escape on auto-show: dialog closes, user works with "Untitled Project"
- Loading a saved project: overwrites `projectName` from JSON, no dialog shown
- Long names: capped at 64 characters
- Special characters: sanitized to Gradle-safe slug, preview shown to user
