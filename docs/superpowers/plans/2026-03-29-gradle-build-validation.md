# Gradle Build Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build generated Kotlin sources in a Docker container with Gradle, parse compile errors, and display them in the UI mapped to originating nodes.

**Architecture:** Backend: new BuildService orchestrates ExportService file generation → writes to temp dir → runs Docker container → parses Gradle output → maps errors to nodes. Frontend: Validate button becomes dropdown (Local/JVM/Native), build errors feed into existing ValidationPanel via validationStore.

**Tech Stack:** Spring Boot 4, Docker (ProcessBuilder), Kotlin, Next.js, Zustand

---

### Task 1: Create Dockerfile.builder

**Files:**
- Create: `Dockerfile.builder`

- [ ] **Step 1: Create the builder Dockerfile**

Create `Dockerfile.builder` in project root:

```dockerfile
FROM gradle:8.10-jdk21
WORKDIR /project
ENTRYPOINT ["gradle", "--no-daemon"]
```

- [ ] **Step 2: Verify it builds**

```bash
docker build -f Dockerfile.builder -t modeler-builder .
```

Expected: Image builds successfully.

- [ ] **Step 3: Commit**

```bash
git add Dockerfile.builder
git commit -m "feat: add Dockerfile.builder for Gradle build validation"
```

---

### Task 2: Create backend models

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt`

- [ ] **Step 1: Create the data models**

Create `src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt`:

```kotlin
package com.agentwork.modeler.build

import com.agentwork.modeler.export.model.WorkspaceGraph

data class BuildRequest(
    val graph: WorkspaceGraph,
    val platform: String = "jvm"
)

data class BuildResult(
    val success: Boolean,
    val errors: List<BuildError>,
    val log: String
)

data class BuildError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String,
    val nodeId: String?
)
```

- [ ] **Step 2: Verify build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt
git commit -m "feat: add BuildRequest, BuildResult, BuildError models"
```

---

### Task 3: Create GradleErrorParser

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/build/GradleErrorParser.kt`

- [ ] **Step 1: Create the parser**

Create `src/main/kotlin/com/agentwork/modeler/build/GradleErrorParser.kt`:

```kotlin
package com.agentwork.modeler.build

import org.springframework.stereotype.Component

@Component
class GradleErrorParser {

    private val errorPattern = Regex("""^(e|w): file:///project/(.+):(\d+):(\d+): (.+)$""", RegexOption.MULTILINE)

    fun parse(output: String, fileToNodeMap: Map<String, String>): List<BuildError> {
        return errorPattern.findAll(output).map { match ->
            val severity = if (match.groupValues[1] == "e") "error" else "warning"
            val file = match.groupValues[2]
            val line = match.groupValues[3].toIntOrNull() ?: 0
            val column = match.groupValues[4].toIntOrNull() ?: 0
            val message = match.groupValues[5]
            val nodeId = fileToNodeMap[file]

            BuildError(
                file = file,
                line = line,
                column = column,
                message = message,
                severity = severity,
                nodeId = nodeId
            )
        }.toList()
    }
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/build/GradleErrorParser.kt
git commit -m "feat: add GradleErrorParser for Kotlin compiler output"
```

---

### Task 4: Extend ExportService with file-to-node mapping

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add generateFilesWithMapping method**

In `ExportService.kt`, read the file first. After the existing `generateFiles` method, add a new public method that wraps it and adds node ID tracking.

The approach: since generators don't track node IDs, we build the mapping externally by matching generated file paths to node data. Add this method after `generateFiles`:

```kotlin
    fun generateFilesWithMapping(graph: WorkspaceGraph): Pair<Map<String, String>, Map<String, String>> {
        val files = generateFiles(graph)
        val fileToNodeId = mutableMapOf<String, String>()

        // Map tool files to tool nodes
        for (node in graph.nodes) {
            when (node.type) {
                "tool" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    fileToNodeId["src/main/kotlin/generated/${name}Tools.kt"] = node.id
                }
                "code" -> {
                    val fileName = node.data["fileName"] as? String ?: continue
                    fileToNodeId["src/main/kotlin/generated/${fileName}.kt"] = node.id
                }
                "agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    fileToNodeId["src/main/kotlin/generated/${name}Agent.kt"] = node.id
                }
                "graph-agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    fileToNodeId["src/main/kotlin/generated/${name}Strategy.kt"] = node.id
                    fileToNodeId["src/main/kotlin/generated/${name}Agent.kt"] = node.id
                }
                "sub-agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    fileToNodeId["src/main/kotlin/generated/${name}SubAgent.kt"] = node.id
                }
                "mcp-server" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    fileToNodeId["src/main/kotlin/generated/${name}McpConfig.kt"] = node.id
                }
            }
        }

        return Pair(files, fileToNodeId)
    }
```

- [ ] **Step 2: Verify build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat: add generateFilesWithMapping for file-to-node ID tracking"
```

---

### Task 5: Create BuildService

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/build/BuildService.kt`

- [ ] **Step 1: Create the service**

Create `src/main/kotlin/com/agentwork/modeler/build/BuildService.kt`:

```kotlin
package com.agentwork.modeler.build

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class BuildService(
    private val exportService: ExportService,
    private val errorParser: GradleErrorParser
) {

    fun build(graph: WorkspaceGraph, platform: String): BuildResult {
        val (files, fileToNodeMap) = exportService.generateFilesWithMapping(graph)

        val tempDir = Files.createTempDirectory("modeler-build-").toFile()
        try {
            // Write all generated files to temp directory
            for ((path, content) in files) {
                val file = File(tempDir, path)
                file.parentFile.mkdirs()
                file.writeText(content)
            }

            // Determine gradle task
            val gradleTask = when (platform) {
                "native" -> "compileKotlinNative"
                else -> "compileKotlin"
            }

            // Run docker container
            val process = ProcessBuilder(
                "docker", "run", "--rm",
                "-v", "${tempDir.absolutePath}:/project",
                "modeler-builder",
                gradleTask
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(120, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return BuildResult(
                    success = false,
                    errors = listOf(BuildError("", 0, 0, "Build timed out after 120 seconds", "error", null)),
                    log = output
                )
            }

            val exitCode = process.exitValue()
            val errors = errorParser.parse(output, fileToNodeMap)

            return BuildResult(
                success = exitCode == 0 && errors.none { it.severity == "error" },
                errors = errors,
                log = output
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew compileKotlin 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/build/BuildService.kt
git commit -m "feat: add BuildService orchestrating Docker-based Gradle builds"
```

---

### Task 6: Create BuildController

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/build/BuildController.kt`

- [ ] **Step 1: Create the controller**

Create `src/main/kotlin/com/agentwork/modeler/build/BuildController.kt`:

```kotlin
package com.agentwork.modeler.build

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BuildController(private val buildService: BuildService) {

    @PostMapping("/api/build")
    fun build(@RequestBody request: BuildRequest): BuildResult {
        return buildService.build(request.graph, request.platform)
    }

    @ExceptionHandler(Exception::class)
    fun handleError(e: Exception): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(mapOf(
            "error" to "Build failed",
            "details" to (e.message ?: "Unknown error")
        ))
    }
}
```

- [ ] **Step 2: Verify build and run tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/build/BuildController.kt
git commit -m "feat: add BuildController with POST /api/build endpoint"
```

---

### Task 7: Create frontend build client

**Files:**
- Create: `frontend/lib/build-client.ts`

- [ ] **Step 1: Create the API client**

Create `frontend/lib/build-client.ts`:

```typescript
import type { WorkspaceGraph } from "@/types/graph";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface BuildError {
  file: string;
  line: number;
  column: number;
  message: string;
  severity: string;
  nodeId: string | null;
}

export interface BuildResult {
  success: boolean;
  errors: BuildError[];
  log: string;
}

export async function buildProject(graph: WorkspaceGraph, platform: "jvm" | "native"): Promise<BuildResult> {
  const response = await fetch(`${API_BASE}/api/build`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ graph, platform }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ details: "Build request failed" }));
    throw new Error(error.details || "Build request failed");
  }

  return response.json();
}
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/build-client.ts
git commit -m "feat: add build-client.ts for POST /api/build API calls"
```

---

### Task 8: Update validationStore for build errors

**Files:**
- Modify: `frontend/store/validationStore.ts`

- [ ] **Step 1: Add build errors to the store**

Read `frontend/store/validationStore.ts` first. Add `buildErrors` state and `setBuildErrors` / `clearBuildErrors` actions alongside the existing validation state.

Add to the interface:
```typescript
  buildErrors: ValidationError[];
  buildLoading: boolean;
  setBuildErrors: (errors: ValidationError[]) => void;
  clearBuildErrors: () => void;
  setBuildLoading: (loading: boolean) => void;
```

Add to the store implementation:
```typescript
  buildErrors: [],
  buildLoading: false,
  setBuildErrors: (errors) => set({ buildErrors: errors }),
  clearBuildErrors: () => set({ buildErrors: [] }),
  setBuildLoading: (loading) => set({ buildLoading: loading }),
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/store/validationStore.ts
git commit -m "feat: add buildErrors state to validationStore"
```

---

### Task 9: Update ToolsMenuDialog — Validate dropdown

**Files:**
- Modify: `frontend/components/dialogs/ToolsMenuDialog.tsx`

- [ ] **Step 1: Add build validation options**

Read `frontend/components/dialogs/ToolsMenuDialog.tsx` first. Find the existing Validate button (around line 52-55).

Add imports at the top:
```typescript
import { buildProject } from "@/lib/build-client";
import { useValidationStore } from "@/store/validationStore";
```

Add build handler function alongside the existing `handleValidate`:
```typescript
  const setBuildErrors = useValidationStore((s) => s.setBuildErrors);
  const setBuildLoading = useValidationStore((s) => s.setBuildLoading);

  const handleBuild = async (platform: "jvm" | "native") => {
    const graph = buildWorkspaceGraph();
    setBuildLoading(true);
    try {
      const result = await buildProject(graph, platform);
      const errors = result.errors.map((e) => ({
        nodeId: e.nodeId || "",
        severity: e.severity === "error" ? "error" as const : "warning" as const,
        message: `[${e.file}:${e.line}] ${e.message}`,
      }));
      setBuildErrors(errors);
    } catch (err: any) {
      setBuildErrors([{ nodeId: "", severity: "error", message: err.message || "Build failed" }]);
    } finally {
      setBuildLoading(false);
    }
  };
```

Replace the single Validate button with 3 buttons (or a dropdown group). Since the existing UI uses simple Buttons, use 3 buttons:

Replace the existing Validate button with:
```tsx
              <Button variant="ghost" size="sm" onClick={handleValidate}>
                Validate (Local)
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleBuild("jvm")}>
                Build (JVM)
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleBuild("native")}>
                Build (Native)
              </Button>
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/dialogs/ToolsMenuDialog.tsx
git commit -m "feat: add Build (JVM) and Build (Native) buttons to validate menu"
```

---

### Task 10: Update ValidationPanel to show build errors

**Files:**
- Modify: `frontend/components/validation/ValidationPanel.tsx`

- [ ] **Step 1: Display build errors alongside validation errors**

Read `frontend/components/validation/ValidationPanel.tsx` first. Add build errors from the store.

Add to the store reads:
```typescript
  const buildErrors = useValidationStore((s) => s.buildErrors);
  const buildLoading = useValidationStore((s) => s.buildLoading);
```

Combine errors for display. After the existing errors rendering, add a build errors section:

```tsx
      {buildLoading && (
        <div className="px-3 py-2 text-xs text-muted-foreground animate-pulse">Building...</div>
      )}
      {buildErrors.length > 0 && (
        <>
          <div className="px-3 py-1 text-[10px] font-semibold uppercase text-muted-foreground border-t mt-2 pt-2">
            Build Errors ({buildErrors.filter(e => e.severity === "error").length}E / {buildErrors.filter(e => e.severity === "warning").length}W)
          </div>
          {buildErrors.map((err, i) => (
            <div key={`build-${i}`} className="px-3 py-1 text-xs border-b border-border/50 flex items-start gap-2">
              <span className={`shrink-0 text-[10px] font-bold px-1 rounded ${err.severity === "error" ? "bg-red-500/20 text-red-400" : "bg-yellow-500/20 text-yellow-400"}`}>
                {err.severity === "error" ? "E" : "W"}
              </span>
              <span className="text-muted-foreground">{err.message}</span>
            </div>
          ))}
        </>
      )}
```

- [ ] **Step 2: Verify build**

```bash
cd frontend && npx next build 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/validation/ValidationPanel.tsx
git commit -m "feat: display build errors in ValidationPanel"
```

---

### Task 11: Build verification

**Files:**
- No new files

- [ ] **Step 1: Run full frontend build**

```bash
cd frontend && npx next build
```

- [ ] **Step 2: Run full backend build and tests**

```bash
./gradlew build 2>&1 | tail -10
```

- [ ] **Step 3: Verify Docker image builds**

```bash
docker build -f Dockerfile.builder -t modeler-builder .
```

- [ ] **Step 4: End-to-end test (manual)**

1. Start backend: `./gradlew bootRun`
2. Start frontend: `cd frontend && npm run dev`
3. Create a simple agent with a tool
4. Click "Build (JVM)" in the tools menu
5. Verify build result appears in ValidationPanel
6. Introduce a typo in a tool body
7. Click "Build (JVM)" again
8. Verify compile error appears with file, line, and message

- [ ] **Step 5: Final commit if adjustments needed**

```bash
git add -A
git commit -m "feat: complete gradle build validation integration"
```
