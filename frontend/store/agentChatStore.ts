import { create } from "zustand";

export interface ToolCallInfo {
  tool: string;
  args: Record<string, unknown>;
  result: string;
}

export interface AgentChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  toolCalls?: ToolCallInfo[];
}

interface AgentChatState {
  messages: AgentChatMessage[];
  status: "idle" | "streaming" | "error";
  addUserMessage: (text: string) => void;
  addAgentMessage: (text: string, toolCalls: ToolCallInfo[]) => void;
  setStatus: (status: "idle" | "streaming" | "error") => void;
  reset: () => void;
}

let nextMsgId = 1;

export const useAgentChatStore = create<AgentChatState>((set) => ({
  messages: [],
  status: "idle",

  addUserMessage: (text) => {
    const id = `acmsg-${nextMsgId++}`;
    set((state) => ({
      messages: [...state.messages, { id, role: "user", text }],
    }));
  },

  addAgentMessage: (text, toolCalls) => {
    const id = `acmsg-${nextMsgId++}`;
    set((state) => ({
      messages: [...state.messages, { id, role: "agent", text, toolCalls: toolCalls.length > 0 ? toolCalls : undefined }],
    }));
  },

  setStatus: (status) => set({ status }),

  reset: () => {
    set({ messages: [], status: "idle" });
  },
}));
