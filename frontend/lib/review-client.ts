import { ProjectGraph } from "@/types/graph";
import { parseSSEStream } from "./sse-utils";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ReviewSSEEvent {
  type: "export-ready" | "review-result" | "review-failed";
  data: Record<string, unknown>;
}

export async function startExportReview(
  graph: ProjectGraph,
  onEvent: (event: ReviewSSEEvent) => void
): Promise<AbortController> {
  const controller = new AbortController();
  const response = await fetch(`${API_BASE}/api/export/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(graph),
    signal: controller.signal,
  });
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || "Failed to start export review");
  }
  parseSSEStream(response.body!, onEvent);
  return controller;
}
