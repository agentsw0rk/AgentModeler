import { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export async function syncGraph(graph: WorkspaceGraph): Promise<void> {
  const response = await fetch(`${API_BASE}/api/editor/sync`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(graph),
  });
  if (!response.ok) {
    throw new Error(`Sync failed: ${response.statusText}`);
  }
}
