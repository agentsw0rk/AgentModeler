# Extended Graph Node Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 6 new strategy node types and 1 new edge type to the graph editor, covering multi-tool execution, history compression, streaming, structured output, and do-nothing nodes.

**Architecture:** Layer-by-layer approach — update types first, then Rete nodes, then visuals/toolbar, then canvas/properties, then backend model/generator/validation. Each layer touches one or two files, adding all 6 nodes at once.

**Tech Stack:** TypeScript/React (frontend), Kotlin/Spring (backend), Rete.js (visual editor)

---

### Task 1: Extend frontend type system

**Files:**
- Modify: `frontend/types/graph-agent.ts`

- [ ] **Step 1: Add 6 new node types, 1 new edge type, and new data fields**

In `frontend/types/graph-agent.ts`, replace the type definitions:

Find the `GraphStrategyNodeType` union (lines 1-7):
```typescript
export type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition";
```

Replace with:
```typescript
export type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition"
  | "node-execute-multiple-tools"
  | "node-send-multiple-results"
  | "node-compress-history"
  | "node-llm-streaming"
  | "node-llm-structured"
  | "node-do-nothing";
```

Find `GraphStrategyNodeData` (lines 16-21):
```typescript
export interface GraphStrategyNodeData {
  label?: string;
  toolName?: string;
  condition?: string;
  temperature?: number;
}
```

Replace with:
```typescript
export interface GraphStrategyNodeData {
  label?: string;
  toolName?: string;
  condition?: string;
  temperature?: number;
  compressionStrategy?: string;
  outputSchema?: string;
}
```

Find `GraphEdgeType` (line 23):
```typescript
export type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition";
```

Replace with:
```typescript
export type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition" | "onMultipleToolCalls";
```

- [ ] **Step 2: Commit**

```bash
git add frontend/types/graph-agent.ts
git commit -m "feat: add 6 new strategy node types and onMultipleToolCalls edge type"
```

---

### Task 2: Add 6 new Rete node classes

**Files:**
- Modify: `frontend/lib/strategy-rete-nodes.ts`

- [ ] **Step 1: Add all 6 new node classes and update StrategyNode union**

In `frontend/lib/strategy-rete-nodes.ts`, after `ConditionReteNode` (line 68), add:

```typescript
export class ExecuteMultipleToolsReteNode extends ClassicPreset.Node {
  width = 220;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Execute Multi Tools");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class SendMultipleResultsReteNode extends ClassicPreset.Node {
  width = 220;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Send Multi Results");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class CompressHistoryReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Compress History");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class LLMStreamingReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Streaming");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class LLMStructuredReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Structured");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class DoNothingReteNode extends ClassicPreset.Node {
  width = 160;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Do Nothing");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}
```

Replace the `StrategyNode` type union (line 70):

Find:
```typescript
export type StrategyNode = StartReteNode | FinishReteNode | LLMRequestReteNode | ExecuteToolReteNode | SendToolResultReteNode | ConditionReteNode;
```

Replace with:
```typescript
export type StrategyNode = StartReteNode | FinishReteNode | LLMRequestReteNode | ExecuteToolReteNode | SendToolResultReteNode | ConditionReteNode | ExecuteMultipleToolsReteNode | SendMultipleResultsReteNode | CompressHistoryReteNode | LLMStreamingReteNode | LLMStructuredReteNode | DoNothingReteNode;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/strategy-rete-nodes.ts
git commit -m "feat: add 6 new Rete node classes for strategy editor"
```

---

### Task 3: Add visuals (colors, labels, type detection)

**Files:**
- Modify: `frontend/components/graph-agent/StrategyCustomNode.tsx`

- [ ] **Step 1: Add imports for new Rete node classes**

Add the 6 new classes to the import from `@/lib/strategy-rete-nodes` (lines 6-13):

```typescript
import {
  StartReteNode,
  FinishReteNode,
  LLMRequestReteNode,
  ExecuteToolReteNode,
  SendToolResultReteNode,
  ConditionReteNode,
  ExecuteMultipleToolsReteNode,
  SendMultipleResultsReteNode,
  CompressHistoryReteNode,
  LLMStreamingReteNode,
  LLMStructuredReteNode,
  DoNothingReteNode,
} from "@/lib/strategy-rete-nodes";
```

- [ ] **Step 2: Add colors for 6 new node types**

Add to `strategyNodeColors` (after line 22):

```typescript
  "node-execute-multiple-tools": { border: "rgba(13, 148, 136, 0.5)", glow: "rgba(13, 148, 136, 0.12)", badge: "rgba(13, 148, 136, 0.15)", badgeText: "#2dd4bf" },
  "node-send-multiple-results":  { border: "rgba(245, 158, 11, 0.5)", glow: "rgba(245, 158, 11, 0.12)", badge: "rgba(245, 158, 11, 0.15)", badgeText: "#fbbf24" },
  "node-compress-history":       { border: "rgba(148, 163, 184, 0.5)", glow: "rgba(148, 163, 184, 0.12)", badge: "rgba(148, 163, 184, 0.15)", badgeText: "#94a3b8" },
  "node-llm-streaming":          { border: "rgba(34, 211, 238, 0.5)", glow: "rgba(34, 211, 238, 0.12)", badge: "rgba(34, 211, 238, 0.15)", badgeText: "#22d3ee" },
  "node-llm-structured":         { border: "rgba(244, 63, 94, 0.5)", glow: "rgba(244, 63, 94, 0.12)", badge: "rgba(244, 63, 94, 0.15)", badgeText: "#fb7185" },
  "node-do-nothing":             { border: "rgba(156, 163, 175, 0.5)", glow: "rgba(156, 163, 175, 0.12)", badge: "rgba(156, 163, 175, 0.15)", badgeText: "#9ca3af" },
```

- [ ] **Step 3: Add labels for 6 new node types**

Add to `strategyNodeLabels` (after line 31):

```typescript
  "node-execute-multiple-tools": "EXEC MULTI TOOLS",
  "node-send-multiple-results": "SEND MULTI RESULTS",
  "node-compress-history": "COMPRESS HISTORY",
  "node-llm-streaming": "LLM STREAMING",
  "node-llm-structured": "LLM STRUCTURED",
  "node-do-nothing": "DO NOTHING",
```

- [ ] **Step 4: Add type detection for 6 new nodes**

Add to `getStrategyNodeType` (before the `return "node-start"` fallback, around line 41):

```typescript
  if (data instanceof ExecuteMultipleToolsReteNode) return "node-execute-multiple-tools";
  if (data instanceof SendMultipleResultsReteNode) return "node-send-multiple-results";
  if (data instanceof CompressHistoryReteNode) return "node-compress-history";
  if (data instanceof LLMStreamingReteNode) return "node-llm-streaming";
  if (data instanceof LLMStructuredReteNode) return "node-llm-structured";
  if (data instanceof DoNothingReteNode) return "node-do-nothing";
```

- [ ] **Step 5: Commit**

```bash
git add frontend/components/graph-agent/StrategyCustomNode.tsx
git commit -m "feat: add colors, labels, and type detection for 6 new strategy nodes"
```

---

### Task 4: Add toolbar buttons

**Files:**
- Modify: `frontend/components/graph-agent/GraphStrategyToolbar.tsx`

- [ ] **Step 1: Add 6 new buttons to the nodeButtons array**

In `frontend/components/graph-agent/GraphStrategyToolbar.tsx`, replace the `nodeButtons` array (lines 6-11):

Find:
```typescript
const nodeButtons: { type: GraphStrategyNodeType; label: string; color: string }[] = [
  { type: "node-llm-request", label: "LLM Request", color: "text-blue-400" },
  { type: "node-execute-tool", label: "Execute Tool", color: "text-emerald-400" },
  { type: "node-llm-send-tool-result", label: "Send Tool Result", color: "text-yellow-400" },
  { type: "node-condition", label: "Condition", color: "text-purple-400" },
];
```

Replace with:
```typescript
const nodeButtons: { type: GraphStrategyNodeType; label: string; color: string }[] = [
  { type: "node-llm-request", label: "LLM Request", color: "text-blue-400" },
  { type: "node-execute-tool", label: "Execute Tool", color: "text-emerald-400" },
  { type: "node-llm-send-tool-result", label: "Send Result", color: "text-yellow-400" },
  { type: "node-condition", label: "Condition", color: "text-purple-400" },
  { type: "node-execute-multiple-tools", label: "Exec Multi Tools", color: "text-teal-400" },
  { type: "node-send-multiple-results", label: "Send Multi Results", color: "text-amber-400" },
  { type: "node-compress-history", label: "Compress History", color: "text-slate-400" },
  { type: "node-llm-streaming", label: "LLM Streaming", color: "text-cyan-400" },
  { type: "node-llm-structured", label: "LLM Structured", color: "text-rose-400" },
  { type: "node-do-nothing", label: "Do Nothing", color: "text-gray-400" },
];
```

- [ ] **Step 2: Commit**

```bash
git add frontend/components/graph-agent/GraphStrategyToolbar.tsx
git commit -m "feat: add toolbar buttons for 6 new strategy node types"
```

---

### Task 5: Update Canvas — createReteNode + autoTypeEdge

**Files:**
- Modify: `frontend/components/graph-agent/GraphStrategyCanvas.tsx`

- [ ] **Step 1: Add imports for new Rete node classes**

Add the 6 new classes to the import from `@/lib/strategy-rete-nodes` (lines 7-9):

```typescript
import {
  StartReteNode, FinishReteNode, LLMRequestReteNode,
  ExecuteToolReteNode, SendToolResultReteNode, ConditionReteNode,
  ExecuteMultipleToolsReteNode, SendMultipleResultsReteNode,
  CompressHistoryReteNode, LLMStreamingReteNode,
  LLMStructuredReteNode, DoNothingReteNode,
} from "@/lib/strategy-rete-nodes";
```

- [ ] **Step 2: Update autoTypeEdge**

Find `autoTypeEdge` (lines 25-29):
```typescript
function autoTypeEdge(sourceType: string, targetType: string): GraphStrategyEdge["edgeType"] {
  if (sourceType === "node-llm-request" && targetType === "node-execute-tool") return "onToolCall";
  if (sourceType === "node-llm-request" && (targetType === "node-finish" || targetType === "node-condition")) return "onAssistantMessage";
  return "onCondition";
}
```

Replace with:
```typescript
function autoTypeEdge(sourceType: string, targetType: string): GraphStrategyEdge["edgeType"] {
  if (sourceType === "node-llm-request" && targetType === "node-execute-tool") return "onToolCall";
  if (sourceType === "node-llm-request" && targetType === "node-execute-multiple-tools") return "onMultipleToolCalls";
  if (sourceType === "node-llm-request" && (targetType === "node-finish" || targetType === "node-condition")) return "onAssistantMessage";
  return "onCondition";
}
```

- [ ] **Step 3: Update createReteNode**

Find `createReteNode` (lines 166-175):
```typescript
function createReteNode(sn: GraphStrategyNode) {
  switch (sn.type) {
    case "node-start": return new StartReteNode(sn.id);
    case "node-finish": return new FinishReteNode(sn.id);
    case "node-llm-request": return new LLMRequestReteNode(sn.id, sn.data.label || "LLM Request");
    case "node-execute-tool": return new ExecuteToolReteNode(sn.id, sn.data.label || "Execute Tool");
    case "node-llm-send-tool-result": return new SendToolResultReteNode(sn.id, sn.data.label || "Send Tool Result");
    case "node-condition": return new ConditionReteNode(sn.id, sn.data.label || "Condition");
  }
}
```

Replace with:
```typescript
function createReteNode(sn: GraphStrategyNode) {
  switch (sn.type) {
    case "node-start": return new StartReteNode(sn.id);
    case "node-finish": return new FinishReteNode(sn.id);
    case "node-llm-request": return new LLMRequestReteNode(sn.id, sn.data.label || "LLM Request");
    case "node-execute-tool": return new ExecuteToolReteNode(sn.id, sn.data.label || "Execute Tool");
    case "node-llm-send-tool-result": return new SendToolResultReteNode(sn.id, sn.data.label || "Send Tool Result");
    case "node-condition": return new ConditionReteNode(sn.id, sn.data.label || "Condition");
    case "node-execute-multiple-tools": return new ExecuteMultipleToolsReteNode(sn.id, sn.data.label || "Execute Multi Tools");
    case "node-send-multiple-results": return new SendMultipleResultsReteNode(sn.id, sn.data.label || "Send Multi Results");
    case "node-compress-history": return new CompressHistoryReteNode(sn.id, sn.data.label || "Compress History");
    case "node-llm-streaming": return new LLMStreamingReteNode(sn.id, sn.data.label || "LLM Streaming");
    case "node-llm-structured": return new LLMStructuredReteNode(sn.id, sn.data.label || "LLM Structured");
    case "node-do-nothing": return new DoNothingReteNode(sn.id, sn.data.label || "Do Nothing");
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/graph-agent/GraphStrategyCanvas.tsx
git commit -m "feat: add createReteNode cases and onMultipleToolCalls auto-edge-typing"
```

---

### Task 6: Add node properties and edge type option

**Files:**
- Modify: `frontend/components/graph-agent/StrategyNodeProperties.tsx`
- Modify: `frontend/components/graph-agent/StrategyEdgeProperties.tsx`

- [ ] **Step 1: Add properties for execute-multiple-tools, compress-history, and llm-structured**

In `frontend/components/graph-agent/StrategyNodeProperties.tsx`, after the `node-execute-tool` block (line 48), add:

```typescript
      {node.type === "node-execute-multiple-tools" && (
        <div>
          <Label>Tool</Label>
          {connectedTools.length > 0 ? (
            <Select value={d.toolName ?? ""} onValueChange={(v) => onUpdate({ toolName: v || undefined })}>
              <SelectTrigger><SelectValue placeholder="Select tool..." /></SelectTrigger>
              <SelectContent>
                {connectedTools.map((t) => (
                  <SelectItem key={t} value={t}>{t}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <p className="text-xs text-muted-foreground mt-1">
              No tools available. Connect Tool or Built-in Tool nodes to this Graph Agent in the main canvas first.
            </p>
          )}
        </div>
      )}
```

After the `node-llm-request` block (line 67), add:

```typescript
      {node.type === "node-compress-history" && (
        <div>
          <Label>Compression Strategy</Label>
          <Input value={d.compressionStrategy ?? ""} onChange={(e) => onUpdate({ compressionStrategy: e.target.value })} placeholder="summarize" />
        </div>
      )}

      {node.type === "node-llm-structured" && (
        <div>
          <Label>Output Schema (Type)</Label>
          <CodeEditor
            value={d.outputSchema ?? ""}
            onChange={(v) => onUpdate({ outputSchema: v })}
            height="80px"
          />
        </div>
      )}
```

- [ ] **Step 2: Add onMultipleToolCalls to edge type select**

In `frontend/components/graph-agent/StrategyEdgeProperties.tsx`, find the SelectContent (lines 24-28):

```tsx
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
```

Replace with:
```tsx
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onMultipleToolCalls">onMultipleToolCalls</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
```

- [ ] **Step 3: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/graph-agent/StrategyNodeProperties.tsx frontend/components/graph-agent/StrategyEdgeProperties.tsx
git commit -m "feat: add properties for new node types and onMultipleToolCalls edge option"
```

---

### Task 7: Update backend model and adapter

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Add new fields to KoogStrategyNode**

In `GraphAgentModel.kt`, find `KoogStrategyNode` (lines 20-27):

```kotlin
data class KoogStrategyNode(
    val id: String,
    val type: String,
    val label: String?,
    val toolName: String?,
    val condition: String?,
    val temperature: Double?
)
```

Replace with:
```kotlin
data class KoogStrategyNode(
    val id: String,
    val type: String,
    val label: String?,
    val toolName: String?,
    val condition: String?,
    val temperature: Double?,
    val compressionStrategy: String?,
    val outputSchema: String?
)
```

- [ ] **Step 2: Extract new fields in adapter**

In `GraphToKoogAdapter.kt`, find the `KoogStrategyNode` construction (around lines 346-353):

```kotlin
                KoogStrategyNode(
                    id = snMap["id"] as? String ?: "",
                    type = snMap["type"] as? String ?: "",
                    label = snData["label"] as? String,
                    toolName = snData["toolName"] as? String,
                    condition = snData["condition"] as? String,
                    temperature = (snData["temperature"] as? Number)?.toDouble()
                )
```

Replace with:
```kotlin
                KoogStrategyNode(
                    id = snMap["id"] as? String ?: "",
                    type = snMap["type"] as? String ?: "",
                    label = snData["label"] as? String,
                    toolName = snData["toolName"] as? String,
                    condition = snData["condition"] as? String,
                    temperature = (snData["temperature"] as? Number)?.toDouble(),
                    compressionStrategy = snData["compressionStrategy"] as? String,
                    outputSchema = snData["outputSchema"] as? String
                )
```

- [ ] **Step 3: Verify backend build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt
git commit -m "feat: add compressionStrategy and outputSchema to KoogStrategyNode"
```

---

### Task 8: Update code generator — node declarations, imports, edge suffix

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`

- [ ] **Step 1: Add imports for new node types and edge type**

In `GraphAgentGenerator.kt`, find the import generation block (lines 24-28). After line 26 (`nodeLLMSendToolResult`), add:

```kotlin
            if ("node-execute-multiple-tools" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools")
            if ("node-send-multiple-results" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults")
            if ("node-compress-history" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory")
            if ("node-llm-streaming" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming")
            if ("node-llm-structured" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured")
            if ("node-do-nothing" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeDoNothing")
```

After line 28 (`onToolCall`), add:
```kotlin
            if ("onMultipleToolCalls" in edgeTypes) appendLine("import ai.koog.agents.core.dsl.extension.onMultipleToolCalls")
```

- [ ] **Step 2: Add node declarations**

In the `when (sn.type)` block (lines 35-41), add after `"node-condition"`:

```kotlin
                    "node-execute-multiple-tools" -> appendLine("    val $varName by nodeExecuteMultipleTools()")
                    "node-send-multiple-results" -> appendLine("    val $varName by nodeLLMSendMultipleToolResults()")
                    "node-compress-history" -> {
                        val strategy = sn.compressionStrategy
                        if (strategy != null && strategy.isNotBlank()) {
                            appendLine("    val $varName by nodeLLMCompressHistory(\"$strategy\")")
                        } else {
                            appendLine("    val $varName by nodeLLMCompressHistory()")
                        }
                    }
                    "node-llm-streaming" -> appendLine("    val $varName by nodeLLMRequestStreaming()")
                    "node-llm-structured" -> {
                        val schema = sn.outputSchema
                        if (schema != null && schema.isNotBlank()) {
                            appendLine("    val $varName by nodeLLMRequestStructured<$schema>()")
                        } else {
                            appendLine("    val $varName by nodeLLMRequestStructured<String>()")
                        }
                    }
                    "node-do-nothing" -> appendLine("    val $varName by nodeDoNothing()")
```

- [ ] **Step 3: Add onMultipleToolCalls to formatEdgeSuffix**

In `formatEdgeSuffix` (lines 72-89), add a new case after `"onToolCall"` (line 74):

```kotlin
            "onMultipleToolCalls" -> " onMultipleToolCalls { true }"
```

- [ ] **Step 4: Verify backend build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt
git commit -m "feat: add code generation for 6 new node types and onMultipleToolCalls edge"
```

---

### Task 9: Update frontend and backend validation

**Files:**
- Modify: `frontend/lib/graph-agent-validation.ts`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`

- [ ] **Step 1: Add frontend validation for new node types**

In `frontend/lib/graph-agent-validation.ts`, after the `node-condition` validation (line 60), add:

```typescript
    if (sn.type === "node-execute-multiple-tools" && !sn.data?.toolName?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-execute-multiple-tools "${sn.data?.label || sn.id}" requires a toolName` });
    }
```

- [ ] **Step 2: Add backend validation for new node types**

In `GraphValidator.kt`, find the `when (snType)` block (around lines 265-274). Add after `"node-condition"`:

```kotlin
                            "node-execute-multiple-tools" -> {
                                val toolName = snData["toolName"] as? String ?: ""
                                if (toolName.isBlank()) errors.add("[${node.id}] node-execute-multiple-tools requires a toolName")
                            }
```

- [ ] **Step 3: Verify both builds**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

```bash
./gradlew build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/graph-agent-validation.ts src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt
git commit -m "feat: add validation for new strategy node types"
```

---

### Task 10: Build verification

**Files:**
- No new files

- [ ] **Step 1: Run full frontend build**

```bash
cd frontend && npx next build
```

- [ ] **Step 2: Run full backend build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 3: Manual verification**

```bash
cd frontend && npm run dev
```

Verify:
1. All 10 toolbar buttons visible (4 original + 6 new)
2. Each new node type creates correct colored node on canvas
3. Execute Multi Tools node shows Tool dropdown
4. Compress History node shows Strategy input
5. LLM Structured node shows Output Schema CodeEditor
6. Connecting LLM Request → Execute Multi Tools creates onMultipleToolCalls edge
7. Edge Properties shows onMultipleToolCalls in dropdown

- [ ] **Step 4: Final commit if adjustments needed**

```bash
git add -A
git commit -m "feat: complete extended graph node types implementation"
```
