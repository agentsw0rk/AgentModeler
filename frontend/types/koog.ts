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
