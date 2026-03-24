# Live Preview & Test Execution — Design Spec

## Goal

Users can run a test execution for a valid graph directly from the editor UI, chat with the agent, and observe execution steps in real time.

## Architecture Decisions

- **Runtime:** Agent built in-memory from graph via `GraphToKoogAdapter` (no code generation)
- **Streaming:** Server-Sent Events (SSE) for execution updates
- **Interaction:** Chat-based — user can send follow-up messages within a session
- **Sessions:** Backend-managed with TTL (30 min), agent + history kept in memory
- **Mock mode:** Frontend-only mock client for UI development without backend

## Backend

### New Package: `com.agentwork.modeler.preview`

**PreviewController.kt** — REST endpoints with SSE responses:
- `POST /api/preview/start` — Body: `{ graph: ProjectGraph, message: string }`. Validates graph, builds agent, creates session, executes first message. Returns SSE stream with `sessionId` as first event.
- `POST /api/preview/{sessionId}/message` — Body: `{ message: string }`. Sends message to existing agent, returns SSE stream.
- `POST /api/preview/{sessionId}/stop` — Ends session, cleans up agent and MCP connections.

**PreviewService.kt** — Orchestrates agent creation and execution:
- Validates graph via `GraphValidator`
- Converts graph via `GraphToKoogAdapter`
- Builds `AIAgent` instance in-memory from `KoogAgent` model
- Executes messages and emits `PreviewEvent`s to the SSE stream

**PreviewSessionManager.kt** — Session lifecycle:
- `ConcurrentHashMap<String, PreviewSession>` for active sessions
- TTL: 30 minutes, lazy cleanup on access
- Creates unique session IDs (UUID)

**PreviewSession.kt** — Data class:
- `sessionId: String`
- `agent: AIAgent<String, String>`
- `history: MutableList<ConversationMessage>`
- `createdAt: Instant`

**PreviewEvent.kt** — Sealed class for SSE events:
- `SessionCreated(sessionId)`
- `AgentInitialized(durationMs)`
- `LlmCallStarted(model)`
- `LlmCallCompleted(durationMs, tokens)`
- `ToolCallStarted(tool, args)`
- `ToolCallCompleted(tool, durationMs, result)`
- `AgentMessage(content)`
- `Error(message)`

**Reused from export package:**
- `GraphToKoogAdapter` — converts ProjectGraph to KoogAgent model
- `GraphValidator` — validates graph before execution

## Frontend

### Types (`frontend/types/preview.ts`)

```typescript
type PreviewStatus = "idle" | "connecting" | "running" | "success" | "error";

interface PreviewEvent {
  type: "session-created" | "agent-initialized" | "llm-call-started" |
        "llm-call-completed" | "tool-call-started" | "tool-call-completed" |
        "agent-message" | "error";
  data: Record<string, unknown>;
}

interface PreviewMessage {
  role: "user" | "agent";
  content: string;
}

interface ExecutionStep {
  id: string;
  type: string;
  label: string;
  status: "running" | "done" | "error";
  durationMs?: number;
  detail?: string;
}
```

### Store (`frontend/store/previewStore.ts`)

Zustand store following existing patterns:
- **State:** `status`, `sessionId`, `messages[]`, `steps[]`, `error`, `isOpen`, `useMock`
- **Actions:** `startSession()`, `addMessage()`, `addStep()`, `updateStep()`, `setError()`, `stopSession()`, `toggleOpen()`, `toggleMock()`, `reset()`

### SSE Client (`frontend/lib/preview-client.ts`)

- `startPreview(graph: ProjectGraph, message: string): EventSource` — opens SSE connection to `/api/preview/start`, parses events, dispatches to store
- `sendMessage(sessionId: string, message: string): EventSource` — SSE connection for follow-up messages
- `stopPreview(sessionId: string): Promise<void>` — REST call to stop session

### Mock Client (`frontend/lib/preview-mock.ts`)

- Same function signatures as preview-client
- Produces simulated events with realistic delays (50-500ms)
- No backend required
- Activated via `useMock` flag in previewStore

### Components

**PreviewPanel.tsx** (`frontend/components/preview/PreviewPanel.tsx`)
- Bottom drawer container, resizable via drag handle at top edge
- Header bar: status badge (Idle/Running/Success/Error), Mock toggle, Stop button, minimize/maximize
- Two-column layout: ExecutionTimeline (left, 220px) + ChatArea (right, flex)
- Conditionally rendered based on `previewStore.isOpen`

**ExecutionTimeline.tsx** (`frontend/components/preview/ExecutionTimeline.tsx`)
- Scrollable list of execution steps
- Status icons: ✓ (done), ⟳ (running), ✗ (error), ○ (pending)
- Duration display per step

**ChatArea.tsx** (`frontend/components/preview/ChatArea.tsx`)
- Message history: user messages (right-aligned), agent messages (left-aligned)
- Streaming cursor (▊) for in-progress agent responses
- Input field + send button at bottom
- Auto-scroll to newest message

### Integration

**page.tsx:**
- PreviewPanel inserted between main content area and ValidationPanel

**Toolbar.tsx:**
- "Run Preview" button added (next to Validate)
- On click: validates graph → if valid, opens PreviewPanel and starts session

## Data Flow

```
User clicks "Run Preview"
  → validateGraph(graph)
  → if errors: show ValidationPanel, don't start
  → if OK: previewStore.startSession()
    → POST /api/preview/start { graph, message }
    → Backend: validate → adapt → build AIAgent → execute
    → SSE stream:
        session-created    { sessionId }
        agent-initialized  { durationMs }
        llm-call-started   { model }
        llm-call-completed { durationMs, tokens }
        tool-call-started  { tool, args }
        tool-call-completed { tool, durationMs }
        agent-message      { content }
    → preview-client parses events → store updates UI

Follow-up message:
  → POST /api/preview/{sessionId}/message { message }
  → Same SSE flow, same session

Stop:
  → POST /api/preview/{sessionId}/stop
  → Backend cleans up agent + MCP connections
  → Store → status: "idle"
```

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Validation errors | Preview doesn't start, errors shown in ValidationPanel |
| Agent build failure | SSE `error` event, status → "error", message in ChatArea |
| LLM error (API key, rate limit) | SSE `error` event, failed step in Timeline (✗) |
| Tool error | Timeline shows tool step as ✗, agent decides to continue or not |
| Connection lost | EventSource `onerror` → status "error", reconnect hint |
| Session expired (TTL) | Backend 404, frontend shows "Session expired, please restart" |

## What Does NOT Change

- **Export flow** — completely separate, no shared runtime state
- **Existing stores** — editorStore and validationStore untouched
- **Canvas/Properties** — no modifications
- **Backend export package** — only reuses adapter and validator (read-only)
