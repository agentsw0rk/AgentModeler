import { ProjectGraph } from "@/types/graph";
import { GenerationResult, FileTreeNode, FileContent } from "@/types/vfs";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function generateCode(
  projectName: string,
  graph: ProjectGraph
): Promise<GenerationResult> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/generate`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(graph),
    }
  );
  if (!response.ok) {
    throw new Error(`Generation failed: ${response.statusText}`);
  }
  return response.json();
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
