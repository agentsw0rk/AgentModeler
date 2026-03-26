export interface A2AConfig {
  enabled: boolean;
  agentCard: AgentCard;
  server: A2AServerConfig;
}

export interface AgentCard {
  name: string;
  description: string;
  url: string;
  version: string;
  capabilities: A2ACapabilities;
  skills: A2ASkill[];
  defaultInputModes: string[];
  defaultOutputModes: string[];
}

export interface A2ACapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

export interface A2ASkill {
  id: string;
  name: string;
  description: string;
  tags: string[];
}

export interface A2AServerConfig {
  transportType: "http-jsonrpc";
  port: number;
  basePath: string;
}

export type A2AAuthType = "none" | "bearer" | "api-key";
export type A2ACommunicationMode = "message" | "task";

export interface A2AAgentConfig {
  name: string;
  description: string;
  url: string;
  authType: A2AAuthType;
  authToken?: string;
  timeout: number;
  communicationMode: A2ACommunicationMode;
}

export type A2ATaskStatus = "submitted" | "working" | "input-required" | "completed" | "failed" | "canceled";

export interface A2AMessage {
  id: string;
  role: "user" | "agent";
  parts: A2AMessagePart[];
  timestamp: string;
}

export interface A2AMessagePart {
  type: "text" | "file" | "data";
  content: string;
  mimeType?: string;
}

export interface A2ATaskEvent {
  id: string;
  taskId: string;
  status: A2ATaskStatus;
  message?: A2AMessage;
  timestamp: string;
}
