export type ModelProvider = "openai" | "anthropic" | "google";

export interface AgentConfig {
  name: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
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
