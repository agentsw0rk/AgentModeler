# Advanced Custom Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace single-function tool nodes with multi-function ToolSet nodes supporting per-function descriptions, extended parameter types, and default values.

**Architecture:** Frontend types and UI are updated to a two-level model (ToolSet → functions → parameters). Backend model mirrors this with `KoogToolFunction`. The Kotlin code generator produces ToolSet classes with multiple `@Tool` functions, `@LLMDescription` annotations, and optional default values. Legacy single-function tools are migrated on load.

**Tech Stack:** TypeScript/React (frontend), Kotlin/Spring Boot (backend), Zustand (state), shadcn/ui (components)

---

### Task 1: Update Frontend Type Definitions

**Files:**
- Modify: `frontend/types/koog.ts`

- [ ] **Step 1: Replace ToolParameter and ToolConfig with new types**

In `frontend/types/koog.ts`, replace lines 14-27 with:

```typescript
export type ToolParamType = "String" | "Int" | "Double" | "Float" | "Boolean" | "List<String>" | "Map<String, String>";

export const TOOL_PARAM_TYPES: ToolParamType[] = ["String", "Int", "Double", "Float", "Boolean", "List<String>", "Map<String, String>"];

export const TOOL_PARAM_TYPE_LABELS: Record<ToolParamType, string> = {
  "String": "String",
  "Int": "Int",
  "Double": "Double",
  "Float": "Float",
  "Boolean": "Boolean",
  "List<String>": "List<String>",
  "Map<String, String>": "Map<String, String>",
};

export interface ToolParameter {
  id: string;
  name: string;
  type: ToolParamType;
  description: string;
  required: boolean;
  defaultValue?: string;
}

export interface ToolFunction {
  id: string;
  name: string;
  description: string;
  parameters: ToolParameter[];
}

export interface ToolConfig {
  name: string;
  description: string;
  functions: ToolFunction[];
}

/** Migrate old flat tool format to new functions format */
export function migrateToolNodeData(data: Record<string, unknown>): ToolConfig {
  if (Array.isArray(data.functions)) {
    return data as unknown as ToolConfig;
  }
  // Legacy format: { name, description, parameters: [...], handlerStubName? }
  const oldParams = (data.parameters as Array<Record<string, unknown>>) || [];
  const params: ToolParameter[] = oldParams.map((p) => ({
    id: (p.id as string) || crypto.randomUUID(),
    name: (p.name as string) || "",
    type: (p.type as ToolParamType) || "String",
    description: (p.description as string) || "",
    required: p.required !== false,
    defaultValue: undefined,
  }));
  const func: ToolFunction = {
    id: crypto.randomUUID(),
    name: (data.name as string) || "",
    description: (data.description as string) || "",
    parameters: params,
  };
  return {
    name: (data.name as string) || "",
    description: (data.description as string) || "",
    functions: func.name ? [func] : [],
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/types/koog.ts
git commit -m "feat(tools): replace flat tool types with multi-function ToolSet model"
```

---

### Task 2: Update Editor Store Defaults and Migration

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Update imports and default tool data**

In `frontend/store/editorStore.ts`, update the import from koog.ts (line 3) to include the migration function:

```typescript
import { AgentConfig, ToolConfig, migrateToolNodeData } from "@/types/koog";
```

Replace the `defaultToolData` (lines 48-52):

```typescript
const defaultToolData: ToolConfig = {
  name: "",
  description: "",
  functions: [],
};
```

- [ ] **Step 2: Add tool migration to applyRemoteGraphReset**

In `applyRemoteGraphReset`, extend the existing migration block (which already handles I/O nodes) to also migrate tool nodes. Find the `applyRemoteGraphReset` method and update the node migration to also handle tools:

```typescript
applyRemoteGraphReset: (graph) => {
    const migratedNodes = graph.nodes.map((n) => {
      if ((n.type === "input" || n.type === "output") && !Array.isArray((n.data as unknown as Record<string, unknown>).fields)) {
        return { ...n, data: migrateIONodeData(n.data as unknown as Record<string, unknown>) };
      }
      if (n.type === "tool" && !Array.isArray((n.data as unknown as Record<string, unknown>).functions)) {
        return { ...n, data: migrateToolNodeData(n.data as unknown as Record<string, unknown>) };
      }
      return n;
    });
    set({
      nodes: migratedNodes,
      edges: graph.edges,
      workspaceName: graph.name,
      selectedNodeIds: [],
    });
  },
```

- [ ] **Step 3: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat(tools): update store defaults and add legacy tool migration"
```

---

### Task 3: Rewrite ToolProperties Component

**Files:**
- Modify: `frontend/components/panels/ToolProperties.tsx`

- [ ] **Step 1: Rewrite ToolProperties with two-level collapsible editor**

Replace the entire contents of `frontend/components/panels/ToolProperties.tsx`:

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { ToolConfig, ToolFunction, ToolParameter, ToolParamType } from "@/types/koog";
import { TOOL_PARAM_TYPES, TOOL_PARAM_TYPE_LABELS } from "@/types/koog";
import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react";

export function ToolProperties({ nodeId, data }: { nodeId: string; data: ToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const [expandedFnIds, setExpandedFnIds] = useState<Set<string>>(new Set());
  const [expandedParamIds, setExpandedParamIds] = useState<Set<string>>(new Set());
  const [dragFnIdx, setDragFnIdx] = useState<number | null>(null);

  const update = (field: Partial<ToolConfig>) => updateNodeData(nodeId, field);
  const updateFunctions = (functions: ToolFunction[]) => update({ functions });

  // --- Function CRUD ---
  const addFunction = () => {
    const fn: ToolFunction = {
      id: crypto.randomUUID(),
      name: "",
      description: "",
      parameters: [],
    };
    updateFunctions([...data.functions, fn]);
    setExpandedFnIds((s) => new Set(s).add(fn.id));
  };

  const updateFunction = (id: string, patch: Partial<ToolFunction>) => {
    updateFunctions(data.functions.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  };

  const removeFunction = (id: string) => {
    updateFunctions(data.functions.filter((f) => f.id !== id));
    setExpandedFnIds((s) => { const next = new Set(s); next.delete(id); return next; });
  };

  const toggleFnExpand = (id: string) => {
    setExpandedFnIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  // --- Function drag ---
  const handleFnDragStart = (idx: number) => setDragFnIdx(idx);
  const handleFnDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragFnIdx === null || dragFnIdx === idx) return;
    const reordered = [...data.functions];
    const [moved] = reordered.splice(dragFnIdx, 1);
    reordered.splice(idx, 0, moved);
    updateFunctions(reordered);
    setDragFnIdx(idx);
  };
  const handleFnDragEnd = () => setDragFnIdx(null);

  // --- Parameter CRUD (scoped to function) ---
  const addParam = (fnId: string) => {
    const param: ToolParameter = {
      id: crypto.randomUUID(),
      name: "",
      type: "String",
      description: "",
      required: true,
    };
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, { parameters: [...fn.parameters, param] });
    setExpandedParamIds((s) => new Set(s).add(param.id));
  };

  const updateParam = (fnId: string, paramId: string, patch: Partial<ToolParameter>) => {
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, {
      parameters: fn.parameters.map((p) => (p.id === paramId ? { ...p, ...patch } : p)),
    });
  };

  const removeParam = (fnId: string, paramId: string) => {
    const fn = data.functions.find((f) => f.id === fnId);
    if (!fn) return;
    updateFunction(fnId, { parameters: fn.parameters.filter((p) => p.id !== paramId) });
    setExpandedParamIds((s) => { const next = new Set(s); next.delete(paramId); return next; });
  };

  const toggleParamExpand = (id: string) => {
    setExpandedParamIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      {/* ToolSet Name */}
      <div>
        <Label htmlFor="tool-name">ToolSet Name</Label>
        <Input
          id="tool-name"
          value={data.name}
          onChange={(e) => update({ name: e.target.value })}
          placeholder="MoneyTransferTools"
        />
      </div>

      {/* ToolSet Description */}
      <div>
        <Label htmlFor="tool-desc">ToolSet Description</Label>
        <Textarea
          id="tool-desc"
          value={data.description}
          onChange={(e) => update({ description: e.target.value })}
          rows={2}
          placeholder="Tools for money transfer operations."
          className="text-xs"
        />
      </div>

      {/* Functions */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Functions</Label>
          <button onClick={addFunction} className="flex items-center gap-1 text-xs text-primary hover:underline">
            <Plus className="w-3 h-3" /> Add Function
          </button>
        </div>

        {data.functions.length === 0 && (
          <p className="text-xs text-muted-foreground">No functions yet. Add a function to define a tool.</p>
        )}

        <div className="space-y-1">
          {data.functions.map((fn, fnIdx) => {
            const fnExpanded = expandedFnIds.has(fn.id);
            return (
              <div
                key={fn.id}
                draggable
                onDragStart={() => handleFnDragStart(fnIdx)}
                onDragOver={(e) => handleFnDragOver(e, fnIdx)}
                onDragEnd={handleFnDragEnd}
                className="border rounded-md bg-muted/30"
              >
                {/* Function header */}
                <div className="flex items-center gap-1 px-2 py-1.5 cursor-pointer" onClick={() => toggleFnExpand(fn.id)}>
                  <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab shrink-0" />
                  <button className="shrink-0">
                    {fnExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                  </button>
                  <span className="text-xs font-medium truncate">{fn.name || "unnamed"}</span>
                  <span className="text-[10px] text-muted-foreground ml-auto shrink-0">
                    {fn.parameters.length} {fn.parameters.length === 1 ? "param" : "params"}
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); removeFunction(fn.id); }}
                    className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>

                {/* Function body */}
                {fnExpanded && (
                  <div className="px-3 pb-3 space-y-2 border-t">
                    <div className="pt-2">
                      <Label className="text-xs">Function Name</Label>
                      <Input
                        value={fn.name}
                        onChange={(e) => updateFunction(fn.id, { name: e.target.value })}
                        placeholder="getBalance"
                        className="h-7 text-xs"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Description (LLMDescription)</Label>
                      <Textarea
                        value={fn.description}
                        onChange={(e) => updateFunction(fn.id, { description: e.target.value })}
                        rows={2}
                        placeholder="Returns the current balance."
                        className="text-xs"
                      />
                    </div>

                    {/* Parameters */}
                    <div>
                      <div className="flex items-center justify-between mb-1">
                        <Label className="text-xs">Parameters</Label>
                        <button onClick={() => addParam(fn.id)} className="flex items-center gap-1 text-[10px] text-primary hover:underline">
                          <Plus className="w-2.5 h-2.5" /> Add
                        </button>
                      </div>

                      {fn.parameters.length === 0 && (
                        <p className="text-[10px] text-muted-foreground">No parameters.</p>
                      )}

                      <div className="space-y-1">
                        {fn.parameters.map((param) => {
                          const paramExpanded = expandedParamIds.has(param.id);
                          return (
                            <div key={param.id} className="border rounded bg-background/50">
                              {/* Parameter header */}
                              <div
                                className="flex items-center gap-1 px-2 py-1 cursor-pointer"
                                onClick={() => toggleParamExpand(param.id)}
                              >
                                <button className="shrink-0">
                                  {paramExpanded ? <ChevronDown className="w-2.5 h-2.5" /> : <ChevronRight className="w-2.5 h-2.5" />}
                                </button>
                                <span className="text-[11px] font-medium truncate">{param.name || "unnamed"}</span>
                                <span className="text-[10px] text-muted-foreground ml-auto shrink-0">{param.type}</span>
                                <button
                                  onClick={(e) => { e.stopPropagation(); removeParam(fn.id, param.id); }}
                                  className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                                >
                                  <Trash2 className="w-2.5 h-2.5" />
                                </button>
                              </div>

                              {/* Parameter body */}
                              {paramExpanded && (
                                <div className="px-2 pb-2 space-y-1.5 border-t">
                                  <div className="pt-1.5">
                                    <Label className="text-[10px]">Name</Label>
                                    <Input
                                      value={param.name}
                                      onChange={(e) => updateParam(fn.id, param.id, { name: e.target.value })}
                                      placeholder="userId"
                                      className="h-6 text-[11px]"
                                    />
                                  </div>
                                  <div>
                                    <Label className="text-[10px]">Type</Label>
                                    <Select value={param.type} onValueChange={(v) => updateParam(fn.id, param.id, { type: v as ToolParamType })}>
                                      <SelectTrigger className="h-6 text-[11px]"><SelectValue /></SelectTrigger>
                                      <SelectContent>
                                        {TOOL_PARAM_TYPES.map((t) => (
                                          <SelectItem key={t} value={t}>{TOOL_PARAM_TYPE_LABELS[t]}</SelectItem>
                                        ))}
                                      </SelectContent>
                                    </Select>
                                  </div>
                                  <div>
                                    <Label className="text-[10px]">Description</Label>
                                    <Input
                                      value={param.description}
                                      onChange={(e) => updateParam(fn.id, param.id, { description: e.target.value })}
                                      placeholder="The unique identifier of the user."
                                      className="h-6 text-[11px]"
                                    />
                                  </div>
                                  <div className="flex items-center justify-between">
                                    <Label className="text-[10px]">Required</Label>
                                    <button
                                      onClick={() => updateParam(fn.id, param.id, { required: !param.required, defaultValue: param.required ? "" : undefined })}
                                      className={`relative inline-flex h-4 w-7 items-center rounded-full transition-colors ${param.required ? "bg-primary" : "bg-muted"}`}
                                    >
                                      <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${param.required ? "translate-x-3.5" : "translate-x-0.5"}`} />
                                    </button>
                                  </div>
                                  {!param.required && (
                                    <div>
                                      <Label className="text-[10px]">Default Value</Label>
                                      <Input
                                        value={param.defaultValue || ""}
                                        onChange={(e) => updateParam(fn.id, param.id, { defaultValue: e.target.value || undefined })}
                                        placeholder="false"
                                        className="h-6 text-[11px]"
                                      />
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/ToolProperties.tsx
git commit -m "feat(tools): rewrite ToolProperties with two-level function/parameter editor"
```

---

### Task 4: Update Frontend Validation

**Files:**
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Update tool validation to check functions instead of parameters**

In `frontend/lib/validation.ts`, find the tool validation block (around lines 44-60) and replace it:

```typescript
    if (node.type === "tool") {
      const data = node.data as ToolConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "ToolSet requires a name" });
      }

      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "ToolSet must be connected to an Agent" });
      }

      for (const fn of data.functions ?? []) {
        if (!fn.name.trim()) {
          errors.push({ nodeId: node.id, severity: "error", message: "Function requires a name" });
        }
        for (const param of fn.parameters) {
          if (!param.name.trim()) {
            errors.push({ nodeId: node.id, severity: "error", message: `Parameter in function "${fn.name || "unnamed"}" requires a name` });
          }
        }
      }
    }
```

Make sure the import of `ToolConfig` at the top of the file still resolves (it should, since ToolConfig is still exported from `@/types/koog`).

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/validation.ts
git commit -m "feat(tools): update validation for multi-function tool model"
```

---

### Task 5: Update Backend KoogModel

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`

- [ ] **Step 1: Replace KoogTool, KoogToolParameter, and KoogParamType**

In `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`, replace lines 26-45 with:

```kotlin
data class KoogTool(
    val name: String,
    val description: String,
    val functions: List<KoogToolFunction>
)

data class KoogToolFunction(
    val name: String,
    val description: String,
    val parameters: List<KoogToolParameter>
)

data class KoogToolParameter(
    val name: String,
    val type: KoogParamType,
    val description: String,
    val required: Boolean,
    val defaultValue: String?
)

enum class KoogParamType(val kotlinType: String) {
    STRING("String"),
    INT("Int"),
    DOUBLE("Double"),
    FLOAT("Float"),
    BOOLEAN("Boolean"),
    LIST_STRING("List<String>"),
    MAP_STRING_STRING("Map<String, String>")
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat(tools): update KoogModel with multi-function tool and extended param types"
```

---

### Task 6: Update GraphToKoogAdapter

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Rewrite convertTool and parseParamType**

Find the `convertTool` method (around line 76) and replace it:

```kotlin
@Suppress("UNCHECKED_CAST")
private fun convertTool(node: GraphNode): KoogTool {
    val rawFunctions = node.data["functions"] as? List<Map<String, Any?>> ?: emptyList()
    val functions = rawFunctions.map { fnMap ->
        val rawParams = fnMap["parameters"] as? List<Map<String, Any?>> ?: emptyList()
        val params = rawParams.map { p ->
            KoogToolParameter(
                name = p["name"] as? String ?: "",
                type = parseParamType(p["type"] as? String ?: "String"),
                description = p["description"] as? String ?: "",
                required = p["required"] as? Boolean ?: true,
                defaultValue = p["defaultValue"] as? String
            )
        }
        KoogToolFunction(
            name = fnMap["name"] as? String ?: "",
            description = fnMap["description"] as? String ?: "",
            parameters = params
        )
    }
    return KoogTool(
        name = node.data["name"] as? String ?: "",
        description = node.data["description"] as? String ?: "",
        functions = functions
    )
}
```

Find the `parseParamType` method (around line 403) and replace it:

```kotlin
private fun parseParamType(value: String): KoogParamType = when (value) {
    "Int" -> KoogParamType.INT
    "Double" -> KoogParamType.DOUBLE
    "Float" -> KoogParamType.FLOAT
    "Boolean" -> KoogParamType.BOOLEAN
    "List<String>" -> KoogParamType.LIST_STRING
    "Map<String, String>" -> KoogParamType.MAP_STRING_STRING
    else -> KoogParamType.STRING
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt
git commit -m "feat(tools): update adapter for multi-function tool extraction"
```

---

### Task 7: Rewrite ToolGenerator with TDD

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt`

- [ ] **Step 1: Write new tests**

Replace the entire contents of `src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ToolGeneratorTest {

    private val generator = ToolGenerator()

    @Test
    fun `generates toolset with multiple functions`() {
        val tool = KoogTool(
            "MoneyTransferTools",
            "Tools for money transfer operations.",
            listOf(
                KoogToolFunction("getBalance", "Returns the current balance.", listOf(
                    KoogToolParameter("userId", KoogParamType.INT, "The unique identifier of the user.", true, null)
                )),
                KoogToolFunction("sendMoney", "Sends money from the user to a contact.", listOf(
                    KoogToolParameter("senderId", KoogParamType.INT, "Sender user id.", true, null),
                    KoogToolParameter("amount", KoogParamType.DOUBLE, "Amount in sender's default currency.", true, null),
                    KoogToolParameter("confirmed", KoogParamType.BOOLEAN, "Whether the user already confirmed.", false, "false")
                ))
            )
        )
        val code = generator.generate(tool)
        assertContains(code, """@LLMDescription("Tools for money transfer operations.")""")
        assertContains(code, "class MoneyTransferTools : ToolSet {")
        assertContains(code, "@Tool")
        assertContains(code, """@LLMDescription("Returns the current balance.")""")
        assertContains(code, "fun getBalance(")
        assertContains(code, """@LLMDescription("The unique identifier of the user.") userId: Int""")
        assertContains(code, """@LLMDescription("Sends money from the user to a contact.")""")
        assertContains(code, "fun sendMoney(")
        assertContains(code, """@LLMDescription("Amount in sender's default currency.") amount: Double""")
        assertContains(code, "confirmed: Boolean = false")
    }

    @Test
    fun `generates toolset with no functions`() {
        val tool = KoogTool("EmptyTools", "Empty toolset.", emptyList())
        val code = generator.generate(tool)
        assertContains(code, "class EmptyTools : ToolSet {")
        assertFalse(code.contains("@Tool"))
    }

    @Test
    fun `generates function with no parameters`() {
        val tool = KoogTool("PingTools", "Ping toolset.", listOf(
            KoogToolFunction("ping", "Pings the server.", emptyList())
        ))
        val code = generator.generate(tool)
        assertContains(code, "fun ping(): String {")
    }

    @Test
    fun `omits LLMDescription for empty param description`() {
        val tool = KoogTool("Tools", "Some tools.", listOf(
            KoogToolFunction("doStuff", "Does stuff.", listOf(
                KoogToolParameter("input", KoogParamType.STRING, "", true, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, "input: String")
        // Should NOT have @LLMDescription("") for the parameter
        assertFalse(code.contains("""@LLMDescription("") input"""))
    }

    @Test
    fun `maps all parameter types correctly`() {
        val tool = KoogTool("TypeTools", "Type test.", listOf(
            KoogToolFunction("allTypes", "All types.", listOf(
                KoogToolParameter("a", KoogParamType.STRING, "", true, null),
                KoogToolParameter("b", KoogParamType.INT, "", true, null),
                KoogToolParameter("c", KoogParamType.DOUBLE, "", true, null),
                KoogToolParameter("d", KoogParamType.FLOAT, "", true, null),
                KoogToolParameter("e", KoogParamType.BOOLEAN, "", true, null),
                KoogToolParameter("f", KoogParamType.LIST_STRING, "", true, null),
                KoogToolParameter("g", KoogParamType.MAP_STRING_STRING, "", true, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, "a: String")
        assertContains(code, "b: Int")
        assertContains(code, "c: Double")
        assertContains(code, "d: Float")
        assertContains(code, "e: Boolean")
        assertContains(code, "f: List<String>")
        assertContains(code, "g: Map<String, String>")
    }

    @Test
    fun `optional params with defaults come after required params`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("fn", "Test fn.", listOf(
                KoogToolParameter("opt", KoogParamType.BOOLEAN, "", false, "false"),
                KoogToolParameter("req", KoogParamType.STRING, "Desc", true, null)
            ))
        ))
        val code = generator.generate(tool)
        val reqIdx = code.indexOf("req: String")
        val optIdx = code.indexOf("opt: Boolean = false")
        assert(reqIdx < optIdx) { "Required params should come before optional" }
    }

    @Test
    fun `default value formatting for different types`() {
        val tool = KoogTool("Tools", "Test.", listOf(
            KoogToolFunction("fn", "Test fn.", listOf(
                KoogToolParameter("s", KoogParamType.STRING, "", false, "hello"),
                KoogToolParameter("n", KoogParamType.INT, "", false, "42"),
                KoogToolParameter("b", KoogParamType.BOOLEAN, "", false, "true"),
                KoogToolParameter("d", KoogParamType.DOUBLE, "", false, "3.14"),
                KoogToolParameter("l", KoogParamType.LIST_STRING, "", false, null),
                KoogToolParameter("m", KoogParamType.MAP_STRING_STRING, "", false, null)
            ))
        ))
        val code = generator.generate(tool)
        assertContains(code, """s: String = "hello"""")
        assertContains(code, "n: Int = 42")
        assertContains(code, "b: Boolean = true")
        assertContains(code, "d: Double = 3.14")
        assertContains(code, "l: List<String> = emptyList()")
        assertContains(code, "m: Map<String, String> = emptyMap()")
    }
}
```

- [ ] **Step 2: Rewrite ToolGenerator**

Replace the entire contents of `src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String = buildString {
        appendLine("package generated.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.annotations.Tool")
        appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
        appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
        appendLine()
        if (tool.description.isNotBlank()) {
            appendLine("""@LLMDescription("${tool.description}")""")
        }
        appendLine("class ${tool.name} : ToolSet {")
        for (fn in tool.functions) {
            appendLine()
            appendLine("    @Tool")
            if (fn.description.isNotBlank()) {
                appendLine("""    @LLMDescription("${fn.description}")""")
            }
            val paramStr = generateParams(fn.parameters)
            if (paramStr.isEmpty()) {
                appendLine("    fun ${fn.name}(): String {")
            } else {
                appendLine("    fun ${fn.name}(")
                append(paramStr)
                appendLine("    ): String {")
            }
            appendLine("        // TODO: Implement tool logic")
            appendLine("        return \"Not implemented\"")
            appendLine("    }")
        }
        appendLine("}")
    }

    private fun generateParams(params: List<KoogToolParameter>): String {
        if (params.isEmpty()) return ""
        val required = params.filter { it.required }
        val optional = params.filter { !it.required }
        val all = required + optional

        return buildString {
            for ((i, param) in all.withIndex()) {
                val comma = if (i < all.size - 1) "," else ""
                val desc = if (param.description.isNotBlank()) {
                    """@LLMDescription("${param.description}") """
                } else ""
                val default = if (!param.required) " = ${defaultLiteral(param.type, param.defaultValue)}" else ""
                appendLine("        $desc${param.name}: ${param.type.kotlinType}$default$comma")
            }
        }
    }

    private fun defaultLiteral(type: KoogParamType, value: String?): String {
        if (value == null || value.isBlank()) {
            return when (type) {
                KoogParamType.STRING -> "\"\""
                KoogParamType.INT -> "0"
                KoogParamType.DOUBLE -> "0.0"
                KoogParamType.FLOAT -> "0.0f"
                KoogParamType.BOOLEAN -> "false"
                KoogParamType.LIST_STRING -> "emptyList()"
                KoogParamType.MAP_STRING_STRING -> "emptyMap()"
            }
        }
        return when (type) {
            KoogParamType.STRING -> "\"$value\""
            KoogParamType.INT -> value.toIntOrNull()?.toString() ?: "0"
            KoogParamType.DOUBLE -> value.toDoubleOrNull()?.toString() ?: "0.0"
            KoogParamType.FLOAT -> value.toFloatOrNull()?.let { "${it}f" } ?: "0.0f"
            KoogParamType.BOOLEAN -> if (value.lowercase() == "true") "true" else "false"
            KoogParamType.LIST_STRING -> "emptyList()"
            KoogParamType.MAP_STRING_STRING -> "emptyMap()"
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*.ToolGeneratorTest" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt
git commit -m "feat(tools): rewrite ToolGenerator with multi-function support and @LLMDescription"
```

---

### Task 8: Update RegistryGenerator and ExportService

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Update RegistryGenerator tool instantiation**

In `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`, find the custom tool instances block (around lines 51-57) and replace:

```kotlin
// Custom tool instances
if (tools.isNotEmpty()) {
    val toolInstances = tools.joinToString("\n    ") { tool ->
        "val ${tool.name.replaceFirstChar { it.lowercase() }}Tools = ${tool.name}()"
    }
    appendLine("    $toolInstances")
    appendLine()
}
```

And the tools registration block (around lines 67-69):

```kotlin
// Custom tools
for (tool in tools) {
    appendLine("        tools(${tool.name.replaceFirstChar { it.lowercase() }}Tools)")
}
```

- [ ] **Step 2: Update ExportService tool file naming**

In `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`, find each block where tool files are generated. The pattern is:

```kotlin
val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
```

Replace with (the tool name IS the class name now):

```kotlin
files["$pkg/tools/${tool.name}.kt"] = toolGenerator.generate(tool)
```

Apply this change in all locations where tool files are generated (main agent, planner agent, graph agent, sub-agent blocks).

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | tail -10`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt \
        src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat(tools): update RegistryGenerator and ExportService for new tool model"
```

---

### Task 9: Update Backend Validation

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` (if tool validation exists)
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt` (if there's tool name validation)

- [ ] **Step 1: Search for backend tool validation**

Run: `grep -rn "tool.*name\|parameters\|KoogToolParameter" src/main/kotlin/ --include="*.kt" | grep -v "model\|generator\|Test"` to find any validation referencing old tool structure.

Fix any references to `tool.parameters` (now `tool.functions[].parameters`) or old `KoogToolParameter` constructor calls.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin compileTestKotlin 2>&1 | tail -20`

- [ ] **Step 3: Commit (if changes were needed)**

```bash
git add -A
git commit -m "fix(tools): update backend validation for multi-function tool model"
```

---

### Task 10: Fix Remaining Compilation Errors and Run Full Test Suite

**Files:**
- Potentially any file referencing old `KoogTool`, `KoogToolParameter`, or `KoogParamType` constructors

- [ ] **Step 1: Search for old constructor usages**

Run: `grep -rn "KoogTool(" src/ --include="*.kt" | grep -v "KoogModel.kt"` — check for old `KoogTool(name, description, parameters, handlerStubName)` calls
Run: `grep -rn "KoogToolParameter(" src/ --include="*.kt" | grep -v "KoogModel.kt"` — check for old `KoogToolParameter(name, type, description, required)` (4-arg, missing defaultValue)

Fix each call site:
- Old: `KoogTool("name", "desc", listOf(...), null)`
- New: `KoogTool("name", "desc", listOf(KoogToolFunction("name", "desc", listOf(...))))`

- Old: `KoogToolParameter("name", KoogParamType.STRING, "desc", true)`
- New: `KoogToolParameter("name", KoogParamType.STRING, "desc", true, null)`

- [ ] **Step 2: Run full backend tests**

Run: `./gradlew test 2>&1 | tail -30`
Expected: All tests pass (except known MCP test failures)

- [ ] **Step 3: Check frontend compilation**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -30`

- [ ] **Step 4: Commit all fixes**

```bash
git add -A
git commit -m "fix(tools): update all call sites for new KoogTool/KoogToolFunction signatures"
```
