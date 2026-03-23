import { create } from "zustand";
import { GraphNode, GraphEdge, NodeType, BuiltinToolType, BuiltinToolConfig } from "@/types/graph";
import { AgentConfig, ToolConfig } from "@/types/koog";
import { SubAgentConfig } from "@/types/subagent";

interface EditorState {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedNodeId: string | null;
  projectName: string;

  addNode: (type: NodeType, position: { x: number; y: number }, toolType?: BuiltinToolType) => void;
  removeNode: (id: string) => void;
  updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig>) => void;
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

const defaultSubAgentData: SubAgentConfig = {
  name: "",
  description: "",
  systemPrompt: "",
  modelProvider: "openai",
  delegationMode: "automatic",
  inputDescription: "",
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

let nextId = 1;

export const useEditorStore = create<EditorState>((set) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  projectName: "Untitled Project",

  addNode: (type, position, toolType?) =>
    set((state) => {
      let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig;
      if (type === "agent") {
        data = { ...defaultAgentData };
      } else if (type === "sub-agent") {
        data = { ...defaultSubAgentData };
      } else if (type === "builtin-tool" && toolType) {
        data = { ...builtinToolDefaults[toolType] };
      } else {
        data = { ...defaultToolData };
      }
      return {
        nodes: [
          ...state.nodes,
          { id: `node-${nextId++}`, type, position, data },
        ],
      };
    }),

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
