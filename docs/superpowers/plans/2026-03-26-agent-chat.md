# Agent Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "Agent Chat" tab where users chat with an AI agent that creates and modifies graph structures via EditorMcpTools, with real-time canvas updates via SSE.

**Architecture:** Spring AI ChatClient with EditorMcpTools registered as tool callbacks. Chat endpoint streams SSE events (tool-calls + agent response). Frontend receives graph updates via existing editor SSE stream, chat responses via dedicated chat SSE stream.

**Tech Stack:** Spring AI ChatClient, Kotlin/Spring Boot, Zustand, SSE, TypeScript/React

**Spec:** `docs/superpowers/specs/2026-03-26-agent-chat-design.md`

---

### Task 1: Backend DTOs

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/agentchat/model/AgentChatDto.kt`

- [ ] **Step 1: Create AgentChatDto.kt**

```kotlin
package com.agentwork.modeler.agentchat.model

data class AgentChatRequest(
    val message: String
)

data class AgentChatEvent(
    val type: String,
    val content: String? = null,
    val tool: String? = null,
    val args: Map<String, Any?>? = null,
    val result: String? = null,
    val message: String? = null
) {
    companion object {
        fun toolCall(tool: String, args: Map<String, Any?>, result: String) =
            AgentChatEvent(type = "tool-call", tool = tool, args = args, result = result)

        fun agentMessage(content: String) =
            AgentChatEvent(type = "agent-message", content = content)

        fun streamComplete() =
            AgentChatEvent(type = "stream-complete")

        fun error(message: String) =
            AgentChatEvent(type = "error", message = message)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/agentchat/model/AgentChatDto.kt
git commit -m "feat: add AgentChat DTOs for request and SSE events"
```

---

### Task 2: Agent Chat Service

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatService.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/agentchat/AgentChatServiceTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentChatServiceTest {

    @Test
    fun `chat history starts empty`() {
        val service = AgentChatService(chatClient = null)
        assertTrue(service.getHistory().isEmpty())
    }

    @Test
    fun `addUserMessage adds to history`() {
        val service = AgentChatService(chatClient = null)
        service.addUserMessage("Hello")
        assertEquals(1, service.getHistory().size)
        assertEquals("user", service.getHistory()[0].role)
        assertEquals("Hello", service.getHistory()[0].content)
    }

    @Test
    fun `addAssistantMessage adds to history`() {
        val service = AgentChatService(chatClient = null)
        service.addAssistantMessage("Hi there")
        assertEquals(1, service.getHistory().size)
        assertEquals("assistant", service.getHistory()[0].role)
    }

    @Test
    fun `resetHistory clears all messages`() {
        val service = AgentChatService(chatClient = null)
        service.addUserMessage("Hello")
        service.addAssistantMessage("Hi")
        service.resetHistory()
        assertTrue(service.getHistory().isEmpty())
    }

    @Test
    fun `AgentChatEvent factory methods produce correct types`() {
        val toolCall = AgentChatEvent.toolCall("createNode", mapOf("type" to "agent"), """{"nodeId":"node-1"}""")
        assertEquals("tool-call", toolCall.type)
        assertEquals("createNode", toolCall.tool)

        val msg = AgentChatEvent.agentMessage("Done")
        assertEquals("agent-message", msg.type)
        assertEquals("Done", msg.content)

        val complete = AgentChatEvent.streamComplete()
        assertEquals("stream-complete", complete.type)

        val err = AgentChatEvent.error("Failed")
        assertEquals("error", err.type)
        assertEquals("Failed", err.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.agentchat.AgentChatServiceTest"`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Implement AgentChatService**

```kotlin
package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.tool.ToolCallback
import org.springframework.stereotype.Service

@Service
class AgentChatService(
    private val chatClient: ChatClient?,
    private val toolCallbacks: List<ToolCallback> = emptyList()
) {

    private val history = mutableListOf<ChatMessage>()
    private val mapper = jacksonObjectMapper()

    companion object {
        val SYSTEM_PROMPT = """
            You are a graph editor assistant for the Agent Studio visual editor.
            You help users create and modify AI agent graphs by calling the available tools.

            Available node types:
            - agent: Main AI agent (requires name, systemPrompt, modelProvider)
            - tool: Custom tool (requires name, description)
            - builtin-tool: Built-in tool (requires toolType: think/shell/filesystem/fetch/memory/todo/user_prompt)
            - sub-agent: Delegable sub-agent (requires name, description, systemPrompt, modelProvider, delegationMode, inputDescription)
            - mcp-server: External MCP server (requires name, transportType: sse/stdio)
            - planner-agent: Planner agent (requires name, description, plannerType: llm/goap)

            Connection rules:
            - Edges go FROM tool/builtin-tool/sub-agent/mcp-server TO agent/sub-agent/planner-agent
            - Never connect agent to agent

            Workflow:
            1. First call getGraph() to understand the current state
            2. Then create/modify nodes and edges as requested
            3. Summarize what you did

            Respond in the same language the user writes in.
        """.trimIndent()
    }

    data class ChatMessage(val role: String, val content: String)

    fun getHistory(): List<ChatMessage> = history.toList()

    fun addUserMessage(content: String) {
        history.add(ChatMessage("user", content))
    }

    fun addAssistantMessage(content: String) {
        history.add(ChatMessage("assistant", content))
    }

    fun resetHistory() {
        history.clear()
    }

    fun chat(userMessage: String, onEvent: (AgentChatEvent) -> Unit) {
        addUserMessage(userMessage)

        if (chatClient == null) {
            onEvent(AgentChatEvent.error("ChatClient not configured"))
            onEvent(AgentChatEvent.streamComplete())
            return
        }

        try {
            val messages = mutableListOf<Message>()
            messages.add(SystemMessage(SYSTEM_PROMPT))
            for (msg in history) {
                when (msg.role) {
                    "user" -> messages.add(UserMessage(msg.content))
                    "assistant" -> messages.add(AssistantMessage(msg.content))
                }
            }

            val response = chatClient.prompt()
                .messages(messages)
                .toolCallbacks(toolCallbacks)
                .call()

            val result = response.chatResponse()
            val assistantContent = result?.result?.output?.text ?: ""

            // Extract tool calls from response metadata if available
            val toolCalls = result?.result?.output?.toolCalls ?: emptyList()
            for (tc in toolCalls) {
                onEvent(AgentChatEvent.toolCall(
                    tool = tc.name(),
                    args = try { mapper.readValue(tc.arguments(), Map::class.java) as Map<String, Any?> } catch (_: Exception) { mapOf("raw" to tc.arguments()) },
                    result = "" // Tool results are captured by the framework
                ))
            }

            addAssistantMessage(assistantContent)
            onEvent(AgentChatEvent.agentMessage(assistantContent))
            onEvent(AgentChatEvent.streamComplete())
        } catch (e: Exception) {
            onEvent(AgentChatEvent.error(e.message ?: "Unknown error"))
            onEvent(AgentChatEvent.streamComplete())
        }
    }
}
```

Note: The exact Spring AI ChatClient API may need adjustment based on the version in use (2.0.0-M3). The implementer should check Spring AI docs if compilation fails and adapt the `chat()` method accordingly. The key contract is: take user message, call ChatClient with history + tools, emit tool-call events and agent-message event.

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.agentchat.AgentChatServiceTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatService.kt src/test/kotlin/com/agentwork/modeler/agentchat/AgentChatServiceTest.kt
git commit -m "feat: add AgentChatService with ChatClient tool-calling integration"
```

---

### Task 3: Agent Chat Controller

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatController.kt`

- [ ] **Step 1: Create AgentChatController**

```kotlin
package com.agentwork.modeler.agentchat

import com.agentwork.modeler.agentchat.model.AgentChatRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlinx.coroutines.*

@RestController
@RequestMapping("/api/agent")
class AgentChatController(
    private val agentChatService: AgentChatService
) {
    private val mapper = jacksonObjectMapper()

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody request: AgentChatRequest): SseEmitter {
        val emitter = SseEmitter(0L)

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                agentChatService.chat(request.message) { event ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name(event.type)
                                .data(mapper.writeValueAsString(event))
                        )
                    } catch (_: Exception) {
                        // Client disconnected
                    }
                }
                emitter.complete()
            } catch (e: Exception) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(mapper.writeValueAsString(mapOf("type" to "error", "message" to (e.message ?: "Unknown error"))))
                    )
                    emitter.complete()
                } catch (_: Exception) {
                    emitter.completeWithError(e)
                }
            }
        }

        return emitter
    }

    @PostMapping("/chat/reset")
    fun resetChat(): ResponseEntity<Map<String, String>> {
        agentChatService.resetHistory()
        return ResponseEntity.ok(mapOf("message" to "Chat history cleared"))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatController.kt
git commit -m "feat: add AgentChatController with SSE streaming and reset endpoint"
```

---

### Task 4: Agent Chat Config (Wire ChatClient + Tools)

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatConfig.kt`

- [ ] **Step 1: Create AgentChatConfig**

This wires the Spring AI ChatClient and the EditorMcpTools callbacks into the AgentChatService.

```kotlin
package com.agentwork.modeler.agentchat

import com.agentwork.modeler.mcpserver.EditorMcpTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentChatConfig {

    @Bean
    fun agentChatToolCallbacks(editorMcpTools: EditorMcpTools): List<ToolCallback> {
        val provider = MethodToolCallbackProvider.builder()
            .toolObjects(editorMcpTools)
            .build()
        return provider.toolCallbacks.toList()
    }

    @Bean
    fun agentChatService(
        chatClientBuilder: ChatClient.Builder,
        agentChatToolCallbacks: List<ToolCallback>
    ): AgentChatService {
        val chatClient = chatClientBuilder.build()
        return AgentChatService(chatClient, agentChatToolCallbacks)
    }
}
```

Note: Remove the `@Service` annotation from `AgentChatService` since it's now created via `@Bean`. Change line 14 of `AgentChatService.kt` from `@Service` to nothing (remove the annotation).

- [ ] **Step 2: Remove @Service from AgentChatService**

In `src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatService.kt`, remove the `@Service` annotation and its import. The class is now instantiated by `AgentChatConfig`.

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatConfig.kt src/main/kotlin/com/agentwork/modeler/agentchat/AgentChatService.kt
git commit -m "feat: add AgentChatConfig wiring ChatClient and EditorMcpTools"
```

---

### Task 5: Frontend Agent Chat Store

**Files:**
- Create: `frontend/store/agentChatStore.ts`

- [ ] **Step 1: Create agentChatStore.ts**

```typescript
import { create } from "zustand";

export interface ToolCallInfo {
  tool: string;
  args: Record<string, unknown>;
  result: string;
}

export interface AgentChatMessage {
  id: string;
  role: "user" | "agent";
  text: string;
  toolCalls?: ToolCallInfo[];
}

interface AgentChatState {
  messages: AgentChatMessage[];
  status: "idle" | "streaming" | "error";
  addUserMessage: (text: string) => void;
  addAgentMessage: (text: string, toolCalls: ToolCallInfo[]) => void;
  setStatus: (status: "idle" | "streaming" | "error") => void;
  reset: () => void;
}

let nextMsgId = 1;

export const useAgentChatStore = create<AgentChatState>((set) => ({
  messages: [],
  status: "idle",

  addUserMessage: (text) => {
    const id = `acmsg-${nextMsgId++}`;
    set((state) => ({
      messages: [...state.messages, { id, role: "user", text }],
    }));
  },

  addAgentMessage: (text, toolCalls) => {
    const id = `acmsg-${nextMsgId++}`;
    set((state) => ({
      messages: [...state.messages, { id, role: "agent", text, toolCalls: toolCalls.length > 0 ? toolCalls : undefined }],
    }));
  },

  setStatus: (status) => set({ status }),

  reset: () => {
    set({ messages: [], status: "idle" });
  },
}));
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/store/agentChatStore.ts
git commit -m "feat: add agentChatStore for agent chat state management"
```

---

### Task 6: Frontend Agent Chat Client

**Files:**
- Create: `frontend/lib/agent-chat-client.ts`

- [ ] **Step 1: Create agent-chat-client.ts**

```typescript
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
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/agent-chat-client.ts
git commit -m "feat: add agent-chat-client for SSE communication with agent"
```

---

### Task 7: Agent Chat Panel Component

**Files:**
- Create: `frontend/components/agent-chat/AgentChatPanel.tsx`

- [ ] **Step 1: Create AgentChatPanel.tsx**

```typescript
"use client";

import { useRef, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAgentChatStore, ToolCallInfo } from "@/store/agentChatStore";
import { sendAgentMessage, resetAgentChat, AgentChatSseEvent } from "@/lib/agent-chat-client";
import { ChevronDownIcon, ChevronRightIcon, RotateCcwIcon } from "lucide-react";

function ToolCallDetails({ toolCalls }: { toolCalls: ToolCallInfo[] }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="mt-1">
      <button
        className="text-xs text-muted-foreground flex items-center gap-1 hover:text-foreground"
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? <ChevronDownIcon className="size-3" /> : <ChevronRightIcon className="size-3" />}
        Tool Calls ({toolCalls.length})
      </button>
      {expanded && (
        <div className="mt-1 space-y-1">
          {toolCalls.map((tc, i) => (
            <div key={i} className="text-xs bg-muted/50 rounded p-2 font-mono">
              <span className="text-blue-400">{tc.tool}</span>
              <span className="text-muted-foreground">(</span>
              <span className="text-foreground">{JSON.stringify(tc.args, null, 0)}</span>
              <span className="text-muted-foreground">)</span>
              {tc.result && (
                <div className="text-green-400 mt-0.5 truncate">→ {tc.result}</div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function AgentChatPanel() {
  const messages = useAgentChatStore((s) => s.messages);
  const status = useAgentChatStore((s) => s.status);
  const addUserMessage = useAgentChatStore((s) => s.addUserMessage);
  const addAgentMessage = useAgentChatStore((s) => s.addAgentMessage);
  const setStatus = useAgentChatStore((s) => s.setStatus);
  const reset = useAgentChatStore((s) => s.reset);

  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || status === "streaming") return;

    setInput("");
    addUserMessage(text);
    setStatus("streaming");

    const collectedToolCalls: ToolCallInfo[] = [];

    try {
      abortRef.current = await sendAgentMessage(text, (event: AgentChatSseEvent) => {
        switch (event.type) {
          case "tool-call":
            if (event.tool) {
              collectedToolCalls.push({
                tool: event.tool,
                args: event.args || {},
                result: event.result || "",
              });
            }
            break;
          case "agent-message":
            addAgentMessage(event.content || "", collectedToolCalls);
            setStatus("idle");
            break;
          case "stream-complete":
            setStatus("idle");
            break;
          case "error":
            addAgentMessage(`Error: ${event.message || "Unknown error"}`, collectedToolCalls);
            setStatus("error");
            break;
        }
      });
    } catch (e) {
      addAgentMessage(`Error: ${e instanceof Error ? e.message : String(e)}`, []);
      setStatus("error");
    }
  };

  const handleReset = async () => {
    abortRef.current?.abort();
    reset();
    try {
      await resetAgentChat();
    } catch {
      // ignore
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {messages.length === 0 && (
          <div className="text-xs text-muted-foreground text-center mt-8">
            Describe what you want to build. The agent will create the graph structure for you.
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[85%] rounded-lg px-3 py-2 text-sm ${
                msg.role === "user"
                  ? "bg-primary text-primary-foreground rounded-br-sm"
                  : "bg-muted rounded-bl-sm"
              }`}
            >
              <div className="whitespace-pre-wrap">{msg.text}</div>
              {msg.toolCalls && msg.toolCalls.length > 0 && (
                <ToolCallDetails toolCalls={msg.toolCalls} />
              )}
            </div>
          </div>
        ))}
        {status === "streaming" && (
          <div className="flex justify-start">
            <div className="bg-muted rounded-lg px-3 py-2 text-sm rounded-bl-sm">
              <span className="animate-pulse">▊</span>
            </div>
          </div>
        )}
        <div ref={scrollRef} />
      </div>
      <div className="border-t p-2 flex gap-2">
        <Button variant="ghost" size="sm" className="h-8 w-8 p-0 shrink-0" onClick={handleReset} title="Reset chat">
          <RotateCcwIcon className="size-3.5" />
        </Button>
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Describe what to build..."
          className="h-8 text-sm"
          disabled={status === "streaming"}
        />
        <Button size="sm" className="h-8 shrink-0" onClick={handleSend} disabled={status === "streaming" || !input.trim()}>
          Send
        </Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add frontend/components/agent-chat/AgentChatPanel.tsx
git commit -m "feat: add AgentChatPanel with tool-call details and streaming"
```

---

### Task 8: Integrate Agent Chat Tab into PreviewPanel

**Files:**
- Modify: `frontend/store/previewStore.ts:12` (activeTab type)
- Modify: `frontend/components/preview/PreviewPanel.tsx` (tab + content)

- [ ] **Step 1: Extend activeTab type in previewStore**

In `frontend/store/previewStore.ts`, find the `activeTab` type (line 12) and the `setActiveTab` signature (line 23). Change both:

```typescript
// Line 12: Change
activeTab: "preview" | "review";
// To:
activeTab: "preview" | "review" | "agent-chat";

// Line 23: Change
setActiveTab: (tab: "preview" | "review") => void;
// To:
setActiveTab: (tab: "preview" | "review" | "agent-chat") => void;
```

- [ ] **Step 2: Add Agent Chat tab and content to PreviewPanel**

In `frontend/components/preview/PreviewPanel.tsx`:

Add import:
```typescript
import { AgentChatPanel } from "@/components/agent-chat/AgentChatPanel";
```

Find the tab buttons section (around lines 174-185). After the "Export Review" button, add:

```tsx
<button
  className={`text-xs px-2 py-0.5 rounded ${activeTab === "agent-chat" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
  onClick={() => setActiveTab("agent-chat")}
>
  Agent Chat
</button>
```

Find the content rendering section (around lines 213-226). Add an `agent-chat` case. The full conditional should become:

```tsx
{activeTab === "preview" && isPlannerSelected && selectedPlannerData ? (
  <div className="flex-1 overflow-y-auto">
    <PlanInspector data={selectedPlannerData} />
  </div>
) : activeTab === "preview" ? (
  <div className="flex flex-1 overflow-hidden">
    <div className="w-56 border-r overflow-y-auto">
      <ExecutionTimeline />
    </div>
    <ChatArea onSendMessage={handleSendMessage} />
  </div>
) : activeTab === "agent-chat" ? (
  <AgentChatPanel />
) : (
  <ExportReviewPanel />
)}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: Verify frontend builds**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 5: Commit**

```bash
git add frontend/store/previewStore.ts frontend/components/preview/PreviewPanel.tsx
git commit -m "feat: integrate Agent Chat tab into PreviewPanel"
```

---

### Task 9: Full Verification

**Files:** None (verification only)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test`
Expected: All tests pass (0 failures)

- [ ] **Step 2: Verify frontend TypeScript**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Verify frontend builds**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds
