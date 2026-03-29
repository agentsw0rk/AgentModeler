# Code-Editor for Tool-Bodies

**Date:** 2026-03-29
**Feature:** 01 — Code-Editor for Tool-Bodies
**Status:** Design approved

## Problem

The current body field for tool functions is a plain `<textarea>`. For non-trivial Kotlin code (10-20+ lines), this is inadequate — no syntax highlighting, no bracket matching, no line numbers.

## Solution

Replace `<textarea>` fields for code-like content with Monaco Editor, providing a full-featured editing experience with Kotlin syntax highlighting.

## Scope

### In Scope

- **Tool Function Body** (`body` field in ToolProperties)
- **Condition-Node Condition** (condition field in Graph-Agent)
- **Edge Condition** (condition field in Graph-Agent edges)

### Out of Scope

- System Prompt / Description fields (remain `<Textarea>`)
- `returnType` field (short text, stays as input)
- Backend changes (none needed — strings pass through 1:1)

## Architecture

### New Component: `frontend/components/shared/CodeEditor.tsx`

Wraps `@monaco-editor/react` with SSR-safe dynamic import.

**Props:**

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `value` | `string` | — | Controlled value |
| `onChange` | `(value: string) => void` | — | Change callback |
| `language` | `string` | `"kotlin"` | Monaco language ID |
| `height` | `string` | `"120px"` | Editor height |
| `readOnly` | `boolean` | `false` | Read-only mode |

**Mode:** Controlled (`value` + `onChange`) — values live in the Zustand store and must stay in sync.

**SSR:** Exported via `next/dynamic` with `ssr: false`. Loading fallback is a dark placeholder div matching the editor dimensions.

### Monaco Editor Options

```typescript
{
  minimap: { enabled: false },           // too narrow in 320px panel
  fontSize: 12,
  lineNumbers: "on",
  lineDecorationsWidth: 0,
  wordWrap: "on",
  scrollBeyondLastLine: false,
  automaticLayout: true,
  bracketPairColorization: { enabled: true },
  autoClosingBrackets: "always",
  tabSize: 4,
  suggestOnTriggerCharacters: true,
  quickSuggestions: true,
}
```

### Custom Theme: "modeler-dark"

Registered in `beforeMount` callback, based on `vs-dark`:

- `editor.background`: transparent (inherits card background from container)
- `editor.foreground`: light gray (matching `--foreground`)
- `editor.lineHighlightBackground`: subtle white overlay (~5%)
- `editor.selectionBackground`: primary blue with opacity
- `editorCursor.foreground`: primary blue

### Container Styling

- `rounded-md border border-border overflow-hidden` — consistent with existing input fields
- `resize-y` with `min-h-[120px]` on the wrapper div
- Dark placeholder during loading to prevent layout shift

## Integration Points

| Location | File | Current | New |
|----------|------|---------|-----|
| Tool Function Body | `ToolProperties.tsx` | Raw `<textarea>` | `<CodeEditor language="kotlin">` |
| Condition-Node | Graph-Agent condition properties | `<textarea>` / `<Textarea>` | `<CodeEditor language="kotlin">` |
| Edge Condition | Graph-Agent edge properties | `<textarea>` / `<Textarea>` | `<CodeEditor language="kotlin">` |

**Data flow unchanged:** `value` from node/edge data in Zustand store, `onChange` calls `updateNodeData()` or edge update function.

## Dependencies

**New:**
- `@monaco-editor/react` — React wrapper for Monaco Editor

**Not needed:**
- `monaco-editor` — loaded from CDN by `@monaco-editor/react` automatically

**No impact on existing:**
- Shiki stays for CodeViewerModal (read-only viewer)
- `<Textarea>` component stays for system prompts and descriptions

## Technical Notes

- Kotlin syntax highlighting is built-in to Monaco — no extra config needed
- Monaco runtime is loaded once from CDN and cached; multiple editor instances share it
- `automaticLayout: true` handles container resizes automatically
- Controlled mode with `onChange` is fine for properties-panel-sized code; debouncing not needed initially

## Acceptance Criteria

- [ ] Tool body field shows Kotlin syntax highlighting
- [ ] Line numbers and bracket matching work
- [ ] Multi-line code is comfortable to edit (min 120px height, resizable)
- [ ] Existing body values load and save correctly
- [ ] Editor works in the dark theme
- [ ] Condition fields in Graph-Agent nodes use CodeEditor
- [ ] Condition fields on Graph-Agent edges use CodeEditor
