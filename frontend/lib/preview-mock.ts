import { PreviewEvent } from "@/types/preview";

const MOCK_EVENTS: { event: PreviewEvent; delayMs: number }[] = [
  { event: { type: "session-created", data: { sessionId: "mock-session-1" } }, delayMs: 100 },
  { event: { type: "agent-initialized", data: { durationMs: 45 } }, delayMs: 200 },
  { event: { type: "llm-call-started", data: { model: "GPT-4o" } }, delayMs: 100 },
  { event: { type: "llm-call-completed", data: { durationMs: 1200, tokens: 64 } }, delayMs: 1200 },
  { event: { type: "agent-message", data: { content: "Hallo! Ich bin dein Agent. Wie kann ich dir helfen?" } }, delayMs: 100 },
  { event: { type: "stream-complete", data: {} as Record<string, never> }, delayMs: 50 },
];

const MOCK_FOLLOWUP_EVENTS: { event: PreviewEvent; delayMs: number }[] = [
  { event: { type: "llm-call-started", data: { model: "GPT-4o" } }, delayMs: 100 },
  { event: { type: "tool-call-started", data: { tool: "fetchWeather", args: { city: "Berlin" } } }, delayMs: 300 },
  { event: { type: "tool-call-completed", data: { tool: "fetchWeather", durationMs: 800, result: "22°C, sunny" } }, delayMs: 800 },
  { event: { type: "llm-call-completed", data: { durationMs: 950, tokens: 48 } }, delayMs: 400 },
  { event: { type: "agent-message", data: { content: "Das Wetter in Berlin ist heute 22°C und sonnig!" } }, delayMs: 100 },
  { event: { type: "stream-complete", data: {} as Record<string, never> }, delayMs: 50 },
];

let storedOnEvent: ((event: PreviewEvent) => void) | null = null;

export async function startPreview(
  _graph: unknown,
  _message: string,
  onEvent: (event: PreviewEvent) => void
): Promise<AbortController> {
  const controller = new AbortController();
  storedOnEvent = onEvent;
  emitEvents(MOCK_EVENTS, onEvent, controller.signal);
  return controller;
}

export async function sendMessage(
  _sessionId: string,
  _message: string
): Promise<void> {
  if (storedOnEvent) {
    emitEvents(MOCK_FOLLOWUP_EVENTS, storedOnEvent, new AbortController().signal);
  }
}

export async function stopPreview(_sessionId: string): Promise<void> {
  storedOnEvent = null;
}

function emitEvents(
  events: { event: PreviewEvent; delayMs: number }[],
  onEvent: (event: PreviewEvent) => void,
  signal: AbortSignal
) {
  let totalDelay = 0;
  for (const { event, delayMs } of events) {
    totalDelay += delayMs;
    setTimeout(() => {
      if (!signal.aborted) onEvent(event);
    }, totalDelay);
  }
}
