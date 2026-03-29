# Edge-Transformation UI

**Date:** 2026-03-29
**Feature:** 03 — Edge-Transformation im UI
**Status:** Design approved

## Problem

The GraphAgentGenerator already supports `transformed { ... }` on edges, but the UI has no way to select edges or edit the transformation field. Currently, clicking an edge deletes it.

## Solution

Change edge click behavior from delete to select. Show edge properties (type, condition, transformation) in the Properties Panel. Add a delete button in the Properties Panel.

## Changes

### 1. StrategyCustomConnection (edge rendering)

- Single-click fires `strategy-edge-select` event (with edge source/target IDs) instead of `strategy-edge-delete`
- Hover color changes from red to a neutral highlight (indigo/blue)
- Edge ID is passed in the event detail

### 2. GraphStrategyCanvas

- New event listener for `strategy-edge-select` → calls `onEdgeSelected(edgeId)` callback
- Maps Rete source/target node IDs to strategy edge IDs to find the correct edge

### 3. GraphStrategyEditor

- Already has `selectedStrategyEdgeId` state and `handleEdgeSelected` handler
- Already renders `StrategyEdgeProperties` when edge is selected
- Passes `onEdgeSelected` callback to Canvas
- Click on canvas background deselects edge (same as nodes)

### 4. StrategyEdgeProperties

- **Edge Type**: shown as readonly for all edge types
- **Condition**: CodeEditor, only shown for `onCondition` edges (unchanged)
- **Transformation**: CodeEditor, shown for **all** edge types (moved outside the `onCondition` block)
- **Delete button**: at the bottom of the panel, calls `onDelete` callback

### 5. Deselection

- Clicking canvas background clears both node and edge selection
- Selecting a node clears edge selection (and vice versa) — already implemented

## Files Changed

| File | Change |
|------|--------|
| `frontend/components/graph-agent/StrategyCustomNode.tsx` | Change click from delete to select event, update hover color |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | Add `strategy-edge-select` listener, add `onEdgeSelected` prop |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Pass `onEdgeSelected` to Canvas, add `onDelete` handler for edges |
| `frontend/components/graph-agent/StrategyEdgeProperties.tsx` | Move transformation outside `onCondition` block, add delete button |

## Backend

No changes needed — `GraphAgentGenerator.formatEdgeSuffix()` already supports `transformation`.

## Acceptance Criteria

- [ ] Edge in graph editor selectable by click
- [ ] Properties Panel shows edge type, condition (for onCondition), and transformation (for all types)
- [ ] Transformation editable for all edge types
- [ ] Delete button in Properties Panel removes the edge
- [ ] Canvas background click deselects edge
- [ ] Generated code contains `onCondition { ... } transformed { ... }` correctly
