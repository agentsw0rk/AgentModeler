import { AgentConfig, ToolConfig } from "./koog";
import { SubAgentConfig } from "./subagent";

export type BuiltinToolType = "think" | "shell" | "filesystem" | "fetch" | "memory" | "todo" | "user_prompt";

export const BUILTIN_TOOL_TYPES: BuiltinToolType[] = ["think", "shell", "filesystem", "fetch", "memory", "todo", "user_prompt"];

export const BUILTIN_TOOL_LABELS: Record<BuiltinToolType, string> = {
  think: "Think",
  shell: "Shell",
  filesystem: "Filesystem",
  fetch: "Fetch",
  memory: "Memory",
  todo: "Todo",
  user_prompt: "User Prompt",
};

export interface BuiltinToolConfig {
  toolType: BuiltinToolType;
  workingDir?: string;
  rootDir?: string;
  baseUrl?: string;
  path?: string;
}

export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent";

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
}

export interface ProjectGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}
