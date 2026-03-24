export type McpTransportType = "sse" | "stdio";

export interface McpEnvVar {
  id: string;
  name: string;
  value?: string;
}

export interface McpDiscoveredTool {
  name: string;
  description: string;
}

export interface McpServerConfig {
  name: string;
  transportType: McpTransportType;
  url?: string;
  command?: string;
  args?: string[];
  envVars: McpEnvVar[];
  discoveredTools: McpDiscoveredTool[];
  lastDiscoveredAt?: string;
}
