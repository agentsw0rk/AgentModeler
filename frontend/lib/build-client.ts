import type { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface BuildError {
  file: string;
  line: number;
  column: number;
  message: string;
  severity: string;
  nodeId: string | null;
}

export interface BuildResult {
  success: boolean;
  errors: BuildError[];
  log: string;
}

export async function buildProject(graph: WorkspaceGraph, platform: "jvm" | "native"): Promise<BuildResult> {
  const response = await fetch(`${API_BASE}/api/build`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ graph, platform }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ details: "Build request failed" }));
    throw new Error(error.details || "Build request failed");
  }

  return response.json();
}
