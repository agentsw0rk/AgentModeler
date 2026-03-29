# Shared Code Node

**Date:** 2026-03-29
**Feature:** 02 — Shared Code Node
**Status:** Design approved

## Problem

In the Banking Tutorial, multiple tools reference shared data (`contactList`, `contactById`) and domain classes (`data class Contact`). The modeler has no concept for code shared between tools.

## Solution

A new node type **"code"** on the canvas. Its content is exported as a separate Kotlin file in the `generated` package, available to all connected tools.

## Data Model

### Frontend — `CodeNodeConfig`

```typescript
interface CodeNodeConfig {
  fileName: string;  // e.g. "Domain" → becomes Domain.kt
  code: string;      // Free-form Kotlin code
}
```

### Backend — `KoogSharedCode`

```kotlin
data class KoogSharedCode(
  val name: String,   // File name without .kt
  val code: String
)
```

## Connection Rules

- `code` → `agent` (direct)
- `code` → `tool` (associates code with the tool's agent)
- NOT allowed: `code` → `code`, `code` → `input`, `code` → `output`, `code` �� `sub-agent`, `code` → `mcp-server`
- A code node can connect to multiple agents — the `.kt` file is generated once (same `generated` package namespace)

## Frontend

### Rete Node: `CodeReteNode`

- Output: `outputs` — connects to Agent/Tool inputs
- No inputs — code node is a source
- Width/height: same as other nodes (200x120)

### Visuals (CustomNode.tsx)

- Color: Purple/Violet (`rgba(168, 85, 247, 0.4)` border, matching glow/badge)
- Icon: `CodeIcon` from lucide-react
- Label: `CODE`
- Subtitle: fileName (e.g. "Domain")

### Properties Panel: `CodeProperties.tsx`

- `fileName`: `<Input>` with placeholder "Domain"
- `code`: `<CodeEditor>` (Monaco from Feature 01) with `height="300px"`

### Toolbar

- New button "+ Code" in the menu, `CodeIcon`, purple accent

### EditorStore Default

```typescript
const defaultCodeData: CodeNodeConfig = {
  fileName: "",
  code: "",
};
```

## Backend Export

### GraphToKoogAdapter

- Filters `code` nodes from the graph
- Converts to `KoogSharedCode(name, code)`
- No per-agent assignment needed — all code nodes land in the same `generated` package

### SharedCodeGenerator (new)

- Input: `KoogSharedCode`
- Output: file `src/main/kotlin/generated/{Name}.kt`
- Template:

```kotlin
package generated

{code}
```

Simply `package generated` + blank line + code as-is.

### ExportService

- Calls `adapter.convertSharedCode()`
- Iterates over all `KoogSharedCode` and generates one file each
- Adds files to the ZIP

### Validation

- Code node without `fileName` → Error
- Duplicate `fileName` across code nodes → Error
- Code node without any connection → Warning (not blocking)

## Files Changed

### Frontend

| File | Change |
|------|--------|
| `frontend/types/graph.ts` | Add `"code"` to NodeType, add `CodeNodeConfig` interface, add to data union |
| `frontend/lib/rete-nodes.ts` | Add `CodeReteNode` class |
| `frontend/lib/rete-setup.ts` | Add connection rules for code nodes |
| `frontend/components/editor/Canvas.tsx` | Add code node instantiation + connection validation |
| `frontend/components/editor/CustomNode.tsx` | Add purple color, CodeIcon, "CODE" label, subtitle |
| `frontend/components/panels/Toolbar.tsx` | Add "+ Code" button |
| `frontend/components/panels/CodeProperties.tsx` | New: properties panel for code node |
| `frontend/components/panels/PropertiesPanel.tsx` | Add dispatch for code node type |
| `frontend/store/editorStore.ts` | Add default code node data |

### Backend

| File | Change |
|------|--------|
| `export/model/KoogModel.kt` | Add `KoogSharedCode` data class |
| `export/adapter/GraphToKoogAdapter.kt` | Add `convertSharedCode()` method |
| `export/generator/SharedCodeGenerator.kt` | New: generates `{Name}.kt` files |
| `export/ExportService.kt` | Add shared code generation + validation to export pipeline |

## Acceptance Criteria

- [ ] Code node placeable on canvas and connectable to Agent and Tool
- [ ] Free-form Kotlin code editable in CodeEditor
- [ ] Export generates separate `.kt` file with the code
- [ ] Tools in the same agent can reference the code (same package)
- [ ] Banking Tutorial `contactList`/`contactById` reproducible
- [ ] Validation: missing fileName → error, duplicate fileName → error, no connection → warning
