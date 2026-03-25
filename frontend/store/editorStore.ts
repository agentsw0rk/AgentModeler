import { create } from "zustand";
import { GraphNode, GraphEdge, NodeType, BuiltinToolType, BuiltinToolConfig, WorkspaceGraph, OtelConfig } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";
import { McpServerConfig } from "@/types/mcp";

export const DEFAULT_WORKSPACE_NAME = "Untitled Workspace";

interface EditorState {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedNodeIds: string[];
  workspaceName: string;

  addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType) => void;
  addNodes: (nodes: GraphNode[], edges: GraphEdge[]) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig>) => void;
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
  otelConfig: { ...DEFAULT_OTEL_CONFIG },

  addNode(type, position, toolType?) {
    pushHistory();
    set((state) => {
      let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig;
      if (type === "agent") {
        data = { ...defaultAgentData };
      } else if (type === "sub-agent") {
        data = { ...defaultSubAgentData };
      } else if (type === "builtin-tool" && toolType) {
        data = { ...builtinToolDefaults[toolType] };
      } else if (type === "mcp-server") {
        data = { ...defaultMcpServerData };
      } else {
        data = { ...defaultToolData };
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
