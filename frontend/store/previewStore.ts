import { create } from "zustand";
import { PreviewStatus, PreviewMessage, ExecutionStep } from "@/types/preview";

interface PreviewState {
  status: PreviewStatus;
  sessionId: string | null;
  messages: PreviewMessage[];
  steps: ExecutionStep[];
  error: string | null;
  isOpen: boolean;
  useMock: boolean;
  activeTab: "preview" | "review";

  setStatus: (status: PreviewStatus) => void;
  setSessionId: (id: string) => void;
  addMessage: (message: PreviewMessage) => void;
  updateLastAgentMessage: (content: string) => void;
  addStep: (step: ExecutionStep) => void;
  updateStep: (id: string, updates: Partial<ExecutionStep>) => void;
  setError: (error: string) => void;
  toggleOpen: () => void;
  toggleMock: () => void;
  setActiveTab: (tab: "preview" | "review") => void;
  reset: () => void;
}

let nextStepId = 1;
export function generateStepId(): string {
  return `step-${nextStepId++}`;
}

let nextMsgId = 1;
export function generateMsgId(): string {
  return `msg-${nextMsgId++}`;
}

export const usePreviewStore = create<PreviewState>((set) => ({
  status: "idle",
  sessionId: null,
  messages: [],
  steps: [],
  error: null,
  isOpen: false,
  useMock: false,
  activeTab: "preview",

  setStatus: (status) => set({ status }),
  setSessionId: (id) => set({ sessionId: id }),
  addMessage: (message) => set((s) => ({ messages: [...s.messages, message] })),
  updateLastAgentMessage: (content) =>
    set((s) => {
      const msgs = [...s.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.role === "agent") {
        msgs[msgs.length - 1] = { ...last, content, isStreaming: false };
      }
      return { messages: msgs };
    }),
  addStep: (step) => set((s) => ({ steps: [...s.steps, step] })),
  updateStep: (id, updates) =>
    set((s) => ({
      steps: s.steps.map((step) =>
        step.id === id ? { ...step, ...updates } : step
      ),
    })),
  setError: (error) => set({ error, status: "error" }),
  toggleOpen: () => set((s) => ({ isOpen: !s.isOpen })),
  toggleMock: () => set((s) => ({ useMock: !s.useMock })),
  setActiveTab: (tab) => set({ activeTab: tab }),
  reset: () => {
    nextStepId = 1;
    nextMsgId = 1;
    return set({
      status: "idle",
      sessionId: null,
      messages: [],
      steps: [],
      error: null,
      activeTab: "preview",
    });
  },
}));
