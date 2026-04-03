import { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function exportToKotlin(graph: WorkspaceGraph, backend?: string, targets?: string[]): Promise<Blob> {
  const body: Record<string, unknown> = backend ? { graph, backend } : { ...graph };
  if (targets && targets.length > 0) {
    body.targets = targets;
    if (!body.graph) { body.graph = graph; }
  }
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
