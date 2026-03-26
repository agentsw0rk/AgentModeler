import type { A2AConfig } from "./a2a";

export type ModelProvider = "openai" | "anthropic" | "google";

export interface AgentConfig {
  name: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  a2aConfig?: A2AConfig;
}

export interface ToolParameter {
  id: string;
  name: string;
  type: "String" | "Int" | "Float" | "Boolean";
  description: string;
  required: boolean;
}

export interface ToolConfig {
  name: string;
  description: string;
  parameters: ToolParameter[];
  handlerStubName?: string;
}

export type { SubAgentConfig, DelegationMode } from "./subagent";
