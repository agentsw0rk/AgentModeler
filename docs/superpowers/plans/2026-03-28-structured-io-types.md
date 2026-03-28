# Structured I/O Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat single-field Input/Output model with multi-field structured types, each field having a name, Kotlin-native type, LLMDescription, and optionality.

**Architecture:** Frontend types and UI components are updated to manage a `fields[]` array per I/O node with an optional custom class name. Backend models mirror this, and the Kotlin code generator produces `@Serializable` data classes with `@property:LLMDescription` annotations. Legacy single-field graphs are migrated on load.

**Tech Stack:** TypeScript/React (frontend), Kotlin/Spring Boot (backend), Zustand (state), shadcn/ui (components)

---

### Task 1: Update Frontend Type Definitions

**Files:**
- Modify: `frontend/types/io.ts`

- [ ] **Step 1: Replace io.ts with new type system**

Replace the entire contents of `frontend/types/io.ts`:

```typescript
export type IOFieldType = "String" | "Int" | "Double" | "Boolean" | "List" | "Map";

export const IO_FIELD_TYPES: IOFieldType[] = ["String", "Int", "Double", "Boolean", "List", "Map"];

export const IO_FIELD_TYPE_LABELS: Record<IOFieldType, string> = {
  String: "String",
  Int: "Int",
  Double: "Double",
  Boolean: "Boolean",
  List: "List<Any>",
  Map: "Map<String, Any>",
};

export interface IOField {
  id: string;
  name: string;
  type: IOFieldType;
  description: string;
  optional: boolean;
}

export interface InputNodeConfig {
  className?: string;
  fields: IOField[];
}

export interface OutputNodeConfig {
  className?: string;
  fields: IOField[];
}

/** Migrate old flat format to new fields format */
export function migrateIONodeData(data: Record<string, unknown>): InputNodeConfig | OutputNodeConfig {
  if (Array.isArray(data.fields)) {
    return data as unknown as InputNodeConfig | OutputNodeConfig;
  }
  // Legacy format: { name, description, dataType, required?, defaultValue? }
  const legacyTypeMap: Record<string, IOFieldType> = {
    string: "String",
    number: "Double",
    boolean: "Boolean",
    object: "Map",
    array: "List",
  };
  const field: IOField = {
    id: crypto.randomUUID(),
    name: (data.name as string) || "",
    type: legacyTypeMap[(data.dataType as string) || "string"] || "String",
    description: (data.description as string) || "",
    optional: data.required === false,
  };
  const result: InputNodeConfig | OutputNodeConfig = {
    className: undefined,
    fields: field.name ? [field] : [],
  };
  return result;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/types/io.ts
git commit -m "feat(io): replace flat I/O types with multi-field structured types"
```

---

### Task 2: Update Editor Store Defaults and Migration

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Update default data and imports**

In `frontend/store/editorStore.ts`, change the import on line 8:

```typescript
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
```

Replace the default data objects (lines 70-81):

```typescript
const defaultInputData: InputNodeConfig = {
  fields: [],
};

const defaultOutputData: OutputNodeConfig = {
  fields: [],
};
```

- [ ] **Step 2: Add migration to graph loading**

In `frontend/store/editorStore.ts`, add migration import at top:

```typescript
import { InputNodeConfig, OutputNodeConfig, migrateIONodeData } from "@/types/io";
```

In `applyRemoteGraphReset` (around line 303), add migration before setting state:

```typescript
applyRemoteGraphReset: (graph) => {
    const migratedNodes = graph.nodes.map((n) => {
      if ((n.type === "input" || n.type === "output") && !Array.isArray((n.data as Record<string, unknown>).fields)) {
        return { ...n, data: migrateIONodeData(n.data as Record<string, unknown>) };
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
git commit -m "feat(io): update store defaults and add legacy migration"
```

---

### Task 3: Rewrite InputProperties Component

**Files:**
- Modify: `frontend/components/panels/InputProperties.tsx`

- [ ] **Step 1: Rewrite InputProperties with field list editor**

Replace the entire contents of `frontend/components/panels/InputProperties.tsx`:

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { InputNodeConfig, IOField, IOFieldType } from "@/types/io";
import { IO_FIELD_TYPE_LABELS, IO_FIELD_TYPES } from "@/types/io";
import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react";

export function InputProperties({ nodeId, data }: { nodeId: string; data: InputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const updateFields = (fields: IOField[]) => updateNodeData(nodeId, { fields });

  const addField = () => {
    const field: IOField = {
      id: crypto.randomUUID(),
      name: "",
      type: "String",
      description: "",
      optional: false,
    };
    updateFields([...data.fields, field]);
    setExpandedIds((s) => new Set(s).add(field.id));
  };

  const updateField = (id: string, patch: Partial<IOField>) => {
    updateFields(data.fields.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  };

  const removeField = (id: string) => {
    updateFields(data.fields.filter((f) => f.id !== id));
    setExpandedIds((s) => { const next = new Set(s); next.delete(id); return next; });
  };

  const toggleExpand = (id: string) => {
    setExpandedIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleDragStart = (idx: number) => setDragIdx(idx);
  const handleDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const reordered = [...data.fields];
    const [moved] = reordered.splice(dragIdx, 1);
    reordered.splice(idx, 0, moved);
    updateFields(reordered);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="io-classname">Class Name</Label>
        <Input
          id="io-classname"
          value={data.className || ""}
          onChange={(e) => updateNodeData(nodeId, { className: e.target.value || undefined })}
          placeholder="Auto-generated from agent name"
          className="text-xs"
        />
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Fields</Label>
          <button onClick={addField} className="flex items-center gap-1 text-xs text-primary hover:underline">
            <Plus className="w-3 h-3" /> Add Field
          </button>
        </div>

        {data.fields.length === 0 && (
          <p className="text-xs text-muted-foreground">No fields yet. Add a field to define the input structure.</p>
        )}

        <div className="space-y-1">
          {data.fields.map((field, idx) => {
            const expanded = expandedIds.has(field.id);
            return (
              <div
                key={field.id}
                draggable
                onDragStart={() => handleDragStart(idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDragEnd={handleDragEnd}
                className="border rounded-md bg-muted/30"
              >
                <div className="flex items-center gap-1 px-2 py-1.5 cursor-pointer" onClick={() => toggleExpand(field.id)}>
                  <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab shrink-0" />
                  <button className="shrink-0">
                    {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                  </button>
                  <span className="text-xs font-medium truncate">{field.name || "unnamed"}</span>
                  <span className="text-[10px] text-muted-foreground ml-auto shrink-0">{field.type}{field.optional ? "?" : ""}</span>
                  <button
                    onClick={(e) => { e.stopPropagation(); removeField(field.id); }}
                    className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
                {expanded && (
                  <div className="px-3 pb-3 space-y-2 border-t">
                    <div className="pt-2">
                      <Label className="text-xs">Name</Label>
                      <Input
                        value={field.name}
                        onChange={(e) => updateField(field.id, { name: e.target.value })}
                        placeholder="fieldName"
                        className="h-7 text-xs"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Type</Label>
                      <Select value={field.type} onValueChange={(v) => updateField(field.id, { type: v as IOFieldType })}>
                        <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {IO_FIELD_TYPES.map((t) => (
                            <SelectItem key={t} value={t}>{IO_FIELD_TYPE_LABELS[t]}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <Label className="text-xs">Description (LLMDescription)</Label>
                      <Textarea
                        value={field.description}
                        onChange={(e) => updateField(field.id, { description: e.target.value })}
                        rows={2}
                        placeholder="What this field represents"
                        className="text-xs"
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <Label className="text-xs">Optional</Label>
                      <button
                        onClick={() => updateField(field.id, { optional: !field.optional })}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${field.optional ? "bg-primary" : "bg-muted"}`}
                      >
                        <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${field.optional ? "translate-x-4.5" : "translate-x-0.5"}`} />
                      </button>
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

- [ ] **Step 2: Verify no TypeScript errors**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/InputProperties.tsx
git commit -m "feat(io): rewrite InputProperties with multi-field editor"
```

---

### Task 4: Rewrite OutputProperties Component

**Files:**
- Modify: `frontend/components/panels/OutputProperties.tsx`

- [ ] **Step 1: Rewrite OutputProperties with field list editor**

Replace the entire contents of `frontend/components/panels/OutputProperties.tsx`. This is nearly identical to InputProperties but uses `OutputNodeConfig` and has no "Optional" toggle (output fields are always required unless explicitly toggled):

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { OutputNodeConfig, IOField, IOFieldType } from "@/types/io";
import { IO_FIELD_TYPE_LABELS, IO_FIELD_TYPES } from "@/types/io";
import { useState } from "react";
import { ChevronDown, ChevronRight, GripVertical, Plus, Trash2 } from "lucide-react";

export function OutputProperties({ nodeId, data }: { nodeId: string; data: OutputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  const updateFields = (fields: IOField[]) => updateNodeData(nodeId, { fields });

  const addField = () => {
    const field: IOField = {
      id: crypto.randomUUID(),
      name: "",
      type: "String",
      description: "",
      optional: false,
    };
    updateFields([...data.fields, field]);
    setExpandedIds((s) => new Set(s).add(field.id));
  };

  const updateField = (id: string, patch: Partial<IOField>) => {
    updateFields(data.fields.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  };

  const removeField = (id: string) => {
    updateFields(data.fields.filter((f) => f.id !== id));
    setExpandedIds((s) => { const next = new Set(s); next.delete(id); return next; });
  };

  const toggleExpand = (id: string) => {
    setExpandedIds((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const handleDragStart = (idx: number) => setDragIdx(idx);
  const handleDragOver = (e: React.DragEvent, idx: number) => {
    e.preventDefault();
    if (dragIdx === null || dragIdx === idx) return;
    const reordered = [...data.fields];
    const [moved] = reordered.splice(dragIdx, 1);
    reordered.splice(idx, 0, moved);
    updateFields(reordered);
    setDragIdx(idx);
  };
  const handleDragEnd = () => setDragIdx(null);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="io-classname">Class Name</Label>
        <Input
          id="io-classname"
          value={data.className || ""}
          onChange={(e) => updateNodeData(nodeId, { className: e.target.value || undefined })}
          placeholder="Auto-generated from agent name"
          className="text-xs"
        />
      </div>

      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Fields</Label>
          <button onClick={addField} className="flex items-center gap-1 text-xs text-primary hover:underline">
            <Plus className="w-3 h-3" /> Add Field
          </button>
        </div>

        {data.fields.length === 0 && (
          <p className="text-xs text-muted-foreground">No fields yet. Add a field to define the output structure.</p>
        )}

        <div className="space-y-1">
          {data.fields.map((field, idx) => {
            const expanded = expandedIds.has(field.id);
            return (
              <div
                key={field.id}
                draggable
                onDragStart={() => handleDragStart(idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDragEnd={handleDragEnd}
                className="border rounded-md bg-muted/30"
              >
                <div className="flex items-center gap-1 px-2 py-1.5 cursor-pointer" onClick={() => toggleExpand(field.id)}>
                  <GripVertical className="w-3 h-3 text-muted-foreground cursor-grab shrink-0" />
                  <button className="shrink-0">
                    {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                  </button>
                  <span className="text-xs font-medium truncate">{field.name || "unnamed"}</span>
                  <span className="text-[10px] text-muted-foreground ml-auto shrink-0">{field.type}{field.optional ? "?" : ""}</span>
                  <button
                    onClick={(e) => { e.stopPropagation(); removeField(field.id); }}
                    className="text-muted-foreground hover:text-destructive shrink-0 ml-1"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
                {expanded && (
                  <div className="px-3 pb-3 space-y-2 border-t">
                    <div className="pt-2">
                      <Label className="text-xs">Name</Label>
                      <Input
                        value={field.name}
                        onChange={(e) => updateField(field.id, { name: e.target.value })}
                        placeholder="fieldName"
                        className="h-7 text-xs"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Type</Label>
                      <Select value={field.type} onValueChange={(v) => updateField(field.id, { type: v as IOFieldType })}>
                        <SelectTrigger className="h-7 text-xs"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          {IO_FIELD_TYPES.map((t) => (
                            <SelectItem key={t} value={t}>{IO_FIELD_TYPE_LABELS[t]}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div>
                      <Label className="text-xs">Description (LLMDescription)</Label>
                      <Textarea
                        value={field.description}
                        onChange={(e) => updateField(field.id, { description: e.target.value })}
                        rows={2}
                        placeholder="What this field represents"
                        className="text-xs"
                      />
                    </div>
                    <div className="flex items-center justify-between">
                      <Label className="text-xs">Optional</Label>
                      <button
                        onClick={() => updateField(field.id, { optional: !field.optional })}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${field.optional ? "bg-primary" : "bg-muted"}`}
                      >
                        <span className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform ${field.optional ? "translate-x-4.5" : "translate-x-0.5"}`} />
                      </button>
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

- [ ] **Step 2: Commit**

```bash
git add frontend/components/panels/OutputProperties.tsx
git commit -m "feat(io): rewrite OutputProperties with multi-field editor"
```

---

### Task 5: Update IOInspection Component

**Files:**
- Modify: `frontend/components/panels/IOInspection.tsx`

- [ ] **Step 1: Update IOInspection to show field count instead of single dataType**

In `frontend/components/panels/IOInspection.tsx`, update the import on line 5:

```typescript
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
```

Remove the `IO_DATA_TYPE_LABELS` import (no longer exists).

Replace the input item rendering (lines 36-54) with:

```tsx
{inputs.map((n) => {
  const d = n.data as InputNodeConfig;
  const fieldCount = d.fields?.length ?? 0;
  const hasOptional = d.fields?.some((f) => f.optional) ?? false;
  return (
    <button
      key={n.id}
      onClick={() => setSelectedNodeIds([n.id])}
      className="w-full flex items-center gap-1.5 text-xs px-2 py-1 rounded hover:bg-muted/50 text-left transition-colors"
    >
      <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 shrink-0" />
      <span className="truncate font-medium">{d.className || "Input"}</span>
      <span className="text-muted-foreground ml-auto shrink-0">
        {fieldCount} {fieldCount === 1 ? "field" : "fields"}
      </span>
      {hasOptional && (
        <span className="text-[10px] text-muted-foreground shrink-0">opt</span>
      )}
    </button>
  );
})}
```

Replace the output item rendering (lines 65-79) with:

```tsx
{outputs.map((n) => {
  const d = n.data as OutputNodeConfig;
  const fieldCount = d.fields?.length ?? 0;
  return (
    <button
      key={n.id}
      onClick={() => setSelectedNodeIds([n.id])}
      className="w-full flex items-center gap-1.5 text-xs px-2 py-1 rounded hover:bg-muted/50 text-left transition-colors"
    >
      <span className="w-1.5 h-1.5 rounded-full bg-orange-500 shrink-0" />
      <span className="truncate font-medium">{d.className || "Output"}</span>
      <span className="text-muted-foreground ml-auto shrink-0">
        {fieldCount} {fieldCount === 1 ? "field" : "fields"}
      </span>
    </button>
  );
})}
```

- [ ] **Step 2: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -30`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/IOInspection.tsx
git commit -m "feat(io): update IOInspection to show field counts"
```

---

### Task 6: Update Backend IOModel

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/IOModel.kt`

- [ ] **Step 1: Replace IOModel with field-based structure**

Replace the entire contents of `src/main/kotlin/com/agentwork/modeler/export/model/IOModel.kt`:

```kotlin
package com.agentwork.modeler.export.model

data class KoogField(
    val name: String,
    val type: String,
    val description: String,
    val optional: Boolean
)

data class KoogInput(
    val className: String?,
    val fields: List<KoogField>
)

data class KoogOutput(
    val className: String?,
    val fields: List<KoogField>
)
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/IOModel.kt
git commit -m "feat(io): replace flat IOModel with field-based KoogField structure"
```

---

### Task 7: Update GraphToKoogAdapter

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`

- [ ] **Step 1: Update convertInput and convertOutput methods**

Replace `convertInput` (lines 422-430) with:

```kotlin
@Suppress("UNCHECKED_CAST")
private fun convertInput(node: GraphNode): KoogInput {
    val className = node.data["className"] as? String
    val rawFields = node.data["fields"] as? List<Map<String, Any?>> ?: emptyList()
    val fields = rawFields.map { f ->
        KoogField(
            name = f["name"] as? String ?: "",
            type = f["type"] as? String ?: "String",
            description = f["description"] as? String ?: "",
            optional = f["optional"] as? Boolean ?: false
        )
    }
    return KoogInput(className = className, fields = fields)
}
```

Replace `convertOutput` (lines 432-438) with:

```kotlin
@Suppress("UNCHECKED_CAST")
private fun convertOutput(node: GraphNode): KoogOutput {
    val className = node.data["className"] as? String
    val rawFields = node.data["fields"] as? List<Map<String, Any?>> ?: emptyList()
    val fields = rawFields.map { f ->
        KoogField(
            name = f["name"] as? String ?: "",
            type = f["type"] as? String ?: "String",
            description = f["description"] as? String ?: "",
            optional = f["optional"] as? Boolean ?: false
        )
    }
    return KoogOutput(className = className, fields = fields)
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | tail -10`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt
git commit -m "feat(io): update adapter to extract fields and className from graph nodes"
```

---

### Task 8: Rewrite InputOutputGenerator with LLMDescription

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt`

- [ ] **Step 1: Write failing tests for the new generator**

Replace the entire contents of `src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogField
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class InputOutputGeneratorTest {

    private val generator = InputOutputGenerator()

    @Test
    fun `generates input class with multiple fields and LLMDescription`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("successful", "Boolean", "Was the order successful?", false),
                KoogField("comment", "String", "Optional comment", true),
                KoogField("totalPrice", "Int", "Total price in USD", false)
            )
        )
        val code = generator.generateInputClass("OrderAgent", input)!!
        assertContains(code, "data class OrderAgentInput(")
        assertContains(code, "@Serializable")
        assertContains(code, "import kotlinx.serialization.Serializable")
        assertContains(code, "import ai.koog.agents.core.tools.annotations.LLMDescription")
        assertContains(code, "@property:LLMDescription(\"Was the order successful?\")")
        assertContains(code, "val successful: Boolean,")
        assertContains(code, "val totalPrice: Int,")
        assertContains(code, "@property:LLMDescription(\"Optional comment\")")
        assertContains(code, "val comment: String? = null")
    }

    @Test
    fun `uses custom className when provided`() {
        val input = KoogInput(
            className = "FinalOrderResult",
            fields = listOf(KoogField("ok", "Boolean", "", false))
        )
        val code = generator.generateInputClass("OrderAgent", input)!!
        assertContains(code, "data class FinalOrderResult(")
    }

    @Test
    fun `omits LLMDescription when description is empty`() {
        val input = KoogInput(
            className = null,
            fields = listOf(KoogField("query", "String", "", false))
        )
        val code = generator.generateInputClass("Test", input)!!
        assert(!code.contains("LLMDescription"))
        assertContains(code, "val query: String")
    }

    @Test
    fun `maps all field types correctly`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("a", "String", "", false),
                KoogField("b", "Int", "", false),
                KoogField("c", "Double", "", false),
                KoogField("d", "Boolean", "", false),
                KoogField("e", "List", "", false),
                KoogField("f", "Map", "", false)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        assertContains(code, "val a: String")
        assertContains(code, "val b: Int")
        assertContains(code, "val c: Double")
        assertContains(code, "val d: Boolean")
        assertContains(code, "val e: List<Any>")
        assertContains(code, "val f: Map<String, Any>")
    }

    @Test
    fun `optional fields get nullable type with null default`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("name", "String", "", false),
                KoogField("age", "Int", "", true)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        assertContains(code, "val name: String,")
        assertContains(code, "val age: Int? = null")
    }

    @Test
    fun `returns null for empty fields`() {
        assertNull(generator.generateInputClass("Test", KoogInput(null, emptyList())))
        assertNull(generator.generateOutputClass("Test", KoogOutput(null, emptyList())))
    }

    @Test
    fun `generates output class with fields and LLMDescription`() {
        val output = KoogOutput(
            className = "OrderResult",
            fields = listOf(
                KoogField("status", "String", "Order status", false),
                KoogField("details", "String", "Extra info", true)
            )
        )
        val code = generator.generateOutputClass("OrderAgent", output)!!
        assertContains(code, "data class OrderResult(")
        assertContains(code, "@property:LLMDescription(\"Order status\")")
        assertContains(code, "val status: String,")
        assertContains(code, "val details: String? = null")
    }

    @Test
    fun `required fields come before optional fields`() {
        val input = KoogInput(
            className = null,
            fields = listOf(
                KoogField("opt1", "String", "", true),
                KoogField("req1", "String", "", false),
                KoogField("opt2", "Int", "", true),
                KoogField("req2", "Boolean", "", false)
            )
        )
        val code = generator.generateInputClass("Test", input)!!
        val req1Idx = code.indexOf("val req1: String")
        val req2Idx = code.indexOf("val req2: Boolean")
        val opt1Idx = code.indexOf("val opt1: String?")
        val opt2Idx = code.indexOf("val opt2: Int?")
        assert(req1Idx < opt1Idx) { "Required fields should come before optional" }
        assert(req2Idx < opt1Idx) { "Required fields should come before optional" }
        assert(opt1Idx < opt2Idx) { "Optional fields should maintain relative order" }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*.InputOutputGeneratorTest" 2>&1 | tail -20`
Expected: Compilation failure (new KoogInput/KoogOutput signatures)

- [ ] **Step 3: Rewrite InputOutputGenerator**

Replace the entire contents of `src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogField
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.springframework.stereotype.Component

@Component
class InputOutputGenerator {

    private fun kotlinType(fieldType: String): String = when (fieldType) {
        "Int" -> "Int"
        "Double" -> "Double"
        "Boolean" -> "Boolean"
        "List" -> "List<Any>"
        "Map" -> "Map<String, Any>"
        else -> "String"
    }

    private fun generateFields(fields: List<KoogField>): String {
        val required = fields.filter { !it.optional }
        val optional = fields.filter { it.optional }
        val all = required + optional

        return buildString {
            for ((i, field) in all.withIndex()) {
                val kt = kotlinType(field.type)
                val comma = if (i < all.size - 1) "," else ""
                if (field.description.isNotBlank()) {
                    appendLine("    @property:LLMDescription(\"${field.description}\")")
                }
                if (field.optional) {
                    appendLine("    val ${field.name}: $kt? = null$comma")
                } else {
                    appendLine("    val ${field.name}: $kt$comma")
                }
            }
        }
    }

    private fun needsLLMDescriptionImport(fields: List<KoogField>): Boolean =
        fields.any { it.description.isNotBlank() }

    fun generateInputClass(agentName: String, input: KoogInput): String? {
        if (input.fields.isEmpty()) return null
        val className = input.className?.ifBlank { null }
            ?: "${agentName.replaceFirstChar { it.uppercase() }}Input"
        return buildClassFile(className, input.fields)
    }

    fun generateOutputClass(agentName: String, output: KoogOutput): String? {
        if (output.fields.isEmpty()) return null
        val className = output.className?.ifBlank { null }
            ?: "${agentName.replaceFirstChar { it.uppercase() }}Output"
        return buildClassFile(className, output.fields)
    }

    private fun buildClassFile(className: String, fields: List<KoogField>): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import kotlinx.serialization.Serializable")
        if (needsLLMDescriptionImport(fields)) {
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
        }
        appendLine()
        appendLine("@Serializable")
        appendLine("data class $className(")
        append(generateFields(fields))
        appendLine(")")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*.InputOutputGeneratorTest" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt
git commit -m "feat(io): rewrite InputOutputGenerator with @LLMDescription and field-based model"
```

---

### Task 9: Update ExportService for New I/O Model

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Update ioClassNames to respect custom className**

Replace the `ioClassNames` method (lines 299-303) with:

```kotlin
private fun ioClassNames(name: String, inputs: List<KoogInput>, outputs: List<KoogOutput>): Pair<String?, String?> {
    val inputClassName = inputs.firstOrNull()?.let {
        it.className?.ifBlank { null } ?: "${name.replaceFirstChar { c -> c.uppercase() }}Input"
    }
    val outputClassName = outputs.firstOrNull()?.let {
        it.className?.ifBlank { null } ?: "${name.replaceFirstChar { c -> c.uppercase() }}Output"
    }
    return Pair(inputClassName, outputClassName)
}
```

- [ ] **Step 2: Update all generateInputClass/generateOutputClass call sites**

The old signature was `generateInputClass(name, List<KoogInput>)` — now it's `generateInputClass(name, KoogInput)`. Since an agent can have multiple connected input nodes, we need to merge them into one.

Add a helper method in ExportService:

```kotlin
private fun mergedInput(inputs: List<KoogInput>): KoogInput {
    val className = inputs.firstNotNullOfOrNull { it.className?.ifBlank { null } }
    val fields = inputs.flatMap { it.fields }
    return KoogInput(className = className, fields = fields)
}

private fun mergedOutput(outputs: List<KoogOutput>): KoogOutput {
    val className = outputs.firstNotNullOfOrNull { it.className?.ifBlank { null } }
    val fields = outputs.flatMap { it.fields }
    return KoogOutput(className = className, fields = fields)
}
```

Update the I/O class generation blocks. For example, lines 283-290 become:

```kotlin
// Input/Output classes
val mergedIn = mergedInput(agent.inputs)
val inputCode = inputOutputGenerator.generateInputClass(agent.name, mergedIn)
if (inputCode != null) {
    val inName = mergedIn.className?.ifBlank { null } ?: "${agent.name.replaceFirstChar { it.uppercase() }}Input"
    files["$pkg/$inName.kt"] = inputCode
}
val mergedOut = mergedOutput(agent.outputs)
val outputCode = inputOutputGenerator.generateOutputClass(agent.name, mergedOut)
if (outputCode != null) {
    val outName = mergedOut.className?.ifBlank { null } ?: "${agent.name.replaceFirstChar { it.uppercase() }}Output"
    files["$pkg/$outName.kt"] = outputCode
}
```

Apply the same pattern to planner agent (lines 118-126) and graph agent I/O generation blocks.

Also update ioClassNames calls to use the merged models:

```kotlin
val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
```

This still works because `ioClassNames` uses `inputs.firstOrNull()`.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | tail -10`

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test 2>&1 | tail -20`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat(io): update ExportService for merged field-based I/O model"
```

---

### Task 10: Fix Remaining Compilation Errors and Integration Test

**Files:**
- Potentially modify: any files with references to old `KoogInput(name, description, dataType, required, defaultValue)` or `KoogOutput(name, description, dataType)` constructors

- [ ] **Step 1: Search for all old constructor usages**

Run: `grep -rn "KoogInput(" src/ --include="*.kt" | grep -v "IOModel.kt"`
Run: `grep -rn "KoogOutput(" src/ --include="*.kt" | grep -v "IOModel.kt"`

Fix each call site to use the new signatures. Common pattern:

Old: `KoogInput("query", "Search query", "string", true, null)`
New: `KoogInput(null, listOf(KoogField("query", "String", "Search query", false)))`

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test 2>&1 | tail -30`
Expected: All tests PASS (except known MCP test failures per project_known_issues)

- [ ] **Step 3: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit --pretty 2>&1 | head -30`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "fix(io): update all call sites for new KoogInput/KoogOutput signatures"
```
