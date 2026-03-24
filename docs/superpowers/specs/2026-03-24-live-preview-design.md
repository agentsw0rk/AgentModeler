# Live Preview & Test Execution — Design Spec

## Goal

Users can run a test execution for a valid graph directly from the editor UI, chat with the agent, and observe execution steps in real time.

## Architecture Decisions

- **Runtime:** Agent built in-memory from graph via `GraphToKoogAdapter` (no code generation)
- **Streaming:** Server-Sent Events (SSE) for execution updates
- **Interaction:** Chat-based — user can send follow-up messages within a session
- **Sessions:** Backend-managed with TTL (30 min), agent + history kept in memory
- **Mock mode:** Frontend-only mock client for UI development without backend
- **API keys:** Read from server environment variables (same as generated projects)

## Backend

### New Package: `com.agentwork.modeler.preview`

**PreviewController.kt** — REST endpoints:
- `POST /api/preview/start` — Body: `{ graph: ProjectGraph, message: string }`. Validates graph, builds agent, creates session, executes first message. Returns `{ sessionId }` synchronously.
- `GET /api/preview/{sessionId}/events` — SSE stream (`text/event-stream`). Client opens this via `EventSource` after session creation. Streams all execution events for the session (current and future messages).
- `POST /api/preview/{sessionId}/message` — Body: `{ message: string }`. Sends follow-up message to existing agent. Events appear on the existing SSE stream.
- `POST /api/preview/{sessionId}/stop` — Ends session, cleans up agent and MCP connections.

**PreviewAgentBuilder.kt** — Bridges `KoogAgent` data model to live `AIAgent` runtime:
- Creates `PromptExecutor` based on `ModelProvider` (reads API key from `System.getenv("${PROVIDER}_API_KEY")`)
- Builds `ToolRegistry` with:
  - **Custom tools:** Registered as stub tools that return `"[Tool ${name} called with args: ${args}] — custom tool execution not available in preview"`. This allows the agent to exercise its tool-calling logic without real implementations.
  - **Builtin tools:** Mapped to Koog SDK built-in implementations (think, shell, filesystem, fetch, memory, todo) — same as generated code.
  - **MCP tools:** Connected via Koog MCP SDK (SSE/STDIO transport), connections lifecycle-managed by session.
  - **Sub-agents:** Recursively built with their own executor and tool registry.
- Uses `AIAgent` with `functionalStrategy` for chat-style interaction. Return type: `AIAgent<String, String>`.

**PreviewService.kt** — Orchestrates agent creation and execution:
- Validates graph via `GraphValidator`
- Converts graph via `GraphToKoogAdapter`
- Builds live `AIAgent` via `PreviewAgentBuilder`
- Executes messages in a session-scoped `CoroutineScope(Dispatchers.IO + SupervisorJob())`
- Emits `PreviewEvent`s to the session's SSE emitter
- Messages within a session are serialized (queued) — no concurrent execution per session

**PreviewSessionManager.kt** — Session lifecycle:
- `ConcurrentHashMap<String, PreviewSession>` for active sessions
- TTL: 30 minutes
- `@Scheduled` cleanup task every 5 minutes evicts expired sessions and closes resources
- Max 5 concurrent sessions (configurable). Returns 429 if limit reached.
- Creates unique session IDs (UUID)

**PreviewSession.kt** — Data class:
- `sessionId: String`
- `agent: AIAgent<String, String>`
- `scope: CoroutineScope` (session-scoped, cancelled on stop/expire)
- `emitter: SseEmitter` (long-lived, shared across messages)
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
- `StreamComplete` — last event sent when execution finishes, signals frontend to transition to "success"

**Reused from export package:**
- `GraphToKoogAdapter` — converts ProjectGraph to KoogAgent model
- `GraphValidator` — validates graph before execution

## Frontend

### Types (`frontend/types/preview.ts`)

```typescript
type PreviewStatus = "idle" | "connecting" | "running" | "success" | "error";

type PreviewEvent =
  | { type: "session-created"; data: { sessionId: string } }
  | { type: "agent-initialized"; data: { durationMs: number } }
  | { type: "llm-call-started"; data: { model: string } }
  | { type: "llm-call-completed"; data: { durationMs: number; tokens: number } }
  | { type: "tool-call-started"; data: { tool: string; args: Record<string, unknown> } }
  | { type: "tool-call-completed"; data: { tool: string; durationMs: number; result?: string } }
  | { type: "agent-message"; data: { content: string } }
  | { type: "error"; data: { message: string } }
  | { type: "stream-complete"; data: Record<string, never> };

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

Two-step flow:
1. `startPreview(graph: ProjectGraph, message: string): Promise<string>` — POST to `/api/preview/start`, returns `sessionId`
2. `connectEvents(sessionId: string, onEvent: (event: PreviewEvent) => void): EventSource` — opens `GET /api/preview/{sessionId}/events` via native `EventSource` API. Parses SSE events and calls `onEvent` callback.
3. `sendMessage(sessionId: string, message: string): Promise<void>` — POST to send follow-up. Events arrive on the existing EventSource connection.
4. `stopPreview(sessionId: string): Promise<void>` — POST to stop session.

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
- Placed below the main content area `[Canvas + PropertiesPanel]` and above `ValidationPanel`; both can be visible simultaneously

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
- On click: validates graph → if valid, opens PreviewPanel with input focused. User types first message and sends to start the session.

## Data Flow

```
User clicks "Run Preview"
  → validateGraph(graph)
  → if errors: show ValidationPanel, don't start
  → if OK: open PreviewPanel, user types first message

User sends first message:
  → POST /api/preview/start { graph, message }
  → Backend returns { sessionId }
  → Frontend opens EventSource: GET /api/preview/{sessionId}/events
  → Backend: validate → adapt → build AIAgent → execute
  → SSE stream:
      session-created    { sessionId }
      agent-initialized  { durationMs }
      llm-call-started   { model }
      llm-call-completed { durationMs, tokens }
      tool-call-started  { tool, args }
      tool-call-completed { tool, durationMs }
      agent-message      { content }
      stream-complete    {}
  → preview-client parses events → store updates UI

Follow-up message:
  → POST /api/preview/{sessionId}/message { message }
  → Events arrive on existing EventSource connection
  → Same event flow

Stop:
  → POST /api/preview/{sessionId}/stop
  → Backend cancels scope, cleans up agent + MCP connections
  → EventSource closed
  → Store → status: "idle"
```

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Validation errors | Preview doesn't start, errors shown in ValidationPanel |
| Missing API key | `POST /start` returns 400 with message, shown in ChatArea |
| Agent build failure | SSE `error` event, status → "error", message in ChatArea |
| LLM error (rate limit, timeout) | SSE `error` event, failed step in Timeline (✗) |
| Tool error | Timeline shows tool step as ✗, agent decides to continue or not |
| Connection lost | EventSource `onerror` → status "error", "Restart" button shown |
| Session expired (TTL) | Backend 404, frontend shows "Session expired, please restart" |
| Max sessions reached | `POST /start` returns 429, frontend shows "Too many active sessions" |

## What Does NOT Change

- **Export flow** — completely separate, no shared runtime state
- **Existing stores** — editorStore and validationStore untouched
- **Canvas/Properties** — no modifications
- **Backend export package** — only reuses adapter and validator (read-only)
