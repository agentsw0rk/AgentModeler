# Code-Editor for Tool-Bodies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace plain textarea/input fields for code content with Monaco Editor, providing Kotlin syntax highlighting, bracket matching, line numbers, and autocomplete.

**Architecture:** A single reusable `CodeEditor` component wrapping `@monaco-editor/react` with SSR-safe dynamic import. Custom dark theme registered on first mount. Integrated at three locations: tool function body, condition-node condition, and edge condition.

**Tech Stack:** `@monaco-editor/react`, Monaco Editor (CDN-loaded), Next.js 16 dynamic imports, Tailwind CSS

---

### Task 1: Install Monaco Editor dependency

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install @monaco-editor/react**

```bash
cd frontend && npm install @monaco-editor/react
```

- [ ] **Step 2: Verify installation**

```bash
cd frontend && node -e "require.resolve('@monaco-editor/react')" && echo "OK"
```

Expected: prints the resolved path and "OK"

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "feat: add @monaco-editor/react dependency"
```

---

### Task 2: Create CodeEditor component

**Files:**
- Create: `frontend/components/shared/CodeEditor.tsx`

- [ ] **Step 1: Create the CodeEditor component**

```tsx
"use client";

import dynamic from "next/dynamic";
import type { EditorProps, Monaco } from "@monaco-editor/react";

const MonacoEditor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => (
    <div className="w-full rounded-md border border-border bg-card min-h-[120px] animate-pulse" />
  ),
});

function handleBeforeMount(monaco: Monaco) {
  monaco.editor.defineTheme("modeler-dark", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#00000000",
      "editor.lineHighlightBackground": "#ffffff08",
      "editor.selectionBackground": "#3b82f640",
      "editorCursor.foreground": "#6d8ee8",
    },
  });
}

const DEFAULT_OPTIONS: EditorProps["options"] = {
  minimap: { enabled: false },
  fontSize: 12,
  lineNumbers: "on",
  lineDecorationsWidth: 0,
  lineNumbersMinChars: 3,
  wordWrap: "on",
  scrollBeyondLastLine: false,
  automaticLayout: true,
  bracketPairColorization: { enabled: true },
  autoClosingBrackets: "always",
  tabSize: 4,
  suggestOnTriggerCharacters: true,
  quickSuggestions: true,
  scrollbar: { vertical: "auto", horizontal: "hidden" },
  overviewRulerLanes: 0,
  hideCursorInOverviewRuler: true,
  overviewRulerBorder: false,
  renderLineHighlight: "line",
  contextmenu: false,
  padding: { top: 8, bottom: 8 },
};

interface CodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  language?: string;
  height?: string;
  readOnly?: boolean;
  placeholder?: string;
}

export default function CodeEditor({
  value,
  onChange,
  language = "kotlin",
  height = "120px",
  readOnly = false,
  placeholder,
}: CodeEditorProps) {
  return (
    <div className="w-full rounded-md border border-border overflow-hidden resize-y min-h-[120px]" style={{ height }}>
      <MonacoEditor
        value={value}
        onChange={(v) => onChange(v ?? "")}
        language={language}
        theme="modeler-dark"
        beforeMount={handleBeforeMount}
        options={{
          ...DEFAULT_OPTIONS,
          readOnly,
          ...(placeholder && !value ? {} : {}),
        }}
      />
    </div>
  );
}
```

- [ ] **Step 2: Verify the component builds**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

Expected: Build succeeds without errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/components/shared/CodeEditor.tsx
git commit -m "feat: add CodeEditor component wrapping Monaco Editor"
```

---

### Task 3: Integrate CodeEditor in ToolProperties (Function Body)

**Files:**
- Modify: `frontend/components/panels/ToolProperties.tsx:293-303`

- [ ] **Step 1: Add import at top of ToolProperties.tsx**

Add this import alongside the existing imports:

```tsx
import CodeEditor from "@/components/shared/CodeEditor";
```

- [ ] **Step 2: Replace the textarea with CodeEditor**

Replace lines 296-302 (the `<textarea>` block inside `{/* Function Body */}`):

```tsx
                      <textarea
                        value={fn.body || ""}
                        onChange={(e) => updateFunction(fn.id, { body: e.target.value })}
                        rows={6}
                        placeholder={fn.returnType === "Unit" ? "// Implementation code" : 'return "Not implemented"'}
                        className="w-full rounded-md border bg-background px-3 py-2 text-xs font-mono resize-y min-h-[80px] focus:outline-none focus:ring-2 focus:ring-ring"
                      />
```

With:

```tsx
                      <CodeEditor
                        value={fn.body || ""}
                        onChange={(v) => updateFunction(fn.id, { body: v })}
                        height="160px"
                      />
```

- [ ] **Step 3: Verify visually**

```bash
cd frontend && npm run dev
```

Open the app, select a Tool node with a function, verify:
- Monaco editor appears instead of textarea
- Kotlin syntax highlighting works
- Existing body content loads correctly
- Typing updates the value
- Dark theme matches the app

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/ToolProperties.tsx
git commit -m "feat: use CodeEditor for tool function body field"
```

---

### Task 4: Integrate CodeEditor in Condition-Node Properties

**Files:**
- Modify: `frontend/components/graph-agent/StrategyNodeProperties.tsx:50-55`

- [ ] **Step 1: Add import at top of StrategyNodeProperties.tsx**

```tsx
import CodeEditor from "@/components/shared/CodeEditor";
```

- [ ] **Step 2: Replace the Input with CodeEditor for condition nodes**

Replace lines 50-55:

```tsx
      {node.type === "node-condition" && (
        <div>
          <Label>Condition</Label>
          <Input value={d.condition ?? ""} onChange={(e) => onUpdate({ condition: e.target.value })} placeholder="result.isNotEmpty()" />
        </div>
      )}
```

With:

```tsx
      {node.type === "node-condition" && (
        <div>
          <Label>Condition</Label>
          <CodeEditor
            value={d.condition ?? ""}
            onChange={(v) => onUpdate({ condition: v })}
            height="80px"
          />
        </div>
      )}
```

- [ ] **Step 3: Verify visually**

Open the app, navigate to a Graph-Agent strategy editor, select a condition node:
- CodeEditor appears for the condition field
- Kotlin highlighting works
- Existing condition values load correctly
- Changes persist

- [ ] **Step 4: Commit**

```bash
git add frontend/components/graph-agent/StrategyNodeProperties.tsx
git commit -m "feat: use CodeEditor for condition-node condition field"
```

---

### Task 5: Integrate CodeEditor in Edge Properties (Condition + Transformation)

**Files:**
- Modify: `frontend/components/graph-agent/StrategyEdgeProperties.tsx:29-40`

- [ ] **Step 1: Add import at top of StrategyEdgeProperties.tsx**

```tsx
import CodeEditor from "@/components/shared/CodeEditor";
```

- [ ] **Step 2: Replace the condition and transformation Inputs with CodeEditor**

Replace lines 29-40:

```tsx
      {edge.edgeType === "onCondition" && (
        <>
          <div>
            <Label>Condition</Label>
            <Input value={edge.condition ?? ""} onChange={(e) => onUpdate({ condition: e.target.value })} placeholder="true" />
          </div>
          <div>
            <Label>Transformation (optional)</Label>
            <Input value={edge.transformation ?? ""} onChange={(e) => onUpdate({ transformation: e.target.value || undefined })} placeholder="it.uppercase()" />
          </div>
        </>
      )}
```

With:

```tsx
      {edge.edgeType === "onCondition" && (
        <>
          <div>
            <Label>Condition</Label>
            <CodeEditor
              value={edge.condition ?? ""}
              onChange={(v) => onUpdate({ condition: v })}
              height="80px"
            />
          </div>
          <div>
            <Label>Transformation (optional)</Label>
            <CodeEditor
              value={edge.transformation ?? ""}
              onChange={(v) => onUpdate({ transformation: v || undefined })}
              height="80px"
            />
          </div>
        </>
      )}
```

- [ ] **Step 3: Verify visually**

Open the app, navigate to a Graph-Agent strategy editor, select an onCondition edge:
- CodeEditor appears for condition and transformation fields
- Kotlin highlighting works
- Changes persist

- [ ] **Step 4: Commit**

```bash
git add frontend/components/graph-agent/StrategyEdgeProperties.tsx
git commit -m "feat: use CodeEditor for edge condition and transformation fields"
```

---

### Task 6: Build verification and final test

**Files:**
- No new files

- [ ] **Step 1: Run production build**

```bash
cd frontend && npx next build
```

Expected: Build succeeds with no errors.

- [ ] **Step 2: Verify all three integration points**

Manual check in dev mode:
1. Tool node → Function Body → Monaco editor with Kotlin highlighting
2. Graph-Agent → Condition node → Monaco editor for condition
3. Graph-Agent → onCondition edge → Monaco editor for condition + transformation

- [ ] **Step 3: Verify data persistence**

1. Enter code in each editor field
2. Switch to another node and back
3. Verify the code is preserved
4. Save/export the project and verify the code strings are in the JSON

- [ ] **Step 4: Final commit if any adjustments were needed**

```bash
git add -A
git commit -m "feat: complete code-editor integration for tool bodies and conditions"
```
