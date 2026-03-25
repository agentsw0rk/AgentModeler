import { WorkspaceGraph } from "@/types/graph";

export function serializeWorkspace(graph: WorkspaceGraph): string {
  return JSON.stringify(graph, null, 2);
}

export function deserializeWorkspace(json: string): WorkspaceGraph {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    throw new Error("Invalid JSON format");
  }

  if (typeof parsed !== "object" || parsed === null) {
    throw new Error("Invalid workspace format");
  }

  const obj = parsed as Record<string, unknown>;

  if (typeof obj.version !== "number") {
    throw new Error("Missing or invalid version field");
  }

  if (!Array.isArray(obj.nodes) || !Array.isArray(obj.edges)) {
    throw new Error("Missing nodes or edges");
  }

  return obj as unknown as WorkspaceGraph;
}

export function downloadWorkspace(graph: WorkspaceGraph): void {
  const json = serializeWorkspace(graph);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${graph.name || "workspace"}.json`;
  a.click();
  URL.revokeObjectURL(url);
}
