# LLM Export Review — Design Spec

## Goal

After generating the Kotlin export, an LLM reviews the complete generated code and returns structured feedback (summary, findings by severity, per-file comments). The review runs automatically as part of a combined export+review flow.

## Architecture Decisions

- **Review runs server-side** — LLM keys stay in backend, review can be persisted
- **Pluggable provider** — `ReviewProvider` interface, OpenAI as default implementation
- **Single LLM call** — all files in one prompt, with token-limit fallback to core files only
- **SSE streaming** — export-ready event first, then review result
- **Review is read-only** — LLM comments on code, does not modify it
- **Tab in bottom drawer** — reuses PreviewPanel area with tab switcher

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

**ReviewRequest.kt:**
```kotlin
data class ReviewRequest(
    val files: Map<String, String>,     // filename → content
    val projectName: String,
    val graphSummary: String            // brief description of agent topology
)
```

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
2. Token limit check: estimate char count. If > 100,000 chars, filter to core files only (AgentConfig.kt, ToolRegistry.kt, build.gradle.kts, settings.gradle.kts, Application.kt, ChatController.kt)
3. Build `ReviewRequest` with files + project name + graph summary (agent count, tool count, etc.)
4. Call `reviewProvider.review(request)`
5. On failure: return `ReviewResult(status = UNAVAILABLE, summary = "Review unavailable: {error}", findings = emptyList())`
6. Optionally embed `review.json` into the ZIP

**ExportReviewRunManager.kt** — Temporary storage:
- `ConcurrentHashMap<String, ExportReviewRun>` with TTL (15 min)
- `ExportReviewRun`: runId, zipBytes, reviewResult, createdAt
- `@Scheduled` cleanup every 5 minutes

### API Endpoints

**Existing `POST /api/export`** — unchanged (ZIP-only).

**New `POST /api/export/review`** — Body: `ProjectGraph`. Returns SSE stream:
- `event: export-ready` — `{ runId, downloadUrl: "/api/export/review/{runId}/download" }`
- `event: review-result` — full `ReviewResult` as JSON
- `event: review-failed` — `{ status: "unavailable", message: "..." }`

**New `GET /api/export/review/{runId}/download`** — Downloads the ZIP file.

**New `GET /api/export/review/{runId}/review.json`** — Downloads the review result as JSON.

**ExportReviewController.kt** — New controller handling the above endpoints. Uses `SseEmitter` pattern (same as PreviewController).

### Integration with ExportService

`ExportService.export()` currently returns `ByteArray` (ZIP). Extend with a new method:
- `exportWithFiles(graph: ProjectGraph): Pair<ByteArray, Map<String, String>>` — returns both the ZIP and the raw file map for review input.

This avoids duplicating the export logic. The review controller calls `exportWithFiles()`, stores the ZIP, passes files to `ReviewService`, streams results.

## Frontend

### Types (`frontend/types/review.ts`)

```typescript
type ReviewStatus = "pass" | "warning" | "critical" | "unavailable";
type FindingSeverity = "critical" | "warning" | "suggestion";

interface ReviewResult {
  status: ReviewStatus;
  summary: string;
  findings: ReviewFinding[];
  metadata: ReviewMetadata;
}

interface ReviewFinding {
  severity: FindingSeverity;
  category: string;
  file: string | null;
  message: string;
}

interface ReviewMetadata {
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
  error: string | null;
  // actions
  setExporting: () => void;
  setReviewing: () => void;
  setResult: (result: ReviewResult, downloadUrl: string) => void;
  setFailed: (error: string) => void;
  reset: () => void;
}
```

### Client (`frontend/lib/review-client.ts`)

- `startExportReview(graph, onEvent)` — POST to `/api/export/review`, parse SSE stream, dispatch events to store
- Same `fetch()` + `ReadableStream` pattern as `preview-client.ts`

### Components

**Tab switcher in PreviewPanel header:**
- Two tabs: "Preview" | "Export Review"
- Active tab switches between chat/timeline view and review view
- Tab state stored in `previewStore` (new `activeTab: "preview" | "review"` field)

**`frontend/components/review/ExportReviewPanel.tsx`:**
- Status badge: PASS (green) / WARNING (yellow) / CRITICAL (red) / UNAVAILABLE (gray)
- Summary text block
- Findings list grouped by severity (critical first, then warnings, then suggestions)
- Each finding: severity badge + category tag + message
- Per-file findings are expandable (click file name to show/hide)
- Metadata footer: model name, files reviewed, skipped count, duration
- "Download ZIP" button (links to downloadUrl)
- "Download Review JSON" button (links to `{downloadUrl}/../review.json`)

**ExportDialog change:**
- Add "Export + Review" button alongside existing "Export" button
- Clicking "Export + Review" closes the dialog, opens the bottom drawer on the "Export Review" tab, and starts the SSE flow

### Integration

- PreviewPanel gets `activeTab` state and renders either Preview content or ExportReviewPanel
- ExportDialog gets a second button
- reviewStore is independent from previewStore (separate concern)

## Fallback Behavior

- LLM not reachable: SSE sends `review-failed` event, UI shows "Review unavailable" with gray badge
- Malformed LLM response: ReviewService parses what it can, returns partial result with WARNING status
- Export fails: SSE sends error event, no review attempted
- Token limit exceeded: core files only are reviewed, `metadata.skippedFiles` reports count

## What Does NOT Change

- `POST /api/export` — unchanged, still returns ZIP directly
- ExportService core logic — only extended with `exportWithFiles()` method
- PreviewService/PreviewController — unchanged
- History/clipboard — unchanged

## Edge Cases

- Empty graph: export validation catches this before review runs
- Very small export (1-2 files): review runs normally, no skipping
- LLM returns non-JSON: catch, return UNAVAILABLE result
- Run TTL expired: download endpoint returns 404
- Multiple concurrent export+reviews: each gets own runId
