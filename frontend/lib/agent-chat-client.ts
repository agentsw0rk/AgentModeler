import { parseSSEStream } from "./sse-utils";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface AgentChatSseEvent {
  type: "tool-call" | "agent-message" | "stream-complete" | "error";
  content?: string;
  tool?: string;
  args?: Record<string, unknown>;
  result?: string;
  message?: string;
}

export async function sendAgentMessage(
  message: string,
  onEvent: (event: AgentChatSseEvent) => void
): Promise<AbortController> {
  const abort = new AbortController();

  const response = await fetch(`${API_BASE}/api/agent/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
    signal: abort.signal,
  });

  if (!response.ok) {
    throw new Error(`Agent chat failed: ${response.statusText}`);
  }

  if (response.body) {
    parseSSEStream<AgentChatSseEvent>(response.body, onEvent);
  }

  return abort;
}

export async function resetAgentChat(): Promise<void> {
  const response = await fetch(`${API_BASE}/api/agent/chat/reset`, {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error(`Reset failed: ${response.statusText}`);
  }
}
