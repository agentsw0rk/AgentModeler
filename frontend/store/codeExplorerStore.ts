import { create } from "zustand";
import { FileTreeNode, FileContent, OpenTab } from "@/types/vfs";

type ExplorerStatus = "idle" | "generating" | "valid" | "stale" | "invalid";

interface CodeExplorerState {
  isOpen: boolean;
  tree: FileTreeNode | null;
  generatedAt: string | null;
  status: ExplorerStatus;
  openTabs: OpenTab[];
  activeTabPath: string | null;
  fileCache: Map<string, FileContent>;
  expandedFolders: Set<string>;

  togglePanel: () => void;
  setTree: (tree: FileTreeNode, generatedAt: string) => void;
  openFile: (path: string, name: string, language: string) => void;
  closeTab: (path: string) => void;
  setActiveTab: (path: string) => void;
  setFileContent: (path: string, content: FileContent) => void;
  markStale: () => void;
  setStatus: (status: ExplorerStatus) => void;
  toggleFolder: (path: string) => void;
  closeAllTabs: () => void;
  clearFileCache: () => void;
  reset: () => void;
}

export const useCodeExplorerStore = create<CodeExplorerState>((set, get) => ({
  isOpen: false,
  tree: null,
  generatedAt: null,
  status: "idle",
  openTabs: [],
  activeTabPath: null,
  fileCache: new Map(),
  expandedFolders: new Set(),

  togglePanel: () => set((s) => ({ isOpen: !s.isOpen })),

  setTree: (tree, generatedAt) =>
    set({ tree, generatedAt, status: "valid" }),

  openFile: (path, name, language) =>
    set((s) => {
      const exists = s.openTabs.some((t) => t.path === path);
      if (exists) {
        return { activeTabPath: path };
      }
      return {
        openTabs: [...s.openTabs, { path, name, language }],
        activeTabPath: path,
      };
    }),

  closeTab: (path) =>
    set((s) => {
      const newTabs = s.openTabs.filter((t) => t.path !== path);
      const newCache = new Map(s.fileCache);
      newCache.delete(path);
      let newActive = s.activeTabPath;
      if (s.activeTabPath === path) {
        const idx = s.openTabs.findIndex((t) => t.path === path);
        newActive = newTabs[Math.min(idx, newTabs.length - 1)]?.path ?? null;
      }
      return { openTabs: newTabs, activeTabPath: newActive, fileCache: newCache };
    }),

  setActiveTab: (path) => set({ activeTabPath: path }),

  setFileContent: (path, content) =>
    set((s) => {
      const newCache = new Map(s.fileCache);
      newCache.set(path, content);
      return { fileCache: newCache };
    }),

  markStale: () =>
    set((s) => (s.status === "generating" ? {} : { status: "stale" })),

  setStatus: (status) => set({ status }),

  toggleFolder: (path) =>
    set((s) => {
      const next = new Set(s.expandedFolders);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return { expandedFolders: next };
    }),

  closeAllTabs: () => set({ openTabs: [], activeTabPath: null, fileCache: new Map() }),

  clearFileCache: () => set({ fileCache: new Map() }),

  reset: () =>
    set({
      isOpen: false,
      tree: null,
      generatedAt: null,
      status: "idle",
      openTabs: [],
      activeTabPath: null,
      fileCache: new Map(),
      expandedFolders: new Set(),
    }),
}));
