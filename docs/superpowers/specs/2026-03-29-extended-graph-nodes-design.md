# Extended Graph Node Types

**Date:** 2026-03-29
**Feature:** 04 — Extended Graph Node Types
**Status:** Design approved

## Problem

The graph editor supports only 6 strategy node types. Koog provides additional predefined nodes needed for realistic agent strategies.

## Solution

Add 6 new node types, 1 new edge type, following the established pattern for each.

## New Node Types

| Node Type | Koog Function | Color | Properties | Label |
|-----------|--------------|-------|------------|-------|
| `node-execute-multiple-tools` | `nodeExecuteMultipleTools()` | Teal (14, 185, 129 → shifted) | `label`, `toolName` | EXECUTE MULTI TOOLS |
| `node-send-multiple-results` | `nodeLLMSendMultipleToolResults()` | Amber (245, 158, 11) | `label` | SEND MULTI RESULTS |
| `node-compress-history` | `nodeLLMCompressHistory()` | Slate (148, 163, 184) | `label`, `compressionStrategy` | COMPRESS HISTORY |
| `node-llm-streaming` | `nodeLLMRequestStreaming()` | Cyan (34, 211, 238) | `label` | LLM STREAMING |
| `node-llm-structured` | `nodeLLMRequestStructured()` | Rose (244, 63, 94) | `label`, `outputSchema` | LLM STRUCTURED |
| `node-do-nothing` | `nodeDoNothing()` | Gray (156, 163, 175) | `label` | DO NOTHING |

## New Edge Type

`onMultipleToolCalls` — used when source is `node-llm-request` and target is `node-execute-multiple-tools`.

### autoTypeEdge() Updates

```
node-llm-request → node-execute-multiple-tools → onMultipleToolCalls
node-llm-request → node-finish/node-condition → onAssistantMessage (unchanged)
node-llm-request → node-execute-tool → onToolCall (unchanged)
```

All other combinations remain `onCondition`.

## Node Properties

### `node-execute-multiple-tools`
- `label`: text input
- `toolName`: dropdown (same as `node-execute-tool`, connected tools)

### `node-compress-history`
- `label`: text input
- `compressionStrategy`: text input (e.g. "summarize", "truncate"), passed as string argument to `nodeLLMCompressHistory("strategy")`

### `node-llm-structured`
- `label`: text input
- `outputSchema`: CodeEditor (Kotlin class name or type expression), passed to `nodeLLMRequestStructured<Type>()`

### All others
- `label`: text input only

## Per Node: Implementation Pattern

Each node requires changes in 13 locations:

### Frontend
1. `frontend/types/graph-agent.ts` — add to `GraphStrategyNodeType` union
2. `frontend/types/graph-agent.ts` — add fields to `GraphStrategyNodeData` if needed
3. `frontend/lib/strategy-rete-nodes.ts` — new Rete node class (input + output, 180x80)
4. `frontend/components/graph-agent/GraphStrategyCanvas.tsx` — `createReteNode()` case
5. `frontend/components/graph-agent/GraphStrategyCanvas.tsx` — `autoTypeEdge()` update (if needed)
6. `frontend/components/graph-agent/GraphStrategyToolbar.tsx` — toolbar button
7. `frontend/components/graph-agent/StrategyCustomNode.tsx` — colors + label
8. `frontend/components/graph-agent/StrategyNodeProperties.tsx` — property fields (if needed)
9. `frontend/lib/graph-agent-validation.ts` — validation rules (if needed)

### Backend
10. `export/model/GraphAgentModel.kt` — add fields to `KoogStrategyNode` if needed
11. `export/adapter/GraphToKoogAdapter.kt` — extract new fields
12. `export/generator/GraphAgentGenerator.kt` — node declaration + import
13. `export/validation/GraphValidator.kt` — validation rules (if needed)

## Edge Type Addition

### Frontend
- Add `"onMultipleToolCalls"` to `GraphEdgeType` union in `graph-agent.ts`
- Add to `StrategyEdgeProperties` select options

### Backend
- Add `onMultipleToolCalls` handling in `formatEdgeSuffix()` in `GraphAgentGenerator.kt`
- Add import: `import ai.koog.agents.core.dsl.extension.onMultipleToolCalls`

## Files Changed

| File | Change |
|------|--------|
| `frontend/types/graph-agent.ts` | 6 new node types, 1 new edge type, new data fields |
| `frontend/lib/strategy-rete-nodes.ts` | 6 new Rete node classes |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | `createReteNode()` + `autoTypeEdge()` |
| `frontend/components/graph-agent/GraphStrategyToolbar.tsx` | 6 new toolbar buttons |
| `frontend/components/graph-agent/StrategyCustomNode.tsx` | 6 new color/label entries |
| `frontend/components/graph-agent/StrategyNodeProperties.tsx` | Properties for compress-history, llm-structured, execute-multiple-tools |
| `frontend/components/graph-agent/StrategyEdgeProperties.tsx` | onMultipleToolCalls in edge type select |
| `frontend/lib/graph-agent-validation.ts` | Validation for new required fields |
| `export/model/GraphAgentModel.kt` | `compressionStrategy`, `outputSchema` fields |
| `export/adapter/GraphToKoogAdapter.kt` | Extract new fields |
| `export/generator/GraphAgentGenerator.kt` | 6 new node declarations + imports, 1 new edge type |
| `export/validation/GraphValidator.kt` | Validation for new required fields |

## Acceptance Criteria

- [ ] All 6 new node types placeable in graph editor via toolbar
- [ ] Each node has correct color, label, and properties
- [ ] `onMultipleToolCalls` edge type auto-detected for execute-multiple-tools
- [ ] Generated Koog code uses correct function names and imports
- [ ] `node-compress-history` has configurable strategy
- [ ] `node-llm-structured` has configurable output schema
- [ ] Backend build + tests pass
- [ ] Frontend build passes
