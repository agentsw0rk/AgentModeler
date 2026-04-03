# KMP Library Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a third `GeneratorBackend` (`kmp-library`) that generates publishable Kotlin Multiplatform library projects with selectable targets, a factory-function API, maven-publish setup, and smoke tests.

**Architecture:** New `KmpLibraryBackend` implements `GeneratorBackend`, remaps shared files to `commonMain`, generates 5 new files (build.gradle, settings.gradle, gradle.properties, AgentFactory, SmokeTest). Reuses `KtorRegistryGenerator` for plain-function registry and `WasmToolGenerator` for wasmJs SimpleTool overrides. Frontend gets target checkboxes and a "Publish to Maven Local" button.

**Tech Stack:** Kotlin 2.2.21, KotlinPoet (not used — string-template generators like Ktor backend), Spring Boot 4, Koog 0.7.2, Next.js 16, Zustand 5

---

## File Structure

### New files (Backend)
- `src/main/kotlin/com/agentwork/modeler/export/BackendConfig.kt` — config data class for backend-specific params
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt` — KMP build.gradle.kts with selectable targets + maven-publish
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibSettingsGradleGenerator.kt` — settings.gradle.kts
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibGradlePropertiesGenerator.kt` — gradle.properties
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt` — createXAgent() factory function
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGenerator.kt` — commonTest smoke test
- `src/main/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackend.kt` — GeneratorBackend implementation

### New files (Tests)
- `src/test/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackendTest.kt`

### Modified files (Backend)
- `src/main/kotlin/com/agentwork/modeler/export/GeneratorBackend.kt` — add `config` parameter to all generate methods
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` — build BackendConfig, pass to backend
- `src/main/kotlin/com/agentwork/modeler/export/ExportController.kt` — add `targets` to ExportRequest
- `src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt` — adapt to new interface signature
- `src/main/kotlin/com/agentwork/modeler/export/backend/KtorWasmBackend.kt` — adapt to new interface signature
- `src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt` — add `targets` to GenerateRequest
- `src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt` — pass targets through
- `src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt` — add `targets` to BuildRequest
- `src/main/kotlin/com/agentwork/modeler/build/BuildService.kt` — handle `publishToMavenLocal` task
- `src/main/kotlin/com/agentwork/modeler/build/BuildController.kt` — pass targets through

### Modified files (Frontend)
- `frontend/store/editorStore.ts` — add `libraryTargets` state
- `frontend/lib/generators-client.ts` — no change (already returns supportedPlatforms)
- `frontend/lib/export-client.ts` — add `targets` parameter
- `frontend/lib/vfs-client.ts` — add `targets` parameter
- `frontend/lib/build-client.ts` — add `targets` parameter
- `frontend/components/dialogs/ToolsMenuDialog.tsx` — target checkboxes + publish button

---

## Task 1: BackendConfig and GeneratorBackend Interface Extension

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/BackendConfig.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/GeneratorBackend.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/backend/KtorWasmBackend.kt`

- [ ] **Step 1: Create BackendConfig data class**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/BackendConfig.kt
package com.agentwork.modeler.export

data class BackendConfig(
    val targets: List<String> = emptyList()
)
```

- [ ] **Step 2: Add config parameter to GeneratorBackend interface**

In `GeneratorBackend.kt`, add `config: BackendConfig = BackendConfig()` as the last parameter to all three methods:

```kotlin
interface GeneratorBackend {
    val id: String
    val displayName: String
    val supportedPlatforms: List<String>
    val remapsSharedFiles: Boolean get() = false

    fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>

    fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>

    fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>
}
```

- [ ] **Step 3: Update SpringBootBackend method signatures**

Add `config: BackendConfig` parameter to all three override methods in `SpringBootBackend.kt`. The body stays unchanged — SpringBootBackend ignores config.

```kotlin
override fun generateAgentFiles(
    agent: KoogAgent,
    graphName: String,
    sharedFiles: Map<String, String>,
    inputClassName: String?,
    outputClassName: String?,
    config: BackendConfig
): Map<String, String> {
    // ... existing body unchanged
}

override fun generateGraphAgentFiles(
    agent: KoogGraphAgent,
    graphName: String,
    sharedFiles: Map<String, String>,
    strategyCall: String,
    inputClassName: String?,
    outputClassName: String?,
    config: BackendConfig
): Map<String, String> {
    // ... existing body unchanged
}

override fun generatePlannerAgentFiles(
    agent: KoogPlannerAgent,
    graphName: String,
    sharedFiles: Map<String, String>,
    config: BackendConfig
): Map<String, String> {
    // ... existing body unchanged
}
```

- [ ] **Step 4: Update KtorWasmBackend method signatures**

Same pattern — add `config: BackendConfig` parameter to all three override methods. Body unchanged.

- [ ] **Step 5: Compile to verify no breakage**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run existing tests**

Run: `./gradlew test`
Expected: All existing tests pass (default parameter means no callers break)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/BackendConfig.kt \
  src/main/kotlin/com/agentwork/modeler/export/GeneratorBackend.kt \
  src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt \
  src/main/kotlin/com/agentwork/modeler/export/backend/KtorWasmBackend.kt
git commit -m "refactor: add BackendConfig parameter to GeneratorBackend interface"
```

---

## Task 2: ExportService and Controllers — Targets Passthrough

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportController.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/build/BuildService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/build/BuildController.kt`

- [ ] **Step 1: Add targets to ExportRequest**

In `ExportController.kt`, add `targets` field to `ExportRequest`:

```kotlin
data class ExportRequest(
    val graph: WorkspaceGraph? = null,
    val backend: String = "spring-boot",
    val targets: List<String>? = null,
    val version: Int? = null,
    val name: String? = null,
    val nodes: List<GraphNode>? = null,
    val edges: List<GraphEdge>? = null
) {
    fun resolveGraph(): WorkspaceGraph = graph ?: WorkspaceGraph(
        version = version ?: 1,
        name = name ?: "export",
        nodes = nodes ?: emptyList(),
        edges = edges ?: emptyList()
    )
}
```

Update the `export` method to pass targets:

```kotlin
@PostMapping("/api/export")
fun export(@RequestBody request: ExportRequest): ResponseEntity<ByteArray> {
    val graph = request.resolveGraph()
    val zip = exportService.export(graph, request.backend, request.targets)
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${graph.name}.zip\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(zip)
}
```

- [ ] **Step 2: Update ExportService to accept and pass targets**

Add `targets` parameter to `export`, `exportWithFiles`, `generateFiles`, and `generateFilesWithMapping`:

```kotlin
fun export(graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): ByteArray {
    val files = generateFiles(graph, backendId, targets)
    return zipBuilder.buildZip(files)
}

fun exportWithFiles(graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): Pair<ByteArray, Map<String, String>> {
    val files = generateFiles(graph, backendId, targets)
    val zip = zipBuilder.buildZip(files)
    return Pair(zip, files)
}

fun generateFiles(graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): Map<String, String> {
    // ... existing validation and adapter code ...
    val config = BackendConfig(targets = targets ?: emptyList())

    if (plannerAgents.isNotEmpty()) {
        val planner = plannerAgents.first()
        val sharedFiles = generatePlannerSharedFiles(planner, sharedCodes, pkg)
        val infraFiles = backend.generatePlannerAgentFiles(planner, graph.name, sharedFiles, config)
        return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
    }

    if (graphAgents.isNotEmpty()) {
        val ga = graphAgents.first()
        val (inputClassName, outputClassName) = ioClassNames(ga.name, ga.inputs, ga.outputs)
        val strategyCall = "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()"
        val sharedFiles = generateGraphAgentSharedFiles(ga, sharedCodes, pkg)
        val infraFiles = backend.generateGraphAgentFiles(ga, graph.name, sharedFiles, strategyCall, inputClassName, outputClassName, config)
        return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
    }

    val agent = agents.first()
    val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
    val sharedFiles = generateAgentSharedFiles(agent, sharedCodes, pkg)
    val infraFiles = backend.generateAgentFiles(agent, graph.name, sharedFiles, inputClassName, outputClassName, config)
    return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
}

fun generateFilesWithMapping(graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): Pair<Map<String, String>, Map<String, String>> {
    val files = generateFiles(graph, backendId, targets)
    // ... rest unchanged, but also add commonMain prefix to the prefixes list:
    val prefixes = listOf("src/main/kotlin/generated", "src/wasmJsMain/kotlin/generated", "src/commonMain/kotlin/generated")
    // ... rest unchanged
}
```

- [ ] **Step 3: Update VFS GenerateRequest and CodeGenerationService**

In `VirtualFileSystemController.kt`, add `targets` to `GenerateRequest`:

```kotlin
data class GenerateRequest(
    val graph: WorkspaceGraph? = null,
    val backend: String = "spring-boot",
    val targets: List<String>? = null,
    val version: Int? = null,
    val name: String? = null,
    val nodes: List<GraphNode>? = null,
    val edges: List<GraphEdge>? = null
) {
    // ... existing resolveGraph()
}
```

Update the `generate` endpoint:

```kotlin
@PostMapping("/{projectName}/generate")
fun generate(
    @PathVariable projectName: String,
    @RequestBody request: GenerateRequest
): GenerationResult {
    return codeGenerationService.generateForProject(projectName, request.resolveGraph(), request.backend, request.targets)
}
```

In `CodeGenerationService.kt`, add `targets` parameter:

```kotlin
fun generateForProject(projectName: String, graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): GenerationResult {
    val files = try {
        exportService.generateFiles(graph, backendId, targets)
    } catch (e: ExportValidationException) {
        return GenerationResult(status = GenerationStatus.INVALID, errors = e.errors)
    }
    vfsService.writeFiles(projectName, files)
    return GenerationResult(status = GenerationStatus.VALID, fileCount = files.size, generatedAt = Instant.now().toString())
}
```

- [ ] **Step 4: Update BuildRequest and BuildService**

In `BuildModels.kt`, add `targets`:

```kotlin
data class BuildRequest(
    val graph: WorkspaceGraph,
    val platform: String = "jvm",
    val backend: String = "spring-boot",
    val targets: List<String>? = null
)
```

In `BuildService.kt`, add `targets` parameter and handle `publishToMavenLocal`:

```kotlin
fun build(graph: WorkspaceGraph, platform: String, backendId: String = "spring-boot", targets: List<String>? = null): BuildResult {
    val (files, fileToNodeMap) = exportService.generateFilesWithMapping(graph, backendId, targets)

    val tempDir = Files.createTempDirectory("modeler-build-").toFile()
    try {
        for ((path, content) in files) {
            val file = File(tempDir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        val gradleTask = when {
            platform == "publish" -> "publishToMavenLocal"
            platform == "native" -> "compileKotlinNative"
            platform == "wasm" -> "compileKotlinWasmJs"
            else -> "compileKotlin"
        }

        // ... rest unchanged
    } finally {
        tempDir.deleteRecursively()
    }
}
```

In `BuildController.kt`, pass targets:

```kotlin
@PostMapping("/api/build")
fun build(@RequestBody request: BuildRequest): BuildResult {
    return buildService.build(request.graph, request.platform, request.backend, request.targets)
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run existing tests**

Run: `./gradlew test`
Expected: All existing tests pass (all new params have defaults)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt \
  src/main/kotlin/com/agentwork/modeler/export/ExportController.kt \
  src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt \
  src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt \
  src/main/kotlin/com/agentwork/modeler/build/BuildModels.kt \
  src/main/kotlin/com/agentwork/modeler/build/BuildService.kt \
  src/main/kotlin/com/agentwork/modeler/build/BuildController.kt
git commit -m "feat: pass targets parameter through export/build/vfs pipeline"
```

---

## Task 3: LibBuildGradleGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGeneratorTest.kt
package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LibBuildGradleGeneratorTest {

    private val generator = LibBuildGradleGenerator()

    private val simpleAgent = KoogAgent(
        name = "TestAgent",
        systemPrompt = "Help",
        modelProvider = ModelProvider.OPENAI,
        modelId = null,
        temperature = null,
        tools = emptyList()
    )

    @Test
    fun `generates KMP build with JVM target only`() {
        val result = generator.generate(simpleAgent, listOf("jvm"))
        assertContains(result, """kotlin("multiplatform")""")
        assertContains(result, """id("maven-publish")""")
        assertContains(result, "jvm()")
        assertContains(result, "koog-agents")
        assertContains(result, """group = "com.example"""")
        assertContains(result, """version = "0.1.0"""")
        assertTrue(!result.contains("wasmJs"))
        assertTrue(!result.contains("iosArm64"))
    }

    @Test
    fun `generates KMP build with all targets`() {
        val result = generator.generate(simpleAgent, listOf("jvm", "wasmJs", "js", "ios"))
        assertContains(result, "jvm()")
        assertContains(result, "wasmJs {")
        assertContains(result, "js(IR) {")
        assertContains(result, "iosArm64()")
        assertContains(result, "iosSimulatorArm64()")
    }

    @Test
    fun `includes MCP dependency when agent has MCP servers`() {
        val agent = simpleAgent.copy(
            mcpServers = listOf(KoogMcpServer("test", "sse", "http://localhost", emptyList()))
        )
        val result = generator.generate(agent, listOf("jvm"))
        assertContains(result, "agents-ext-mcp")
    }

    @Test
    fun `includes test dependencies`() {
        val result = generator.generate(simpleAgent, listOf("jvm"))
        assertContains(result, """kotlin("test")""")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.LibBuildGradleGeneratorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write LibBuildGradleGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt
package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class LibBuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.2.21"
        const val KOOG_VERSION = "0.7.2"
    }

    fun generate(agent: KoogAgent, targets: List<String>): String = buildString {
        appendLine("""plugins {""")
        appendLine("""    kotlin("multiplatform") version "$KOTLIN_VERSION"""")
        appendLine("""    kotlin("plugin.serialization") version "$KOTLIN_VERSION"""")
        appendLine("""    id("maven-publish")""")
        appendLine("""}""")
        appendLine()
        appendLine("""group = "com.example"""")
        appendLine("""version = "0.1.0"""")
        appendLine()

        val hasWasm = "wasmJs" in targets
        if (hasWasm) {
            appendLine("""@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)""")
        }
        appendLine("""kotlin {""")

        for (target in targets) {
            when (target) {
                "jvm" -> appendLine("""    jvm()""")
                "wasmJs" -> {
                    appendLine("""    wasmJs {""")
                    appendLine("""        nodejs()""")
                    appendLine("""    }""")
                }
                "js" -> {
                    appendLine("""    js(IR) {""")
                    appendLine("""        nodejs()""")
                    appendLine("""    }""")
                }
                "ios" -> {
                    appendLine("""    iosArm64()""")
                    appendLine("""    iosSimulatorArm64()""")
                }
            }
        }

        appendLine()
        appendLine("""    sourceSets {""")
        appendLine("""        commonMain.dependencies {""")
        appendLine("""            implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")""")
        if (agent.mcpServers.isNotEmpty()) {
            appendLine("""            implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
        }
        appendLine("""        }""")
        appendLine("""        commonTest.dependencies {""")
        appendLine("""            implementation(kotlin("test"))""")
        appendLine("""        }""")
        appendLine("""    }""")
        appendLine("""}""")
        appendLine()
        appendLine("""repositories {""")
        appendLine("""    mavenCentral()""")
        appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")""")
        appendLine("""}""")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.LibBuildGradleGeneratorTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt \
  src/test/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGeneratorTest.kt
git commit -m "feat: add LibBuildGradleGenerator for KMP library projects"
```

---

## Task 4: LibSettingsGradleGenerator and LibGradlePropertiesGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibSettingsGradleGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibGradlePropertiesGenerator.kt`

- [ ] **Step 1: Create LibSettingsGradleGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibSettingsGradleGenerator.kt
package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class LibSettingsGradleGenerator {
    fun generate(projectName: String): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
        return "rootProject.name = \"$safeName\""
    }
}
```

- [ ] **Step 2: Create LibGradlePropertiesGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibGradlePropertiesGenerator.kt
package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class LibGradlePropertiesGenerator {
    fun generate(): String = buildString {
        appendLine("kotlin.code.style=official")
        appendLine("org.gradle.jvmargs=-Xmx512m")
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibSettingsGradleGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibGradlePropertiesGenerator.kt
git commit -m "feat: add LibSettingsGradle and LibGradleProperties generators"
```

---

## Task 5: AgentFactoryGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGeneratorTest.kt
package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AgentFactoryGeneratorTest {

    private val generator = AgentFactoryGenerator()

    @Test
    fun `generates factory function for simple agent`() {
        val agent = KoogAgent(
            name = "banking",
            systemPrompt = "You are a banking assistant.",
            modelProvider = ModelProvider.OPENAI,
            modelId = "gpt-4o",
            temperature = 0.7,
            tools = listOf(KoogTool("transfer", "Transfers money", emptyList()))
        )
        val result = generator.generateAgentFactory(agent)

        assertContains(result, "package generated")
        assertContains(result, "fun createBankingAgent(")
        assertContains(result, "executor: SingleLLMPromptExecutor")
        assertContains(result, "model: LLMModel = OpenAIModels.Chat.GPT4o")
        assertContains(result, "AIAgent(")
        assertContains(result, "promptExecutor = executor")
        assertContains(result, "systemPrompt = \"You are a banking assistant.\"")
        assertContains(result, "llmModel = model")
        assertContains(result, "temperature = 0.7")
        assertContains(result, "toolRegistry = createToolRegistry()")
    }

    @Test
    fun `generates factory with strategy call for graph agent`() {
        val result = generator.generateAgentFactory(
            KoogAgent(
                name = "workflow",
                systemPrompt = "Help",
                modelProvider = ModelProvider.ANTHROPIC,
                modelId = null,
                temperature = null,
                tools = emptyList()
            ),
            strategyCall = "createWorkflowStrategy()"
        )
        assertContains(result, "fun createWorkflowAgent(")
        assertContains(result, "strategy = createWorkflowStrategy()")
        assertContains(result, "AnthropicModels.Sonnet_4")
    }

    @Test
    fun `omits toolRegistry when no tools`() {
        val result = generator.generateAgentFactory(
            KoogAgent(
                name = "simple",
                systemPrompt = "Help",
                modelProvider = ModelProvider.GOOGLE,
                modelId = null,
                temperature = null,
                tools = emptyList()
            )
        )
        assertTrue(!result.contains("toolRegistry"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.AgentFactoryGeneratorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write AgentFactoryGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt
package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class AgentFactoryGenerator {

    fun generateAgentFactory(agent: KoogAgent, strategyCall: String? = null, inputClassName: String? = null, outputClassName: String? = null): String {
        val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
        val effectivePrompt = if (hasThink) {
            agent.systemPrompt + "\nThink step by step before answering."
        } else {
            agent.systemPrompt
        }
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val funcName = "create${agent.name.replaceFirstChar { it.uppercase() }}Agent"

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor")
            appendLine("import ai.koog.prompt.llm.LLMModel")
            appendLine("import ${agent.modelProvider.importPackage}")
            appendLine()

            val escapedPrompt = effectivePrompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

            appendLine("fun $funcName(")
            appendLine("    executor: SingleLLMPromptExecutor,")
            appendLine("    model: LLMModel = $modelRef")
            appendLine("): AIAgent = AIAgent(")
            appendLine("    promptExecutor = executor,")
            appendLine("    systemPrompt = \"$escapedPrompt\",")
            append("    llmModel = model")
            agent.temperature?.let { append(",\n    temperature = $it") }
            if (strategyCall != null) {
                append(",\n    strategy = $strategyCall")
            }
            if (hasTools) {
                append(",\n    toolRegistry = createToolRegistry()")
            }
            appendLine()
            appendLine(")")
        }
    }

    fun generatePlannerFactory(planner: KoogPlannerAgent): String {
        val modelRef = if (planner.modelProvider != null) {
            planner.modelId?.let { mapModelId(planner.modelProvider, it) } ?: planner.modelProvider.defaultModel
        } else {
            "OpenAIModels.Chat.GPT4o"
        }
        val funcName = "create${planner.name.replaceFirstChar { it.uppercase() }}Planner"
        val importPkg = planner.modelProvider?.importPackage ?: "ai.koog.prompt.executor.clients.openai.OpenAIModels"

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor")
            appendLine("import ai.koog.prompt.llm.LLMModel")
            appendLine("import $importPkg")
            appendLine("import generated.planner.*")
            appendLine()
            appendLine("fun $funcName(")
            appendLine("    executor: SingleLLMPromptExecutor,")
            appendLine("    model: LLMModel = $modelRef")
            appendLine(") {")
            appendLine("    // Planner agent setup — see PlannerGoals.kt and PlannerActions.kt")
            appendLine("}")
        }
    }

    private fun mapModelId(provider: ModelProvider, id: String): String = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
            "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
            "gpt-4-1" -> "OpenAIModels.Chat.GPT4_1"
            else -> provider.defaultModel
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
            "claude-opus-4" -> "AnthropicModels.Opus_4"
            "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
            else -> provider.defaultModel
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
            "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
            else -> provider.defaultModel
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.AgentFactoryGeneratorTest"`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt \
  src/test/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGeneratorTest.kt
git commit -m "feat: add AgentFactoryGenerator for KMP library public API"
```

---

## Task 6: SmokeTestGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGeneratorTest.kt
package com.agentwork.modeler.export.generator.lib

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SmokeTestGeneratorTest {

    private val generator = SmokeTestGenerator()

    @Test
    fun `generates test with toolRegistry check`() {
        val result = generator.generate(hasTools = true)
        assertContains(result, "package generated")
        assertContains(result, "import kotlin.test.Test")
        assertContains(result, "fun toolRegistryIsCreatable()")
        assertContains(result, "createToolRegistry()")
        assertContains(result, "assertNotNull")
    }

    @Test
    fun `generates test with strategy check for graph agents`() {
        val result = generator.generate(hasTools = true, strategyFunctionName = "createWorkflowStrategy")
        assertContains(result, "fun strategyIsCreatable()")
        assertContains(result, "createWorkflowStrategy()")
    }

    @Test
    fun `omits toolRegistry test when no tools`() {
        val result = generator.generate(hasTools = false)
        assertTrue(!result.contains("toolRegistryIsCreatable"))
        assertTrue(!result.contains("createToolRegistry"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.SmokeTestGeneratorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write SmokeTestGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGenerator.kt
package com.agentwork.modeler.export.generator.lib

import org.springframework.stereotype.Component

@Component
class SmokeTestGenerator {

    fun generate(hasTools: Boolean, strategyFunctionName: String? = null): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import kotlin.test.Test")
        appendLine("import kotlin.test.assertNotNull")
        appendLine()
        appendLine("class AgentSmokeTest {")

        if (hasTools) {
            appendLine()
            appendLine("    @Test")
            appendLine("    fun toolRegistryIsCreatable() {")
            appendLine("        val registry = createToolRegistry()")
            appendLine("        assertNotNull(registry)")
            appendLine("    }")
        }

        if (strategyFunctionName != null) {
            appendLine()
            appendLine("    @Test")
            appendLine("    fun strategyIsCreatable() {")
            appendLine("        val strategy = $strategyFunctionName()")
            appendLine("        assertNotNull(strategy)")
            appendLine("    }")
        }

        appendLine("}")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.lib.SmokeTestGeneratorTest"`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGenerator.kt \
  src/test/kotlin/com/agentwork/modeler/export/generator/lib/SmokeTestGeneratorTest.kt
git commit -m "feat: add SmokeTestGenerator for KMP library commonTest"
```

---

## Task 7: KmpLibraryBackend

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackend.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackendTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackendTest.kt
package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.BackendConfig
import com.agentwork.modeler.export.generator.lib.*
import com.agentwork.modeler.export.generator.ktor.KtorRegistryGenerator
import com.agentwork.modeler.export.generator.ktor.WasmToolGenerator
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class KmpLibraryBackendTest {

    private val backend = KmpLibraryBackend(
        libBuildGradleGenerator = LibBuildGradleGenerator(),
        libSettingsGradleGenerator = LibSettingsGradleGenerator(),
        libGradlePropertiesGenerator = LibGradlePropertiesGenerator(),
        agentFactoryGenerator = AgentFactoryGenerator(),
        smokeTestGenerator = SmokeTestGenerator(),
        ktorRegistryGenerator = KtorRegistryGenerator(),
        wasmToolGenerator = WasmToolGenerator()
    )

    private val simpleAgent = KoogAgent(
        name = "TestAgent",
        systemPrompt = "Help",
        modelProvider = ModelProvider.OPENAI,
        modelId = null,
        temperature = null,
        tools = listOf(KoogTool("search", "Searches", listOf(
            KoogToolFunction("find", "Finds things", listOf(
                KoogToolParameter("query", KoogParamType.STRING, "query", true, null)
            ))
        )))
    )

    @Test
    fun `has correct id and metadata`() {
        assertTrue(backend.id == "kmp-library")
        assertTrue(backend.displayName == "KMP Library")
        assertTrue(backend.remapsSharedFiles)
    }

    @Test
    fun `generates commonMain layout for JVM-only target`() {
        val sharedFiles = mapOf(
            "src/main/kotlin/generated/tools/search.kt" to "// tool code",
            "src/main/kotlin/generated/ToolRegistry.kt" to "// registry"
        )
        val config = BackendConfig(targets = listOf("jvm"))
        val files = backend.generateAgentFiles(simpleAgent, "TestProject", sharedFiles, null, null, config)

        assertTrue(files.containsKey("build.gradle.kts"))
        assertTrue(files.containsKey("settings.gradle.kts"))
        assertTrue(files.containsKey("gradle.properties"))
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/AgentFactory.kt"))
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/ToolRegistry.kt"))
        assertTrue(files.containsKey("src/commonTest/kotlin/generated/AgentSmokeTest.kt"))

        // Shared files remapped from src/main to src/commonMain
        assertTrue(files.containsKey("src/commonMain/kotlin/generated/tools/search.kt"))
        assertTrue(!files.containsKey("src/main/kotlin/generated/tools/search.kt"))

        // build.gradle contains jvm() but not wasmJs
        assertContains(files["build.gradle.kts"]!!, "jvm()")
        assertTrue(!files["build.gradle.kts"]!!.contains("wasmJs"))
    }

    @Test
    fun `generates wasmJsMain tool overrides when wasmJs target selected`() {
        val sharedFiles = mapOf(
            "src/main/kotlin/generated/tools/search.kt" to "// tool code",
            "src/main/kotlin/generated/ToolRegistry.kt" to "// registry"
        )
        val config = BackendConfig(targets = listOf("jvm", "wasmJs"))
        val files = backend.generateAgentFiles(simpleAgent, "TestProject", sharedFiles, null, null, config)

        // Should have wasmJsMain tool files
        assertTrue(files.containsKey("src/wasmJsMain/kotlin/generated/tools/search.kt"))
        assertContains(files["build.gradle.kts"]!!, "wasmJs {")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.backend.KmpLibraryBackendTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Write KmpLibraryBackend**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackend.kt
package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.BackendConfig
import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.lib.*
import com.agentwork.modeler.export.generator.ktor.KtorRegistryGenerator
import com.agentwork.modeler.export.generator.ktor.WasmToolGenerator
import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class KmpLibraryBackend(
    private val libBuildGradleGenerator: LibBuildGradleGenerator,
    private val libSettingsGradleGenerator: LibSettingsGradleGenerator,
    private val libGradlePropertiesGenerator: LibGradlePropertiesGenerator,
    private val agentFactoryGenerator: AgentFactoryGenerator,
    private val smokeTestGenerator: SmokeTestGenerator,
    private val ktorRegistryGenerator: KtorRegistryGenerator,
    private val wasmToolGenerator: WasmToolGenerator
) : GeneratorBackend {

    override val id = "kmp-library"
    override val displayName = "KMP Library"
    override val supportedPlatforms = listOf("jvm", "wasmJs", "js", "ios")
    override val remapsSharedFiles = true

    private val jvmPrefix = "src/main/kotlin/generated/"
    private val commonPrefix = "src/commonMain/kotlin/generated/"
    private val wasmPrefix = "src/wasmJsMain/kotlin/generated/"
    private val testPrefix = "src/commonTest/kotlin/generated/"

    private fun remapSharedFiles(sharedFiles: Map<String, String>, hasWasmTarget: Boolean): Map<String, String> =
        sharedFiles
            .filterKeys { path ->
                // Exclude JVM ToolSet-based tool files and Spring ToolRegistry — we generate our own
                !path.matches(Regex(".*/tools/[^/]+\\.kt$")) && !path.endsWith("ToolRegistry.kt") && !path.contains("/rag/")
            }
            .mapKeys { (path, _) ->
                if (path.startsWith(jvmPrefix)) {
                    commonPrefix + path.removePrefix(jvmPrefix)
                } else {
                    path
                }
            }

    private fun generateCommonToolFiles(sharedFiles: Map<String, String>): Map<String, String> =
        sharedFiles
            .filterKeys { path -> path.matches(Regex(".*/tools/[^/]+\\.kt$")) }
            .mapKeys { (path, _) ->
                if (path.startsWith(jvmPrefix)) {
                    commonPrefix + path.removePrefix(jvmPrefix)
                } else {
                    path
                }
            }

    private fun generateWasmToolFiles(tools: List<KoogTool>): Map<String, String> {
        val files = mutableMapOf<String, String>()
        for (tool in tools) {
            files["${wasmPrefix}tools/${tool.name}.kt"] = wasmToolGenerator.generate(tool)
        }
        return files
    }

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasWasm = "wasmJs" in targets
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = libBuildGradleGenerator.generate(agent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()

        // Factory
        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generateAgentFactory(agent, inputClassName = inputClassName, outputClassName = outputClassName)

        // Tool registry (plain function, no Spring)
        if (hasTools) {
            files["${commonPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = agent.tools,
                builtinTools = agent.builtinTools,
                subAgents = agent.subAgents,
                mcpServers = agent.mcpServers
            )
        }

        // Smoke test
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools)

        // Remapped shared files (code, subagents, mcp, a2a — but not tools/registry)
        files.putAll(remapSharedFiles(sharedFiles, hasWasm))

        // Common tool files (ToolSet-based, work on all targets except WasmJS)
        files.putAll(generateCommonToolFiles(sharedFiles))

        // WasmJS SimpleTool overrides
        if (hasWasm && agent.tools.isNotEmpty()) {
            files.putAll(generateWasmToolFiles(agent.tools))
        }

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasWasm = "wasmJs" in targets
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        val infraAgent = KoogAgent(
            name = agent.name,
            systemPrompt = agent.systemPrompt,
            modelProvider = agent.modelProvider,
            modelId = agent.modelId,
            temperature = agent.temperature,
            tools = agent.tools,
            builtinTools = agent.builtinTools,
            subAgents = agent.subAgents,
            mcpServers = agent.mcpServers,
            inputs = agent.inputs,
            outputs = agent.outputs,
            ragNodes = agent.ragNodes
        )

        // Infrastructure
        files["build.gradle.kts"] = libBuildGradleGenerator.generate(infraAgent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()

        // Factory
        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generateAgentFactory(infraAgent, strategyCall, inputClassName, outputClassName)

        // Tool registry
        if (hasTools) {
            files["${commonPrefix}ToolRegistry.kt"] = ktorRegistryGenerator.generate(
                tools = infraAgent.tools,
                builtinTools = infraAgent.builtinTools,
                subAgents = infraAgent.subAgents,
                mcpServers = infraAgent.mcpServers
            )
        }

        // Smoke test (with strategy check)
        val strategyFuncName = "create${agent.name.replaceFirstChar { it.uppercase() }}Strategy"
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools, strategyFunctionName = strategyFuncName)

        // Shared files
        files.putAll(remapSharedFiles(sharedFiles, hasWasm))
        files.putAll(generateCommonToolFiles(sharedFiles))
        if (hasWasm && infraAgent.tools.isNotEmpty()) {
            files.putAll(generateWasmToolFiles(infraAgent.tools))
        }

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        config: BackendConfig
    ): Map<String, String> {
        val targets = config.targets.ifEmpty { listOf("jvm") }
        val hasTools = agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.mcpServers.isNotEmpty()
        val files = mutableMapOf<String, String>()

        val infraAgent = KoogAgent(
            name = agent.name,
            systemPrompt = "",
            modelProvider = agent.modelProvider ?: ModelProvider.OPENAI,
            modelId = agent.modelId,
            temperature = agent.temperature,
            tools = agent.tools,
            builtinTools = agent.builtinTools,
            mcpServers = agent.mcpServers
        )

        // Infrastructure
        files["build.gradle.kts"] = libBuildGradleGenerator.generate(infraAgent, targets)
        files["settings.gradle.kts"] = libSettingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = libGradlePropertiesGenerator.generate()

        // Factory
        files["${commonPrefix}AgentFactory.kt"] = agentFactoryGenerator.generatePlannerFactory(agent)

        // Smoke test
        files["${testPrefix}AgentSmokeTest.kt"] = smokeTestGenerator.generate(hasTools = hasTools)

        // Shared files
        files.putAll(remapSharedFiles(sharedFiles, false))

        return files
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.backend.KmpLibraryBackendTest"`
Expected: All 3 tests PASS

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackend.kt \
  src/test/kotlin/com/agentwork/modeler/export/backend/KmpLibraryBackendTest.kt
git commit -m "feat: add KmpLibraryBackend implementing GeneratorBackend"
```

---

## Task 8: ExportService Integration Test

**Files:**
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Add KMP library backend test to ExportServiceTest**

Add the following to `ExportServiceTest.kt`. First, add the backend instance alongside `springBootBackend`:

```kotlin
private val kmpLibraryBackend = KmpLibraryBackend(
    libBuildGradleGenerator = LibBuildGradleGenerator(),
    libSettingsGradleGenerator = LibSettingsGradleGenerator(),
    libGradlePropertiesGenerator = LibGradlePropertiesGenerator(),
    agentFactoryGenerator = AgentFactoryGenerator(),
    smokeTestGenerator = SmokeTestGenerator(),
    ktorRegistryGenerator = KtorRegistryGenerator(),
    wasmToolGenerator = WasmToolGenerator()
)
```

Update the `service` to include both backends:

```kotlin
private val service = ExportService(
    // ... existing params ...
    backends = listOf(springBootBackend, kmpLibraryBackend)
)
```

Add imports:

```kotlin
import com.agentwork.modeler.export.backend.KmpLibraryBackend
import com.agentwork.modeler.export.generator.lib.*
import com.agentwork.modeler.export.generator.ktor.KtorRegistryGenerator
import com.agentwork.modeler.export.generator.ktor.WasmToolGenerator
```

Then add the test:

```kotlin
@Test
fun `exports KMP library with commonMain layout`() {
    val graph = WorkspaceGraph(1, "LibProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "searchTool", "description" to "Searches",
                "functions" to listOf(mapOf("name" to "search", "description" to "Searches", "parameters" to listOf(mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "query", "required" to true))))))
        ),
        listOf(GraphEdge("e1", "t1", "a1"))
    )
    val zip = service.export(graph, "kmp-library", listOf("jvm", "wasmJs"))
    val entries = readZipEntries(zip)

    // Infrastructure
    assertTrue(entries.contains("build.gradle.kts"))
    assertTrue(entries.contains("settings.gradle.kts"))
    assertTrue(entries.contains("gradle.properties"))

    // No server infrastructure
    assertTrue(!entries.contains("Dockerfile"))
    assertTrue(!entries.contains("src/main/resources/application.properties"))
    assertTrue(!entries.any { it.contains("Application.kt") })
    assertTrue(!entries.any { it.contains("Controller.kt") })

    // CommonMain sources
    assertTrue(entries.contains("src/commonMain/kotlin/generated/AgentFactory.kt"))
    assertTrue(entries.contains("src/commonMain/kotlin/generated/ToolRegistry.kt"))
    assertTrue(entries.contains("src/commonMain/kotlin/generated/tools/searchTool.kt"))

    // WasmJS overrides
    assertTrue(entries.contains("src/wasmJsMain/kotlin/generated/tools/searchTool.kt"))

    // Test
    assertTrue(entries.contains("src/commonTest/kotlin/generated/AgentSmokeTest.kt"))

    // Verify content
    val buildContent = readZipFileContent(zip, "build.gradle.kts")
    assertContains(buildContent, "maven-publish")
    assertContains(buildContent, "jvm()")
    assertContains(buildContent, "wasmJs")

    val factoryContent = readZipFileContent(zip, "src/commonMain/kotlin/generated/AgentFactory.kt")
    assertContains(factoryContent, "createMyAgentAgent")
    assertContains(factoryContent, "SingleLLMPromptExecutor")
}
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass including the new integration test

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "test: add KMP library backend integration test to ExportServiceTest"
```

---

## Task 9: Frontend — EditorStore and API Clients

**Files:**
- Modify: `frontend/store/editorStore.ts`
- Modify: `frontend/lib/export-client.ts`
- Modify: `frontend/lib/vfs-client.ts`
- Modify: `frontend/lib/build-client.ts`

- [ ] **Step 1: Add libraryTargets to editorStore**

In `frontend/store/editorStore.ts`, add to the state interface and initial state:

After the `activeBackend: "spring-boot",` line, add:

```typescript
libraryTargets: ["jvm", "wasmJs"] as string[],
```

After the `setActiveBackend(id)` method, add:

```typescript
setLibraryTargets(targets: string[]) {
    set({ libraryTargets: targets });
},
```

- [ ] **Step 2: Update export-client.ts to accept targets**

```typescript
export async function exportToKotlin(graph: WorkspaceGraph, backend?: string, targets?: string[]): Promise<Blob> {
  const body: Record<string, unknown> = backend ? { graph, backend } : { ...graph };
  if (targets && targets.length > 0) {
    body.targets = targets;
    if (!body.graph) { body.graph = graph; }
  }
  const response = await fetch(`${API_BASE}/api/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Export failed: ${error}`);
  }

  return response.blob();
}
```

- [ ] **Step 3: Update vfs-client.ts to accept targets**

In `generateCode` function, change the body construction:

```typescript
export async function generateCode(
  projectName: string,
  graph: WorkspaceGraph,
  signal?: AbortSignal,
  backend?: string,
  targets?: string[]
): Promise<GenerationResult> {
  const body: Record<string, unknown> = backend ? { graph, backend } : { ...graph };
  if (targets && targets.length > 0) {
    body.targets = targets;
    if (!body.graph) { body.graph = graph; }
  }
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/generate`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    }
  );
  if (!response.ok) {
    throw new Error(`Generation failed: ${response.statusText}`);
  }
  return response.json();
}
```

- [ ] **Step 4: Update build-client.ts to accept targets**

```typescript
export async function buildProject(graph: WorkspaceGraph, platform: string, backend?: string, targets?: string[]): Promise<BuildResult> {
  const response = await fetch(`${API_BASE}/api/build`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      graph,
      platform,
      ...(backend ? { backend } : {}),
      ...(targets && targets.length > 0 ? { targets } : {}),
    }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ details: "Build request failed" }));
    throw new Error(error.details || "Build request failed");
  }

  return response.json();
}
```

- [ ] **Step 5: Run frontend lint**

Run: `cd frontend && npm run lint`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add frontend/store/editorStore.ts \
  frontend/lib/export-client.ts \
  frontend/lib/vfs-client.ts \
  frontend/lib/build-client.ts
git commit -m "feat: add libraryTargets state and targets parameter to API clients"
```

---

## Task 10: Frontend — ToolsMenuDialog Target Checkboxes and Publish Button

**Files:**
- Modify: `frontend/components/dialogs/ToolsMenuDialog.tsx`

- [ ] **Step 1: Add target checkboxes and publish button**

In `ToolsMenuDialog.tsx`, add the following changes:

Add to imports:

```typescript
import { CheckSquareIcon } from "lucide-react";
```

Add state and store access after existing hooks:

```typescript
const libraryTargets = useEditorStore((s) => s.libraryTargets);
const setLibraryTargets = useEditorStore((s) => s.setLibraryTargets);

const isLibrary = activeBackend === "kmp-library";
```

Add `PLATFORM_LABELS` entries for the publish action:

```typescript
const PLATFORM_LABELS: Record<string, string> = {
  jvm: "Build (JVM)",
  native: "Build (Native)",
  wasm: "Build (Wasm)",
  publish: "Publish to Maven Local",
};
```

Replace the `handleBuild` call to pass targets:

```typescript
const handleBuild = async (platform: string) => {
    const graph = buildWorkspaceGraph();
    setBuildLoading(true);
    try {
      const targets = isLibrary ? libraryTargets : undefined;
      const result = await buildProject(graph, platform, activeBackend, targets);
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

Add a target toggle handler:

```typescript
const toggleTarget = (target: string) => {
    const current = libraryTargets;
    if (current.includes(target)) {
      if (current.length > 1) {
        setLibraryTargets(current.filter((t) => t !== target));
      }
    } else {
      setLibraryTargets([...current, target]);
    }
  };
```

After the backend dropdown `</div>` (line 141), add target checkboxes conditionally:

```tsx
{isLibrary && (
  <div className="px-4 py-2">
    <div className="text-xs text-muted-foreground mb-1.5">Targets</div>
    <div className="grid grid-cols-2 gap-1">
      {["jvm", "wasmJs", "js", "ios"].map((target) => (
        <button
          key={target}
          onClick={() => toggleTarget(target)}
          className={`flex items-center gap-2 px-2 py-1.5 rounded text-sm transition-colors ${
            libraryTargets.includes(target)
              ? "bg-muted font-medium"
              : "hover:bg-muted/50 text-muted-foreground"
          }`}
        >
          <CheckSquareIcon
            className={`size-3.5 ${
              libraryTargets.includes(target)
                ? "text-primary"
                : "text-muted-foreground/30"
            }`}
          />
          {target === "ios" ? "iOS" : target}
        </button>
      ))}
    </div>
  </div>
)}
```

Replace the build buttons section (lines 147-157) with:

```tsx
{isLibrary ? (
  <Button
    variant="ghost"
    className="justify-start gap-3 h-10"
    onClick={() => handleBuild("publish")}
  >
    <PackageIcon className="size-4 text-muted-foreground" />
    Publish to Maven Local
  </Button>
) : (
  platforms.map((platform) => (
    <Button
      key={platform}
      variant="ghost"
      className="justify-start gap-3 h-10"
      onClick={() => handleBuild(platform)}
    >
      <HammerIcon className="size-4 text-muted-foreground" />
      {PLATFORM_LABELS[platform] ?? `Build (${platform})`}
    </Button>
  ))
)}
```

- [ ] **Step 2: Run frontend lint**

Run: `cd frontend && npm run lint`
Expected: No errors

- [ ] **Step 3: Run frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/components/dialogs/ToolsMenuDialog.tsx
git commit -m "feat: add target checkboxes and Publish to Maven Local button for KMP Library backend"
```

---

## Task 11: Wire Targets in VFS Code Generation Hook

**Files:**
- Find and modify the hook that calls `generateCode` (likely in a `useCodeGeneration` hook or similar)

- [ ] **Step 1: Find the code generation hook**

Search for `generateCode` usage in frontend to find where the VFS generation is triggered:

Run: `grep -r "generateCode" frontend/`

- [ ] **Step 2: Update the hook to pass targets**

Wherever `generateCode(projectName, graph, signal, activeBackend)` is called, add targets:

```typescript
const libraryTargets = useEditorStore.getState().libraryTargets;
const targets = activeBackend === "kmp-library" ? libraryTargets : undefined;
await generateCode(projectName, graph, signal, activeBackend, targets);
```

- [ ] **Step 3: Update ExportDialog to pass targets**

Find where `exportToKotlin` is called in the ExportDialog and add targets:

```typescript
const libraryTargets = useEditorStore.getState().libraryTargets;
const targets = activeBackend === "kmp-library" ? libraryTargets : undefined;
const blob = await exportToKotlin(graph, activeBackend, targets);
```

- [ ] **Step 4: Run frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add frontend/
git commit -m "feat: wire targets parameter in VFS code generation and export"
```

---

## Task 12: Full Stack Verification

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run frontend lint and build**

Run: `cd frontend && npm run lint && npm run build`
Expected: No errors

- [ ] **Step 3: Manual smoke test**

Start backend and frontend:
- `./gradlew bootRun` (terminal 1)
- `cd frontend && npm run dev` (terminal 2)

Verify:
1. Open http://localhost:3000
2. Tools menu shows "KMP Library" in backend dropdown
3. Selecting it shows target checkboxes
4. Code explorer regenerates with `commonMain` layout
5. Export downloads ZIP with correct structure
6. "Publish to Maven Local" button appears

- [ ] **Step 4: Final commit if any fixes needed**
