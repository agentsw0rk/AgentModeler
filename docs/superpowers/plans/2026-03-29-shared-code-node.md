# Shared Code Node Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "code" node type that lets users write shared Kotlin code exported as a separate `.kt` file, usable by all connected tools.

**Architecture:** New node type following the established pattern (types → rete-node → connection rules → canvas sync → visuals → toolbar → properties → store → backend model → adapter → generator → export service). Frontend uses the existing CodeEditor (Monaco) from Feature 01.

**Tech Stack:** TypeScript/React (frontend), Kotlin/Ktor (backend), Rete.js (visual editor), Monaco Editor (code editing)

---

### Task 1: Add `code` to frontend type system

**Files:**
- Modify: `frontend/types/graph.ts`

- [ ] **Step 1: Add CodeNodeConfig interface and extend NodeType**

In `frontend/types/graph.ts`, add the `CodeNodeConfig` interface and extend the unions.

After the existing `OutputNodeConfig` interface (around line 40), add:

```typescript
export interface CodeNodeConfig {
  fileName: string;
  code: string;
}
```

Add `"code"` to the `NodeType` union (line 3):

```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output" | "graph-agent" | "code";
```

Add `CodeNodeConfig` to the `GraphNode.data` union (line 11):

```typescript
data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig | CodeNodeConfig;
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

Expected: Build succeeds (existing code doesn't depend on exhaustive type checks for NodeType).

- [ ] **Step 3: Commit**

```bash
git add frontend/types/graph.ts
git commit -m "feat: add code node type and CodeNodeConfig to type system"
```

---

### Task 2: Add CodeReteNode and connection rules

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`
- Modify: `frontend/lib/rete-setup.ts`

- [ ] **Step 1: Add CodeReteNode class**

In `frontend/lib/rete-nodes.ts`, after the `GraphAgentReteNode` class (around line 122), add:

```typescript
export class CodeReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public storeNodeId: string, label: string) {
    super(label || "Code");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}
```

Add `CodeReteNode` to the `AppNode` type union at the bottom of the file:

```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode | InputReteNode | OutputReteNode | GraphAgentReteNode | CodeReteNode;
```

- [ ] **Step 2: Add connection rules for code nodes**

In `frontend/lib/rete-setup.ts`, in the connection validation pipe (inside `editor.addPipe`), add a new rule block. Find the block that handles Input → Agent/SubAgent/PlannerAgent (around lines 93-97) and add after it:

```typescript
        // Code → Agent/Tool
        if (source instanceof CodeReteNode) {
          if (target instanceof AgentReteNode || target instanceof ToolReteNode || target instanceof SubAgentReteNode || target instanceof PlannerAgentReteNode || target instanceof GraphAgentReteNode) {
            return context;
          }
          return undefined;
        }
```

Add `CodeReteNode` to the import at the top of the file.

- [ ] **Step 3: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/lib/rete-setup.ts
git commit -m "feat: add CodeReteNode class and connection rules"
```

---

### Task 3: Add code node visuals to CustomNode

**Files:**
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add color, icon, label, and type detection for code nodes**

In `frontend/components/editor/CustomNode.tsx`:

Add `CodeIcon` to the lucide-react import (around line 5).

Add to `nodeIcons` map (around line 33):
```typescript
  code: <CodeIcon size={10} />,
```

Add to `nodeColors` map (around line 71):
```typescript
  code: {
    border: "rgba(168, 85, 247, 0.4)",
    glow: "rgba(168, 85, 247, 0.15)",
    badge: "rgba(168, 85, 247, 0.2)",
    badgeText: "rgb(192, 132, 252)",
  },
```

Add to `nodeTypeLabels` map (around line 84):
```typescript
  code: "CODE",
```

Add `CodeReteNode` to the import from `@/lib/rete-nodes` and add to `getNodeType` function (around line 98):
```typescript
  if (node instanceof CodeReteNode) return "code";
```

Add subtitle logic in the switch statement inside the component (where subtitles are computed for other node types). Find the subtitle switch and add:
```typescript
    case "code": {
      const codeData = storeNode?.data as CodeNodeConfig | undefined;
      subtitle = codeData?.fileName || undefined;
      break;
    }
```

Add `CodeNodeConfig` to the import from `@/types/graph`.

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/editor/CustomNode.tsx
git commit -m "feat: add purple code node visuals to CustomNode"
```

---

### Task 4: Add code node to Canvas sync

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`

- [ ] **Step 1: Add code node instantiation in Canvas sync**

In `frontend/components/editor/Canvas.tsx`, find the node creation section in the subscription handler (around lines 147-176). After the `graph-agent` case, add:

```typescript
          } else if (node.type === "code") {
            const d = node.data as CodeNodeConfig;
            reteNode = new CodeReteNode(node.id, d.fileName || "Code");
```

Add `CodeReteNode` to the import from `@/lib/rete-nodes`.
Add `CodeNodeConfig` to the import from `@/types/graph`.

Also find the node label update section (around lines 187-202) and add a case for code nodes:

```typescript
          } else if (node.type === "code") {
            const d = node.data as CodeNodeConfig;
            if (existing.label !== (d.fileName || "Code")) {
              existing.label = d.fileName || "Code";
              needsUpdate = true;
            }
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/editor/Canvas.tsx
git commit -m "feat: add code node to Canvas sync"
```

---

### Task 5: Add CodeProperties panel

**Files:**
- Create: `frontend/components/panels/CodeProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Create CodeProperties component**

Create `frontend/components/panels/CodeProperties.tsx`:

```tsx
"use client";

import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import CodeEditor from "@/components/shared/CodeEditor";
import type { CodeNodeConfig } from "@/types/graph";
import { useEditorStore } from "@/store/editorStore";

interface CodePropertiesProps {
  nodeId: string;
  data: CodeNodeConfig;
}

export default function CodeProperties({ nodeId, data }: CodePropertiesProps) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);

  return (
    <div className="space-y-4 p-1">
      <div>
        <Label htmlFor="code-filename" className="text-xs">File Name</Label>
        <Input
          id="code-filename"
          value={data.fileName}
          onChange={(e) => updateNodeData(nodeId, { fileName: e.target.value })}
          placeholder="Domain"
          className="h-7 text-xs"
        />
        <p className="text-[10px] text-muted-foreground mt-1">{data.fileName ? `${data.fileName}.kt` : "Enter a name"}</p>
      </div>
      <div>
        <Label className="text-xs">Kotlin Code</Label>
        <CodeEditor
          value={data.code}
          onChange={(v) => updateNodeData(nodeId, { code: v })}
          height="300px"
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Add dispatch in PropertiesPanel**

In `frontend/components/panels/PropertiesPanel.tsx`, add import:

```typescript
import CodeProperties from "@/components/panels/CodeProperties";
```

Add `CodeNodeConfig` to the import from `@/types/graph`.

Add the dispatch block alongside the other type checks:

```tsx
        {selectedNode.type === "code" && (
          <CodeProperties nodeId={selectedNode.id} data={selectedNode.data as CodeNodeConfig} />
        )}
```

- [ ] **Step 3: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/CodeProperties.tsx frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat: add CodeProperties panel with Monaco editor"
```

---

### Task 6: Add code node to Toolbar and EditorStore

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Add default data in editorStore**

In `frontend/store/editorStore.ts`, after the existing default data objects (around line 107), add:

```typescript
const defaultCodeData: CodeNodeConfig = {
  fileName: "",
  code: "",
};
```

Add `CodeNodeConfig` to the import from `@/types/graph`.

In the `addNode` function (around line 144-178), find the switch/if-else block that assigns default data by type. Add:

```typescript
      } else if (type === "code") {
        data = { ...defaultCodeData, ...initialData };
```

- [ ] **Step 2: Add toolbar button**

In `frontend/components/panels/Toolbar.tsx`, add `CodeIcon` to the lucide-react import.

Add a new menu item. Find the section with the other node buttons (after the Output button, around line 140). Add:

```tsx
              <NavigationMenuLink
                className="cursor-pointer gap-2"
                onClick={() => { addNode("code", { x: 300, y: 200 }); close(); }}
              >
                <CodeIcon className="size-4 text-purple-400" /> Code
              </NavigationMenuLink>
```

- [ ] **Step 3: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add frontend/store/editorStore.ts frontend/components/panels/Toolbar.tsx
git commit -m "feat: add code node to toolbar and editor store"
```

---

### Task 7: Add KoogSharedCode model and adapter

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Add KoogSharedCode data class**

In `KoogModel.kt`, at the end of the file (after the last data class), add:

```kotlin
data class KoogSharedCode(
    val name: String,
    val code: String
)
```

- [ ] **Step 2: Add convertSharedCode method to adapter**

In `GraphToKoogAdapter.kt`, add a new method at the end of the class:

```kotlin
    fun convertSharedCode(graph: WorkspaceGraph): List<KoogSharedCode> {
        return graph.nodes
            .filter { it.type == "code" }
            .map { node ->
                val data = node.data as Map<*, *>
                KoogSharedCode(
                    name = data["fileName"] as? String ?: "",
                    code = data["code"] as? String ?: ""
                )
            }
    }
```

- [ ] **Step 3: Verify backend build**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt backend/src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt
git commit -m "feat: add KoogSharedCode model and adapter conversion"
```

---

### Task 8: Create SharedCodeGenerator

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/modeler/export/generator/SharedCodeGenerator.kt`

- [ ] **Step 1: Create the generator**

Create `backend/src/main/kotlin/com/agentwork/modeler/export/generator/SharedCodeGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogSharedCode

class SharedCodeGenerator {
    fun generate(sharedCode: KoogSharedCode): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            append(sharedCode.code)
        }
    }
}
```

- [ ] **Step 2: Verify backend build**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/modeler/export/generator/SharedCodeGenerator.kt
git commit -m "feat: add SharedCodeGenerator for code node export"
```

---

### Task 9: Integrate SharedCode into ExportService

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add SharedCodeGenerator dependency and validation**

In `ExportService.kt`:

Add `SharedCodeGenerator` to the constructor parameters:

```kotlin
    private val sharedCodeGenerator: SharedCodeGenerator = SharedCodeGenerator(),
```

In the `validateGraph` function (or at the start of `generateFiles`), find where validation errors are collected. Add shared code validation:

```kotlin
        // Validate shared code nodes
        val codeNodes = graph.nodes.filter { it.type == "code" }
        for (node in codeNodes) {
            val data = node.data as Map<*, *>
            val fileName = data["fileName"] as? String ?: ""
            if (fileName.isBlank()) {
                errors.add("Code node '${node.id}' has no file name")
            }
        }
        val codeFileNames = codeNodes.mapNotNull { (it.data as Map<*, *>)["fileName"] as? String }.filter { it.isNotBlank() }
        val duplicates = codeFileNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate code file names: ${duplicates.joinToString(", ")}")
        }
```

- [ ] **Step 2: Add shared code generation to the file generation pipeline**

In `generateFiles`, after the adapter conversions and before the ZIP is assembled, add:

```kotlin
        // Generate shared code files
        val sharedCodes = adapter.convertSharedCode(graph)
        for (code in sharedCodes) {
            if (code.name.isNotBlank()) {
                files["src/main/kotlin/generated/${code.name}.kt"] = sharedCodeGenerator.generate(code)
            }
        }
```

Find the appropriate location in the `generateFiles` function where other generators produce files (look for where `files[...] = ...generator.generate(...)` patterns exist) and add the shared code generation there.

- [ ] **Step 3: Verify backend build**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 4: Run existing tests**

```bash
cd backend && ./gradlew test 2>&1 | tail -10
```

Expected: All existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat: integrate SharedCodeGenerator into export pipeline with validation"
```

---

### Task 10: Build verification and end-to-end test

**Files:**
- No new files

- [ ] **Step 1: Run full frontend build**

```bash
cd frontend && npx next build
```

Expected: Build succeeds.

- [ ] **Step 2: Run full backend build and tests**

```bash
cd backend && ./gradlew build 2>&1 | tail -10
```

Expected: Build and tests succeed.

- [ ] **Step 3: Manual verification in dev mode**

```bash
cd frontend && npm run dev
```

Verify:
1. "+ Code" button visible in toolbar
2. Clicking it creates a purple CODE node on canvas
3. Selecting the node shows CodeProperties with fileName input and Monaco editor
4. Code node connects to Agent node (drag connection)
5. Code node connects to Tool node (drag connection)
6. Code node does NOT connect to Output or Input nodes
7. fileName appears as subtitle on the node
8. Editing code in Monaco works with Kotlin syntax highlighting

- [ ] **Step 4: Final commit if adjustments needed**

```bash
git add -A
git commit -m "feat: complete shared code node implementation"
```
