import { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function exportToKotlin(graph: WorkspaceGraph, backend?: string): Promise<Blob> {
  const body = backend ? { graph, backend } : graph;
  const response = await fetch(`${API_BASE}/api/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Export failed: ${error}`);
  }

  return response.blob();
}
