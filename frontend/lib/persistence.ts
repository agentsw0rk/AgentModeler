import { ProjectGraph } from "@/types/graph";

export function serializeProject(graph: ProjectGraph): string {
  return JSON.stringify(graph, null, 2);
}

export function deserializeProject(json: string): ProjectGraph {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    throw new Error("Invalid JSON format");
  }

  if (typeof parsed !== "object" || parsed === null) {
    throw new Error("Invalid project format");
  }

  const obj = parsed as Record<string, unknown>;

  if (typeof obj.version !== "number") {
    throw new Error("Missing or invalid version field");
  }

  if (!Array.isArray(obj.nodes) || !Array.isArray(obj.edges)) {
    throw new Error("Missing nodes or edges");
  }

  return obj as unknown as ProjectGraph;
}

export function downloadProject(graph: ProjectGraph): void {
  const json = serializeProject(graph);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${graph.name || "project"}.json`;
  a.click();
  URL.revokeObjectURL(url);
}
