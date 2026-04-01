import { AgentConfig, ToolConfig } from "./koog";
import { SubAgentConfig } from "./subagent";
import { McpServerConfig } from "./mcp";
import { PlannerAgentConfig } from "./planner";
import { A2AAgentConfig } from "./a2a";
import { InputNodeConfig, OutputNodeConfig } from "./io";
import { GraphAgentConfig } from "./graph-agent";
import { RagNodeConfig } from "./rag";

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

export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output" | "graph-agent" | "code" | "rag";

export interface CodeNodeConfig {
  fileName: string;
  code: string;
}

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig | CodeNodeConfig | RagNodeConfig;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
}

export type OtelExporterType = "http" | "grpc" | "logging";

export interface OtelConfig {
  enabled: boolean;
  exporterType: OtelExporterType;
  endpoint?: string;
  serviceName?: string;
  serviceVersion?: string;
  authorizationHeader?: string;
  verbose: boolean;
}

export interface WorkspaceGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  otelConfig?: OtelConfig;
}
