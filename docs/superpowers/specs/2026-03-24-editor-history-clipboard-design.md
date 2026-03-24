# Editor History & Clipboard — Design Spec

## Goal

Users can undo/redo graph changes and copy/paste nodes (including across browser tabs) in the Koog Agent Editor.

## Architecture Decisions

- **History:** Snapshot-based (full state snapshots on past/future stacks), not command-pattern
- **History size:** Capped at 100 entries (oldest dropped when exceeded)
- **Tracked actions:** All structural changes + debounced position changes (on drag end)
- **Clipboard:** System clipboard via `navigator.clipboard` API (enables cross-tab paste)
- **Paste edges:** Only internal edges (edges between pasted nodes). Edges to external nodes are discarded.
- **ID generation for paste:** `crypto.randomUUID()` — avoids collision with the module-level `nextId` counter

## History Store (`frontend/store/historyStore.ts`)

Separate Zustand store:

```typescript
interface Snapshot {
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
  canUndo: () => boolean;
  canRedo: () => boolean;
}
```

- `pushState(snapshot)` — pushes snapshot to `past` (cap at 100, drop oldest), clears `future`
- `undo()` — pops from `past`, pushes current editorStore state to `future`, applies popped snapshot to editorStore
- `redo()` — pops from `future`, pushes current editorStore state to `past`, applies popped snapshot to editorStore
- `clear()` — empties both stacks (called on `reset()` / new project)

**Undo/redo apply snapshots via `useEditorStore.setState()` directly** — not through mutating actions. This avoids triggering history-push wrappers and eliminates the need for a `_skipHistory` flag.

**Integration with editorStore:** Every mutating action (`addNode`, `removeNode`, `updateNodeData`, `addEdge`, `removeEdge`, `setProjectName`) snapshots the pre-mutation state into historyStore via `pushState()` before performing the mutation.

**Position handling:** `updateNodePosition` does NOT create history entries (would flood on every drag pixel). A new `commitPosition()` action is called on drag-end and creates one history entry for the position change.

**Drag-end detection:** Rete does not emit a native "drag end" event. Detect via `pointerup` listener on the document after a `nodepicked` event, then call `commitPosition()`.

## Clipboard Store (`frontend/store/clipboardStore.ts`)

```typescript
interface ClipboardData {
  nodes: GraphNode[];
  edges: GraphEdge[];
}
```

**`copy(nodeIds: string[])`:**
1. Deep-clone selected nodes from editorStore
2. Filter edges to only those where both `source` and `target` are in `nodeIds`
3. Serialize as JSON, write to `navigator.clipboard.writeText()`
4. Catch and ignore clipboard API errors (permission denied, no focus)

**`paste()`:**
1. Read from `navigator.clipboard.readText()`, parse JSON
2. Validate structure (nodes array, edges array). Invalid content → silent no-op
3. Generate new IDs for all nodes using `crypto.randomUUID()` (mapping: `oldId → newId`)
4. Remap edge `source`/`target` using the ID mapping
5. Generate new IDs for edges using `crypto.randomUUID()`
6. Offset positions by (+20, +20) to avoid overlap
7. Add all nodes and edges to editorStore via `addNodes()` batch action (single history entry)
8. Select the pasted nodes
9. Catch and ignore clipboard API errors

## Keyboard Shortcuts

Registered in `page.tsx` via `useEffect` + `keydown` listener:

| Shortcut | Action |
|----------|--------|
| `Cmd/Ctrl+Z` | Undo |
| `Cmd/Ctrl+Shift+Z` | Redo |
| `Cmd/Ctrl+C` | Copy selected nodes |
| `Cmd/Ctrl+V` | Paste |
| `Delete` / `Backspace` | Delete selected node(s) |

Shortcuts are suppressed when an `input`, `textarea`, or `[contenteditable]` element is focused.

## Toolbar Changes

Add Undo and Redo buttons to the toolbar (after "New Project" button, before node-add buttons):
- Undo button: disabled when `past` is empty
- Redo button: disabled when `future` is empty
- No Copy/Paste buttons — keyboard shortcuts only

## EditorStore Changes

- Add `commitPosition()` action for drag-end position snapshotting
- Add `addNodes(nodes, edges)` batch action for paste (single history entry)
- Add `selectedNodeIds: string[]` (replace `selectedNodeId: string | null`) to support multi-selection for copy
- Wrap all mutating actions to call `historyStore.pushState()` before mutation

**Multi-selection:** The existing `selectedNodeId` (singular) is replaced with `selectedNodeIds: string[]`. Canvas Rete integration is updated to support Ctrl+Click for multi-select (Rete already has `accumulateOnCtrl` enabled).

**PropertiesPanel with multi-selection:** When `selectedNodeIds.length > 1`, show a placeholder "N nodes selected" instead of node properties. When exactly 1 node is selected, show properties as before.

## Rete Canvas Integration

- Drag-end: `pointerup` after `nodepicked` → call `commitPosition()`
- Node selection → sync `selectedNodeIds` array (already handles Ctrl for accumulation)
- After paste → translate pasted Rete nodes to offset positions
- **Edge sync after undo/redo:** The existing Zustand→Rete subscriber only syncs nodes. Extend it to also sync edges: after node sync, diff current Rete connections against editorStore edges and add/remove as needed. This ensures undo/redo correctly restores visual connections.

## What Does NOT Change

- Backend — no changes
- Export/Save/Load — work with editorStore state as before
- PreviewPanel — reads from editorStore as before
- Validation — reads from editorStore as before

## Edge Cases

- Undo after paste: removes all pasted nodes+edges in one step
- Undo after delete: restores node + its connected edges
- Copy with no selection: no-op
- Paste with invalid clipboard content: no-op (silent, no error)
- Paste from different tab: works via system clipboard (same JSON format)
- New project / load project: clears history stacks
- Clipboard API permission denied: silent no-op
- Rapid undo/redo: Rete sync is async; each undo/redo replaces full state, intermediate syncs are overwritten by the latest state
