# Frontend Canvas Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a visual node-based editor where developers can model Koog agents with tools on a canvas, configure them via properties panel, validate the graph, and persist projects as JSON.

**Architecture:** Next.js App Router frontend with Rete.js as canvas engine, shadcn/ui for all UI chrome (panels, dialogs, forms), and Zustand for editor state management. The editor produces a JSON graph model that the backend Kotlin export consumes.

**Tech Stack:** Next.js 14+ (App Router), TypeScript, Rete.js 2, shadcn/ui, Zustand, Zod, Tailwind CSS

**Note:** The Feature.md spec mentions Input/Output nodes as part of the MVP scope. These are deferred to a follow-up iteration after the core Agent/Tool node flow is working end-to-end. The type system (`NodeType`) is extensible to add `"input" | "output"` later.

**API Configuration:** The frontend communicates with the Spring Boot backend (port 8080) via `NEXT_PUBLIC_API_URL` environment variable. Default: `http://localhost:8080`. CORS is configured on the backend side.

---

## File Structure

```
frontend/
├── package.json
├── tsconfig.json
├── tailwind.config.ts
├── next.config.ts
├── app/
│   ├── layout.tsx                    # Root layout with providers
│   ├── page.tsx                      # Main editor page
│   └── globals.css                   # Tailwind + custom styles
├── components/
│   ├── editor/
│   │   ├── Canvas.tsx                # Rete.js canvas wrapper
│   │   ├── nodes/
│   │   │   ├── AgentNode.tsx         # Agent node Rete component
│   │   │   ├── ToolNode.tsx          # Tool node Rete component
│   │   │   └── BaseNode.tsx          # Shared node rendering
│   │   ├── connections/
│   │   │   └── Connection.tsx        # Custom connection rendering
│   │   └── controls/
│   │       └── NodeControl.tsx       # Inline node controls
│   ├── panels/
│   │   ├── Toolbar.tsx               # Top toolbar (add nodes, actions)
│   │   ├── PropertiesPanel.tsx       # Right sidebar properties editor
│   │   ├── AgentProperties.tsx       # Agent-specific form fields
│   │   └── ToolProperties.tsx        # Tool-specific form fields
│   ├── dialogs/
│   │   ├── ExportDialog.tsx          # Export options dialog
│   │   └── SaveLoadDialog.tsx        # Save/Load project dialog
│   └── validation/
│       └── ValidationPanel.tsx       # Error/warning display
├── store/
│   ├── editorStore.ts                # Zustand store: nodes, edges, selection
│   └── validationStore.ts           # Zustand store: validation results
├── lib/
│   ├── rete-setup.ts                 # Rete.js editor factory + plugins
│   ├── validation.ts                 # Graph validation rules
│   ├── persistence.ts                # JSON save/load logic
│   └── export-client.ts             # API client for Kotlin export
├── types/
│   ├── graph.ts                      # Node, Edge, Graph types
│   └── koog.ts                       # Koog-specific model types (Agent, Tool, Parameter)
└── __tests__/
    ├── store/
    │   ├── editorStore.test.ts
    │   └── validationStore.test.ts
    ├── lib/
    │   ├── validation.test.ts
    │   └── persistence.test.ts
    └── components/
        └── panels/
            └── PropertiesPanel.test.tsx
```

---

### Task 1: Next.js Project Setup

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tailwind.config.ts`
- Create: `frontend/next.config.ts`
- Create: `frontend/app/layout.tsx`
- Create: `frontend/app/page.tsx`
- Create: `frontend/app/globals.css`

- [ ] **Step 1: Initialize Next.js project**

```bash
cd /Users/czarnik/IdeaProjects/modeler
npx create-next-app@latest frontend --typescript --tailwind --eslint --app --src-dir=false --import-alias="@/*" --no-turbopack
```

- [ ] **Step 2: Install core dependencies**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend
npm install zustand zod rete rete-area-plugin rete-connection-plugin rete-render-utils rete-react-plugin rete-context-menu-plugin styled-components
npm install -D @testing-library/react @testing-library/jest-dom jest jest-environment-jsdom @types/jest ts-jest @types/styled-components
```

- [ ] **Step 3: Install shadcn/ui**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend
npx shadcn@latest init -d
npx shadcn@latest add button input label select textarea card dialog sheet tabs scroll-area separator badge alert
```

- [ ] **Step 4: Verify dev server starts**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev
```
Expected: Server starts on localhost:3000

- [ ] **Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: initialize Next.js frontend with shadcn/ui, Rete.js, Zustand"
```

---

### Task 2: Graph Type Definitions

**Files:**
- Create: `frontend/types/graph.ts`
- Create: `frontend/types/koog.ts`

- [ ] **Step 1: Define Koog model types**

```typescript
// frontend/types/koog.ts

export type ModelProvider = "openai" | "anthropic" | "google";

export interface AgentConfig {
  name: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
}

export interface ToolParameter {
  id: string;
  name: string;
  type: "String" | "Int" | "Float" | "Boolean";
  description: string;
  required: boolean;
}

export interface ToolConfig {
  name: string;
  description: string;
  parameters: ToolParameter[];
  handlerStubName?: string;
}
```

- [ ] **Step 2: Define graph types**

```typescript
// frontend/types/graph.ts

import { AgentConfig, ToolConfig } from "./koog";

export type NodeType = "agent" | "tool";

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig;
}

export interface GraphEdge {
  id: string;
  source: string; // tool node id
  target: string; // agent node id
}

export interface ProjectGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/types/
git commit -m "feat: add graph and Koog model type definitions"
```

---

### Task 3: Editor Zustand Store

**Files:**
- Create: `frontend/store/editorStore.ts`
- Create: `frontend/__tests__/store/editorStore.test.ts`

- [ ] **Step 1: Write failing tests for editor store**

```typescript
// frontend/__tests__/store/editorStore.test.ts

import { useEditorStore } from "@/store/editorStore";

describe("editorStore", () => {
  beforeEach(() => {
    useEditorStore.getState().reset();
  });

  it("adds an agent node", () => {
    const store = useEditorStore.getState();
    store.addNode("agent", { x: 100, y: 200 });
    const nodes = useEditorStore.getState().nodes;
    expect(nodes).toHaveLength(1);
    expect(nodes[0].type).toBe("agent");
    expect(nodes[0].position).toEqual({ x: 100, y: 200 });
  });

  it("adds a tool node", () => {
    const store = useEditorStore.getState();
    store.addNode("tool", { x: 300, y: 400 });
    const nodes = useEditorStore.getState().nodes;
    expect(nodes).toHaveLength(1);
    expect(nodes[0].type).toBe("tool");
  });

  it("removes a node", () => {
    const store = useEditorStore.getState();
    store.addNode("agent", { x: 0, y: 0 });
    const nodeId = useEditorStore.getState().nodes[0].id;
    store.removeNode(nodeId);
    expect(useEditorStore.getState().nodes).toHaveLength(0);
  });

  it("adds an edge between tool and agent", () => {
    const store = useEditorStore.getState();
    store.addNode("tool", { x: 0, y: 0 });
    store.addNode("agent", { x: 200, y: 0 });
    const nodes = useEditorStore.getState().nodes;
    store.addEdge(nodes[0].id, nodes[1].id);
    expect(useEditorStore.getState().edges).toHaveLength(1);
  });

  it("removes edges when a node is removed", () => {
    const store = useEditorStore.getState();
    store.addNode("tool", { x: 0, y: 0 });
    store.addNode("agent", { x: 200, y: 0 });
    const nodes = useEditorStore.getState().nodes;
    store.addEdge(nodes[0].id, nodes[1].id);
    store.removeNode(nodes[0].id);
    expect(useEditorStore.getState().edges).toHaveLength(0);
  });

  it("updates node data", () => {
    const store = useEditorStore.getState();
    store.addNode("agent", { x: 0, y: 0 });
    const nodeId = useEditorStore.getState().nodes[0].id;
    store.updateNodeData(nodeId, { name: "MyAgent" });
    const updated = useEditorStore.getState().nodes[0];
    expect((updated.data as any).name).toBe("MyAgent");
  });

  it("sets selected node", () => {
    const store = useEditorStore.getState();
    store.addNode("agent", { x: 0, y: 0 });
    const nodeId = useEditorStore.getState().nodes[0].id;
    store.setSelectedNodeId(nodeId);
    expect(useEditorStore.getState().selectedNodeId).toBe(nodeId);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/store/editorStore.test.ts
```
Expected: FAIL — module not found

- [ ] **Step 3: Implement editor store**

```typescript
// frontend/store/editorStore.ts

import { create } from "zustand";
import { GraphNode, GraphEdge, NodeType } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";

interface EditorState {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedNodeId: string | null;
  projectName: string;

  addNode: (type: NodeType, position: { x: number; y: number }) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig>) => void;
  updateNodePosition: (id: string, position: { x: number; y: number }) => void;
  addEdge: (source: string, target: string) => void;
  removeEdge: (id: string) => void;
  setSelectedNodeId: (id: string | null) => void;
  setProjectName: (name: string) => void;
  reset: () => void;
}

const defaultAgentData: AgentConfig = {
  name: "",
  systemPrompt: "",
  modelProvider: "openai",
};

const defaultToolData: ToolConfig = {
  name: "",
  description: "",
  parameters: [],
};

let nextId = 1;

export const useEditorStore = create<EditorState>((set) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  projectName: "Untitled Project",

  addNode: (type, position) =>
    set((state) => ({
      nodes: [
        ...state.nodes,
        {
          id: `node-${nextId++}`,
          type,
          position,
          data: type === "agent" ? { ...defaultAgentData } : { ...defaultToolData },
        },
      ],
    })),

  removeNode: (id) =>
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeId: state.selectedNodeId === id ? null : state.selectedNodeId,
    })),

  updateNodeData: (id, data) =>
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, data: { ...n.data, ...data } } : n
      ),
    })),

  updateNodePosition: (id, position) =>
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, position } : n
      ),
    })),

  addEdge: (source, target) =>
    set((state) => ({
      edges: [
        ...state.edges,
        { id: `edge-${nextId++}`, source, target },
      ],
    })),

  removeEdge: (id) =>
    set((state) => ({
      edges: state.edges.filter((e) => e.id !== id),
    })),

  setSelectedNodeId: (id) => set({ selectedNodeId: id }),

  setProjectName: (name) => set({ projectName: name }),

  reset: () => {
    nextId = 1;
    return set({
      nodes: [],
      edges: [],
      selectedNodeId: null,
      projectName: "Untitled Project",
    });
  },
}));
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/store/editorStore.test.ts
```
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/store/editorStore.ts frontend/__tests__/store/editorStore.test.ts
git commit -m "feat: add Zustand editor store with node/edge CRUD"
```

---

### Task 4: Graph Validation Logic

**Files:**
- Create: `frontend/lib/validation.ts`
- Create: `frontend/store/validationStore.ts`
- Create: `frontend/__tests__/lib/validation.test.ts`

- [ ] **Step 1: Write failing tests for validation**

```typescript
// frontend/__tests__/lib/validation.test.ts

import { validateGraph, ValidationError } from "@/lib/validation";
import { ProjectGraph } from "@/types/graph";

const baseGraph: ProjectGraph = {
  version: 1,
  name: "Test",
  nodes: [],
  edges: [],
};

describe("validateGraph", () => {
  it("returns no errors for a valid graph", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "a1", type: "agent", position: { x: 0, y: 0 }, data: { name: "Agent1", systemPrompt: "Help", modelProvider: "openai" } },
        { id: "t1", type: "tool", position: { x: 200, y: 0 }, data: { name: "Tool1", description: "Does stuff", parameters: [{ id: "p1", name: "input", type: "String", description: "input", required: true }] } },
      ],
      edges: [{ id: "e1", source: "t1", target: "a1" }],
    };
    expect(validateGraph(graph)).toEqual([]);
  });

  it("reports agent without name", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "a1", type: "agent", position: { x: 0, y: 0 }, data: { name: "", systemPrompt: "Help", modelProvider: "openai" } },
      ],
      edges: [],
    };
    const errors = validateGraph(graph);
    expect(errors).toContainEqual(expect.objectContaining({ nodeId: "a1", message: expect.stringContaining("name") }));
  });

  it("reports agent without system prompt", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "a1", type: "agent", position: { x: 0, y: 0 }, data: { name: "Agent1", systemPrompt: "", modelProvider: "openai" } },
      ],
      edges: [],
    };
    const errors = validateGraph(graph);
    expect(errors).toContainEqual(expect.objectContaining({ nodeId: "a1", message: expect.stringContaining("prompt") }));
  });

  it("reports tool without name", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "t1", type: "tool", position: { x: 0, y: 0 }, data: { name: "", description: "Desc", parameters: [] } },
      ],
      edges: [],
    };
    const errors = validateGraph(graph);
    expect(errors).toContainEqual(expect.objectContaining({ nodeId: "t1", message: expect.stringContaining("name") }));
  });

  it("reports tool without connected agent", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "t1", type: "tool", position: { x: 0, y: 0 }, data: { name: "Tool1", description: "Desc", parameters: [] } },
      ],
      edges: [],
    };
    const errors = validateGraph(graph);
    expect(errors).toContainEqual(expect.objectContaining({ nodeId: "t1", message: expect.stringContaining("Agent") }));
  });

  it("reports parameter without name or type", () => {
    const graph: ProjectGraph = {
      ...baseGraph,
      nodes: [
        { id: "t1", type: "tool", position: { x: 0, y: 0 }, data: { name: "Tool1", description: "Desc", parameters: [{ id: "p1", name: "", type: "String", description: "", required: true }] } },
      ],
      edges: [],
    };
    const errors = validateGraph(graph);
    expect(errors).toContainEqual(expect.objectContaining({ nodeId: "t1", message: expect.stringContaining("Parameter") }));
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/lib/validation.test.ts
```
Expected: FAIL — module not found

- [ ] **Step 3: Implement validation logic**

```typescript
// frontend/lib/validation.ts

import { ProjectGraph, GraphNode } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";

export interface ValidationError {
  nodeId: string;
  severity: "error" | "warning";
  message: string;
}

export function validateGraph(graph: ProjectGraph): ValidationError[] {
  const errors: ValidationError[] = [];

  for (const node of graph.nodes) {
    if (node.type === "agent") {
      const data = node.data as AgentConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Agent requires a name" });
      }
      if (!data.systemPrompt.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Agent requires a system prompt" });
      }
    }

    if (node.type === "tool") {
      const data = node.data as ToolConfig;
      if (!data.name.trim()) {
        errors.push({ nodeId: node.id, severity: "error", message: "Tool requires a name" });
      }

      const isConnected = graph.edges.some((e) => e.source === node.id);
      if (!isConnected) {
        errors.push({ nodeId: node.id, severity: "error", message: "Tool must be connected to an Agent" });
      }

      for (const param of data.parameters) {
        if (!param.name.trim()) {
          errors.push({ nodeId: node.id, severity: "error", message: `Parameter requires a name` });
        }
      }
    }
  }

  return errors;
}

export function hasBlockingErrors(errors: ValidationError[]): boolean {
  return errors.some((e) => e.severity === "error");
}
```

- [ ] **Step 4: Create validation store**

```typescript
// frontend/store/validationStore.ts

import { create } from "zustand";
import { ValidationError } from "@/lib/validation";

interface ValidationState {
  errors: ValidationError[];
  setErrors: (errors: ValidationError[]) => void;
  clearErrors: () => void;
  errorsForNode: (nodeId: string) => ValidationError[];
}

export const useValidationStore = create<ValidationState>((set, get) => ({
  errors: [],
  setErrors: (errors) => set({ errors }),
  clearErrors: () => set({ errors: [] }),
  errorsForNode: (nodeId) => get().errors.filter((e) => e.nodeId === nodeId),
}));
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/lib/validation.test.ts
```
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/validation.ts frontend/store/validationStore.ts frontend/__tests__/lib/validation.test.ts
git commit -m "feat: add graph validation rules and validation store"
```

---

### Task 5: JSON Persistence

**Files:**
- Create: `frontend/lib/persistence.ts`
- Create: `frontend/__tests__/lib/persistence.test.ts`

- [ ] **Step 1: Write failing tests for persistence**

```typescript
// frontend/__tests__/lib/persistence.test.ts

import { serializeProject, deserializeProject } from "@/lib/persistence";
import { ProjectGraph } from "@/types/graph";

const validGraph: ProjectGraph = {
  version: 1,
  name: "Test Project",
  nodes: [
    { id: "a1", type: "agent", position: { x: 0, y: 0 }, data: { name: "Agent1", systemPrompt: "Help", modelProvider: "openai" } },
    { id: "t1", type: "tool", position: { x: 200, y: 0 }, data: { name: "Tool1", description: "Does stuff", parameters: [] } },
  ],
  edges: [{ id: "e1", source: "t1", target: "a1" }],
};

describe("persistence", () => {
  it("serializes a project to JSON string", () => {
    const json = serializeProject(validGraph);
    const parsed = JSON.parse(json);
    expect(parsed.version).toBe(1);
    expect(parsed.nodes).toHaveLength(2);
  });

  it("deserializes valid JSON back to ProjectGraph", () => {
    const json = serializeProject(validGraph);
    const result = deserializeProject(json);
    expect(result.name).toBe("Test Project");
    expect(result.nodes).toHaveLength(2);
    expect(result.edges).toHaveLength(1);
  });

  it("throws on invalid JSON", () => {
    expect(() => deserializeProject("not json")).toThrow();
  });

  it("throws on missing version", () => {
    const noVersion = JSON.stringify({ name: "Test", nodes: [], edges: [] });
    expect(() => deserializeProject(noVersion)).toThrow(/version/i);
  });

  it("roundtrips without data loss", () => {
    const json = serializeProject(validGraph);
    const restored = deserializeProject(json);
    expect(restored).toEqual(validGraph);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/lib/persistence.test.ts
```
Expected: FAIL

- [ ] **Step 3: Implement persistence**

```typescript
// frontend/lib/persistence.ts

import { ProjectGraph } from "@/types/graph";

export function serializeProject(graph: ProjectGraph): string {
  return JSON.stringify(graph, null, 2);
}

export function deserializeProject(json: string): ProjectGraph {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    throw new Error("Invalid JSON format");
  }

  if (typeof parsed !== "object" || parsed === null) {
    throw new Error("Invalid project format");
  }

  const obj = parsed as Record<string, unknown>;

  if (typeof obj.version !== "number") {
    throw new Error("Missing or invalid version field");
  }

  if (!Array.isArray(obj.nodes) || !Array.isArray(obj.edges)) {
    throw new Error("Missing nodes or edges");
  }

  return obj as unknown as ProjectGraph;
}

export function downloadProject(graph: ProjectGraph): void {
  const json = serializeProject(graph);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${graph.name || "project"}.json`;
  a.click();
  URL.revokeObjectURL(url);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npx jest __tests__/lib/persistence.test.ts
```
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/persistence.ts frontend/__tests__/lib/persistence.test.ts
git commit -m "feat: add JSON persistence with serialize/deserialize/download"
```

---

### Task 6: Rete.js Canvas Setup

**Reference docs:** `doc/retejs/docs/04.guides/02.renderers/1.react.md`, `doc/retejs/docs/04.guides/01.basic.md`, `doc/retejs/examples/01.basic/1.react.md`

**Files:**
- Create: `frontend/lib/rete-setup.ts`
- Create: `frontend/lib/rete-nodes.ts`
- Create: `frontend/components/editor/Canvas.tsx`
- Create: `frontend/components/editor/nodes/CustomAgentNode.tsx`
- Create: `frontend/components/editor/nodes/CustomToolNode.tsx`

- [ ] **Step 1: Define Rete node classes with sockets**

```typescript
// frontend/lib/rete-nodes.ts

import { ClassicPreset } from "rete";

// Socket for Tool→Agent connections. Uses isCompatibleWith for validation.
export class ToolSocket extends ClassicPreset.Socket {
  constructor() {
    super("Tool");
  }
  isCompatibleWith(socket: ClassicPreset.Socket): boolean {
    return socket instanceof ToolSocket;
  }
}

export const toolSocket = new ToolSocket();

// Agent node: accepts tool connections as input (multiConnection = true)
export class AgentReteNode extends ClassicPreset.Node {
  width = 200;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Agent");
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true)); // true = multi-connection
  }
}

// Tool node: outputs a connection to an Agent
export class ToolReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Tool");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export type AppNode = AgentReteNode | ToolReteNode;
export type AppConnection = ClassicPreset.Connection<AppNode, AppNode>;
```

- [ ] **Step 2: Create Rete.js editor factory with all plugins**

```typescript
// frontend/lib/rete-setup.ts

import { NodeEditor, GetSchemes } from "rete";
import { createRoot } from "react-dom/client";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets, ReactArea2D } from "rete-react-plugin";
import { ContextMenuPlugin, Presets as ContextMenuPresets } from "rete-context-menu-plugin";
import { AppNode, AppConnection, AgentReteNode, ToolReteNode, toolSocket } from "./rete-nodes";
import { useEditorStore } from "@/store/editorStore";

export type Schemes = GetSchemes<AppNode, AppConnection>;
type AreaExtra = ReactArea2D<Schemes>;

export async function createEditor(container: HTMLElement) {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });

  // Classic presets for node/connection rendering
  render.addPreset(ReactPresets.classic.setup({
    customize: {
      node(context) {
        // Custom styling per node type can be added here later
        return ReactPresets.classic.Node;
      },
    },
  }));

  // Context menu for adding nodes via right-click
  const contextMenu = new ContextMenuPlugin<Schemes>({
    items: ContextMenuPresets.classic.setup([
      ["Agent", () => {
        const store = useEditorStore.getState();
        const { x, y } = area.area.transform;
        store.addNode("agent", { x: -x + 200, y: -y + 200 });
        return null as any; // Node creation handled via Zustand sync
      }],
      ["Tool", () => {
        const store = useEditorStore.getState();
        const { x, y } = area.area.transform;
        store.addNode("tool", { x: -x + 200, y: -y + 200 });
        return null as any;
      }],
    ]),
  });

  // Connection preset
  connection.addPreset(ConnectionPresets.classic.setup());

  // Context menu rendering
  render.addPreset(ReactPresets.contextMenu.setup());

  // Plugin connection order matters: editor → area → connection → render
  editor.use(area);
  area.use(connection);
  area.use(contextMenu);
  area.use(render);

  // Enable node selection (click to select, Ctrl+click to accumulate)
  const selector = AreaExtensions.selector();
  const accumulating = AreaExtensions.accumulateOnCtrl();
  AreaExtensions.selectableNodes(area, selector, { accumulating });

  // Bring selected nodes to front
  AreaExtensions.simpleNodesOrder(area);

  // Connection validation: only ToolSocket↔ToolSocket
  editor.addPipe((context) => {
    if (context.type === "connectioncreate") {
      const { source, target } = context.data;
      const sourceNode = editor.getNode(source);
      const targetNode = editor.getNode(target);
      // Only allow Tool→Agent connections
      if (!(sourceNode instanceof ToolReteNode) || !(targetNode instanceof AgentReteNode)) {
        return; // Prevent invalid connection
      }
    }
    return context;
  });

  return { editor, area, connection, render };
}
```

- [ ] **Step 3: Create Canvas component using `useRete` pattern**

```tsx
// frontend/components/editor/Canvas.tsx

"use client";

import { useEffect, useRef, useCallback } from "react";
import { createEditor, Schemes } from "@/lib/rete-setup";
import { useEditorStore } from "@/store/editorStore";
import { AgentReteNode, ToolReteNode } from "@/lib/rete-nodes";
import { NodeEditor } from "rete";
import { AreaPlugin } from "rete-area-plugin";

// Module-level refs to avoid stale closures in callbacks
let editorRef: NodeEditor<Schemes> | null = null;
let areaRef: AreaPlugin<Schemes, any> | null = null;
let syncing = false; // Prevents feedback loops during bidirectional sync

export function Canvas() {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const init = async () => {
      const { editor, area } = await createEditor(containerRef.current!);
      editorRef = editor;
      areaRef = area;

      // --- Rete → Zustand: node selection on click ---
      area.addPipe((context) => {
        if (context.type === "nodepicked") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "storeNodeId" in reteNode) {
            useEditorStore.getState().setSelectedNodeId(reteNode.storeNodeId);
          }
        }
        return context;
      });

      // --- Rete → Zustand: update position after node drag ---
      area.addPipe((context) => {
        if (context.type === "nodetranslated") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "storeNodeId" in reteNode) {
            syncing = true;
            useEditorStore.getState().updateNodePosition(
              reteNode.storeNodeId,
              context.data.position
            );
            syncing = false;
          }
        }
        return context;
      });

      // --- Rete → Zustand: connection created ---
      editor.addPipe((context) => {
        if (context.type === "connectioncreated") {
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          if (sourceNode instanceof ToolReteNode && targetNode instanceof AgentReteNode) {
            syncing = true;
            useEditorStore.getState().addEdge(
              sourceNode.storeNodeId,
              targetNode.storeNodeId
            );
            syncing = false;
          }
        }
        return context;
      });

      // --- Rete → Zustand: connection removed ---
      editor.addPipe((context) => {
        if (context.type === "connectionremoved") {
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          if (sourceNode instanceof ToolReteNode && targetNode instanceof AgentReteNode) {
            syncing = true;
            // Find matching edge in store and remove it
            const edges = useEditorStore.getState().edges;
            const edge = edges.find(
              (e) => e.source === sourceNode.storeNodeId && e.target === targetNode.storeNodeId
            );
            if (edge) useEditorStore.getState().removeEdge(edge.id);
            syncing = false;
          }
        }
        return context;
      });

      // --- Zustand → Rete: subscribe and sync ---
      useEditorStore.subscribe(async (state, prevState) => {
        if (syncing || !editorRef || !areaRef) return;

        // Find Rete node by storeNodeId helper
        const findReteNode = (storeId: string) =>
          editorRef!.getNodes().find((n) => "storeNodeId" in n && n.storeNodeId === storeId);

        // Sync added nodes
        for (const node of state.nodes) {
          if (!findReteNode(node.id)) {
            const reteNode = node.type === "agent"
              ? new AgentReteNode(node.id, (node.data as any).name || "Agent")
              : new ToolReteNode(node.id, (node.data as any).name || "Tool");
            await editorRef.addNode(reteNode);
            await areaRef.translate(reteNode.id, node.position);
          }
        }

        // Sync removed nodes
        for (const reteNode of editorRef.getNodes()) {
          if ("storeNodeId" in reteNode) {
            if (!state.nodes.find((n) => n.id === reteNode.storeNodeId)) {
              await editorRef.removeNode(reteNode.id);
            }
          }
        }

        // Update node labels when name changes
        for (const node of state.nodes) {
          const reteNode = findReteNode(node.id);
          if (reteNode) {
            const newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
            if (reteNode.label !== newLabel) {
              reteNode.label = newLabel;
              areaRef.update("node", reteNode.id); // Force re-render
            }
          }
        }
      });
    };

    init();

    return () => {
      areaRef?.destroy();
      editorRef = null;
      areaRef = null;
    };
  }, []);

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full bg-muted/30"
      style={{ minHeight: "600px" }}
    />
  );
}
```

**Key Rete.js patterns used:**
- `area.addPipe()` for intercepting area events (`nodepicked`, `nodetranslated`)
- `editor.addPipe()` for intercepting editor events (`connectioncreated`, `connectionremoved`)
- `area.update("node", id)` to force re-render after programmatic label change
- `area.translate(nodeId, position)` to position nodes
- Connection validation in editor pipe prevents invalid Tool→Tool or Agent→Agent connections
- `syncing` flag prevents infinite feedback loops during bidirectional sync

- [ ] **Step 4: Verify canvas renders in browser**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev
```
Open http://localhost:3000 — verify:
- Canvas area renders without errors
- Right-click shows context menu with "Agent" and "Tool" options
- Nodes can be dragged
- Connections can be drawn from Tool output to Agent input

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/rete-setup.ts frontend/lib/rete-nodes.ts frontend/components/editor/
git commit -m "feat: add Rete.js canvas with custom nodes, context menu, and connection validation"
```

---

### Task 7: Toolbar & Node Creation

**Files:**
- Create: `frontend/components/panels/Toolbar.tsx`
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Create Toolbar component**

```tsx
// frontend/components/panels/Toolbar.tsx

"use client";

import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";

export function Toolbar() {
  const addNode = useEditorStore((s) => s.addNode);

  return (
    <div className="flex items-center gap-2 p-2 border-b bg-background">
      <Button variant="outline" size="sm" onClick={() => addNode("agent", { x: 100, y: 100 })}>
        + Agent
      </Button>
      <Button variant="outline" size="sm" onClick={() => addNode("tool", { x: 300, y: 100 })}>
        + Tool
      </Button>
    </div>
  );
}
```

- [ ] **Step 2: Compose main editor page**

```tsx
// frontend/app/page.tsx

import { Toolbar } from "@/components/panels/Toolbar";
import { Canvas } from "@/components/editor/Canvas";
import { PropertiesPanel } from "@/components/panels/PropertiesPanel";

export default function EditorPage() {
  return (
    <div className="flex flex-col h-screen">
      <Toolbar />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
        <PropertiesPanel />
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx frontend/app/page.tsx
git commit -m "feat: add toolbar with node creation buttons and editor layout"
```

---

### Task 8: Properties Panel

**Files:**
- Create: `frontend/components/panels/PropertiesPanel.tsx`
- Create: `frontend/components/panels/AgentProperties.tsx`
- Create: `frontend/components/panels/ToolProperties.tsx`

- [ ] **Step 1: Create AgentProperties form**

```tsx
// frontend/components/panels/AgentProperties.tsx

"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { AgentConfig, ModelProvider } from "@/types/koog";

export function AgentProperties({ nodeId, data }: { nodeId: string; data: AgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);

  const update = (field: Partial<AgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="agent-name">Name</Label>
        <Input id="agent-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyAgent" />
      </div>
      <div>
        <Label htmlFor="agent-prompt">System Prompt</Label>
        <Textarea id="agent-prompt" value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} placeholder="You are a helpful assistant." rows={4} />
      </div>
      <div>
        <Label>Model Provider</Label>
        <Select value={data.modelProvider} onValueChange={(v) => update({ modelProvider: v as ModelProvider })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="openai">OpenAI</SelectItem>
            <SelectItem value="anthropic">Anthropic</SelectItem>
            <SelectItem value="google">Google</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label htmlFor="agent-model-id">Model ID (optional)</Label>
        <Input id="agent-model-id" value={data.modelId || ""} onChange={(e) => update({ modelId: e.target.value })} placeholder="gpt-4o" />
      </div>
      <div>
        <Label htmlFor="agent-temperature">Temperature (optional)</Label>
        <Input id="agent-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create ToolProperties form**

```tsx
// frontend/components/panels/ToolProperties.tsx

"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { ToolConfig, ToolParameter } from "@/types/koog";

export function ToolProperties({ nodeId, data }: { nodeId: string; data: ToolConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);

  const update = (field: Partial<ToolConfig>) => updateNodeData(nodeId, field);

  const addParameter = () => {
    const newParam: ToolParameter = {
      id: `param-${Date.now()}`,
      name: "",
      type: "String",
      description: "",
      required: true,
    };
    update({ parameters: [...data.parameters, newParam] });
  };

  const updateParameter = (paramId: string, field: Partial<ToolParameter>) => {
    update({
      parameters: data.parameters.map((p) =>
        p.id === paramId ? { ...p, ...field } : p
      ),
    });
  };

  const removeParameter = (paramId: string) => {
    update({ parameters: data.parameters.filter((p) => p.id !== paramId) });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="tool-name">Name</Label>
        <Input id="tool-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="myTool" />
      </div>
      <div>
        <Label htmlFor="tool-desc">Description</Label>
        <Textarea id="tool-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={3} />
      </div>
      <div>
        <div className="flex items-center justify-between mb-2">
          <Label>Parameters</Label>
          <Button variant="outline" size="sm" onClick={addParameter}>+ Parameter</Button>
        </div>
        {data.parameters.map((param) => (
          <div key={param.id} className="border rounded p-2 mb-2 space-y-2">
            <div className="flex gap-2">
              <Input value={param.name} onChange={(e) => updateParameter(param.id, { name: e.target.value })} placeholder="paramName" className="flex-1" />
              <Select value={param.type} onValueChange={(v) => updateParameter(param.id, { type: v as ToolParameter["type"] })}>
                <SelectTrigger className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="String">String</SelectItem>
                  <SelectItem value="Int">Int</SelectItem>
                  <SelectItem value="Float">Float</SelectItem>
                  <SelectItem value="Boolean">Boolean</SelectItem>
                </SelectContent>
              </Select>
              <Button variant="ghost" size="sm" onClick={() => removeParameter(param.id)}>×</Button>
            </div>
            <Input value={param.description} onChange={(e) => updateParameter(param.id, { description: e.target.value })} placeholder="Description" />
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create PropertiesPanel**

```tsx
// frontend/components/panels/PropertiesPanel.tsx

"use client";

import { useEditorStore } from "@/store/editorStore";
import { AgentProperties } from "./AgentProperties";
import { ToolProperties } from "./ToolProperties";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { ScrollArea } from "@/components/ui/scroll-area";

export function PropertiesPanel() {
  const selectedNodeId = useEditorStore((s) => s.selectedNodeId);
  const nodes = useEditorStore((s) => s.nodes);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId);

  if (!selectedNode) {
    return (
      <div className="w-80 border-l bg-background p-4 text-muted-foreground text-sm">
        Select a node to edit its properties.
      </div>
    );
  }

  return (
    <ScrollArea className="w-80 border-l bg-background">
      <div className="p-4">
        <h3 className="font-semibold mb-4 capitalize">{selectedNode.type} Properties</h3>
        {selectedNode.type === "agent" && (
          <AgentProperties nodeId={selectedNode.id} data={selectedNode.data as AgentConfig} />
        )}
        {selectedNode.type === "tool" && (
          <ToolProperties nodeId={selectedNode.id} data={selectedNode.data as ToolConfig} />
        )}
      </div>
    </ScrollArea>
  );
}
```

- [ ] **Step 4: Verify in browser**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev
```
Click "+ Agent", click the node → right panel shows agent properties form.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/panels/
git commit -m "feat: add properties panel with Agent and Tool configuration forms"
```

---

### Task 9: Validation Panel & Export Dialog

**Files:**
- Create: `frontend/components/validation/ValidationPanel.tsx`
- Create: `frontend/components/dialogs/ExportDialog.tsx`
- Create: `frontend/components/dialogs/SaveLoadDialog.tsx`
- Create: `frontend/lib/export-client.ts`

- [ ] **Step 1: Create ValidationPanel**

```tsx
// frontend/components/validation/ValidationPanel.tsx

"use client";

import { useValidationStore } from "@/store/validationStore";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

export function ValidationPanel() {
  const errors = useValidationStore((s) => s.errors);

  if (errors.length === 0) return null;

  return (
    <div className="border-t p-2 bg-background space-y-1 max-h-40 overflow-auto">
      {errors.map((err, i) => (
        <div key={i} className="flex items-center gap-2 text-sm">
          <Badge variant={err.severity === "error" ? "destructive" : "secondary"}>
            {err.severity}
          </Badge>
          <span className="text-muted-foreground">[{err.nodeId}]</span>
          <span>{err.message}</span>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Create export API client**

```typescript
// frontend/lib/export-client.ts

import { ProjectGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function exportToKotlin(graph: ProjectGraph): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(graph),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Export failed: ${error}`);
  }

  return response.blob();
}
```

- [ ] **Step 3: Create ExportDialog**

```tsx
// frontend/components/dialogs/ExportDialog.tsx

"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph, hasBlockingErrors } from "@/lib/validation";
import { exportToKotlin } from "@/lib/export-client";
import { ProjectGraph } from "@/types/graph";

export function ExportDialog() {
  const [status, setStatus] = useState<"idle" | "exporting" | "done" | "error">("idle");
  const [errorMsg, setErrorMsg] = useState("");
  const { nodes, edges, projectName } = useEditorStore();
  const setErrors = useValidationStore((s) => s.setErrors);

  const handleExport = async () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    const errors = validateGraph(graph);
    setErrors(errors);

    if (hasBlockingErrors(errors)) {
      setStatus("error");
      setErrorMsg("Fix validation errors before exporting.");
      return;
    }

    setStatus("exporting");
    try {
      const blob = await exportToKotlin(graph);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${projectName || "koog-agent"}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      setStatus("done");
    } catch (e: any) {
      setStatus("error");
      setErrorMsg(e.message);
    }
  };

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button size="sm">Export Kotlin</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Export Koog Agent</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Export your agent configuration as compilable Kotlin code.
          </p>
          {status === "error" && <p className="text-sm text-destructive">{errorMsg}</p>}
          {status === "done" && <p className="text-sm text-green-600">Export successful!</p>}
          <Button onClick={handleExport} disabled={status === "exporting"}>
            {status === "exporting" ? "Exporting..." : "Export"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 4: Create SaveLoadDialog**

```tsx
// frontend/components/dialogs/SaveLoadDialog.tsx

"use client";

import { useRef } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { serializeProject, deserializeProject, downloadProject } from "@/lib/persistence";
import { ProjectGraph } from "@/types/graph";

export function SaveLoadDialog() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { nodes, edges, projectName, reset } = useEditorStore();
  const addNode = useEditorStore((s) => s.addNode);

  const handleSave = () => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
    downloadProject(graph);
  };

  const handleLoad = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const graph = deserializeProject(e.target?.result as string);
        reset();
        useEditorStore.setState({
          nodes: graph.nodes,
          edges: graph.edges,
          projectName: graph.name,
        });
      } catch (err: any) {
        alert(`Failed to load project: ${err.message}`);
      }
    };
    reader.readAsText(file);
  };

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">Save/Load</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Project</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <Button onClick={handleSave} className="w-full">Save Project (JSON)</Button>
          <div>
            <Button variant="outline" onClick={() => fileInputRef.current?.click()} className="w-full">
              Load Project
            </Button>
            <input ref={fileInputRef} type="file" accept=".json" onChange={handleLoad} className="hidden" />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 5: Wire dialogs into Toolbar**

Update `Toolbar.tsx` to include ExportDialog and SaveLoadDialog buttons.

- [ ] **Step 6: Verify in browser**

```bash
cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev
```
Verify: Save/Load dialog works, Export dialog shows validation errors for empty graph.

- [ ] **Step 7: Commit**

```bash
git add frontend/components/validation/ frontend/components/dialogs/ frontend/lib/export-client.ts
git commit -m "feat: add validation panel, export dialog, save/load dialog"
```

---

### Task 10: Integration & Polish

**Files:**
- Modify: `frontend/app/page.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`
- Modify: `frontend/app/layout.tsx`

- [ ] **Step 1: Verify Rete↔Zustand sync works end-to-end**

The bidirectional sync is already implemented in Task 6 (`Canvas.tsx`). Verify:
- Adding a node via Toolbar → appears on canvas
- Dragging a node on canvas → position updates in Zustand
- Clicking a node → `selectedNodeId` updates → Properties Panel shows correct data
- Deleting a node → removed from both Rete and Zustand
- Creating a connection on canvas → edge added to Zustand
- Renaming a node in Properties Panel → label updates on canvas (via `area.update("node", id)`)

- [ ] **Step 2: Add validation trigger on Toolbar**

Add a "Validate" button to Toolbar that runs `validateGraph` on current state and updates `validationStore`.

- [ ] **Step 3: Add ValidationPanel to page layout**

```tsx
// Update frontend/app/page.tsx to include ValidationPanel at the bottom
```

- [ ] **Step 4: End-to-end manual test**

1. Create Agent node → fill name, prompt, provider
2. Create Tool node → fill name, description, add parameter
3. Connect Tool → Agent
4. Click Validate → no errors
5. Click Export → triggers API call (will fail without backend, but dialog should work)
6. Save project → downloads JSON
7. Load project → restores state

- [ ] **Step 5: Commit**

```bash
git add -A frontend/
git commit -m "feat: integrate canvas, store, validation, and dialogs into editor"
```
