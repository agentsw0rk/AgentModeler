# Toolbar Restructure

**Date:** 2026-03-29
**Feature:** Toolbar Navigation Restructure
**Status:** Design approved

## Problem

Both toolbars (main canvas and strategy canvas) have grown organically with many flat buttons. The main toolbar mixes dropdown and standalone buttons inconsistently. The strategy toolbar has 12 flat buttons that are hard to scan.

## Solution

Group toolbar items into dropdown menus using the existing `NavigationMenu` component pattern.

## Main Canvas Toolbar (Toolbar.tsx)

### Before

Agent (button), Tools (dropdown), Sub-Agent (button), MCP Server (button), Planner Agent (button), Input (button), Output (button), Graph Agent (button), Code (button)

### After — 3 Dropdown Groups

| Group | Icon | Color | Items |
|-------|------|-------|-------|
| Agents | BotIcon | blue-400 | Agent, Sub-Agent, Planner Agent, Graph Agent (opens template dialog) |
| Tools | WrenchIcon | green-400 | Custom Tool, separator, Think, Shell, Filesystem, Fetch, Memory, Todo, User Prompt |
| Resources | ServerIcon | purple-400 | MCP Server, Input, Output, Code |

- Tools group stays exactly as-is (already a dropdown)
- Agents group replaces 4 separate buttons with one dropdown
- Resources group replaces 4 separate buttons with one dropdown
- Graph Agent item in Agents dropdown opens the existing template dialog (same behavior)

## Strategy Canvas Toolbar (GraphStrategyToolbar.tsx)

### Before

12 flat buttons in a row

### After — 3 Dropdown Groups

| Group | Color | Items |
|-------|-------|-------|
| LLM | blue-400 | LLM Request, LLM Streaming, LLM Structured |
| Tools | emerald-400 | Execute Tool, Execute Multi Tools, Send Result, Send Multi Results |
| Flow | purple-400 | Condition, Compress History, Do Nothing, Custom, Subgraph |

- Uses `NavigationMenu` components (new import for this file)
- `hideSubgraph` prop still filters Subgraph from the Flow group
- Each item in the dropdown calls `onAddNode(type)` on click

## Files Changed

| File | Change |
|------|--------|
| `frontend/components/panels/Toolbar.tsx` | Replace flat Agent/MCP/Planner/Input/Output/Graph Agent/Code buttons with Agents and Resources dropdowns |
| `frontend/components/graph-agent/GraphStrategyToolbar.tsx` | Replace 12 flat buttons with 3 dropdown groups using NavigationMenu |

## No Backend Changes

This is a pure UI restructure. No data model, store, or export changes.

## Acceptance Criteria

- [ ] Main toolbar shows 3 dropdown groups: Agents, Tools, Resources
- [ ] Agents dropdown contains Agent, Sub-Agent, Planner Agent, Graph Agent
- [ ] Graph Agent in Agents dropdown still opens template dialog
- [ ] Tools dropdown unchanged (Custom Tool + builtin tools)
- [ ] Resources dropdown contains MCP Server, Input, Output, Code
- [ ] Strategy toolbar shows 3 dropdown groups: LLM, Tools, Flow
- [ ] hideSubgraph still works in strategy toolbar
- [ ] All addNode calls produce correct node types
