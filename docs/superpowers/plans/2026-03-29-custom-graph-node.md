# Custom Graph Node Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `node-custom` strategy node type with configurable input/output types and a Kotlin body code editor, generating `val name by node<I, O> { input -> body }`.

**Architecture:** Same pattern as Feature 04 — add type, Rete class, visuals, canvas, properties, backend model/generator. The properties panel is more complex: Select with "Custom..." option for types, and a CodeEditor for the body. autoTypeEdge needs store access for inputType inference.

**Tech Stack:** TypeScript/React, Kotlin/Spring, Rete.js, Monaco Editor

---

### Task 1: Extend types and add Rete node class

**Files:**
- Modify: `frontend/types/graph-agent.ts`
- Modify: `frontend/lib/strategy-rete-nodes.ts`

- [ ] **Step 1: Add node-custom type and data fields**

In `frontend/types/graph-agent.ts`:

Add `"node-custom"` to `GraphStrategyNodeType` union (after `"node-do-nothing"`):
```typescript
  | "node-custom";
```

Add 3 new fields to `GraphStrategyNodeData` (after `outputSchema`):
```typescript
  inputType?: string;
  outputType?: string;
  body?: string;
```

- [ ] **Step 2: Add CustomReteNode class**

In `frontend/lib/strategy-rete-nodes.ts`, after `DoNothingReteNode`, add:

```typescript
export class CustomReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Custom");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}
```

Add `CustomReteNode` to the `StrategyNode` type union.

- [ ] **Step 3: Commit**

```bash
git add frontend/types/graph-agent.ts frontend/lib/strategy-rete-nodes.ts
git commit -m "feat: add node-custom type, data fields, and CustomReteNode class"
```

---

### Task 2: Add visuals, toolbar, and canvas integration

**Files:**
- Modify: `frontend/components/graph-agent/StrategyCustomNode.tsx`
- Modify: `frontend/components/graph-agent/GraphStrategyToolbar.tsx`
- Modify: `frontend/components/graph-agent/GraphStrategyCanvas.tsx`

- [ ] **Step 1: Add CustomReteNode visual styling**

In `frontend/components/graph-agent/StrategyCustomNode.tsx`:

Add `CustomReteNode` to the import from `@/lib/strategy-rete-nodes`.

Add to `strategyNodeColors`:
```typescript
  "node-custom": { border: "rgba(99, 102, 241, 0.5)", glow: "rgba(99, 102, 241, 0.12)", badge: "rgba(99, 102, 241, 0.15)", badgeText: "#818cf8" },
```

Add to `strategyNodeLabels`:
```typescript
  "node-custom": "CUSTOM",
```

Add to `getStrategyNodeType` (before fallback):
```typescript
  if (data instanceof CustomReteNode) return "node-custom";
```

- [ ] **Step 2: Add toolbar button**

In `frontend/components/graph-agent/GraphStrategyToolbar.tsx`, add to `nodeButtons` array:
```typescript
  { type: "node-custom", label: "Custom", color: "text-indigo-400" },
```

- [ ] **Step 3: Add canvas createReteNode case and update autoTypeEdge**

In `frontend/components/graph-agent/GraphStrategyCanvas.tsx`:

Add `CustomReteNode` to the import from `@/lib/strategy-rete-nodes`.

Add case in `createReteNode`:
```typescript
    case "node-custom": return new CustomReteNode(sn.id, sn.data.label || "Custom");
```

Update `autoTypeEdge` — the function needs access to strategy nodes to check inputType. Replace the entire function:

```typescript
function autoTypeEdge(sourceType: string, targetType: string, targetNodeId?: string, strategyNodes?: GraphStrategyNode[]): GraphStrategyEdge["edgeType"] {
  if (sourceType === "node-llm-request" && targetType === "node-execute-tool") return "onToolCall";
  if (sourceType === "node-llm-request" && targetType === "node-execute-multiple-tools") return "onMultipleToolCalls";
  if (sourceType === "node-llm-request" && (targetType === "node-finish" || targetType === "node-condition")) return "onAssistantMessage";
  if (sourceType === "node-llm-request" && targetType === "node-custom" && targetNodeId && strategyNodes) {
    const targetNode = strategyNodes.find(sn => sn.id === targetNodeId);
    if (targetNode?.data.inputType === "String" || !targetNode?.data.inputType) return "onAssistantMessage";
  }
  return "onCondition";
}
```

Then find where `autoTypeEdge` is called (in the connectioncreated pipe) and update the call to pass the additional arguments. Find the line that calls `autoTypeEdge(sourceStrategyType, targetStrategyType)` and replace with:

```typescript
autoTypeEdge(sourceStrategyType, targetStrategyType, tgtId, propsRef.current.strategyEdges.length >= 0 ? propsRef.current.strategyNodes : undefined)
```

Wait — the function needs strategyNodes, not strategyEdges. Find the exact call site. It should be something like:

```typescript
const edgeType = autoTypeEdge(sourceNode.type, targetNode.type);
```

But the strategy node types aren't directly on Rete nodes. Read the connectioncreated pipe to find how it resolves types. The Rete nodes have `strategyNodeId` but not the strategy type. The strategy type comes from `propsRef.current.strategyNodes`. Update the call:

```typescript
const sourceStrategy = propsRef.current.strategyNodes.find(sn => sn.id === srcId);
const targetStrategy = propsRef.current.strategyNodes.find(sn => sn.id === tgtId);
const edgeType = autoTypeEdge(
  sourceStrategy?.type || "",
  targetStrategy?.type || "",
  tgtId,
  propsRef.current.strategyNodes
);
```

**Important:** Read the file first to find the exact call site and adapt accordingly.

- [ ] **Step 4: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add frontend/components/graph-agent/StrategyCustomNode.tsx frontend/components/graph-agent/GraphStrategyToolbar.tsx frontend/components/graph-agent/GraphStrategyCanvas.tsx
git commit -m "feat: add custom node visuals, toolbar button, and canvas integration"
```

---

### Task 3: Add custom node properties panel

**Files:**
- Modify: `frontend/components/graph-agent/StrategyNodeProperties.tsx`

- [ ] **Step 1: Add the custom node properties block**

In `frontend/components/graph-agent/StrategyNodeProperties.tsx`, add `useState` to the React import at the top of the file (if not already imported):

```typescript
import { useState } from "react";
```

After the `node-llm-structured` block (around line 105), add the custom node properties:

```tsx
      {node.type === "node-custom" && (
        <>
          <TypeSelect
            label="Input Type"
            value={d.inputType ?? "String"}
            onChange={(v) => onUpdate({ inputType: v })}
          />
          <TypeSelect
            label="Output Type"
            value={d.outputType ?? "String"}
            onChange={(v) => onUpdate({ outputType: v })}
          />
          <div>
            <Label>Body</Label>
            <CodeEditor
              value={d.body ?? ""}
              onChange={(v) => onUpdate({ body: v })}
              height="200px"
            />
          </div>
        </>
      )}
```

Add the `TypeSelect` helper component at the bottom of the file (before the closing of the module, or as a separate function before the main component):

```tsx
const TYPE_OPTIONS = ["String", "Message.Response", "ReceivedToolResult", "List<Message.Response>", "Any"];

function TypeSelect({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const isCustom = !TYPE_OPTIONS.includes(value) && value !== "";
  const [showCustom, setShowCustom] = useState(isCustom);

  return (
    <div>
      <Label>{label}</Label>
      <Select
        value={showCustom ? "__custom__" : value}
        onValueChange={(v) => {
          if (v === "__custom__") {
            setShowCustom(true);
            onChange("");
          } else {
            setShowCustom(false);
            onChange(v);
          }
        }}
      >
        <SelectTrigger><SelectValue /></SelectTrigger>
        <SelectContent>
          {TYPE_OPTIONS.map((t) => (
            <SelectItem key={t} value={t}>{t}</SelectItem>
          ))}
          <SelectItem value="__custom__">Custom...</SelectItem>
        </SelectContent>
      </Select>
      {showCustom && (
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="e.g. MyDataClass"
          className="mt-1 h-7 text-xs"
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/StrategyNodeProperties.tsx
git commit -m "feat: add custom node properties with type select and code editor"
```

---

### Task 4: Update backend model, adapter, and generator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`

- [ ] **Step 1: Add fields to KoogStrategyNode**

In `GraphAgentModel.kt`, add 3 fields to `KoogStrategyNode` (after `outputSchema`):

```kotlin
    val inputType: String?,
    val outputType: String?,
    val body: String?
```

- [ ] **Step 2: Extract new fields in adapter**

In `GraphToKoogAdapter.kt`, in the `KoogStrategyNode` construction block, add after `outputSchema`:

```kotlin
                    inputType = snData["inputType"] as? String,
                    outputType = snData["outputType"] as? String,
                    body = snData["body"] as? String
```

- [ ] **Step 3: Add code generation for node-custom**

In `GraphAgentGenerator.kt`:

Add import (after existing node type imports):
```kotlin
            if ("node-custom" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.builder.node")
```

Add node declaration in the `when (sn.type)` block:
```kotlin
                    "node-custom" -> {
                        val inputT = sn.inputType?.ifBlank { null } ?: "String"
                        val outputT = sn.outputType?.ifBlank { null } ?: "String"
                        val body = sn.body ?: ""
                        appendLine("    val $varName by node<$inputT, $outputT> { input ->")
                        appendLine("        $body")
                        appendLine("    }")
                    }
```

- [ ] **Step 4: Fix tests — update KoogStrategyNode constructor calls**

The existing tests in `GraphAgentGeneratorTest.kt` use positional arguments for `KoogStrategyNode`. Add the 3 new `null` arguments to all existing test calls. Find each `KoogStrategyNode(...)` call and add `, null, null, null` after the last current argument (which is `outputSchema`).

- [ ] **Step 5: Verify build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt
git commit -m "feat: add custom node code generation with input/output types and body"
```

---

### Task 5: Add validation and build verification

**Files:**
- Modify: `frontend/lib/graph-agent-validation.ts`

- [ ] **Step 1: Add warning for custom nodes without body**

In `frontend/lib/graph-agent-validation.ts`, after the `node-execute-multiple-tools` validation, add:

```typescript
    if (sn.type === "node-custom" && !sn.data?.body?.trim()) {
      errors.push({ nodeId, severity: "warning", message: `Custom node "${sn.data?.label || sn.id}" has no body code` });
    }
```

- [ ] **Step 2: Run full frontend build**

```bash
cd frontend && npx next build
```

- [ ] **Step 3: Run full backend build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/graph-agent-validation.ts
git commit -m "feat: add validation warning for custom nodes without body"
```
