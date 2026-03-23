import type { ModelProvider } from "./koog";

export type DelegationMode = "automatic" | "manual" | "fallback" | "specialized";

export interface SubAgentConfig {
  name: string;
  description: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  delegationMode: DelegationMode;
  inputDescription: string;
}
