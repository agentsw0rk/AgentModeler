# Agent Chat with MCP Graph Manipulation Design

## Overview

Users can chat with an AI agent in the editor. The agent interprets natural language requests, calls EditorMcpTools directly to create/modify graph structures, and the frontend updates in real-time via the existing SSE infrastructure. Chat appears as a new "Agent Chat" tab in the PreviewPanel.

## Key Decisions

- **Agent architecture:** Agent calls EditorMcpTools directly (no MCP loopback)
- **LLM provider:** Configurable Spring AI ChatClient (uses existing `spring-ai-starter-model-openai` config)
- **UI position:** New "Agent Chat" tab in PreviewPanel (alongside Preview, Export Review)
- **Feedback:** Agent summary + expandable tool-call details per message
- **Session:** Single in-memory chat history (matches MCP server single-session approach)

## Backend Architecture

### New Package: `agentchat/`

```
src/main/kotlin/com/agentwork/modeler/agentchat/
├── AgentChatController.kt    # SSE endpoint POST /api/agent/chat
├── AgentChatService.kt       # Spring AI ChatClient + tool calling
└── model/
    └── AgentChatDto.kt       # Request/Response DTOs
```

### AgentChatService

- Uses Spring AI `ChatClient` with tool calling
- System prompt instructs the agent as a graph editor assistant:
  - Knows all node types (agent, tool, builtin-tool, sub-agent, mcp-server, planner-agent)
  - Knows connection rules (tools → agents, not agents → agents)
  - Instructed to read the current graph before making changes
  - Responds in the user's language
- EditorMcpTools methods registered as tool callbacks via `ToolCallbackProvider`
- Maintains chat history in-memory (single list, reset via endpoint)
- Records tool calls with arguments and results for each agent turn

### AgentChatController

- `POST /api/agent/chat` → `SseEmitter`
  - Request body: `AgentChatRequest(message: String)`
  - Streams events:
    - `tool-call`: `{ tool: "createNode", args: {...}, result: "..." }`
    - `agent-message`: `{ content: "I created an agent with two tools..." }`
    - `stream-complete`: `{}`
    - `error`: `{ message: "..." }`
- `POST /api/agent/chat/reset` → Clears chat history, returns 200 OK

### Event Format

```
event: tool-call
data: {"type":"tool-call","tool":"createNode","args":{"type":"agent","x":200,"y":200,"data":{"name":"MyAgent"}},"result":"{\"nodeId\":\"node-1\"}"}

event: agent-message
data: {"type":"agent-message","content":"I created an Agent node 'MyAgent' at position (200, 200)."}

event: stream-complete
data: {"type":"stream-complete"}
```

## Frontend Architecture

### New Files

- `frontend/store/agentChatStore.ts` — Zustand store for chat state
- `frontend/lib/agent-chat-client.ts` — API client for agent chat
- `frontend/components/agent-chat/AgentChatPanel.tsx` — Chat UI component

### Modified Files

- `frontend/store/previewStore.ts` — Add "agent-chat" to activeTab type
- `frontend/components/preview/PreviewPanel.tsx` — Add "Agent Chat" tab + render AgentChatPanel

### agentChatStore

```typescript
interface AgentChatState {
  messages: AgentChatMessage[];
  status: "idle" | "streaming" | "error";
  addUserMessage: (text: string) => void;
  addAgentMessage: (text: string, toolCalls: ToolCallInfo[]) => void;
  setStatus: (status: "idle" | "streaming" | "error") => void;
  reset: () => void;
}

interface AgentChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  toolCalls?: ToolCallInfo[];
}

interface ToolCallInfo {
  tool: string;
  args: Record<string, unknown>;
  result: string;
}
```

### agent-chat-client

- `sendAgentMessage(message, onEvent)` → POST to `/api/agent/chat`, parse SSE stream
  - Collects `tool-call` events into array
  - On `agent-message`, calls store with text + collected tool calls
  - Returns AbortController for cancellation
- `resetAgentChat()` → POST to `/api/agent/chat/reset`

### AgentChatPanel

- Message list: user messages (left-aligned, muted background), agent messages (left-aligned, slightly different background)
- Agent messages with tool calls: expandable "Tool Calls (N)" section showing each call with tool name, args, result
- Input field at bottom with send button
- Streaming indicator when agent is working
- Scroll-to-bottom on new messages

### PreviewPanel Integration

- Add "Agent Chat" tab button in tab bar
- Extend `activeTab` type in previewStore to include `"agent-chat"`
- When tab active: render `<AgentChatPanel />`

## Data Flow

```
1. User types "Create an agent with two tools" in Agent Chat
2. Frontend: POST /api/agent/chat { message: "..." }
3. Backend: AgentChatService passes message + history to ChatClient
4. ChatClient (LLM) decides tool calls: createNode, createNode, createNode, createEdge, createEdge
5. For each tool call:
   a. EditorMcpTools.createNode() → EditorStateService mutates graph
   b. EditorStateService emits SSE event (node-created) on /api/editor/events
   c. Frontend receives SSE event → editorStore.applyRemoteNodeCreated() → canvas updates
   d. tool-call event streams to chat: { tool: "createNode", args: {...}, result: "..." }
6. Agent responds: "I created an Agent 'MyAgent' and two Tools, all connected."
7. Frontend shows response + expandable tool-call details
```

Graph updates and chat response run in parallel — nodes appear on canvas while agent is still responding.

## Error Handling

- Tool call fails (e.g. invalid node type) → agent receives error JSON, explains in chat
- LLM error (API down, rate limit) → `error` event to frontend, status "error" in store
- Empty message → frontend validation, no API call
- SSE disconnect → existing auto-reconnect handles it

## Tests

| Test Class | Coverage |
|------------|----------|
| `AgentChatServiceTest` | Chat message forwarded to ChatClient, tool calls recorded, history maintained |
| `AgentChatControllerTest` | SSE endpoint returns events, reset endpoint clears history |

Tool logic already tested in `EditorMcpToolsTest`. Tests here focus on the chat→tool integration.

## Files Summary

### Create
1. `src/main/kotlin/com/agentwork/modeler/agentchat/model/AgentChatDto.kt`
2. `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatService.kt`
3. `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatController.kt`
4. `src/test/kotlin/com/agentwork/modeler/agentchat/AgentChatServiceTest.kt`
5. `frontend/store/agentChatStore.ts`
6. `frontend/lib/agent-chat-client.ts`
7. `frontend/components/agent-chat/AgentChatPanel.tsx`

### Modify
1. `frontend/store/previewStore.ts` — Add "agent-chat" to activeTab union
2. `frontend/components/preview/PreviewPanel.tsx` — Add tab + render AgentChatPanel
