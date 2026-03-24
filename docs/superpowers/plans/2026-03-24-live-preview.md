# Live Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Live Preview panel to the Koog Agent Editor that lets users test-run their agent graphs with a chat interface and real-time execution timeline.

**Architecture:** Backend builds agents in-memory from graph via existing `GraphToKoogAdapter`, manages chat sessions with TTL, streams execution events via SSE. Frontend has a bottom drawer with execution timeline + chat area, connected via native `EventSource` API. Mock mode for UI development without backend.

**Tech Stack:** Kotlin/Spring Boot (SSE via `SseEmitter`), Koog SDK (`AIAgent`, `ToolRegistry`, `McpToolRegistryProvider`), React/Zustand, TypeScript, `EventSource` API

---

## File Structure

### Backend (`src/main/kotlin/com/agentwork/modeler/preview/`)

| File | Responsibility |
|------|---------------|
| `PreviewEvent.kt` | Sealed class for all SSE event types |
| `PreviewSession.kt` | Data class holding agent, scope, emitter, history |
| `PreviewSessionManager.kt` | Session CRUD, TTL cleanup, concurrency limits |
| `PreviewAgentBuilder.kt` | Bridges KoogAgent data model → live AIAgent runtime |
| `PreviewService.kt` | Orchestrates validation, agent building, execution |
| `PreviewController.kt` | REST + SSE endpoints |

### Frontend

| File | Responsibility |
|------|---------------|
| `frontend/types/preview.ts` | TypeScript types for events, messages, steps |
| `frontend/store/previewStore.ts` | Zustand store for preview state |
| `frontend/lib/preview-client.ts` | SSE client (real backend) |
| `frontend/lib/preview-mock.ts` | Mock client for UI development |
| `frontend/components/preview/PreviewPanel.tsx` | Bottom drawer container |
| `frontend/components/preview/ExecutionTimeline.tsx` | Left column: step list |
| `frontend/components/preview/ChatArea.tsx` | Right column: messages + input |

### Modified Files

| File | Change |
|------|--------|
| `frontend/app/page.tsx` | Add PreviewPanel to layout |
| `frontend/components/panels/Toolbar.tsx` | Add "Run Preview" button |

---

### Task 1: Backend — PreviewEvent sealed class

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewEvent.kt`

- [ ] **Step 1: Create the event types**

```kotlin
package com.agentwork.modeler.preview

sealed class PreviewEvent(val type: String) {
    companion object {
        private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    }

    data class SessionCreated(val sessionId: String) : PreviewEvent("session-created")
    data class AgentInitialized(val durationMs: Long) : PreviewEvent("agent-initialized")
    data class LlmCallStarted(val model: String) : PreviewEvent("llm-call-started")
    data class LlmCallCompleted(val durationMs: Long, val tokens: Int) : PreviewEvent("llm-call-completed")
    data class ToolCallStarted(val tool: String, val args: Map<String, Any?>) : PreviewEvent("tool-call-started")
    data class ToolCallCompleted(val tool: String, val durationMs: Long, val result: String?) : PreviewEvent("tool-call-completed")
    data class AgentMessage(val content: String) : PreviewEvent("agent-message")
    data class Error(val message: String) : PreviewEvent("error")
    data object StreamComplete : PreviewEvent("stream-complete")

    fun toJson(): String {
        val data = when (this) {
            is SessionCreated -> mapOf("sessionId" to sessionId)
            is AgentInitialized -> mapOf("durationMs" to durationMs)
            is LlmCallStarted -> mapOf("model" to model)
            is LlmCallCompleted -> mapOf("durationMs" to durationMs, "tokens" to tokens)
            is ToolCallStarted -> mapOf("tool" to tool, "args" to args)
            is ToolCallCompleted -> mapOf("tool" to tool, "durationMs" to durationMs, "result" to result)
            is AgentMessage -> mapOf("content" to content)
            is Error -> mapOf("message" to message)
            is StreamComplete -> emptyMap()
        }
        return objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/preview/PreviewEvent.kt
git commit -m "feat(preview): add PreviewEvent sealed class"
```

---

### Task 2: Backend — PreviewSession and PreviewSessionManager

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewSession.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewSessionManager.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/preview/PreviewSessionManagerTest.kt`

- [ ] **Step 1: Create PreviewSession data class**

```kotlin
package com.agentwork.modeler.preview

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.CoroutineScope
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

data class PreviewSession(
    val sessionId: String,
    val agent: AIAgent<String, String>,
    val scope: CoroutineScope,
    val emitter: SseEmitter,
    val history: MutableList<ConversationMessage> = mutableListOf(),
    val createdAt: Instant = Instant.now()
)

data class ConversationMessage(
    val role: String,
    val content: String
)
```

- [ ] **Step 2: Create PreviewSessionManager**

```kotlin
package com.agentwork.modeler.preview

import kotlinx.coroutines.cancel
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PreviewSessionManager {

    companion object {
        private const val TTL_MINUTES = 30L
        private const val MAX_SESSIONS = 5
    }

    private val sessions = ConcurrentHashMap<String, PreviewSession>()

    fun createSession(session: PreviewSession): String {
        if (sessions.size >= MAX_SESSIONS) {
            throw PreviewException("Too many active sessions (max $MAX_SESSIONS)")
        }
        sessions[session.sessionId] = session
        return session.sessionId
    }

    fun getSession(sessionId: String): PreviewSession? = sessions[sessionId]

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)?.let { session ->
            session.scope.cancel()
            session.emitter.complete()
        }
    }

    fun generateId(): String = UUID.randomUUID().toString()

    fun activeCount(): Int = sessions.size

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    fun cleanupExpired() {
        val now = Instant.now()
        sessions.entries.removeIf { (_, session) ->
            val expired = session.createdAt.plusSeconds(TTL_MINUTES * 60).isBefore(now)
            if (expired) {
                session.scope.cancel()
                session.emitter.complete()
            }
            expired
        }
    }
}

class PreviewException(message: String) : RuntimeException(message)
```

- [ ] **Step 3: Write test for session manager**

**Note:** The test uses `org.mockito.Mockito.mock()` for AIAgent. If Mockito is not in `build.gradle.kts` test dependencies, add `testImplementation("org.mockito:mockito-core:5.+")` and `testImplementation("org.mockito.kotlin:mockito-kotlin:5.+")`.


```kotlin
package com.agentwork.modeler.preview

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PreviewSessionManagerTest {

    private lateinit var manager: PreviewSessionManager

    @BeforeEach
    fun setUp() {
        manager = PreviewSessionManager()
    }

    @Test
    fun `generates unique session IDs`() {
        val id1 = manager.generateId()
        val id2 = manager.generateId()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `stores and retrieves session`() {
        val session = createTestSession("test-1")
        manager.createSession(session)
        assertNotNull(manager.getSession("test-1"))
        assertEquals(1, manager.activeCount())
    }

    @Test
    fun `removes session`() {
        val session = createTestSession("test-1")
        manager.createSession(session)
        manager.removeSession("test-1")
        assertNull(manager.getSession("test-1"))
        assertEquals(0, manager.activeCount())
    }

    @Test
    fun `returns null for unknown session`() {
        assertNull(manager.getSession("unknown"))
    }

    @Test
    fun `enforces max session limit`() {
        repeat(5) { manager.createSession(createTestSession("s-$it")) }
        assertThrows<PreviewException> {
            manager.createSession(createTestSession("s-overflow"))
        }
    }

    private fun createTestSession(id: String): PreviewSession {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
        )
        return PreviewSession(
            sessionId = id,
            agent = org.mockito.Mockito.mock(),
            scope = scope,
            emitter = org.springframework.web.servlet.mvc.method.annotation.SseEmitter()
        )
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "*PreviewSessionManagerTest*"`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/preview/PreviewSession.kt \
        src/main/kotlin/com/agentwork/modeler/preview/PreviewSessionManager.kt \
        src/test/kotlin/com/agentwork/modeler/preview/PreviewSessionManagerTest.kt
git commit -m "feat(preview): add PreviewSession and PreviewSessionManager with tests"
```

---

### Task 3: Backend — PreviewAgentBuilder

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewAgentBuilder.kt`

- [ ] **Step 1: Create the agent builder**

This class bridges the `KoogAgent` data model to a live `AIAgent` instance. Custom tools become stubs, builtin tools use SDK implementations, MCP connections are established live.

```kotlin
package com.agentwork.modeler.preview

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.ToolSet
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import com.agentwork.modeler.export.model.*
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component

@Component
class PreviewAgentBuilder {

    suspend fun build(agent: KoogAgent, scope: CoroutineScope): AIAgent<String, String> {
        val apiKeyEnv = "${agent.modelProvider.name}_API_KEY"
        val apiKey = System.getenv(apiKeyEnv)
            ?: throw PreviewException("Missing environment variable: $apiKeyEnv")

        val executor = when (agent.modelProvider) {
            ModelProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            ModelProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            ModelProvider.GOOGLE -> simpleGoogleExecutor(apiKey)
        }

        val model = agent.modelId?.let { resolveModel(agent.modelProvider, it) }
            ?: resolveDefaultModel(agent.modelProvider)

        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.mcpServers.isNotEmpty()

        return if (hasTools) {
            val registry = buildToolRegistry(agent, scope)
            AIAgent(
                promptExecutor = executor,
                systemPrompt = agent.systemPrompt,
                llmModel = model,
                toolRegistry = registry
            )
        } else {
            AIAgent(
                promptExecutor = executor,
                systemPrompt = agent.systemPrompt,
                llmModel = model
            )
        }
    }

    private suspend fun buildToolRegistry(agent: KoogAgent, scope: CoroutineScope): ToolRegistry {
        val registries = mutableListOf<ToolRegistry>()

        // MCP tool registries
        for (mcp in agent.mcpServers) {
            val transport = when (mcp.transportType) {
                "sse" -> McpToolRegistryProvider.defaultSseTransport(mcp.url!!)
                "stdio" -> {
                    val cmdList = mutableListOf(mcp.command!!)
                    mcp.args?.let { cmdList.addAll(it) }
                    val pb = ProcessBuilder(cmdList)
                    mcp.envVars.forEach { envVar ->
                        System.getenv(envVar.name)?.let { pb.environment()[envVar.name] = it }
                    }
                    McpToolRegistryProvider.defaultStdioTransport(pb.start())
                }
                else -> continue
            }
            registries.add(McpToolRegistryProvider.fromTransport(transport))
        }

        // Combine all registries (builtin + MCP). Custom tools are stubs logged via system prompt.
        return if (registries.isEmpty()) {
            ToolRegistry {}
        } else {
            registries.reduce { acc, reg -> acc + reg }
        }
    }

    private fun resolveModel(provider: ModelProvider, id: String) = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
            "gpt-4-1" -> OpenAIModels.Chat.GPT4_1
            else -> OpenAIModels.Chat.GPT4o
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> AnthropicModels.Sonnet_4
            "claude-opus-4" -> AnthropicModels.Opus_4
            "claude-haiku-3.5" -> AnthropicModels.Haiku_35
            else -> AnthropicModels.Sonnet_4
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> GoogleModels.Gemini2Flash
            "gemini-2-pro" -> GoogleModels.Gemini2Pro
            else -> GoogleModels.Gemini2Flash
        }
    }

    private fun resolveDefaultModel(provider: ModelProvider) = when (provider) {
        ModelProvider.OPENAI -> OpenAIModels.Chat.GPT4o
        ModelProvider.ANTHROPIC -> AnthropicModels.Sonnet_4
        ModelProvider.GOOGLE -> GoogleModels.Gemini2Flash
    }
}
```

**Note:** The exact Koog SDK APIs for `ToolRegistry` combination, `ToolSet` creation, and model references may need adjustment during implementation based on the actual SDK version (0.7.2). The implementer should consult `node_modules` / gradle cache for the exact API. The pattern above follows `MyAgentService.kt` and `McpDiscoveryService.kt` as references.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (may need import adjustments — see note above)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/preview/PreviewAgentBuilder.kt
git commit -m "feat(preview): add PreviewAgentBuilder for in-memory agent construction"
```

---

### Task 4: Backend — PreviewService

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewService.kt`

- [ ] **Step 1: Create the preview service**

```kotlin
package com.agentwork.modeler.preview

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.export.validation.GraphValidator
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class PreviewService(
    private val validator: GraphValidator,
    private val adapter: GraphToKoogAdapter,
    private val agentBuilder: PreviewAgentBuilder,
    private val sessionManager: PreviewSessionManager
) {

    fun startSession(graph: ProjectGraph, initialMessage: String): SseEmitter {
        val errors = validator.validate(graph)
        if (errors.isNotEmpty()) {
            throw PreviewValidationException(errors)
        }

        val agents = adapter.convert(graph)
        if (agents.isEmpty()) {
            throw PreviewException("No agent found in graph")
        }
        val koogAgent = agents.first()

        val sessionId = sessionManager.generateId()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val emitter = SseEmitter(0L) // no timeout — managed by session TTL

        emitter.onCompletion { sessionManager.removeSession(sessionId) }
        emitter.onTimeout { sessionManager.removeSession(sessionId) }
        emitter.onError { sessionManager.removeSession(sessionId) }

        // Register session immediately (before async work) to avoid race conditions
        // Agent is set to a placeholder and replaced after build
        val session = PreviewSession(
            sessionId = sessionId,
            agent = null as Any as AIAgent<String, String>, // replaced below
            scope = scope,
            emitter = emitter
        )

        scope.launch {
            try {
                sendEvent(emitter, PreviewEvent.SessionCreated(sessionId))

                val start = System.currentTimeMillis()
                val agent = agentBuilder.build(koogAgent, scope)
                val buildTime = System.currentTimeMillis() - start
                sendEvent(emitter, PreviewEvent.AgentInitialized(buildTime))

                val readySession = session.copy(agent = agent)
                sessionManager.createSession(readySession)

                executeMessage(readySession, initialMessage)
            } catch (e: Exception) {
                sendEvent(emitter, PreviewEvent.Error(e.message ?: "Unknown error"))
                sendEvent(emitter, PreviewEvent.StreamComplete)
                emitter.complete()
            }
        }

        return emitter
    }

    fun sendMessage(sessionId: String, message: String) {
        val session = sessionManager.getSession(sessionId)
            ?: throw PreviewException("Session not found: $sessionId")

        session.scope.launch {
            try {
                executeMessage(session, message)
            } catch (e: Exception) {
                sendEvent(session.emitter, PreviewEvent.Error(e.message ?: "Unknown error"))
                sendEvent(session.emitter, PreviewEvent.StreamComplete)
            }
        }
    }

    fun stopSession(sessionId: String) {
        sessionManager.removeSession(sessionId)
    }

    private suspend fun executeMessage(session: PreviewSession, message: String) {
        session.history.add(ConversationMessage("user", message))

        sendEvent(session.emitter, PreviewEvent.LlmCallStarted(
            session.agent.toString() // model info — adjust based on SDK API
        ))

        val start = System.currentTimeMillis()
        val response = session.agent.run(message)
        val duration = System.currentTimeMillis() - start

        sendEvent(session.emitter, PreviewEvent.LlmCallCompleted(duration, 0))
        sendEvent(session.emitter, PreviewEvent.AgentMessage(response))

        session.history.add(ConversationMessage("agent", response))
        sendEvent(session.emitter, PreviewEvent.StreamComplete)
    }

    private fun sendEvent(emitter: SseEmitter, event: PreviewEvent) {
        try {
            emitter.send(SseEmitter.event()
                .name(event.type)
                .data(event.toJson()))
        } catch (_: Exception) {
            // Client disconnected
        }
    }
}

class PreviewValidationException(val errors: List<String>) : RuntimeException("Validation failed")
```

**Note on race condition:** The session is registered in `sessionManager` only after the agent is built. This means `POST /{sessionId}/message` will return 404 if called before the agent is ready, which is the correct behavior — the frontend should wait for the `agent-initialized` event before enabling the send button.

**Note:** The `agent.run()` call is simplified. The real Koog SDK may emit intermediate events (tool calls, etc.) via callbacks or flows. The implementer should check the SDK for agent execution hooks/listeners to capture tool call events and LLM call details. If the SDK provides an event listener, wire it to emit `ToolCallStarted`/`ToolCallCompleted` events. If not, the MVP can just emit `LlmCallStarted` → `AgentMessage` → `StreamComplete`.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/preview/PreviewService.kt
git commit -m "feat(preview): add PreviewService for session orchestration"
```

---

### Task 5: Backend — PreviewController

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/preview/PreviewController.kt`

- [ ] **Step 1: Create the controller**

```kotlin
package com.agentwork.modeler.preview

import com.agentwork.modeler.export.model.ProjectGraph
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

data class StartPreviewRequest(
    val graph: ProjectGraph,
    val message: String
)

data class SendMessageRequest(
    val message: String
)

@RestController
@RequestMapping("/api/preview")
class PreviewController(
    private val previewService: PreviewService
) {

    @PostMapping("/start", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun startPreview(@RequestBody request: StartPreviewRequest): SseEmitter {
        return previewService.startSession(request.graph, request.message)
    }

    @PostMapping("/{sessionId}/message")
    fun sendMessage(
        @PathVariable sessionId: String,
        @RequestBody request: SendMessageRequest
    ): ResponseEntity<Void> {
        previewService.sendMessage(sessionId, request.message)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{sessionId}/stop")
    fun stopSession(@PathVariable sessionId: String): ResponseEntity<Void> {
        previewService.stopSession(sessionId)
        return ResponseEntity.ok().build()
    }

    @ExceptionHandler(PreviewValidationException::class)
    fun handleValidation(e: PreviewValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(mapOf(
            "error" to "Validation failed",
            "details" to e.errors
        ))
    }

    @ExceptionHandler(PreviewException::class)
    fun handlePreviewError(e: PreviewException): ResponseEntity<Map<String, String>> {
        val status = if (e.message?.contains("Too many") == true) HttpStatus.TOO_MANY_REQUESTS else HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(mapOf("error" to (e.message ?: "Preview error")))
    }
}
```

**Design note:** The spec originally proposed a two-step flow (`POST /start` returns sessionId, then `GET /events` for SSE). However, Spring WebMVC's `SseEmitter` works naturally as a return value from any `@RequestMapping`. The `POST /start` returns the `SseEmitter` directly, which produces `text/event-stream`. The first event on the stream contains the `sessionId`. This is simpler and avoids the race condition of events firing before the client connects. The `EventSource` browser API only supports GET, so the frontend must use `fetch()` with stream parsing instead — see Task 8 (preview-client.ts).

- [ ] **Step 2: Enable scheduling in application**

Run: `grep -r "EnableScheduling" src/main/kotlin/`

If not found (likely), add `@EnableScheduling` to the main application class. Find it with:
`grep -r "@SpringBootApplication" src/main/kotlin/`

Add `import org.springframework.scheduling.annotation.EnableScheduling` and `@EnableScheduling` annotation above `@SpringBootApplication`.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/preview/PreviewController.kt
git commit -m "feat(preview): add PreviewController with SSE endpoints"
```

---

### Task 6: Frontend — Preview types

**Files:**
- Create: `frontend/types/preview.ts`

- [ ] **Step 1: Create the types file**

```typescript
// frontend/types/preview.ts

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
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/types/preview.ts
git commit -m "feat(preview): add frontend preview types"
```

---

### Task 7: Frontend — Preview store

**Files:**
- Create: `frontend/store/previewStore.ts`

- [ ] **Step 1: Create the store**

```typescript
// frontend/store/previewStore.ts
import { create } from "zustand";
import { PreviewStatus, PreviewMessage, ExecutionStep } from "@/types/preview";

interface PreviewState {
  status: PreviewStatus;
  sessionId: string | null;
  messages: PreviewMessage[];
  steps: ExecutionStep[];
  error: string | null;
  isOpen: boolean;
  useMock: boolean;

  setStatus: (status: PreviewStatus) => void;
  setSessionId: (id: string) => void;
  addMessage: (message: PreviewMessage) => void;
  updateLastAgentMessage: (content: string) => void;
  addStep: (step: ExecutionStep) => void;
  updateStep: (id: string, updates: Partial<ExecutionStep>) => void;
  setError: (error: string) => void;
  toggleOpen: () => void;
  toggleMock: () => void;
  reset: () => void;
}

let nextStepId = 1;
export function generateStepId(): string {
  return `step-${nextStepId++}`;
}

let nextMsgId = 1;
export function generateMsgId(): string {
  return `msg-${nextMsgId++}`;
}

export const usePreviewStore = create<PreviewState>((set) => ({
  status: "idle",
  sessionId: null,
  messages: [],
  steps: [],
  error: null,
  isOpen: false,
  useMock: false,

  setStatus: (status) => set({ status }),
  setSessionId: (id) => set({ sessionId: id }),
  addMessage: (message) => set((s) => ({ messages: [...s.messages, message] })),
  updateLastAgentMessage: (content) =>
    set((s) => {
      const msgs = [...s.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.role === "agent") {
        msgs[msgs.length - 1] = { ...last, content, isStreaming: false };
      }
      return { messages: msgs };
    }),
  addStep: (step) => set((s) => ({ steps: [...s.steps, step] })),
  updateStep: (id, updates) =>
    set((s) => ({
      steps: s.steps.map((step) =>
        step.id === id ? { ...step, ...updates } : step
      ),
    })),
  setError: (error) => set({ error, status: "error" }),
  toggleOpen: () => set((s) => ({ isOpen: !s.isOpen })),
  toggleMock: () => set((s) => ({ useMock: !s.useMock })),
  reset: () => {
    nextStepId = 1;
    nextMsgId = 1;
    return set({
      status: "idle",
      sessionId: null,
      messages: [],
      steps: [],
      error: null,
    });
  },
}));
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/store/previewStore.ts
git commit -m "feat(preview): add previewStore with Zustand"
```

---

### Task 8: Frontend — Preview client (real + mock)

**Files:**
- Create: `frontend/lib/preview-client.ts`
- Create: `frontend/lib/preview-mock.ts`

- [ ] **Step 1: Create the real SSE client**

Since `POST` + SSE requires `fetch()` with stream parsing (not `EventSource`), we use the Fetch API's `ReadableStream`:

```typescript
// frontend/lib/preview-client.ts
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

      let currentEvent = "";
      let currentData = "";

      for (const line of lines) {
        if (line.startsWith("event:")) {
          currentEvent = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          currentData = line.slice(5).trim();
        } else if (line === "" && currentData) {
          try {
            const parsed = JSON.parse(currentData) as PreviewEvent;
            onEvent(parsed);
          } catch {
            // skip malformed events
          }
          currentEvent = "";
          currentData = "";
        }
      }

      processChunk();
    });
  }

  processChunk();
}
```

- [ ] **Step 2: Create the mock client**

```typescript
// frontend/lib/preview-mock.ts
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
```

- [ ] **Step 3: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/preview-client.ts frontend/lib/preview-mock.ts
git commit -m "feat(preview): add SSE preview client and mock client"
```

---

### Task 9: Frontend — ExecutionTimeline component

**Files:**
- Create: `frontend/components/preview/ExecutionTimeline.tsx`

- [ ] **Step 1: Create the component**

```tsx
// frontend/components/preview/ExecutionTimeline.tsx
"use client";

import { usePreviewStore } from "@/store/previewStore";

const statusIcons: Record<string, string> = {
  running: "⟳",
  done: "✓",
  error: "✗",
};

const statusColors: Record<string, string> = {
  running: "text-blue-500",
  done: "text-green-500",
  error: "text-red-500",
};

export function ExecutionTimeline() {
  const steps = usePreviewStore((s) => s.steps);

  if (steps.length === 0) {
    return (
      <div className="p-3 text-xs text-muted-foreground">
        Waiting for execution...
      </div>
    );
  }

  return (
    <div className="p-3 space-y-2 overflow-y-auto">
      <div className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">
        Execution Steps
      </div>
      {steps.map((step) => (
        <div key={step.id} className="flex gap-2 items-start text-xs">
          <span className={statusColors[step.status] || "text-muted-foreground"}>
            {statusIcons[step.status] || "○"}
          </span>
          <div>
            <div className="font-medium">{step.label}</div>
            {step.durationMs !== undefined && (
              <div className="text-muted-foreground text-[10px]">
                {step.durationMs < 1000
                  ? `${step.durationMs}ms`
                  : `${(step.durationMs / 1000).toFixed(1)}s`}
              </div>
            )}
            {step.detail && (
              <div className="text-muted-foreground text-[10px]">{step.detail}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/preview/ExecutionTimeline.tsx
git commit -m "feat(preview): add ExecutionTimeline component"
```

---

### Task 10: Frontend — ChatArea component

**Files:**
- Create: `frontend/components/preview/ChatArea.tsx`

- [ ] **Step 1: Create the component**

```tsx
// frontend/components/preview/ChatArea.tsx
"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { usePreviewStore } from "@/store/previewStore";

interface ChatAreaProps {
  onSendMessage: (message: string) => void;
}

export function ChatArea({ onSendMessage }: ChatAreaProps) {
  const messages = usePreviewStore((s) => s.messages);
  const status = usePreviewStore((s) => s.status);
  const error = usePreviewStore((s) => s.error);
  const [input, setInput] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || status === "running" || status === "connecting") return;
    onSendMessage(trimmed);
    setInput("");
  };

  return (
    <div className="flex flex-col flex-1">
      <div ref={scrollRef} className="flex-1 p-3 overflow-y-auto space-y-2">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`max-w-[70%] rounded-xl px-3 py-2 text-sm ${
              msg.role === "user"
                ? "ml-auto bg-primary text-primary-foreground rounded-br-sm"
                : "mr-auto bg-muted rounded-bl-sm"
            }`}
          >
            {msg.content}
            {msg.isStreaming && <span className="animate-pulse">▊</span>}
          </div>
        ))}
        {error && (
          <div className="mr-auto max-w-[70%] rounded-xl px-3 py-2 text-sm bg-destructive/10 text-destructive">
            {error}
          </div>
        )}
      </div>
      <div className="p-2 border-t flex gap-2">
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") handleSend(); }}
          placeholder="Nachricht eingeben..."
          disabled={status === "connecting"}
          autoFocus
        />
        <Button
          onClick={handleSend}
          disabled={!input.trim() || status === "running" || status === "connecting"}
          size="sm"
        >
          Senden
        </Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/preview/ChatArea.tsx
git commit -m "feat(preview): add ChatArea component"
```

---

### Task 11: Frontend — PreviewPanel container

**Files:**
- Create: `frontend/components/preview/PreviewPanel.tsx`

- [ ] **Step 1: Create the panel**

This wires together ExecutionTimeline + ChatArea and handles the event dispatch logic:

```tsx
// frontend/components/preview/PreviewPanel.tsx
"use client";

import { useCallback, useRef } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { usePreviewStore, generateStepId, generateMsgId } from "@/store/previewStore";
import { useEditorStore } from "@/store/editorStore";
import { useValidationStore } from "@/store/validationStore";
import { validateGraph, hasBlockingErrors } from "@/lib/validation";
import { ExecutionTimeline } from "./ExecutionTimeline";
import { ChatArea } from "./ChatArea";
import { ProjectGraph } from "@/types/graph";
import { PreviewEvent } from "@/types/preview";
import * as realClient from "@/lib/preview-client";
import * as mockClient from "@/lib/preview-mock";

const statusBadge: Record<string, { label: string; variant: "default" | "secondary" | "destructive" }> = {
  idle: { label: "Idle", variant: "secondary" },
  connecting: { label: "Connecting...", variant: "default" },
  running: { label: "Running", variant: "default" },
  success: { label: "Done", variant: "secondary" },
  error: { label: "Error", variant: "destructive" },
};

export function PreviewPanel() {
  const isOpen = usePreviewStore((s) => s.isOpen);
  const status = usePreviewStore((s) => s.status);
  const sessionId = usePreviewStore((s) => s.sessionId);
  const useMock = usePreviewStore((s) => s.useMock);
  const toggleOpen = usePreviewStore((s) => s.toggleOpen);
  const toggleMock = usePreviewStore((s) => s.toggleMock);
  const reset = usePreviewStore((s) => s.reset);
  const setStatus = usePreviewStore((s) => s.setStatus);
  const setSessionId = usePreviewStore((s) => s.setSessionId);
  const setError = usePreviewStore((s) => s.setError);
  const addMessage = usePreviewStore((s) => s.addMessage);
  const updateLastAgentMessage = usePreviewStore((s) => s.updateLastAgentMessage);
  const addStep = usePreviewStore((s) => s.addStep);
  const updateStep = usePreviewStore((s) => s.updateStep);

  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);
  const projectName = useEditorStore((s) => s.projectName);
  const setErrors = useValidationStore((s) => s.setErrors);

  const abortRef = useRef<AbortController | null>(null);
  const currentStepRef = useRef<string | null>(null);

  const client = useMock ? mockClient : realClient;

  const handleEvent = useCallback((event: PreviewEvent) => {
    switch (event.type) {
      case "session-created":
        setSessionId(event.data.sessionId);
        break;
      case "agent-initialized": {
        const stepId = generateStepId();
        addStep({ id: stepId, type: "init", label: "Agent initialized", status: "done", durationMs: event.data.durationMs });
        break;
      }
      case "llm-call-started": {
        const stepId = generateStepId();
        currentStepRef.current = stepId;
        addStep({ id: stepId, type: "llm", label: `LLM call (${event.data.model})`, status: "running" });
        break;
      }
      case "llm-call-completed":
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, {
            status: "done",
            durationMs: event.data.durationMs,
            detail: `${event.data.tokens} tokens`,
          });
          currentStepRef.current = null;
        }
        break;
      case "tool-call-started": {
        const stepId = generateStepId();
        currentStepRef.current = stepId;
        addStep({ id: stepId, type: "tool", label: `Tool: ${event.data.tool}`, status: "running" });
        break;
      }
      case "tool-call-completed":
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, {
            status: "done",
            durationMs: event.data.durationMs,
            detail: event.data.result || undefined,
          });
          currentStepRef.current = null;
        }
        break;
      case "agent-message":
        addMessage({ id: generateMsgId(), role: "agent", content: event.data.content });
        break;
      case "error":
        setError(event.data.message);
        if (currentStepRef.current) {
          updateStep(currentStepRef.current, { status: "error" });
          currentStepRef.current = null;
        }
        break;
      case "stream-complete":
        setStatus("success");
        break;
    }
  }, [setSessionId, addStep, updateStep, addMessage, setError, setStatus]);

  const handleSendMessage = useCallback(async (message: string) => {
    const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };

    addMessage({ id: generateMsgId(), role: "user", content: message });

    if (!sessionId) {
      // First message — start new session
      const errors = validateGraph(graph);
      setErrors(errors);
      if (hasBlockingErrors(errors)) {
        setError("Fix validation errors before running preview.");
        return;
      }

      setStatus("connecting");
      try {
        const abort = await client.startPreview(graph, message, handleEvent);
        abortRef.current = abort;
        setStatus("running");
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    } else {
      // Follow-up message — events arrive on existing SSE stream (real) or stored callback (mock)
      setStatus("running");
      try {
        await client.sendMessage(sessionId, message);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }
  }, [sessionId, nodes, edges, projectName, client, handleEvent, setStatus, setError, addMessage, setErrors]);

  const handleStop = useCallback(async () => {
    abortRef.current?.abort();
    if (sessionId) {
      try { await client.stopPreview(sessionId); } catch { /* ignore */ }
    }
    reset();
  }, [sessionId, client, reset]);

  if (!isOpen) return null;

  const badge = statusBadge[status] || statusBadge.idle;

  return (
    <div className="border-t flex flex-col" style={{ height: 300 }}>
      <div className="flex items-center justify-between px-3 py-1.5 border-b bg-muted/30">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">Live Preview</span>
          <Badge variant={badge.variant}>{badge.label}</Badge>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            className="h-6 text-xs"
            onClick={toggleMock}
          >
            {useMock ? "Mock ✓" : "Mock"}
          </Button>
          {(status === "running" || status === "connecting") && (
            <Button
              variant="outline"
              size="sm"
              className="h-6 text-xs text-destructive"
              onClick={handleStop}
            >
              Stop
            </Button>
          )}
          <Button variant="ghost" size="sm" className="h-6 text-xs" onClick={toggleOpen}>
            ▼
          </Button>
        </div>
      </div>
      <div className="flex flex-1 overflow-hidden">
        <div className="w-56 border-r overflow-y-auto">
          <ExecutionTimeline />
        </div>
        <ChatArea onSendMessage={handleSendMessage} />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/preview/PreviewPanel.tsx
git commit -m "feat(preview): add PreviewPanel container with event dispatch"
```

---

### Task 12: Frontend — Integration into page.tsx and Toolbar

**Files:**
- Modify: `frontend/app/page.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Add "Run Preview" button to Toolbar**

In `frontend/components/panels/Toolbar.tsx`, add import and button:

Add import at top:
```typescript
import { usePreviewStore } from "@/store/previewStore";
```

Add store selector inside the `Toolbar` function:
```typescript
const previewIsOpen = usePreviewStore((s) => s.isOpen);
const togglePreview = usePreviewStore((s) => s.toggleOpen);
const resetPreview = usePreviewStore((s) => s.reset);
```

Add button in the toolbar, after the "Validate" button and before `<SaveLoadDialog />`:

```tsx
<Button
  variant={previewIsOpen ? "default" : "outline"}
  size="sm"
  onClick={() => {
    if (!previewIsOpen) {
      resetPreview();
    }
    togglePreview();
  }}
>
  {previewIsOpen ? "■ Preview" : "▶ Preview"}
</Button>
```

- [ ] **Step 2: Add PreviewPanel to page.tsx**

Add import at top of `frontend/app/page.tsx`:
```typescript
import { PreviewPanel } from "@/components/preview/PreviewPanel";
```

Add `<PreviewPanel />` between the main content div and `<ValidationPanel />`:

```tsx
    <div className="flex flex-col h-screen">
      <Toolbar onNewProject={handleNewProjectRequest} />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
        <PropertiesPanel />
      </div>
      <PreviewPanel />
      <ValidationPanel />
      <NewProjectDialog ... />
    </div>
```

- [ ] **Step 3: Verify full build succeeds**

Run: `cd frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/app/page.tsx frontend/components/panels/Toolbar.tsx
git commit -m "feat(preview): integrate PreviewPanel into page and toolbar"
```

---

### Task 13: Integration test — Mock mode

- [ ] **Step 1: Start frontend dev server**

Run: `cd frontend && npm run dev`

- [ ] **Step 2: Test mock mode**

1. Open `http://localhost:3000` (or whichever port)
2. Create a new project, add an agent node
3. Click "▶ Preview" in toolbar — panel opens at bottom
4. Click "Mock ✓" to enable mock mode
5. Type "Hallo" and press Enter/Send
6. Verify: Timeline shows steps (Agent initialized → LLM call), Chat shows agent response
7. Type a follow-up "Wie ist das Wetter?" — verify tool call appears in timeline

- [ ] **Step 3: Test panel controls**

1. Click "▼" to minimize — panel disappears
2. Click "▶ Preview" to re-open
3. Click "Stop" while running — panel resets
4. Toggle mock on/off — verify state

- [ ] **Step 4: Fix any issues and commit**

```bash
git add -A
git commit -m "fix: adjustments from mock mode integration testing"
```

---

### Task 14: Backend integration test

- [ ] **Step 1: Start full stack**

Run: `./start.sh` (or start backend + frontend separately)

- [ ] **Step 2: Test real preview (requires API key)**

Ensure `OPENAI_API_KEY` is set in the environment. Then:
1. Create a project with an agent (name: "Test Agent", system prompt: "You are helpful", model: OpenAI)
2. Disable mock mode in Preview panel
3. Type a message — verify SSE stream works, timeline updates, agent responds
4. Send follow-up message — verify session persists

- [ ] **Step 3: Test error cases**

1. Try preview without any agent node → should show validation errors
2. Remove API key, try preview → should show "Missing environment variable" error
3. Open 6 sessions → should show "Too many active sessions"

- [ ] **Step 4: Fix any issues and commit**

```bash
git add -A
git commit -m "fix: adjustments from backend integration testing"
```
