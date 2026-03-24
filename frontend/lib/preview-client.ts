import { ProjectGraph } from "@/types/graph";
import { PreviewEvent } from "@/types/preview";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function startPreview(
  graph: ProjectGraph,
  message: string,
  onEvent: (event: PreviewEvent) => void
): Promise<AbortController> {
  const controller = new AbortController();

  const response = await fetch(`${API_BASE}/api/preview/start`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ graph, message }),
    signal: controller.signal,
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || "Failed to start preview");
  }

  parseSSEStream(response.body!, onEvent);
  return controller;
}

export async function sendMessage(
  sessionId: string,
  message: string
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/preview/${sessionId}/message`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || "Failed to send message");
  }
}

export async function stopPreview(sessionId: string): Promise<void> {
  await fetch(`${API_BASE}/api/preview/${sessionId}/stop`, {
    method: "POST",
  });
}

function parseSSEStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: PreviewEvent) => void
) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  function processChunk() {
    reader.read().then(({ done, value }) => {
      if (done) return;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      let currentData = "";

      for (const line of lines) {
        if (line.startsWith("data:")) {
          currentData = line.slice(5).trim();
        } else if (line === "" && currentData) {
          try {
            const parsed = JSON.parse(currentData) as PreviewEvent;
            onEvent(parsed);
          } catch {
            // skip malformed events
          }
          currentData = "";
        }
      }

      processChunk();
    });
  }

  processChunk();
}
