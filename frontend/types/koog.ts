import type { A2AConfig } from "./a2a";

export type ModelProvider = "openai" | "anthropic" | "google" | "bedrock";

export const MODEL_OPTIONS: Record<ModelProvider, { id: string; label: string }[]> = {
  openai: [
    { id: "gpt-4o", label: "GPT-4o" },
    { id: "gpt-4o-mini", label: "GPT-4o Mini" },
    { id: "gpt-4-1", label: "GPT-4.1" },
  ],
  anthropic: [
    { id: "claude-sonnet-4", label: "Claude Sonnet 4" },
    { id: "claude-opus-4", label: "Claude Opus 4" },
    { id: "claude-haiku-3.5", label: "Claude Haiku 3.5" },
  ],
  google: [
    { id: "gemini-2-flash", label: "Gemini 2 Flash" },
    { id: "gemini-2-pro", label: "Gemini 2 Pro" },
  ],
  bedrock: [
    { id: "claude-4.5-sonnet", label: "Claude 4.5 Sonnet" },
    { id: "claude-4-sonnet", label: "Claude 4 Sonnet" },
    { id: "claude-4-opus", label: "Claude 4 Opus" },
    { id: "nova-pro", label: "Nova Pro" },
    { id: "nova-lite", label: "Nova Lite" },
    { id: "nova-micro", label: "Nova Micro" },
    { id: "llama-3.3-70b", label: "Llama 3.3 70B" },
    { id: "llama-3.1-405b", label: "Llama 3.1 405B" },
  ],
};

export interface AgentConfig {
  name: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  a2aConfig?: A2AConfig;
}

export type ToolParamType = "String" | "Int" | "Double" | "Float" | "Boolean" | "List<String>" | "Map<String, String>";

export const TOOL_PARAM_TYPES: ToolParamType[] = ["String", "Int", "Double", "Float", "Boolean", "List<String>", "Map<String, String>"];

export const TOOL_PARAM_TYPE_LABELS: Record<ToolParamType, string> = {
  "String": "String",
  "Int": "Int",
  "Double": "Double",
  "Float": "Float",
  "Boolean": "Boolean",
  "List<String>": "List<String>",
  "Map<String, String>": "Map<String, String>",
};

export interface ToolParameter {
  id: string;
  name: string;
  type: ToolParamType;
  description: string;
  required: boolean;
  defaultValue?: string;
}

export interface ToolFunction {
  id: string;
  name: string;
  description: string;
  parameters: ToolParameter[];
  returnType: "String" | "Unit";
  body: string;
}

export interface ToolConfig {
  name: string;
  description: string;
  functions: ToolFunction[];
}

/** Migrate old flat tool format to new functions format */
export function migrateToolNodeData(data: Record<string, unknown>): ToolConfig {
  if (Array.isArray(data.functions)) {
    return data as unknown as ToolConfig;
  }
  // Legacy format: { name, description, parameters: [...], handlerStubName? }
  const oldParams = (data.parameters as Array<Record<string, unknown>>) || [];
  const params: ToolParameter[] = oldParams.map((p) => ({
    id: (p.id as string) || crypto.randomUUID(),
    name: (p.name as string) || "",
    type: (p.type as ToolParamType) || "String",
    description: (p.description as string) || "",
    required: p.required !== false,
    defaultValue: undefined,
  }));
  const func: ToolFunction = {
    id: crypto.randomUUID(),
    name: (data.name as string) || "",
    description: (data.description as string) || "",
    parameters: params,
    returnType: "String",
    body: "",
  };
  return {
    name: (data.name as string) || "",
    description: (data.description as string) || "",
    functions: func.name ? [func] : [],
  };
}

export type { SubAgentConfig, DelegationMode } from "./subagent";
