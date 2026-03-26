import { create } from "zustand";
import type { A2AMessage, A2ATaskEvent } from "@/types/a2a";

interface A2AInspectorState {
  isOpen: boolean;
  messages: A2AMessage[];
  taskEvents: A2ATaskEvent[];
  activeTab: "messages" | "tasks";
  togglePanel: () => void;
  setActiveTab: (tab: "messages" | "tasks") => void;
  addMessage: (msg: A2AMessage) => void;
  addTaskEvent: (event: A2ATaskEvent) => void;
  clear: () => void;
}

export const useA2AInspectorStore = create<A2AInspectorState>((set) => ({
  isOpen: false,
  messages: [],
  taskEvents: [],
  activeTab: "messages",
  togglePanel: () => set((s) => ({ isOpen: !s.isOpen })),
  setActiveTab: (tab) => set({ activeTab: tab }),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  addTaskEvent: (event) => set((s) => ({ taskEvents: [...s.taskEvents, event] })),
  clear: () => set({ messages: [], taskEvents: [] }),
}));
