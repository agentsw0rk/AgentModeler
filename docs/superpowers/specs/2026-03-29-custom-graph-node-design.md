# Custom Node im Graph-Editor

**Date:** 2026-03-29
**Feature:** 05 — Custom Node im Graph-Editor
**Status:** Design approved

## Problem

Koog supports custom nodes via `node<Input, Output> { ... }` for arbitrary Kotlin logic (data validation, format conversion, intent classification). The graph editor doesn't offer this yet.

## Solution

New node type `node-custom` with configurable input type, output type, and Kotlin body code.

## Data Model

New fields in `GraphStrategyNodeData`:
- `inputType?: string` — e.g. "String", "Message.Response", or custom type
- `outputType?: string` — same options
- `body?: string` — Kotlin lambda body

New fields in `KoogStrategyNode`:
- `inputType: String?`
- `outputType: String?`
- `body: String?`

## Properties Panel

| Field | Component | Description |
|-------|-----------|-------------|
| Label | Input | Node name → Kotlin variable |
| Input Type | Select + custom option | `String`, `Message.Response`, `ReceivedToolResult`, `List<Message.Response>`, `Any`, or "Custom..." (shows free-text Input) |
| Output Type | Select + custom option | Same options as Input Type |
| Body | CodeEditor (Monaco) | Kotlin lambda body, height 200px |

### Type Select Options

Predefined: `String`, `Message.Response`, `ReceivedToolResult`, `List<Message.Response>`, `Any`

When "Custom..." is selected, a text Input appears below for arbitrary Kotlin type expressions.

## Visual

- Rete class: `CustomReteNode` (200x80, input + output)
- Color: Indigo (`rgba(99, 102, 241, 0.5)`)
- Label: `CUSTOM`
- Subtitle: label (node name)
- Toolbar button: "Custom" with `text-indigo-400`

## autoTypeEdge() Logic

```
node-llm-request → node-custom (inputType === "String") → onAssistantMessage
node-llm-request → node-custom (other inputTypes) → onCondition
all other → node-custom → onCondition
```

The inference reads `inputType` from the target custom node's data in the strategy nodes array.

## Backend Code Generation

Generated code pattern:
```kotlin
val classifyIntent by node<String, String> { input ->
    // body
}
```

- Import: `import ai.koog.agents.core.dsl.builder.node`
- Input/output types default to `String` if not specified
- Body is inserted as-is after `{ input ->`

## Validation

- Custom node without `body` → Warning (not error, can be placeholder)
- Custom node without `inputType`/`outputType` → defaults to "String"

## Files Changed

### Frontend

| File | Change |
|------|--------|
| `frontend/types/graph-agent.ts` | Add `"node-custom"` to NodeType, add `inputType`, `outputType`, `body` to data |
| `frontend/lib/strategy-rete-nodes.ts` | Add `CustomReteNode` class |
| `frontend/components/graph-agent/StrategyCustomNode.tsx` | Indigo color, CUSTOM label, type detection |
| `frontend/components/graph-agent/GraphStrategyToolbar.tsx` | Custom button |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | `createReteNode()` + `autoTypeEdge()` |
| `frontend/components/graph-agent/StrategyNodeProperties.tsx` | Input/Output type selects + body CodeEditor |
| `frontend/lib/graph-agent-validation.ts` | Warning for empty body |

### Backend

| File | Change |
|------|--------|
| `export/model/GraphAgentModel.kt` | Add `inputType`, `outputType`, `body` to `KoogStrategyNode` |
| `export/adapter/GraphToKoogAdapter.kt` | Extract new fields |
| `export/generator/GraphAgentGenerator.kt` | `node-custom` declaration + import |

## Acceptance Criteria

- [ ] Custom node placeable in graph editor
- [ ] Input type and output type configurable (select + custom option)
- [ ] Kotlin body editable in CodeEditor
- [ ] Generated code produces `val name by node<I, O> { input -> body }`
- [ ] Edge types between custom nodes and predefined nodes correctly inferred
- [ ] Import `ai.koog.agents.core.dsl.builder.node` generated when custom node present
