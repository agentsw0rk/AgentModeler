# Subgraphs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `node-subgraph` strategy node type with a nested graph editor, tool selection, and code generation for `subgraph<I, O>(...) { ... }`.

**Architecture:** Extends the existing strategy node pattern (type → rete → visuals → canvas → properties). The key new element is a nested editor: `GraphStrategyEditor` manages an `editingSubgraphId` state that renders a second overlay with the subgraph's inner nodes/edges. The generator extracts a `generateStrategyBody` helper to reuse node declaration and edge generation for both main graphs and subgraphs.

**Tech Stack:** TypeScript/React, Kotlin/Spring, Rete.js, Monaco Editor

---

### Task 1: Extend types and add SubgraphReteNode

**Files:**
- Modify: `frontend/types/graph-agent.ts`
- Modify: `frontend/lib/strategy-rete-nodes.ts`

- [ ] **Step 1: Add node-subgraph type and data fields**

In `frontend/types/graph-agent.ts`:

Add `| "node-subgraph"` to `GraphStrategyNodeType` union (after `"node-custom"`).

Add these fields to `GraphStrategyNodeData` (after `body`):
```typescript
  subgraphName?: string;
  subgraphInputType?: string;
  subgraphOutputType?: string;
  subgraphToolSelection?: "ALL" | "SPECIFIC" | "NONE";
  subgraphTools?: string[];
  subgraphNodes?: GraphStrategyNode[];
  subgraphEdges?: GraphStrategyEdge[];
```

- [ ] **Step 2: Add SubgraphReteNode class**

In `frontend/lib/strategy-rete-nodes.ts`, after `CustomReteNode`, add:

```typescript
export class SubgraphReteNode extends ClassicPreset.Node {
  width = 220;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Subgraph");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}
```

Add `SubgraphReteNode` to the `StrategyNode` type union.

- [ ] **Step 3: Commit**

```bash
git add frontend/types/graph-agent.ts frontend/lib/strategy-rete-nodes.ts
git commit -m "feat: add node-subgraph type, data fields, and SubgraphReteNode class"
```

---

### Task 2: Add visuals, toolbar, and canvas

**Files:**
- Modify: `frontend/components/graph-agent/StrategyCustomNode.tsx`
- Modify: `frontend/components/graph-agent/GraphStrategyToolbar.tsx`
- Modify: `frontend/components/graph-agent/GraphStrategyCanvas.tsx`

- [ ] **Step 1: Add SubgraphReteNode visuals**

In `frontend/components/graph-agent/StrategyCustomNode.tsx`:

Add `SubgraphReteNode` to the import from `@/lib/strategy-rete-nodes`.

Add to `strategyNodeColors`:
```typescript
  "node-subgraph": { border: "rgba(249, 115, 22, 0.5)", glow: "rgba(249, 115, 22, 0.12)", badge: "rgba(249, 115, 22, 0.15)", badgeText: "#fb923c" },
```

Add to `strategyNodeLabels`:
```typescript
  "node-subgraph": "SUBGRAPH",
```

Add to `getStrategyNodeType` (before fallback):
```typescript
  if (data instanceof SubgraphReteNode) return "node-subgraph";
```

- [ ] **Step 2: Add toolbar button**

In `frontend/components/graph-agent/GraphStrategyToolbar.tsx`:

Add a prop to hide the subgraph button when inside a subgraph. Update the component:

```typescript
export function GraphStrategyToolbar({ onAddNode, hideSubgraph }: { onAddNode: (type: GraphStrategyNodeType) => void; hideSubgraph?: boolean }) {
```

Add to `nodeButtons` array:
```typescript
  { type: "node-subgraph", label: "Subgraph", color: "text-orange-400" },
```

Filter out the subgraph button when `hideSubgraph` is true. Change the render to:

```tsx
      {nodeButtons.filter(btn => !(hideSubgraph && btn.type === "node-subgraph")).map((btn) => (
```

- [ ] **Step 3: Add canvas createReteNode case and update autoTypeEdge**

In `frontend/components/graph-agent/GraphStrategyCanvas.tsx`:

Add `SubgraphReteNode` to the import from `@/lib/strategy-rete-nodes`.

Add case in `createReteNode`:
```typescript
    case "node-subgraph": return new SubgraphReteNode(sn.id, sn.data.subgraphName || sn.data.label || "Subgraph");
```

Update `autoTypeEdge` — add after the `node-custom` check:
```typescript
  if (sourceType === "node-llm-request" && targetType === "node-subgraph" && targetNodeId && strategyNodes) {
    const targetNode = strategyNodes.find(sn => sn.id === targetNodeId);
    if (targetNode?.data.subgraphInputType === "String" || !targetNode?.data.subgraphInputType) return "onAssistantMessage";
  }
```

Also add a double-click event dispatcher. In the Canvas, add a new custom event listener for `strategy-node-dblclick`. Find where the area plugin is set up and add a listener that fires when a node is double-clicked. Add this after the `handleEdgeSelect` listener setup:

```typescript
      const handleNodeDblClick = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        if (detail?.strategyNodeId) {
          propsRef.current.onNodeDoubleClick?.(detail.strategyNodeId);
        }
      };
      window.addEventListener("strategy-node-dblclick", handleNodeDblClick);
```

Add cleanup:
```typescript
      window.removeEventListener("strategy-node-dblclick", handleNodeDblClick);
```

Add `onNodeDoubleClick?: (nodeId: string) => void;` to the `Props` interface.

- [ ] **Step 4: Add double-click handler to StrategyCustomNode**

In `frontend/components/graph-agent/StrategyCustomNode.tsx`, find the `StrategyCustomNode` component's main clickable area. Add an `onDoubleClick` handler that dispatches the event:

Find the outer div that handles the node rendering (it should already have onClick or similar). Add:

```typescript
  const handleDoubleClick = () => {
    if ("strategyNodeId" in data) {
      window.dispatchEvent(new CustomEvent("strategy-node-dblclick", {
        detail: { strategyNodeId: (data as any).strategyNodeId }
      }));
    }
  };
```

Add `onDoubleClick={handleDoubleClick}` to the outermost interactive div of the node.

- [ ] **Step 5: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/graph-agent/StrategyCustomNode.tsx frontend/components/graph-agent/GraphStrategyToolbar.tsx frontend/components/graph-agent/GraphStrategyCanvas.tsx
git commit -m "feat: add subgraph node visuals, toolbar, canvas, and double-click event"
```

---

### Task 3: Add nested subgraph editor to GraphStrategyEditor

**Files:**
- Modify: `frontend/components/graph-agent/GraphStrategyEditor.tsx`

This is the most complex task — it adds the nested overlay editor.

- [ ] **Step 1: Add editingSubgraphId state and handlers**

In `frontend/components/graph-agent/GraphStrategyEditor.tsx`, add state after existing state:

```typescript
  const [editingSubgraphId, setEditingSubgraphId] = useState<string | null>(null);
```

Add default labels for the new node types in `handleAddNode` (the `defaultLabels` object):
```typescript
      "node-execute-multiple-tools": "executeMultipleTools",
      "node-send-multiple-results": "sendMultipleResults",
      "node-compress-history": "compressHistory",
      "node-llm-streaming": "llmStreaming",
      "node-llm-structured": "llmStructured",
      "node-do-nothing": "doNothing",
      "node-custom": "custom",
      "node-subgraph": "subgraph",
```

Add a handler for when subgraph nodes need default inner nodes:

```typescript
  const handleAddSubgraphNode = (type: GraphStrategyNodeType) => {
    const id = `sn-${nextStrategyNodeId++}`;
    const defaultLabels: Record<string, string> = {
      "node-llm-request": "llmRequest",
      "node-execute-tool": "executeTool",
      "node-llm-send-tool-result": "sendResult",
      "node-condition": "condition",
      "node-execute-multiple-tools": "executeMultipleTools",
      "node-send-multiple-results": "sendMultipleResults",
      "node-compress-history": "compressHistory",
      "node-llm-streaming": "llmStreaming",
      "node-llm-structured": "llmStructured",
      "node-do-nothing": "doNothing",
      "node-custom": "custom",
    };
    const newNode: GraphStrategyNode = {
      id,
      type,
      position: { x: 300, y: 200 },
      data: { label: defaultLabels[type] || type },
    };
    if (!editingSubgraphId) return;
    const sgNode = data.strategyNodes.find(sn => sn.id === editingSubgraphId);
    if (!sgNode) return;
    const innerNodes = [...(sgNode.data.subgraphNodes || []), newNode];
    handleUpdateStrategyNodeData(editingSubgraphId, { subgraphNodes: innerNodes });
  };
```

Add subgraph inner data change handlers:

```typescript
  const editingSubgraphNode = editingSubgraphId ? data.strategyNodes.find(sn => sn.id === editingSubgraphId) : null;
  const subgraphNodes = editingSubgraphNode?.data.subgraphNodes || [];
  const subgraphEdges = editingSubgraphNode?.data.subgraphEdges || [];

  const handleSubgraphNodesChanged = (nodes: GraphStrategyNode[]) => {
    if (editingSubgraphId) handleUpdateStrategyNodeData(editingSubgraphId, { subgraphNodes: nodes });
  };

  const handleSubgraphEdgesChanged = (edges: GraphStrategyEdge[]) => {
    if (editingSubgraphId) handleUpdateStrategyNodeData(editingSubgraphId, { subgraphEdges: edges });
  };

  const handleNodeDoubleClick = (snId: string) => {
    const sn = data.strategyNodes.find(n => n.id === snId);
    if (sn?.type === "node-subgraph") {
      // Initialize inner graph if empty
      if (!sn.data.subgraphNodes || sn.data.subgraphNodes.length === 0) {
        handleUpdateStrategyNodeData(snId, {
          subgraphNodes: [
            { id: `sn-${nextStrategyNodeId++}`, type: "node-start", position: { x: 100, y: 200 }, data: {} },
            { id: `sn-${nextStrategyNodeId++}`, type: "node-finish", position: { x: 600, y: 200 }, data: {} },
          ],
          subgraphEdges: [],
        });
      }
      setEditingSubgraphId(snId);
    }
  };
```

- [ ] **Step 2: Pass onNodeDoubleClick to Canvas and add nested overlay**

Pass `onNodeDoubleClick` to the main `GraphStrategyCanvas`:

```tsx
        <GraphStrategyCanvas
          strategyNodes={data.strategyNodes}
          strategyEdges={data.strategyEdges}
          onNodeSelected={handleNodeSelected}
          onEdgeSelected={handleEdgeSelected}
          onNodesChanged={handleNodesChanged}
          onEdgesChanged={handleEdgesChanged}
          onNodeDoubleClick={handleNodeDoubleClick}
        />
```

Add the nested subgraph overlay. After the main editor's closing `</div>` but still inside the outer container, add:

```tsx
      {editingSubgraphId && editingSubgraphNode && (
        <div className="fixed inset-y-0 right-0 w-[70%] bg-background border-l-2 border-orange-500/40 z-[60] flex flex-col shadow-2xl">
          <div className="flex items-center justify-between px-4 py-3 border-b border-border">
            <div className="flex items-center gap-2 text-sm">
              <button className="text-muted-foreground hover:text-foreground" onClick={() => setEditingSubgraphId(null)}>Strategy</button>
              <span className="text-muted-foreground">/</span>
              <span className="text-orange-400">Subgraph: {editingSubgraphNode.data.subgraphName || "Unnamed"}</span>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setEditingSubgraphId(null)} className="h-7 w-7 p-0">
              <XIcon className="h-4 w-4" />
            </Button>
          </div>

          <GraphStrategyToolbar onAddNode={handleAddSubgraphNode} hideSubgraph />

          <div className="flex-1 flex overflow-hidden">
            <GraphStrategyCanvas
              strategyNodes={subgraphNodes}
              strategyEdges={subgraphEdges}
              onNodeSelected={handleNodeSelected}
              onEdgeSelected={handleEdgeSelected}
              onNodesChanged={handleSubgraphNodesChanged}
              onEdgesChanged={handleSubgraphEdgesChanged}
            />

            <div className="w-64 border-l bg-background overflow-y-auto">
              {selectedNode && subgraphNodes.some(sn => sn.id === selectedNode.id) && (
                <div>
                  <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Node Properties</div>
                  <StrategyNodeProperties
                    node={selectedNode}
                    connectedTools={connectedToolNames}
                    onUpdate={(dataUpdate) => {
                      const updatedInnerNodes = subgraphNodes.map(sn =>
                        sn.id === selectedNode.id ? { ...sn, data: { ...sn.data, ...dataUpdate } } : sn
                      );
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphNodes: updatedInnerNodes });
                    }}
                  />
                </div>
              )}
              {selectedEdge && subgraphEdges.some(se => se.id === selectedEdge.id) && (
                <div>
                  <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Edge Properties</div>
                  <StrategyEdgeProperties
                    edge={selectedEdge}
                    onUpdate={(edgeUpdate) => {
                      const updatedInnerEdges = subgraphEdges.map(se =>
                        se.id === selectedEdge.id ? { ...se, ...edgeUpdate } : se
                      );
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphEdges: updatedInnerEdges });
                    }}
                    onDelete={() => {
                      const updatedInnerEdges = subgraphEdges.filter(se => se.id !== selectedEdge.id);
                      handleUpdateStrategyNodeData(editingSubgraphId!, { subgraphEdges: updatedInnerEdges });
                      setSelectedStrategyEdgeId(null);
                    }}
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      )}
```

- [ ] **Step 3: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/graph-agent/GraphStrategyEditor.tsx
git commit -m "feat: add nested subgraph editor overlay with breadcrumb navigation"
```

---

### Task 4: Add subgraph node properties

**Files:**
- Modify: `frontend/components/graph-agent/StrategyNodeProperties.tsx`

- [ ] **Step 1: Add subgraph properties block**

In `frontend/components/graph-agent/StrategyNodeProperties.tsx`, after the `node-custom` block, add:

```tsx
      {node.type === "node-subgraph" && (
        <>
          <div>
            <Label>Subgraph Name</Label>
            <Input value={d.subgraphName ?? ""} onChange={(e) => onUpdate({ subgraphName: e.target.value })} placeholder="transfer" />
          </div>
          <TypeSelect
            label="Input Type"
            value={d.subgraphInputType ?? "String"}
            onChange={(v) => onUpdate({ subgraphInputType: v })}
          />
          <TypeSelect
            label="Output Type"
            value={d.subgraphOutputType ?? "String"}
            onChange={(v) => onUpdate({ subgraphOutputType: v })}
          />
          <div>
            <Label>Tool Selection</Label>
            <Select value={d.subgraphToolSelection ?? "ALL"} onValueChange={(v) => onUpdate({ subgraphToolSelection: v as "ALL" | "SPECIFIC" | "NONE" })}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Tools</SelectItem>
                <SelectItem value="SPECIFIC">Specific Tools</SelectItem>
                <SelectItem value="NONE">No Tools</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {d.subgraphToolSelection === "SPECIFIC" && connectedTools.length > 0 && (
            <div>
              <Label>Tools</Label>
              <div className="space-y-1 mt-1">
                {connectedTools.map((tool) => (
                  <label key={tool} className="flex items-center gap-2 text-xs">
                    <input
                      type="checkbox"
                      checked={(d.subgraphTools || []).includes(tool)}
                      onChange={(e) => {
                        const current = d.subgraphTools || [];
                        const updated = e.target.checked
                          ? [...current, tool]
                          : current.filter(t => t !== tool);
                        onUpdate({ subgraphTools: updated });
                      }}
                      className="rounded"
                    />
                    {tool}
                  </label>
                ))}
              </div>
            </div>
          )}
        </>
      )}
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/StrategyNodeProperties.tsx
git commit -m "feat: add subgraph properties with name, types, and tool selection"
```

---

### Task 5: Update backend model and adapter

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Add subgraph fields to KoogStrategyNode**

In `GraphAgentModel.kt`, add fields to `KoogStrategyNode` (after `body`):

```kotlin
    val subgraphName: String?,
    val subgraphInputType: String?,
    val subgraphOutputType: String?,
    val subgraphToolSelection: String?,
    val subgraphTools: List<String>?,
    val subgraphNodes: List<KoogStrategyNode>?,
    val subgraphEdges: List<KoogStrategyEdge>?
```

- [ ] **Step 2: Extract subgraph data in adapter**

In `GraphToKoogAdapter.kt`, in the `KoogStrategyNode` construction block, add after `body`:

```kotlin
                    subgraphName = snData["subgraphName"] as? String,
                    subgraphInputType = snData["subgraphInputType"] as? String,
                    subgraphOutputType = snData["subgraphOutputType"] as? String,
                    subgraphToolSelection = snData["subgraphToolSelection"] as? String,
                    subgraphTools = (snData["subgraphTools"] as? List<*>)?.filterIsInstance<String>(),
                    subgraphNodes = (snData["subgraphNodes"] as? List<Map<String, Any?>>)?.map { innerSnMap ->
                        @Suppress("UNCHECKED_CAST")
                        val innerSnData = innerSnMap["data"] as? Map<String, Any?> ?: emptyMap()
                        KoogStrategyNode(
                            id = innerSnMap["id"] as? String ?: "",
                            type = innerSnMap["type"] as? String ?: "",
                            label = innerSnData["label"] as? String,
                            toolName = innerSnData["toolName"] as? String,
                            condition = innerSnData["condition"] as? String,
                            temperature = (innerSnData["temperature"] as? Number)?.toDouble(),
                            compressionStrategy = innerSnData["compressionStrategy"] as? String,
                            outputSchema = innerSnData["outputSchema"] as? String,
                            inputType = innerSnData["inputType"] as? String,
                            outputType = innerSnData["outputType"] as? String,
                            body = innerSnData["body"] as? String,
                            subgraphName = null, subgraphInputType = null, subgraphOutputType = null,
                            subgraphToolSelection = null, subgraphTools = null,
                            subgraphNodes = null, subgraphEdges = null
                        )
                    },
                    subgraphEdges = (snData["subgraphEdges"] as? List<Map<String, Any?>>)?.map { innerSeMap ->
                        KoogStrategyEdge(
                            source = innerSeMap["source"] as? String ?: "",
                            target = innerSeMap["target"] as? String ?: "",
                            edgeType = innerSeMap["edgeType"] as? String ?: "",
                            condition = innerSeMap["condition"] as? String,
                            transformation = innerSeMap["transformation"] as? String
                        )
                    }
```

- [ ] **Step 3: Verify build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt
git commit -m "feat: add subgraph fields to KoogStrategyNode and adapter extraction"
```

---

### Task 6: Update code generator for subgraphs

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`

- [ ] **Step 1: Extract helper functions and add subgraph generation**

In `GraphAgentGenerator.kt`:

Add import at the top of the import block:
```kotlin
            if ("node-subgraph" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.builder.subgraph")
```

Add `node-subgraph` to the `when (sn.type)` block:
```kotlin
                    "node-subgraph" -> {
                        val sgName = sn.subgraphName?.ifBlank { null } ?: "subgraph"
                        val inputT = sn.subgraphInputType?.ifBlank { null } ?: "String"
                        val outputT = sn.subgraphOutputType?.ifBlank { null } ?: "String"
                        val toolStrategy = when (sn.subgraphToolSelection) {
                            "NONE" -> ",\n        toolSelectionStrategy = ToolSelectionStrategy.None"
                            "SPECIFIC" -> {
                                val toolDescriptors = (sn.subgraphTools ?: emptyList()).joinToString(", ") { "${sanitizeIdentifier(it)}.descriptor" }
                                ",\n        toolSelectionStrategy = ToolSelectionStrategy.Tools(listOf($toolDescriptors))"
                            }
                            else -> "" // ALL is the default, no parameter needed
                        }
                        appendLine("    val $varName by subgraph<$inputT, $outputT>(")
                        appendLine("        name = \"$sgName\"$toolStrategy")
                        appendLine("    ) {")
                        // Generate inner nodes
                        val innerNodeMap = (sn.subgraphNodes ?: emptyList()).associateBy { it.id }
                        for (innerSn in sn.subgraphNodes ?: emptyList()) {
                            val innerVarName = sanitizeIdentifier(innerSn.label ?: innerSn.id)
                            when (innerSn.type) {
                                "node-start", "node-finish" -> {}
                                "node-llm-request" -> appendLine("        val $innerVarName by nodeLLMRequest()")
                                "node-execute-tool" -> appendLine("        val $innerVarName by nodeExecuteTool()")
                                "node-llm-send-tool-result" -> appendLine("        val $innerVarName by nodeLLMSendToolResult()")
                                "node-condition" -> appendLine("        val $innerVarName by nodeCondition(\"${innerSn.condition}\")")
                                "node-execute-multiple-tools" -> appendLine("        val $innerVarName by nodeExecuteMultipleTools()")
                                "node-send-multiple-results" -> appendLine("        val $innerVarName by nodeLLMSendMultipleToolResults()")
                                "node-compress-history" -> appendLine("        val $innerVarName by nodeLLMCompressHistory()")
                                "node-llm-streaming" -> appendLine("        val $innerVarName by nodeLLMRequestStreaming()")
                                "node-llm-structured" -> appendLine("        val $innerVarName by nodeLLMRequestStructured<String>()")
                                "node-do-nothing" -> appendLine("        val $innerVarName by nodeDoNothing()")
                                "node-custom" -> {
                                    val iT = innerSn.inputType?.ifBlank { null } ?: "String"
                                    val oT = innerSn.outputType?.ifBlank { null } ?: "String"
                                    val b = innerSn.body ?: ""
                                    appendLine("        val $innerVarName by node<$iT, $oT> { input ->")
                                    appendLine("            $b")
                                    appendLine("        }")
                                }
                            }
                        }
                        appendLine()
                        // Generate inner edges
                        for (innerSe in sn.subgraphEdges ?: emptyList()) {
                            val srcRef = resolveNodeRef(innerSe.source, innerNodeMap)
                            val tgtRef = resolveNodeRef(innerSe.target, innerNodeMap)
                            val suffix = formatEdgeSuffix(innerSe)
                            appendLine("        edge($srcRef forwardTo $tgtRef$suffix)")
                        }
                        appendLine("    }")
                    }
```

Also, collect imports from inner subgraph nodes. Find where `nodeTypes` is built (line 17) and extend it to include inner node types:

Replace:
```kotlin
        val nodeTypes = agent.strategyNodes.map { it.type }.toSet()
```

With:
```kotlin
        val innerNodeTypes = agent.strategyNodes
            .filter { it.type == "node-subgraph" }
            .flatMap { it.subgraphNodes?.map { sn -> sn.type } ?: emptyList() }
        val nodeTypes = (agent.strategyNodes.map { it.type } + innerNodeTypes).toSet()
```

Do the same for edge types:

Replace:
```kotlin
        val edgeTypes = agent.strategyEdges.map { it.edgeType }.toSet()
```

With:
```kotlin
        val innerEdgeTypes = agent.strategyNodes
            .filter { it.type == "node-subgraph" }
            .flatMap { it.subgraphEdges?.map { se -> se.edgeType } ?: emptyList() }
        val edgeTypes = (agent.strategyEdges.map { it.edgeType } + innerEdgeTypes).toSet()
```

- [ ] **Step 2: Fix tests — update KoogStrategyNode constructor calls**

In `GraphAgentGeneratorTest.kt`, every existing `KoogStrategyNode(...)` call needs 7 new null arguments (subgraphName, subgraphInputType, subgraphOutputType, subgraphToolSelection, subgraphTools, subgraphNodes, subgraphEdges). Add `, null, null, null, null, null, null, null` to each.

- [ ] **Step 3: Verify build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt
git commit -m "feat: add subgraph code generation with inner nodes, edges, and tool selection"
```

---

### Task 7: Add validation and build verification

**Files:**
- Modify: `frontend/lib/graph-agent-validation.ts`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`

- [ ] **Step 1: Add frontend validation for subgraph nodes**

In `frontend/lib/graph-agent-validation.ts`, after the `node-custom` validation, add:

```typescript
    if (sn.type === "node-subgraph") {
      if (!sn.data?.subgraphName?.trim()) {
        errors.push({ nodeId, severity: "error", message: `Subgraph node "${sn.data?.label || sn.id}" requires a name` });
      }
      // Validate inner graph
      const innerNodes = sn.data?.subgraphNodes || [];
      const innerEdges = sn.data?.subgraphEdges || [];
      const innerStartCount = innerNodes.filter((n: any) => n.type === "node-start").length;
      const innerFinishCount = innerNodes.filter((n: any) => n.type === "node-finish").length;
      if (innerNodes.length > 0) {
        if (innerStartCount !== 1) errors.push({ nodeId, severity: "error", message: `Subgraph "${sn.data?.subgraphName || sn.id}" must have exactly one node-start` });
        if (innerFinishCount !== 1) errors.push({ nodeId, severity: "error", message: `Subgraph "${sn.data?.subgraphName || sn.id}" must have exactly one node-finish` });
      }
      if (sn.data?.subgraphToolSelection === "SPECIFIC" && (!sn.data?.subgraphTools || sn.data.subgraphTools.length === 0)) {
        errors.push({ nodeId, severity: "warning", message: `Subgraph "${sn.data?.subgraphName || sn.id}" has SPECIFIC tool selection but no tools selected` });
      }
    }
```

- [ ] **Step 2: Add backend validation for subgraph nodes**

In `GraphValidator.kt`, in the `when (snType)` block, add:

```kotlin
                            "node-subgraph" -> {
                                val sgName = snData["subgraphName"] as? String ?: ""
                                if (sgName.isBlank()) errors.add("[${node.id}] node-subgraph requires a subgraphName")
                            }
```

- [ ] **Step 3: Run full frontend build**

```bash
cd frontend && npx next build
```

- [ ] **Step 4: Run full backend build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/graph-agent-validation.ts src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt
git commit -m "feat: add subgraph validation for name, inner graph, and tool selection"
```
