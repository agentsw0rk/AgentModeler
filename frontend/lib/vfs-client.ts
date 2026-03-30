import { WorkspaceGraph } from "@/types/graph";
import { GenerationResult, FileTreeNode, FileContent } from "@/types/vfs";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function generateCode(
  projectName: string,
  graph: WorkspaceGraph,
  signal?: AbortSignal,
  backend?: string
): Promise<GenerationResult> {
  const body = backend ? { graph, backend } : graph;
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/generate`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    }
  );
  if (!response.ok) {
    throw new Error(`Generation failed: ${response.statusText}`);
  }
  return response.json();
}

export async function deleteProject(
  projectName: string,
  signal?: AbortSignal
): Promise<void> {
  await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}`,
    { method: "DELETE", signal }
  );
}

export async function getFileTree(
  projectName: string,
  signal?: AbortSignal
): Promise<{ root: FileTreeNode; generatedAt: string }> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/tree`,
    { signal }
  );
  if (!response.ok) {
    throw new Error(`Failed to get file tree: ${response.statusText}`);
  }
  return response.json();
}

export async function getFileContent(
  projectName: string,
  path: string,
  signal?: AbortSignal
): Promise<FileContent> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/files?path=${encodeURIComponent(path)}`,
    { signal }
  );
  if (!response.ok) {
    throw new Error(`Failed to get file: ${response.statusText}`);
  }
  return response.json();
}
