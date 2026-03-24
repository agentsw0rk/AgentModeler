import { create } from "zustand";
import { GraphNode, GraphEdge } from "@/types/graph";

const MAX_HISTORY = 100;

export interface Snapshot {
  nodes: GraphNode[];
  edges: GraphEdge[];
  projectName: string;
}

interface HistoryState {
  past: Snapshot[];
  future: Snapshot[];
  pushState: (snapshot: Snapshot) => void;
  undo: () => void;
  redo: () => void;
  clear: () => void;
}

export const useHistoryStore = create<HistoryState>((set, get) => ({
  past: [],
  future: [],

  pushState: (snapshot) =>
    set((s) => ({
      past: [...s.past.slice(-(MAX_HISTORY - 1)), snapshot],
      future: [],
    })),

  undo: () => {
    const { past } = get();
    if (past.length === 0) return;

    const previous = past[past.length - 1];
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { useEditorStore } = require("./editorStore");
    const { nodes, edges, projectName } = useEditorStore.getState();
    const current: Snapshot = {
      nodes: structuredClone(nodes),
      edges: structuredClone(edges),
      projectName,
    };

    set({ past: past.slice(0, -1), future: [...get().future, current] });
    useEditorStore.setState({
      nodes: previous.nodes,
      edges: previous.edges,
      projectName: previous.projectName,
    });
  },

  redo: () => {
    const { future } = get();
    if (future.length === 0) return;

    const next = future[future.length - 1];
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { useEditorStore } = require("./editorStore");
    const { nodes, edges, projectName } = useEditorStore.getState();
    const current: Snapshot = {
      nodes: structuredClone(nodes),
      edges: structuredClone(edges),
      projectName,
    };

    set({ future: future.slice(0, -1), past: [...get().past, current] });
    useEditorStore.setState({
      nodes: next.nodes,
      edges: next.edges,
      projectName: next.projectName,
    });
  },

  clear: () => set({ past: [], future: [] }),
}));
