export type PreviewStatus = "idle" | "connecting" | "running" | "success" | "error";

export type PreviewEvent =
  | { type: "session-created"; data: { sessionId: string } }
  | { type: "agent-initialized"; data: { durationMs: number } }
  | { type: "llm-call-started"; data: { model: string } }
  | { type: "llm-call-completed"; data: { durationMs: number; tokens: number } }
  | { type: "tool-call-started"; data: { tool: string; args: Record<string, unknown> } }
  | { type: "tool-call-completed"; data: { tool: string; durationMs: number; result?: string } }
  | { type: "agent-message"; data: { content: string } }
  | { type: "error"; data: { message: string } }
  | { type: "stream-complete"; data: Record<string, never> };

export interface PreviewMessage {
  id: string;
  role: "user" | "agent";
  content: string;
  isStreaming?: boolean;
}

export interface ExecutionStep {
  id: string;
  type: string;
  label: string;
  status: "running" | "done" | "error";
  durationMs?: number;
  detail?: string;
}
