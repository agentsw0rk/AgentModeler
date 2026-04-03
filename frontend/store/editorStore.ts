import { create } from "zustand";
import { GraphNode, GraphEdge, NodeType, BuiltinToolType, BuiltinToolConfig, WorkspaceGraph, OtelConfig, CodeNodeConfig } from "@/types/graph";
import { AgentConfig, ToolConfig, migrateToolNodeData } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerConfig } from "@/types/mcp";
import { PlannerAgentConfig } from "@/types/planner";
import { A2AAgentConfig } from "@/types/a2a";
import { InputNodeConfig, OutputNodeConfig, migrateIONodeData } from "@/types/io";
import { GraphAgentConfig } from "@/types/graph-agent";
import { defaultRagData } from "@/lib/rag-defaults";
import type { RagNodeConfig } from "@/types/rag";

export const DEFAULT_WORKSPACE_NAME = "Untitled Workspace";

interface EditorState {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedNodeIds: string[];
  workspaceName: string;
  activeBackend: string;
  libraryTargets: string[];

  setActiveBackend: (id: string) => void;
  setLibraryTargets: (targets: string[]) => void;
  addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType, initialData?: Record<string, unknown>) => void;
  addNodes: (nodes: GraphNode[], edges: GraphEdge[]) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Record<string, unknown>) => void;
  updateNodePosition: (id: string, position: { x: number; y: number }) => void;
  commitPosition: () => void;
  addEdge: (source: string, target: string) => void;
  removeEdge: (id: string) => void;
  setSelectedNodeIds: (ids: string[]) => void;
  toggleNodeSelection: (id: string) => void;
  setWorkspaceName: (name: string) => void;
  otelConfig: OtelConfig;
  updateOtelConfig: (partial: Partial<OtelConfig>) => void;
  reset: () => void;
  applyRemoteNodeCreated: (node: GraphNode) => void;
  applyRemoteNodeUpdated: (nodeId: string, data: Record<string, unknown>) => void;
  applyRemoteNodeDeleted: (nodeId: string) => void;
  applyRemoteEdgeCreated: (edge: GraphEdge) => void;
  applyRemoteEdgeDeleted: (edgeId: string) => void;
  applyRemoteGraphReset: (graph: { name: string; nodes: GraphNode[]; edges: GraphEdge[] }) => void;
  applyRemoteWorkspaceRenamed: (name: string) => void;
}

const defaultAgentData: AgentConfig = {
  name: "",
  systemPrompt: "",
  modelProvider: "openai",
};

const defaultToolData: ToolConfig = {
  name: "",
  description: "",
  functions: [],
};

const defaultSubAgentData: SubAgentConfig = {
  name: "",
  description: "",
  systemPrompt: "",
  modelProvider: "openai",
  delegationMode: "automatic",
  inputDescription: "",
};

const defaultMcpServerData: McpServerConfig = {
  name: "",
  transportType: "sse",
  envVars: [],
  discoveredTools: [],
};

const defaultInputData: InputNodeConfig = {
  fields: [],
};

const defaultOutputData: OutputNodeConfig = {
  fields: [],
};

const defaultA2AAgentData: A2AAgentConfig = {
  name: "",
  description: "",
  url: "",
  authType: "none",
  timeout: 30000,
  communicationMode: "message",
};

const defaultPlannerAgentData: PlannerAgentConfig = {
  name: "",
  description: "",
  plannerType: "llm",
  modelProvider: "anthropic",
  modelId: undefined,
  temperature: undefined,
  maxReplanAttempts: 3,
  goals: [],
  actions: [],
  initialState: [],
};

const defaultGraphAgentData: GraphAgentConfig = {
  name: "",
  description: "",
  systemPrompt: "",
  modelProvider: "openai",
  strategyNodes: [],
  strategyEdges: [],
};

const defaultCodeData: CodeNodeConfig = {
  fileName: "",
  code: "",
};

const builtinToolDefaults: Record<BuiltinToolType, BuiltinToolConfig> = {
  think:       { toolType: "think" },
  shell:       { toolType: "shell" },
  filesystem:  { toolType: "filesystem" },
  fetch:       { toolType: "fetch" },
  memory:      { toolType: "memory" },
  todo:        { toolType: "todo" },
  user_prompt: { toolType: "user_prompt" },
};

export const DEFAULT_OTEL_CONFIG: OtelConfig = {
  enabled: false,
  exporterType: "http",
  verbose: false,
};

let nextId = 1;

function pushHistory() {
  const { useHistoryStore } = require("./historyStore");
  const { nodes, edges, workspaceName } = useEditorStore.getState();
  useHistoryStore.getState().pushState({
    nodes: structuredClone(nodes),
    edges: structuredClone(edges),
    workspaceName,
  });
}

export const useEditorStore = create<EditorState>((set) => ({
  nodes: [],
  edges: [],
  selectedNodeIds: [],
  workspaceName: DEFAULT_WORKSPACE_NAME,
  activeBackend: "spring-boot",
  libraryTargets: ["jvm"] as string[],
  otelConfig: { ...DEFAULT_OTEL_CONFIG },

  setActiveBackend(id) {
    set({ activeBackend: id });
  },

  setLibraryTargets(targets: string[]) {
    set({ libraryTargets: targets });
  },

  addNode(type, position, toolType?, initialData?) {
    pushHistory();
    set((state) => {
      let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig | CodeNodeConfig | RagNodeConfig;
      if (type === "agent") {
        data = { ...defaultAgentData };
      } else if (type === "sub-agent") {
        data = { ...defaultSubAgentData };
      } else if (type === "builtin-tool" && toolType) {
        data = { ...builtinToolDefaults[toolType] };
      } else if (type === "mcp-server") {
        data = { ...defaultMcpServerData };
      } else if (type === "planner-agent") {
        data = { ...defaultPlannerAgentData };
      } else if (type === "a2a-agent") {
        data = { ...defaultA2AAgentData };
      } else if (type === "input") {
        data = { ...defaultInputData };
      } else if (type === "output") {
        data = { ...defaultOutputData };
      } else if (type === "graph-agent") {
        data = { ...defaultGraphAgentData };
      } else if (type === "code") {
        data = { ...defaultCodeData, ...initialData };
      } else if (type === "rag") {
        data = { ...defaultRagData };
      } else {
        data = { ...defaultToolData };
      }
      if (initialData) {
        data = { ...data, ...initialData } as typeof data;
      }
      const newNode: GraphNode = { id: `node-${nextId++}`, type, position, data };
      return {
        nodes: [...state.nodes, newNode],
        selectedNodeIds: [newNode.id],
      };
    });
  },

  addNodes(nodes, edges) {
    pushHistory();
    set((state) => ({
      nodes: [...state.nodes, ...nodes],
      edges: [...state.edges, ...edges],
      selectedNodeIds: nodes.map((n) => n.id),
    }));
  },

  removeNode(id) {
    pushHistory();
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== id),
      edges: state.edges.filter((e) => e.source !== id && e.target !== id),
      selectedNodeIds: state.selectedNodeIds.filter((nid) => nid !== id),
    }));
  },

  updateNodeData(id, data) {
    pushHistory();
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, data: { ...n.data, ...data } } : n
      ),
    }));
  },

  updateNodePosition(id, position) {
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === id ? { ...n, position } : n
      ),
    }));
  },

  commitPosition() {
    pushHistory();
  },

  addEdge(source, target) {
    pushHistory();
    set((state) => ({
      edges: [
        ...state.edges,
        { id: `edge-${nextId++}`, source, target },
      ],
    }));
  },

  removeEdge(id) {
    pushHistory();
    set((state) => ({
      edges: state.edges.filter((e) => e.id !== id),
    }));
  },

  setSelectedNodeIds(ids) {
    set({ selectedNodeIds: ids });
  },

  toggleNodeSelection(id) {
    set((state) => {
      const idx = state.selectedNodeIds.indexOf(id);
      if (idx >= 0) {
        return { selectedNodeIds: state.selectedNodeIds.filter((nid) => nid !== id) };
      }
      return { selectedNodeIds: [...state.selectedNodeIds, id] };
    });
  },

  setWorkspaceName(name) {
    pushHistory();
    set({ workspaceName: name });
  },

  updateOtelConfig(partial) {
    set((state) => ({
      otelConfig: { ...state.otelConfig, ...partial },
    }));
  },

  reset() {
    nextId = 1;
    const { useHistoryStore } = require("./historyStore");
    useHistoryStore.getState().clear();
    set({
      nodes: [],
      edges: [],
      selectedNodeIds: [],
      workspaceName: DEFAULT_WORKSPACE_NAME,
      otelConfig: { ...DEFAULT_OTEL_CONFIG },
    });
  },

  applyRemoteNodeCreated: (node) => {
    set((state) => ({ nodes: [...state.nodes, node] }));
  },
  applyRemoteNodeUpdated: (nodeId, data) => {
    set((state) => ({
      nodes: state.nodes.map((n) =>
        n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n
      ),
    }));
  },
  applyRemoteNodeDeleted: (nodeId) => {
    set((state) => ({
      nodes: state.nodes.filter((n) => n.id !== nodeId),
      edges: state.edges.filter((e) => e.source !== nodeId && e.target !== nodeId),
      selectedNodeIds: state.selectedNodeIds.filter((id) => id !== nodeId),
    }));
  },
  applyRemoteEdgeCreated: (edge) => {
    set((state) => ({ edges: [...state.edges, edge] }));
  },
  applyRemoteEdgeDeleted: (edgeId) => {
    set((state) => ({
      edges: state.edges.filter((e) => e.id !== edgeId),
    }));
  },
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
  applyRemoteWorkspaceRenamed: (name) => {
    set({ workspaceName: name });
  },
}));

export function buildWorkspaceGraph(): WorkspaceGraph {
  const { nodes, edges, workspaceName, otelConfig } = useEditorStore.getState();
  return {
    version: 1,
    name: workspaceName,
    nodes,
    edges,
    otelConfig: otelConfig.enabled ? otelConfig : undefined,
  };
}
