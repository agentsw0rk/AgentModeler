const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export interface McpDiscoveryRequest {
  transportType: string;
  url?: string;
  command?: string;
  args?: string[];
  envVars?: { name: string; value?: string }[];
}

export interface McpDiscoveryResponse {
  tools?: { name: string; description: string }[];
  error?: string;
}

export async function discoverMcpTools(config: McpDiscoveryRequest): Promise<McpDiscoveryResponse> {
  const response = await fetch(`${API_BASE}/api/mcp/discover`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  });

  const data = await response.json();

  if (!response.ok) {
    return { error: data.error || "Discovery failed" };
  }

  return data;
}
