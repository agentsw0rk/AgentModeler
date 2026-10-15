import { GraphNode, GraphEdge, WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export interface EditorSseEvent {
  type: string;
  node?: GraphNode;
  nodeId?: string;
  data?: Record<string, unknown>;
  edge?: GraphEdge;
  edgeId?: string;
  graph?: WorkspaceGraph;
  name?: string;
  errors?: Array<{ nodeId: string; severity: string; message: string }>;
}

type EventHandler = (event: EditorSseEvent) => void;

let eventSource: EventSource | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

export function connectEditorSse(onEvent: EventHandler): () => void {
  const eventTypes = [
    "node-created",
    "node-updated",
    "node-deleted",
    "edge-created",
    "edge-deleted",
    "graph-reset",
    "workspace-renamed",
    "validation-updated",
  ];

  function connect() {
    if (eventSource) {
      eventSource.close();
    }

    eventSource = new EventSource(`${API_BASE}/api/editor/events`);

    for (const eventType of eventTypes) {
      eventSource.addEventListener(eventType, (e: MessageEvent) => {
        try {
          const parsed = JSON.parse(e.data) as EditorSseEvent;
          onEvent(parsed);
        } catch {
          console.error("Failed to parse SSE event:", e.data);
        }
      });
    }

    eventSource.onerror = () => {
      eventSource?.close();
      eventSource = null;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      reconnectTimer = setTimeout(connect, 3000);
    };
  }

  connect();

  return () => {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    eventSource?.close();
    eventSource = null;
  };
}

export function isEditorSseConnected(): boolean {
  return eventSource !== null && eventSource.readyState === EventSource.OPEN;
}
