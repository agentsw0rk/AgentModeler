# LLM Export Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an LLM-based code review step after Kotlin export that provides structured feedback (summary, findings by severity, per-file comments) displayed in a new "Export Review" tab in the bottom drawer.

**Architecture:** Backend `review` package with pluggable `ReviewProvider` (OpenAI default). `ExportService` exposes `exportWithFiles()`. `ExportReviewController` returns `SseEmitter` streaming export-ready + review-result events. Frontend gets `reviewStore`, SSE client, and `ExportReviewPanel` in a tabbed bottom drawer.

**Tech Stack:** Kotlin/Spring Boot, Spring AI OpenAI, `SseEmitter`, React/Zustand, SSE via `fetch` + `ReadableStream`

---

## File Structure

### Backend — New Files (`src/main/kotlin/com/agentwork/modeler/review/`)

| File | Responsibility |
|------|---------------|
| `ReviewModels.kt` | Data classes: ReviewRequest, ReviewResult, ReviewFinding, ReviewMetadata, enums |
| `ReviewProvider.kt` | Interface for pluggable LLM review |
| `OpenAIReviewProvider.kt` | Default implementation using Spring AI |
| `ReviewService.kt` | Orchestration: token check, graph summary, call provider |
| `ExportReviewRunManager.kt` | Temporary run storage with TTL |
| `ExportReviewController.kt` | SSE endpoints for export+review flow |

### Backend — Modified Files

| File | Change |
|------|--------|
| `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` | Extract `generateFiles()`, add `exportWithFiles()` |

### Frontend — New Files

| File | Responsibility |
|------|---------------|
| `frontend/types/review.ts` | TypeScript types for review |
| `frontend/store/reviewStore.ts` | Review state management |
| `frontend/lib/sse-utils.ts` | Shared SSE parser extracted from preview-client |
| `frontend/lib/review-client.ts` | SSE client for export+review |
| `frontend/components/review/ExportReviewPanel.tsx` | Review results display |

### Frontend — Modified Files

| File | Change |
|------|--------|
| `frontend/lib/preview-client.ts` | Use shared `parseSSEStream` from sse-utils |
| `frontend/store/previewStore.ts` | Add `activeTab` field |
| `frontend/components/preview/PreviewPanel.tsx` | Tab switcher (Preview / Export Review) |
| `frontend/components/dialogs/ExportDialog.tsx` | Add "Export + Review" button |

---

### Task 1: Backend — Review models

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/review/ReviewModels.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/review/ReviewProvider.kt`

- [ ] **Step 1: Create ReviewModels.kt**

```kotlin
package com.agentwork.modeler.review

enum class ReviewStatus { PASS, WARNING, CRITICAL, UNAVAILABLE }
enum class FindingSeverity { CRITICAL, WARNING, SUGGESTION }

data class ReviewRequest(
    val files: Map<String, String>,
    val projectName: String,
    val graphSummary: String
)

data class ReviewResult(
    val status: ReviewStatus,
    val summary: String,
    val findings: List<ReviewFinding>,
    val metadata: ReviewMetadata
)

data class ReviewFinding(
    val severity: FindingSeverity,
    val category: String,
    val file: String?,
    val message: String
)

data class ReviewMetadata(
    val model: String,
    val reviewedFiles: Int,
    val skippedFiles: Int,
    val durationMs: Long
)
```

- [ ] **Step 2: Create ReviewProvider.kt**

```kotlin
package com.agentwork.modeler.review

interface ReviewProvider {
    suspend fun review(request: ReviewRequest): ReviewResult
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/review/
git commit -m "feat(review): add review models and ReviewProvider interface"
```

---

### Task 2: Backend — OpenAIReviewProvider

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/review/OpenAIReviewProvider.kt`

- [ ] **Step 1: Create the provider**

This uses Spring AI's `ChatClient` or the `OpenAiChatModel` bean. Read the existing Spring AI setup — check `build.gradle.kts` for `spring-ai-starter-model-openai` and any existing `@Bean` configuration.

```kotlin
package com.agentwork.modeler.review

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OpenAIReviewProvider(
    chatClientBuilder: ChatClient.Builder,
    @Value("\${review.model:gpt-4o}") private val model: String
) : ReviewProvider {

    private val chatClient = chatClientBuilder.build()
    private val objectMapper = ObjectMapper()

    override suspend fun review(request: ReviewRequest): ReviewResult {
        val systemPrompt = """
            You are an expert Kotlin code reviewer specializing in the Koog AI agent framework.
            Review the provided generated code and return a JSON response with this exact structure:
            {
              "status": "PASS" | "WARNING" | "CRITICAL",
              "summary": "brief overall assessment",
              "findings": [
                {
                  "severity": "CRITICAL" | "WARNING" | "SUGGESTION",
                  "category": "correctness" | "structure" | "dependencies" | "wiring" | "readability" | "koog-specific",
                  "file": "filename or null for cross-file issues",
                  "message": "description of the finding"
                }
              ]
            }

            Set status to CRITICAL if any finding is CRITICAL, WARNING if any is WARNING, PASS if all are SUGGESTION or no findings.
            Be concise. Focus on real issues, not style preferences.
            Return ONLY valid JSON, no markdown fences.
        """.trimIndent()

        val userMessage = buildString {
            appendLine("Project: ${request.projectName}")
            appendLine("Graph: ${request.graphSummary}")
            appendLine()
            appendLine("Generated files:")
            for ((filename, content) in request.files) {
                appendLine("--- $filename ---")
                appendLine(content)
                appendLine()
            }
        }

        val response = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content() ?: throw RuntimeException("Empty LLM response")

        return parseResponse(response, request.files.size)
    }

    private fun parseResponse(response: String, fileCount: Int): ReviewResult {
        return try {
            val json = objectMapper.readTree(response)
            val status = ReviewStatus.valueOf(json["status"]?.asText() ?: "PASS")
            val summary = json["summary"]?.asText() ?: "No summary provided"
            val findings = json["findings"]?.map { node ->
                ReviewFinding(
                    severity = FindingSeverity.valueOf(node["severity"]?.asText() ?: "SUGGESTION"),
                    category = node["category"]?.asText() ?: "general",
                    file = node["file"]?.asText(),
                    message = node["message"]?.asText() ?: ""
                )
            } ?: emptyList()

            ReviewResult(
                status = status,
                summary = summary,
                findings = findings,
                metadata = ReviewMetadata(model = model, reviewedFiles = fileCount, skippedFiles = 0, durationMs = 0)
            )
        } catch (e: Exception) {
            ReviewResult(
                status = ReviewStatus.WARNING,
                summary = "Review completed but response parsing was partial: ${e.message}",
                findings = emptyList(),
                metadata = ReviewMetadata(model = model, reviewedFiles = fileCount, skippedFiles = 0, durationMs = 0)
            )
        }
    }
}
```

**Note:** The exact Spring AI API may differ. The implementer should check what beans are auto-configured by `spring-ai-starter-model-openai` and adapt. The `ChatClient.Builder` is typically auto-configured. If not, use `OpenAiChatModel` directly.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/review/OpenAIReviewProvider.kt
git commit -m "feat(review): add OpenAIReviewProvider with Spring AI"
```

---

### Task 3: Backend — ReviewService + ExportService refactor

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/review/ReviewService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Refactor ExportService**

Read `ExportService.kt`. Extract the file-generation logic (lines 37-106) into an internal method `generateFiles()`. Then add `exportWithFiles()`:

```kotlin
fun export(graph: ProjectGraph): ByteArray {
    val files = generateFiles(graph)
    return zipBuilder.buildZip(files)
}

fun exportWithFiles(graph: ProjectGraph): Pair<ByteArray, Map<String, String>> {
    val files = generateFiles(graph)
    return zipBuilder.buildZip(files) to files
}

internal fun generateFiles(graph: ProjectGraph): Map<String, String> {
    val validationErrors = validator.validate(graph)
    if (validationErrors.isNotEmpty()) {
        throw ExportValidationException(validationErrors)
    }

    val agents = adapter.convert(graph)
    val agent = agents.first()
    val pkg = "src/main/kotlin/generated"
    val files = mutableMapOf<String, String>()

    // ... all the existing file generation code ...

    return files
}
```

- [ ] **Step 2: Create ReviewService**

```kotlin
package com.agentwork.modeler.review

import com.agentwork.modeler.export.model.ProjectGraph
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val reviewProvider: ReviewProvider,
    @Value("\${review.max-chars:100000}") private val maxChars: Int
) {

    private val coreFiles = listOf(
        "AgentConfig.kt", "ToolRegistry.kt", "build.gradle.kts",
        "settings.gradle.kts", "Application.kt", "ChatController.kt"
    )

    suspend fun review(files: Map<String, String>, graph: ProjectGraph): ReviewResult {
        val totalChars = files.values.sumOf { it.length }
        val reviewFiles: Map<String, String>
        val skipped: Int

        if (totalChars > maxChars) {
            reviewFiles = files.filter { (name, _) ->
                coreFiles.any { core -> name.endsWith(core) } ||
                name.contains("/subagents/") ||
                name.contains("/mcp/")
            }
            skipped = files.size - reviewFiles.size
        } else {
            reviewFiles = files
            skipped = 0
        }

        val summary = buildGraphSummary(graph)
        val request = ReviewRequest(
            files = reviewFiles,
            projectName = graph.name,
            graphSummary = summary
        )

        return try {
            val start = System.currentTimeMillis()
            val result = reviewProvider.review(request)
            val duration = System.currentTimeMillis() - start
            result.copy(
                metadata = result.metadata.copy(
                    skippedFiles = skipped,
                    durationMs = duration
                )
            )
        } catch (e: Exception) {
            ReviewResult(
                status = ReviewStatus.UNAVAILABLE,
                summary = "Review unavailable: ${e.message}",
                findings = emptyList(),
                metadata = ReviewMetadata(
                    model = "unknown",
                    reviewedFiles = reviewFiles.size,
                    skippedFiles = skipped,
                    durationMs = 0
                )
            )
        }
    }

    private fun buildGraphSummary(graph: ProjectGraph): String {
        val agents = graph.nodes.count { it.type == "agent" }
        val tools = graph.nodes.count { it.type == "tool" }
        val builtins = graph.nodes.count { it.type == "builtin-tool" }
        val subAgents = graph.nodes.count { it.type == "sub-agent" }
        val mcps = graph.nodes.count { it.type == "mcp-server" }
        val parts = mutableListOf<String>()
        if (agents > 0) parts.add("$agents agent(s)")
        if (tools > 0) parts.add("$tools custom tool(s)")
        if (builtins > 0) parts.add("$builtins builtin tool(s)")
        if (subAgents > 0) parts.add("$subAgents sub-agent(s)")
        if (mcps > 0) parts.add("$mcps MCP server(s)")
        return parts.joinToString(", ")
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/main/kotlin/com/agentwork/modeler/review/ReviewService.kt
git commit -m "feat(review): add ReviewService and refactor ExportService.exportWithFiles"
```

---

### Task 4: Backend — ExportReviewRunManager + Controller

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/review/ExportReviewRunManager.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/review/ExportReviewController.kt`

- [ ] **Step 1: Create ExportReviewRunManager**

```kotlin
package com.agentwork.modeler.review

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExportReviewRun(
    val runId: String,
    val zipBytes: ByteArray,
    val reviewResult: ReviewResult? = null,
    val createdAt: Instant = Instant.now()
)

@Component
class ExportReviewRunManager {

    companion object {
        private const val TTL_MINUTES = 15L
        private const val MAX_RUNS = 10
    }

    private val runs = ConcurrentHashMap<String, ExportReviewRun>()

    fun createRun(zipBytes: ByteArray): String {
        if (runs.size >= MAX_RUNS) {
            throw ReviewException("Too many active export reviews (max $MAX_RUNS)")
        }
        val runId = UUID.randomUUID().toString()
        runs[runId] = ExportReviewRun(runId = runId, zipBytes = zipBytes)
        return runId
    }

    fun getRun(runId: String): ExportReviewRun? = runs[runId]

    fun updateReview(runId: String, result: ReviewResult) {
        runs.computeIfPresent(runId) { _, run -> run.copy(reviewResult = result) }
    }

    @Scheduled(fixedRate = 300_000)
    fun cleanupExpired() {
        val now = Instant.now()
        runs.entries.removeIf { (_, run) ->
            run.createdAt.plusSeconds(TTL_MINUTES * 60).isBefore(now)
        }
    }
}

class ReviewException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: Create ExportReviewController**

```kotlin
package com.agentwork.modeler.review

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.ExportValidationException
import com.agentwork.modeler.export.model.ProjectGraph
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/export/review")
class ExportReviewController(
    private val exportService: ExportService,
    private val reviewService: ReviewService,
    private val runManager: ExportReviewRunManager
) {
    private val objectMapper = ObjectMapper()

    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun exportAndReview(@RequestBody graph: ProjectGraph): SseEmitter {
        val emitter = SseEmitter(0L)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        emitter.onCompletion { scope.cancel() }
        emitter.onTimeout { scope.cancel() }
        emitter.onError { scope.cancel() }

        scope.launch {
            try {
                // Phase 1: Export
                val (zipBytes, files) = exportService.exportWithFiles(graph)
                val runId = runManager.createRun(zipBytes)

                sendEvent(emitter, "export-ready", mapOf(
                    "runId" to runId,
                    "downloadUrl" to "/api/export/review/$runId/download",
                    "reviewJsonUrl" to "/api/export/review/$runId/review.json"
                ))

                // Phase 2: Review
                val result = reviewService.review(files, graph)
                runManager.updateReview(runId, result)

                sendEvent(emitter, "review-result", result)
                emitter.complete()
            } catch (e: ExportValidationException) {
                sendEvent(emitter, "review-failed", mapOf("message" to "Export validation failed: ${e.errors.joinToString("; ")}"))
                emitter.complete()
            } catch (e: Exception) {
                sendEvent(emitter, "review-failed", mapOf("message" to (e.message ?: "Unknown error")))
                emitter.complete()
            }
        }

        return emitter
    }

    @GetMapping("/{runId}/download")
    fun downloadZip(@PathVariable runId: String): ResponseEntity<ByteArray> {
        val run = runManager.getRun(runId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(run.zipBytes)
    }

    @GetMapping("/{runId}/review.json")
    fun downloadReview(@PathVariable runId: String): ResponseEntity<ReviewResult> {
        val run = runManager.getRun(runId)
            ?: return ResponseEntity.notFound().build()
        val result = run.reviewResult
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @ExceptionHandler(ReviewException::class)
    fun handleReviewError(e: ReviewException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(429).body(mapOf("error" to (e.message ?: "Review error")))
    }

    private fun sendEvent(emitter: SseEmitter, type: String, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
            emitter.send(SseEmitter.event().data(json))
        } catch (_: Exception) {
            // Client disconnected
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/review/ExportReviewRunManager.kt src/main/kotlin/com/agentwork/modeler/review/ExportReviewController.kt
git commit -m "feat(review): add ExportReviewController with SSE streaming"
```

---

### Task 5: Frontend — Review types + store

**Files:**
- Create: `frontend/types/review.ts`
- Create: `frontend/store/reviewStore.ts`

- [ ] **Step 1: Create review types**

```typescript
// frontend/types/review.ts
export type ReviewStatus = "pass" | "warning" | "critical" | "unavailable";
export type FindingSeverity = "critical" | "warning" | "suggestion";

export interface ReviewResult {
  status: ReviewStatus;
  summary: string;
  findings: ReviewFinding[];
  metadata: ReviewMetadata;
}

export interface ReviewFinding {
  severity: FindingSeverity;
  category: string;
  file: string | null;
  message: string;
}

export interface ReviewMetadata {
  model: string;
  reviewedFiles: number;
  skippedFiles: number;
  durationMs: number;
}
```

- [ ] **Step 2: Create review store**

```typescript
// frontend/store/reviewStore.ts
import { create } from "zustand";
import { ReviewResult } from "@/types/review";

interface ReviewState {
  status: "idle" | "exporting" | "reviewing" | "done" | "failed";
  result: ReviewResult | null;
  downloadUrl: string | null;
  reviewJsonUrl: string | null;
  error: string | null;

  setExporting: () => void;
  setReviewing: () => void;
  setResult: (result: ReviewResult, downloadUrl: string, reviewJsonUrl: string) => void;
  setFailed: (error: string) => void;
  reset: () => void;
}

export const useReviewStore = create<ReviewState>((set) => ({
  status: "idle",
  result: null,
  downloadUrl: null,
  reviewJsonUrl: null,
  error: null,

  setExporting: () => set({ status: "exporting", error: null }),
  setReviewing: () => set({ status: "reviewing" }),
  setResult: (result, downloadUrl, reviewJsonUrl) =>
    set({ status: "done", result, downloadUrl, reviewJsonUrl }),
  setFailed: (error) => set({ status: "failed", error }),
  reset: () => set({ status: "idle", result: null, downloadUrl: null, reviewJsonUrl: null, error: null }),
}));
```

- [ ] **Step 3: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 4: Commit**

```bash
git add frontend/types/review.ts frontend/store/reviewStore.ts
git commit -m "feat(review): add frontend review types and store"
```

---

### Task 6: Frontend — SSE utils + review client

**Files:**
- Create: `frontend/lib/sse-utils.ts`
- Create: `frontend/lib/review-client.ts`
- Modify: `frontend/lib/preview-client.ts`

- [ ] **Step 1: Extract shared SSE parser**

```typescript
// frontend/lib/sse-utils.ts
export function parseSSEStream<T>(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: T) => void
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
            const parsed = JSON.parse(currentData) as T;
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
```

- [ ] **Step 2: Update preview-client to use shared parser**

In `frontend/lib/preview-client.ts`:
- Add import: `import { parseSSEStream } from "./sse-utils";`
- Remove the local `parseSSEStream` function (lines 51-88)
- The `startPreview` function already calls `parseSSEStream(response.body!, onEvent)` — this will now use the imported version

- [ ] **Step 3: Create review client**

```typescript
// frontend/lib/review-client.ts
import { ProjectGraph } from "@/types/graph";
import { parseSSEStream } from "./sse-utils";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ReviewSSEEvent {
  type: "export-ready" | "review-result" | "review-failed";
  data: Record<string, unknown>;
}

export async function startExportReview(
  graph: ProjectGraph,
  onEvent: (event: ReviewSSEEvent) => void
): Promise<AbortController> {
  const controller = new AbortController();

  const response = await fetch(`${API_BASE}/api/export/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(graph),
    signal: controller.signal,
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || "Failed to start export review");
  }

  parseSSEStream(response.body!, onEvent);
  return controller;
}
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/sse-utils.ts frontend/lib/review-client.ts frontend/lib/preview-client.ts
git commit -m "feat(review): add shared SSE parser and review client"
```

---

### Task 7: Frontend — ExportReviewPanel component

**Files:**
- Create: `frontend/components/review/ExportReviewPanel.tsx`

- [ ] **Step 1: Create the review panel**

```tsx
// frontend/components/review/ExportReviewPanel.tsx
"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useReviewStore } from "@/store/reviewStore";
import { FindingSeverity } from "@/types/review";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const statusConfig: Record<string, { label: string; variant: "default" | "secondary" | "destructive" }> = {
  pass: { label: "PASS", variant: "default" },
  warning: { label: "WARNING", variant: "secondary" },
  critical: { label: "CRITICAL", variant: "destructive" },
  unavailable: { label: "UNAVAILABLE", variant: "secondary" },
};

const severityColors: Record<FindingSeverity, string> = {
  critical: "text-red-500",
  warning: "text-yellow-500",
  suggestion: "text-blue-400",
};

const severityIcons: Record<FindingSeverity, string> = {
  critical: "✗",
  warning: "⚠",
  suggestion: "→",
};

export function ExportReviewPanel() {
  const status = useReviewStore((s) => s.status);
  const result = useReviewStore((s) => s.result);
  const downloadUrl = useReviewStore((s) => s.downloadUrl);
  const reviewJsonUrl = useReviewStore((s) => s.reviewJsonUrl);
  const error = useReviewStore((s) => s.error);
  const [expandedFiles, setExpandedFiles] = useState<Set<string>>(new Set());

  const toggleFile = (file: string) => {
    setExpandedFiles((prev) => {
      const next = new Set(prev);
      if (next.has(file)) next.delete(file);
      else next.add(file);
      return next;
    });
  };

  if (status === "idle") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">Use "Export + Review" to generate and review your agent code</p>
      </div>
    );
  }

  if (status === "exporting") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">Exporting...</p>
      </div>
    );
  }

  if (status === "reviewing") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-muted-foreground text-xs">LLM is reviewing your code...</p>
      </div>
    );
  }

  if (status === "failed") {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-destructive text-xs">{error || "Review failed"}</p>
      </div>
    );
  }

  if (!result) return null;

  const config = statusConfig[result.status] || statusConfig.unavailable;

  // Group findings by severity
  const grouped: Record<FindingSeverity, typeof result.findings> = {
    critical: result.findings.filter((f) => f.severity === "critical"),
    warning: result.findings.filter((f) => f.severity === "warning"),
    suggestion: result.findings.filter((f) => f.severity === "suggestion"),
  };

  // Unique files for expandable sections
  const filesWithFindings = [...new Set(result.findings.filter((f) => f.file).map((f) => f.file!))];

  return (
    <div className="flex-1 overflow-y-auto p-3 space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge variant={config.variant}>{config.label}</Badge>
          <span className="text-sm">{result.summary}</span>
        </div>
        <div className="flex gap-1">
          {downloadUrl && (
            <Button variant="outline" size="sm" className="h-6 text-xs" asChild>
              <a href={`${API_BASE}${downloadUrl}`} download>ZIP</a>
            </Button>
          )}
          {reviewJsonUrl && (
            <Button variant="outline" size="sm" className="h-6 text-xs" asChild>
              <a href={`${API_BASE}${reviewJsonUrl}`} download>JSON</a>
            </Button>
          )}
        </div>
      </div>

      {/* Findings by severity */}
      {(["critical", "warning", "suggestion"] as FindingSeverity[]).map((severity) =>
        grouped[severity].length > 0 ? (
          <div key={severity} className="space-y-1">
            <div className={`text-xs font-semibold uppercase ${severityColors[severity]}`}>
              {severity} ({grouped[severity].length})
            </div>
            {grouped[severity].map((finding, i) => (
              <div key={i} className="flex gap-2 text-xs">
                <span className={severityColors[finding.severity]}>{severityIcons[finding.severity]}</span>
                <div>
                  {finding.file && (
                    <button
                      className="font-mono text-muted-foreground hover:text-foreground mr-1"
                      onClick={() => toggleFile(finding.file!)}
                    >
                      [{finding.file}]
                    </button>
                  )}
                  <span className="text-muted-foreground mr-1">[{finding.category}]</span>
                  <span>{finding.message}</span>
                </div>
              </div>
            ))}
          </div>
        ) : null
      )}

      {result.findings.length === 0 && (
        <p className="text-sm text-green-500">No issues found — code looks good!</p>
      )}

      {/* Metadata footer */}
      <div className="text-[10px] text-muted-foreground pt-2 border-t">
        Model: {result.metadata.model} · {result.metadata.reviewedFiles} files reviewed
        {result.metadata.skippedFiles > 0 && ` · ${result.metadata.skippedFiles} skipped`}
        {" "}· {(result.metadata.durationMs / 1000).toFixed(1)}s
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 3: Commit**

```bash
git add frontend/components/review/ExportReviewPanel.tsx
git commit -m "feat(review): add ExportReviewPanel component"
```

---

### Task 8: Frontend — Tab switcher in PreviewPanel + ExportDialog button

**Files:**
- Modify: `frontend/store/previewStore.ts`
- Modify: `frontend/components/preview/PreviewPanel.tsx`
- Modify: `frontend/components/dialogs/ExportDialog.tsx`

- [ ] **Step 1: Add activeTab to previewStore**

Read `frontend/store/previewStore.ts`. Add to the interface:
```typescript
activeTab: "preview" | "review";
setActiveTab: (tab: "preview" | "review") => void;
```

Add to initial state: `activeTab: "preview"`.
Add action: `setActiveTab: (tab) => set({ activeTab: tab })`.
In `reset()`, also set `activeTab: "preview"`.

- [ ] **Step 2: Add tab switcher to PreviewPanel**

Read `frontend/components/preview/PreviewPanel.tsx`. Add imports:
```typescript
import { ExportReviewPanel } from "@/components/review/ExportReviewPanel";
import { useReviewStore } from "@/store/reviewStore";
```

Add selectors:
```typescript
const activeTab = usePreviewStore((s) => s.activeTab);
const setActiveTab = usePreviewStore((s) => s.setActiveTab);
```

In the header bar, after "Live Preview" title, add tab buttons:
```tsx
<div className="flex items-center gap-2">
  <button
    className={`text-xs px-2 py-0.5 rounded ${activeTab === "preview" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
    onClick={() => setActiveTab("preview")}
  >
    Preview
  </button>
  <button
    className={`text-xs px-2 py-0.5 rounded ${activeTab === "review" ? "bg-muted font-medium" : "text-muted-foreground hover:text-foreground"}`}
    onClick={() => setActiveTab("review")}
  >
    Export Review
  </button>
</div>
```

Replace the existing content area (ExecutionTimeline + ChatArea) with conditional rendering:
```tsx
{activeTab === "preview" ? (
  <div className="flex flex-1 overflow-hidden">
    <div className="w-56 border-r overflow-y-auto">
      <ExecutionTimeline />
    </div>
    <ChatArea onSendMessage={handleSendMessage} />
  </div>
) : (
  <ExportReviewPanel />
)}
```

- [ ] **Step 3: Add "Export + Review" to ExportDialog**

Read `frontend/components/dialogs/ExportDialog.tsx`. Add imports:
```typescript
import { usePreviewStore } from "@/store/previewStore";
import { useReviewStore } from "@/store/reviewStore";
import { startExportReview } from "@/lib/review-client";
import { ReviewResult } from "@/types/review";
```

Add a handler:
```typescript
const handleExportAndReview = async () => {
  const graph: ProjectGraph = { version: 1, name: projectName, nodes, edges };
  const errors = validateGraph(graph);
  setErrors(errors);
  if (hasBlockingErrors(errors)) {
    setStatus("error");
    setErrorMsg("Fix validation errors before exporting.");
    return;
  }

  // Open bottom drawer on review tab
  const previewStore = usePreviewStore.getState();
  if (!previewStore.isOpen) previewStore.toggleOpen();
  previewStore.setActiveTab("review");

  const reviewStore = useReviewStore.getState();
  reviewStore.reset();
  reviewStore.setExporting();

  try {
    await startExportReview(graph, (event) => {
      const store = useReviewStore.getState();
      switch (event.type) {
        case "export-ready":
          store.setReviewing();
          break;
        case "review-result":
          store.setResult(
            event.data as unknown as ReviewResult,
            (event.data as any).downloadUrl || "",
            (event.data as any).reviewJsonUrl || ""
          );
          break;
        case "review-failed":
          store.setFailed((event.data as any).message || "Review failed");
          break;
      }
    });
  } catch (e) {
    useReviewStore.getState().setFailed(e instanceof Error ? e.message : String(e));
  }
};
```

Wait — the `export-ready` event's data has `downloadUrl` and `reviewJsonUrl`, but the `review-result` event's data is the `ReviewResult` object. Let me fix the handler:

```typescript
// In the onEvent callback:
switch (event.type) {
  case "export-ready": {
    const d = event.data as { downloadUrl: string; reviewJsonUrl: string };
    // Store URLs temporarily, move to reviewing state
    reviewStore.setReviewing();
    // Save URLs for later - we need them when result arrives
    (reviewStore as any)._pendingUrls = { downloadUrl: d.downloadUrl, reviewJsonUrl: d.reviewJsonUrl };
    break;
  }
  case "review-result": {
    const urls = (reviewStore as any)._pendingUrls || { downloadUrl: "", reviewJsonUrl: "" };
    reviewStore.setResult(event.data as unknown as ReviewResult, urls.downloadUrl, urls.reviewJsonUrl);
    break;
  }
  case "review-failed":
    reviewStore.setFailed((event.data as any).message || "Review failed");
    break;
}
```

Actually, cleaner approach — add `pendingDownloadUrl` and `pendingReviewJsonUrl` to the review store. But simplest: store the URLs when `export-ready` arrives by extending `setReviewing` to accept URLs:

Update `reviewStore.ts`: change `setReviewing` to `setReviewing: (downloadUrl: string, reviewJsonUrl: string) => void` and store the URLs at that point.

Then the handler becomes:
```typescript
case "export-ready": {
  const d = event.data as { downloadUrl: string; reviewJsonUrl: string };
  useReviewStore.getState().setReviewing(d.downloadUrl, d.reviewJsonUrl);
  break;
}
case "review-result":
  useReviewStore.getState().setResult(event.data as unknown as ReviewResult);
  break;
```

And `setResult` just sets `result` and `status: "done"` without needing URLs (they're already set).

**Update reviewStore accordingly:**
```typescript
setReviewing: (downloadUrl: string, reviewJsonUrl: string) =>
  set({ status: "reviewing", downloadUrl, reviewJsonUrl }),
setResult: (result: ReviewResult) =>
  set({ status: "done", result }),
```

Add an "Export + Review" button in the ExportDialog JSX, next to the existing Export button:
```tsx
<div className="flex gap-2">
  <Button onClick={handleExport} disabled={status === "exporting"}>
    {status === "exporting" ? "Exporting..." : "Export"}
  </Button>
  <Button variant="outline" onClick={handleExportAndReview} disabled={status === "exporting"}>
    Export + Review
  </Button>
</div>
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npx next build`

- [ ] **Step 5: Commit**

```bash
git add frontend/store/previewStore.ts frontend/store/reviewStore.ts frontend/components/preview/PreviewPanel.tsx frontend/components/dialogs/ExportDialog.tsx
git commit -m "feat(review): add tab switcher, Export+Review button, and event wiring"
```

---

### Task 9: Manual integration test

- [ ] **Step 1: Start full stack**

Backend: `./gradlew bootRun`
Frontend: `cd frontend && npm run dev`
Ensure `OPENAI_API_KEY` is set.

- [ ] **Step 2: Test export + review flow**

1. Create an agent, add tools, configure
2. Click Export → "Export + Review" button
3. Bottom drawer opens on "Export Review" tab
4. Status shows "Exporting..." then "Reviewing..."
5. Review result appears with badge, summary, findings
6. "Download ZIP" and "Download JSON" buttons work

- [ ] **Step 3: Test fallback**

1. Remove/invalidate `OPENAI_API_KEY`
2. Try "Export + Review" → should show "UNAVAILABLE" badge with error message

- [ ] **Step 4: Test tab switching**

1. Switch between "Preview" and "Export Review" tabs
2. Both retain their state independently

- [ ] **Step 5: Commit fixes if any**

```bash
git add -A
git commit -m "fix: adjustments from export review integration testing"
```
