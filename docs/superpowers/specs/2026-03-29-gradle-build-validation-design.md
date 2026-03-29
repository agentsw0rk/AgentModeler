# Gradle Build Validation

**Date:** 2026-03-29
**Feature:** 07 — Gradle Build Validation
**Status:** Design approved

## Problem

The modeler generates Kotlin code but provides no feedback whether the code actually compiles. The existing local validation catches structural issues, and the LLM review catches quality issues, but neither catches real compile errors.

## Solution

Build the generated sources in a Docker container using Gradle. Parse compile errors and display them in the UI, mapped to the originating nodes.

## Architecture

```
UI (Validate Dropdown) → POST /api/build → BuildService
  → ExportService.generateFiles() → temp dir
  → docker run --rm -v /tmp/build:/project modeler-builder gradle compileKotlin
  → capture stdout/stderr → GradleErrorParser → map errors to nodes
  → BuildResult { success, errors[], log }
```

## Dockerfile (Builder Container)

New file: `Dockerfile.builder` in project root.

```dockerfile
FROM gradle:8.10-jdk21
WORKDIR /project
ENTRYPOINT ["gradle", "--no-daemon"]
```

- User builds: `docker build -f Dockerfile.builder -t modeler-builder .`
- JVM build: `docker run --rm -v /tmp/build:/project modeler-builder compileKotlin`
- Native build: `docker run --rm -v /tmp/build:/project modeler-builder compileKotlinNative`
- Sources mounted as volume, no COPY needed
- Container is ephemeral (--rm)

## Backend

### Models

```kotlin
data class BuildError(
    val file: String,       // e.g. "src/main/kotlin/generated/Agent.kt"
    val line: Int,          // line number
    val column: Int,        // column number
    val message: String,    // error message
    val severity: String,   // "error" or "warning"
    val nodeId: String?     // mapped node ID (nullable if unmappable)
)

data class BuildResult(
    val success: Boolean,
    val errors: List<BuildError>,
    val log: String         // raw gradle output
)

data class BuildRequest(
    val graph: WorkspaceGraph,
    val platform: String    // "jvm" or "native"
)
```

### GradleErrorParser

Parses Kotlin compiler error format from Gradle output:
```
e: file:///project/src/main/kotlin/generated/Foo.kt:15:10: Unresolved reference: bar
w: file:///project/src/main/kotlin/generated/Foo.kt:20:5: Parameter is never used
```

Regex pattern: `(e|w): file:///project/(.+):(\d+):(\d+): (.+)`

Returns `List<BuildError>` with file, line, column, message, severity.

### BuildService

1. Call `ExportService.generateFilesWithMapping(graph)` → returns `Pair<Map<String, String>, Map<String, String>>` (files map + fileToNodeId map)
2. Write files to temp directory
3. Start Docker container via `ProcessBuilder`:
   ```
   docker run --rm -v {tempDir}:/project modeler-builder {gradleTask}
   ```
   - JVM: `compileKotlin`
   - Native: `compileKotlinNative`
4. Capture stdout + stderr (with timeout, e.g. 120 seconds)
5. Parse errors via `GradleErrorParser`
6. Map each error's file path to a nodeId using the fileToNodeId map
7. Clean up temp directory
8. Return `BuildResult`

### ExportService Extension

Add method `generateFilesWithMapping(graph)` that returns both the files map and a mapping of file paths to node IDs. The existing generators already know which node they're generating for — this just needs to be tracked.

`Map<String, String>` — key: file path (e.g. `src/main/kotlin/generated/MoneyTransferTools.kt`), value: node ID (e.g. `node-2`)

### BuildController

```kotlin
@RestController
class BuildController(private val buildService: BuildService) {
    @PostMapping("/api/build")
    fun build(@RequestBody request: BuildRequest): BuildResult {
        return buildService.build(request.graph, request.platform)
    }
}
```

## Frontend

### API Client

New file: `frontend/lib/build-client.ts`

```typescript
export async function buildProject(graph: WorkspaceGraph, platform: "jvm" | "native"): Promise<BuildResult> {
  const res = await fetch(`${API_BASE}/api/build`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ graph, platform }),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}
```

### Validate Button Restructure

The existing "Validate" in `ToolsMenuDialog.tsx` becomes a dropdown with 3 options:

| Option | Action |
|--------|--------|
| Validate (Local) | Existing `validateGraph()` — runs in browser, instant |
| Build (JVM) | Calls `POST /api/build` with `platform: "jvm"` |
| Build (Native) | Calls `POST /api/build` with `platform: "native"` |

Build options show a loading spinner while the Docker build runs. On completion, errors are displayed in the existing ValidationPanel (or a new BuildErrorPanel if the format differs significantly).

### Build Error Display

Reuse the existing `ValidationPanel` pattern. Build errors are converted to `ValidationError[]` format:
- `nodeId`: from the BuildError's mapped nodeId
- `severity`: "error" or "warning"
- `message`: `[file:line] message`

Nodes with build errors get a red border indicator (same as existing validation errors).

### Build Error Store

New Zustand store or extend existing validation store with a `buildErrors` array that gets populated from the BuildResult. The UI subscribes to this for rendering.

## Error → Node Mapping

The ExportService tracks which generator produces which file for which node. The mapping is:

| File Pattern | Node Type |
|-------------|-----------|
| `src/main/kotlin/generated/{ToolName}Tools.kt` | tool node |
| `src/main/kotlin/generated/{AgentName}Agent.kt` | agent node |
| `src/main/kotlin/generated/{Name}.kt` (shared code) | code node |
| `src/main/kotlin/generated/{GraphAgent}Strategy.kt` | graph-agent node |
| `build.gradle.kts` | no specific node |
| `src/main/kotlin/generated/Application.kt` | no specific node |

Files without a clear node mapping get `nodeId: null` — these errors show without node highlighting.

## Files Changed

### New Files

| File | Description |
|------|-------------|
| `Dockerfile.builder` | Builder container with Gradle + JDK 21 |
| `src/.../build/BuildService.kt` | Orchestrates Docker build and error parsing |
| `src/.../build/BuildController.kt` | REST endpoint POST /api/build |
| `src/.../build/BuildResult.kt` | BuildResult, BuildError, BuildRequest models |
| `src/.../build/GradleErrorParser.kt` | Parses Kotlin compiler errors from Gradle output |
| `frontend/lib/build-client.ts` | API client for build endpoint |

### Modified Files

| File | Change |
|------|--------|
| `src/.../export/ExportService.kt` | Add `generateFilesWithMapping()` returning file→nodeId map |
| `frontend/components/dialogs/ToolsMenuDialog.tsx` | Validate button → dropdown with Local/JVM/Native options |
| `frontend/components/validation/ValidationPanel.tsx` | Display build errors alongside validation errors |

## Acceptance Criteria

- [ ] Dockerfile.builder builds an image with JDK 21 + Gradle
- [ ] POST /api/build accepts WorkspaceGraph + platform, returns BuildResult
- [ ] Gradle build runs in Docker container with generated sources
- [ ] Compile errors parsed with file, line, column, message, severity
- [ ] Errors mapped to node IDs via file→node mapping
- [ ] Validate button offers Local / Build (JVM) / Build (Native) options
- [ ] Build errors displayed in UI with node highlighting
- [ ] Build timeout after 120 seconds with appropriate error message
- [ ] Temp directories cleaned up after build
- [ ] Works for JVM and Native platforms
