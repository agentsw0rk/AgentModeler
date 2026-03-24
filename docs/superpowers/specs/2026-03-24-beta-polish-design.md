# Beta Polish — Design Spec

## Goal

Polish the Koog Agent Editor for beta demos and real usage: better empty states, node validation indicators, improved node readability, toolbar cleanup, and keyboard shortcut discoverability.

## 1. Empty States

### Empty Canvas
Centered muted text "Add an Agent to get started" over the dot grid when `nodes.length === 0`. Disappears when first node is added. Rendered in `Canvas.tsx` as an overlay.

### Empty ChatArea
Centered placeholder "Send a message to start the conversation" in muted-foreground when `messages.length === 0`.

### Validation Success
After running Validate with no errors, show "No issues found ✓" in green in the ValidationPanel for 3 seconds, then hide. Currently returns `null` when no errors — change to show brief positive feedback that auto-dismisses.

## 2. Node Validation Status on Canvas

Nodes display their validation status visually:
- **Error:** Red border + ✗ badge top-right
- **Warning:** Orange border + ⚠ badge top-right
- **Valid (configured):** Small green ● dot top-right (subtle)
- **New/unconfigured:** Default styling, no extra indicator

### Implementation
- Extend `validationStore` with `errorsForNode(nodeId)` results accessible per-node (already exists)
- Run `validateGraph()` reactively on editorStore changes — store results in validationStore automatically
- `CustomNode.tsx` reads validation errors for its `storeNodeId` and adjusts border color + badge accordingly
- Validation runs on a `useEditorStore.subscribe()` in `page.tsx` (or a dedicated hook), debounced to avoid excessive re-validation

### Validation State → Node Styling
| State | Border | Badge |
|-------|--------|-------|
| Error | `rgba(239, 68, 68, 0.5)` (red) | ✗ red |
| Warning | `rgba(251, 191, 36, 0.5)` (orange) | ⚠ orange |
| Valid | Default (type color) | ● green dot |
| New | Default (type color) | None |

A node is "valid" when it has required fields filled (name, etc.) and no validation errors. A node is "new" when it was just added and has no data yet.

## 3. Toolbar Structure

Current grouping is already good. Changes:
- Add **⌨ Shortcuts button** at the far right (after Export), opens `KeyboardShortcutsDialog`
- No other structural changes needed

## 4. Node Readability

Extend `CustomNode.tsx` to show a subtitle line under the node name with contextual info from `node.data`:

| Node Type | Subtitle |
|-----------|----------|
| Agent | Model info: "OpenAI · GPT-4o" |
| Sub-Agent | Delegation mode: "automatic" |
| Tool | Parameter count: "2 parameters" |
| MCP Server | Transport: "SSE · http://..." (truncated) |
| Builtin Tool | No subtitle (badge + name sufficient) |

The subtitle reads from the Rete node's backing store data. Since `CustomNode` only has the Rete node (not the store node), the data must be passed through. Two options:
- **Option A:** Read from `useEditorStore` inside CustomNode using the `storeNodeId`
- **Option B:** Embed subtitle info in the Rete node's label or a custom field

**Chosen: Option A** — CustomNode reads `useEditorStore.getState().nodes.find(n => n.id === storeNodeId)` to get the full data. This is a synchronous read (not a hook — CustomNode is rendered by Rete, not React state).

## 5. Keyboard Shortcuts Dialog

New file: `frontend/components/dialogs/KeyboardShortcutsDialog.tsx`

Simple shadcn Dialog opened by ⌨ button in toolbar. Content is a styled table:

| Shortcut | Action |
|----------|--------|
| Cmd/Ctrl+Z | Undo |
| Cmd/Ctrl+Shift+Z | Redo |
| Cmd/Ctrl+C | Copy nodes |
| Cmd/Ctrl+V | Paste nodes |
| Delete / Backspace | Delete selected |

Uses `Dialog`, `DialogContent`, `DialogHeader`, `DialogTitle` from existing UI components. Shows "⌘" on Mac, "Ctrl" on other platforms (detect via `navigator.platform`).

## Files Changed

### New Files
- `frontend/components/dialogs/KeyboardShortcutsDialog.tsx`

### Modified Files
- `frontend/components/editor/Canvas.tsx` — empty state overlay
- `frontend/components/editor/CustomNode.tsx` — validation badges, subtitle line
- `frontend/components/preview/ChatArea.tsx` — empty message placeholder
- `frontend/components/validation/ValidationPanel.tsx` — success state with auto-dismiss
- `frontend/store/validationStore.ts` — add auto-validation on store subscribe, success flag
- `frontend/components/panels/Toolbar.tsx` — add shortcuts button
- `frontend/app/page.tsx` — auto-validation subscription

## What Does NOT Change
- Backend — no changes
- History/clipboard — already done
- Preview panel — Phase 2
- MCP flow — Phase 2
