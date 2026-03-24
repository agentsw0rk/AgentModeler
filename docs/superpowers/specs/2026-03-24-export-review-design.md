# LLM Export Review — Design Spec

## Goal

After generating the Kotlin export, an LLM reviews the complete generated code and returns structured feedback (summary, findings by severity, per-file comments). The review runs automatically as part of a combined export+review flow.

## Architecture Decisions

- **Review runs server-side** — LLM keys stay in backend, review can be persisted
- **Pluggable provider** — `ReviewProvider` interface, OpenAI GPT-4o as default (via existing Spring AI dependency)
- **Single LLM call** — all files in one prompt, with token-limit fallback to core files only
- **SSE streaming** — events use JSON `type` field in `data:` payload (matching preview-client pattern)
- **Review is read-only** — LLM comments on code, does not modify it
- **Tab in bottom drawer** — reuses PreviewPanel area with tab switcher
- **OpenAI config:** Uses `spring.ai.openai.api-key` (already configured for preview). Default model: `gpt-4o`.

## Backend

### New Package: `com.agentwork.modeler.review`

**ReviewProvider.kt** — Pluggable interface:
```kotlin
interface ReviewProvider {
    suspend fun review(request: ReviewRequest): ReviewResult
}
```

**OpenAIReviewProvider.kt** — Default implementation using Spring AI OpenAI:
- System prompt instructs LLM as a Kotlin/Koog code reviewer
- All files sent as user message in structured format (filename + content)
- Expects JSON response matching `ReviewResult` structure
- Parses LLM response, handles malformed JSON gracefully
- Uses `gpt-4o` model (configurable via `application.properties`)

**ReviewRequest.kt:**
```kotlin
data class ReviewRequest(
    val files: Map<String, String>,     // filename → content
    val projectName: String,
    val graphSummary: String            // e.g. "1 agent (OpenAI/GPT-4o), 2 tools, 1 MCP server (SSE)"
)
```

**`graphSummary` format:** Built by `ReviewService` from the `ProjectGraph`: counts agents, tools, builtin tools, sub-agents, MCP servers, and lists their providers/types. Example: `"1 agent (OpenAI/GPT-4o), 2 custom tools, 1 builtin tool (shell), 1 sub-agent, 1 MCP server (SSE)"`

**ReviewResult.kt:**
```kotlin
enum class ReviewStatus { PASS, WARNING, CRITICAL, UNAVAILABLE }
enum class FindingSeverity { CRITICAL, WARNING, SUGGESTION }

data class ReviewResult(
    val status: ReviewStatus,
    val summary: String,
    val findings: List<ReviewFinding>,
    val metadata: ReviewMetadata
)

data class ReviewFinding(
    val severity: FindingSeverity,
    val category: String,               // "correctness", "structure", "dependencies", "wiring", "readability", "koog-specific"
    val file: String?,                  // null for cross-file issues
    val message: String
)

data class ReviewMetadata(
    val model: String,
    val reviewedFiles: Int,
    val skippedFiles: Int,
    val durationMs: Long
)
```

**ReviewService.kt** — Orchestration:
1. Receives `Map<String, String>` (generated files) + `ProjectGraph` from export
2. Token limit check: configurable char threshold (default 100,000, via `review.max-chars` property). If exceeded, filter to priority-ordered core files: AgentConfig.kt, ToolRegistry.kt, sub-agent files, MCP files, build.gradle.kts, settings.gradle.kts, Application.kt, ChatController.kt
3. Build `graphSummary` from ProjectGraph (count nodes by type, list providers)
4. Build `ReviewRequest` with files + project name + graph summary
5. Call `reviewProvider.review(request)`
6. On failure: return `ReviewResult(status = UNAVAILABLE, summary = "Review unavailable: {error}", findings = emptyList())`
7. Embed `review.json` into the ZIP

**ExportReviewRunManager.kt** — Temporary storage:
- `ConcurrentHashMap<String, ExportReviewRun>` with TTL (15 min)
- `ExportReviewRun`: runId, zipBytes, reviewResult, createdAt
- Max 10 concurrent runs (returns 429 if exceeded)
- `@Scheduled` cleanup every 5 minutes

### API Endpoints

**Existing `POST /api/export`** — unchanged (ZIP-only).

**New `POST /api/export/review`** — Body: `ProjectGraph`. Returns SSE stream (`text/event-stream`). Events use `data:` JSON payload with `type` field (matching preview-client pattern, NOT named SSE events):
- `data: { "type": "export-ready", "data": { "runId": "...", "downloadUrl": "...", "reviewJsonUrl": "..." } }`
- `data: { "type": "review-result", "data": { ...ReviewResult... } }`
- `data: { "type": "review-failed", "data": { "message": "..." } }`

**New `GET /api/export/review/{runId}/download`** — Downloads the ZIP file.

**New `GET /api/export/review/{runId}/review.json`** — Downloads the review result as JSON.

**ExportReviewController.kt** — New controller. Returns `SseEmitter` from non-suspend endpoint. Internally launches `CoroutineScope(Dispatchers.IO + SupervisorJob())` to call export + review asynchronously and sends SSE events at each stage (same pattern as `PreviewService.startSession()`).

### Integration with ExportService

`ExportService.export()` currently builds a `files: Map<String, String>` internally then passes it to `ZipBuilder`. Refactor:
- Extract file-map generation into `generateFiles(graph: ProjectGraph): Map<String, String>` (private → internal)
- Existing `export()` calls `generateFiles()` then `zipBuilder.buildZip(files)` — no behavior change
- New `exportWithFiles(graph: ProjectGraph): Pair<ByteArray, Map<String, String>>` calls `generateFiles()`, builds ZIP, returns both

## Frontend

### Types (`frontend/types/review.ts`)

```typescript
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

### Store (`frontend/store/reviewStore.ts`)

```typescript
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
```

### SSE Utilities (`frontend/lib/sse-utils.ts`)

Extract the `parseSSEStream` function from `preview-client.ts` into a shared utility. Both `preview-client.ts` and `review-client.ts` import from here. The parser handles `data:` lines and dispatches parsed JSON objects via callback.

### Client (`frontend/lib/review-client.ts`)

- `startExportReview(graph, onEvent)` — POST to `/api/export/review`, parse SSE via shared `parseSSEStream`, dispatch events
- Returns `AbortController` for cancellation

### Components

**Tab switcher in PreviewPanel header:**
- Two tabs: "Preview" | "Export Review"
- Active tab switches between chat/timeline view and review view
- Tab state stored in `previewStore` (new `activeTab: "preview" | "review"` field). This is acceptable coupling since PreviewPanel owns the drawer.

**`frontend/components/review/ExportReviewPanel.tsx`:**
- Status badge: PASS (green) / WARNING (yellow) / CRITICAL (red) / UNAVAILABLE (gray)
- Summary text block
- Findings list grouped by severity (critical first, then warnings, then suggestions)
- Each finding: severity badge + category tag + message
- Per-file findings are expandable (click file name to show/hide)
- Metadata footer: model name, files reviewed, skipped count, duration
- "Download ZIP" button (uses `downloadUrl` from store)
- "Download Review JSON" button (uses `reviewJsonUrl` from store)

**ExportDialog change:**
- Add "Export + Review" button alongside existing "Export" button
- Clicking "Export + Review" closes the dialog, opens the bottom drawer on the "Export Review" tab, and starts the SSE flow

### Integration

- PreviewPanel gets `activeTab` state and renders either Preview content or ExportReviewPanel
- ExportDialog gets a second button
- reviewStore is independent from previewStore (separate concern, only tab state is shared)

## Fallback Behavior

- LLM not reachable: SSE sends `review-failed` event, UI shows "Review unavailable" with gray badge
- Malformed LLM response: ReviewService parses what it can, returns partial result with WARNING status
- Export fails: SSE sends error event, no review attempted
- Token limit exceeded: priority-ordered core files only, `metadata.skippedFiles` reports count

## What Does NOT Change

- `POST /api/export` — unchanged, still returns ZIP directly
- ExportService core logic — only refactored to expose `generateFiles()`, behavior unchanged
- PreviewService/PreviewController — unchanged
- History/clipboard — unchanged

## Edge Cases

- Empty graph: export validation catches this before review runs
- Very small export (1-2 files): review runs normally, no skipping
- LLM returns non-JSON: catch, return UNAVAILABLE result
- Run TTL expired: download endpoint returns 404
- Multiple concurrent export+reviews: each gets own runId, max 10 concurrent
