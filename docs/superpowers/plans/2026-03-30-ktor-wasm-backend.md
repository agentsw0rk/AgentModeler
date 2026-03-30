# Ktor/Wasm Generator Backend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a `GeneratorBackend` interface and implement a second code generator that produces Ktor/WasmJS projects alongside the existing Spring Boot generator.

**Architecture:** Extract infrastructure-specific generators behind a `GeneratorBackend` interface. The `ExportService` delegates to the selected backend while keeping shared generators (Tools, Strategy, SharedCode) centralized. A new `KtorWasmBackend` produces Kotlin Multiplatform WasmJS projects using Ktor for HTTP.

**Tech Stack:** Kotlin, Spring Boot (host), Koog 0.7.2 (KMP), Ktor 3.x (generated code), Kotlin/WasmJS

---

### Task 1: Create GeneratorBackend Interface

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/GeneratorBackend.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent

interface GeneratorBackend {
    val id: String
    val displayName: String
    val supportedPlatforms: List<String>

    fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String>

    fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String>

    fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String>
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/GeneratorBackend.kt
git commit -m "feat: add GeneratorBackend interface for pluggable code generation"
```

---

### Task 2: Create SpringBootBackend

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt`

This wraps existing generators behind the `GeneratorBackend` interface. All logic is extracted from `ExportService.generateFiles()`.

- [ ] **Step 1: Create SpringBootBackend**

```kotlin
package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent
import org.springframework.stereotype.Component

@Component
class SpringBootBackend(
    private val agentGenerator: AgentGenerator,
    private val controllerGenerator: ControllerGenerator,
    private val registryGenerator: RegistryGenerator,
    private val applicationGenerator: ApplicationGenerator,
    private val buildGradleGenerator: BuildGradleGenerator,
    private val settingsGradleGenerator: SettingsGradleGenerator,
    private val gradlePropertiesGenerator: GradlePropertiesGenerator,
    private val applicationPropertiesGenerator: ApplicationPropertiesGenerator,
    private val dockerfileGenerator: DockerfileGenerator,
    private val plannerAgentGenerator: PlannerAgentGenerator,
    private val a2aServerGenerator: A2AServerGenerator,
) : GeneratorBackend {

    override val id = "spring-boot"
    override val displayName = "Spring Boot (JVM)"
    override val supportedPlatforms = listOf("jvm", "native")

    private val pkg = "src/main/kotlin/generated"

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(agent)

        // Application + Agent config + Controller
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(agent, inputClassName = inputClassName, outputClassName = outputClassName)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)

        // A2A Server
        val a2aConfig = agent.a2aConfig
        if (a2aConfig != null && a2aConfig.enabled) {
            val a2aFiles = a2aServerGenerator.generate(a2aConfig)
            for ((fileName, content) in a2aFiles) {
                files["$pkg/a2a/$fileName"] = content
            }
        }

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Create a KoogAgent facade for infrastructure generators
        val infraAgent = com.agentwork.modeler.export.model.KoogAgent(
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
            outputs = agent.outputs
        )

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(infraAgent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(infraAgent)

        // Application + Agent config + Controller
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(infraAgent, strategyCall, inputClassName = inputClassName, outputClassName = outputClassName)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()

        // Planner-specific build (may not have a full agent for build.gradle)
        files["build.gradle.kts"] = generatePlannerBuildGradle()
        files["src/main/resources/application.properties"] = generatePlannerApplicationProperties(agent)

        // Application
        files["$pkg/Application.kt"] = applicationGenerator.generate()

        // Planner-specific files
        val plannerFiles = plannerAgentGenerator.generate(agent)
        files["$pkg/planner/PlannerGoals.kt"] = plannerFiles["PlannerGoals.kt"] ?: ""
        files["$pkg/planner/PlannerActions.kt"] = plannerFiles["PlannerActions.kt"] ?: ""
        files["$pkg/PlannerAgent.kt"] = plannerFiles["PlannerAgent.kt"] ?: ""

        // PlanController
        files["$pkg/PlanController.kt"] = generatePlanController()

        // A2A Server
        val a2aConfig = agent.a2aConfig
        if (a2aConfig != null && a2aConfig.enabled) {
            val a2aFiles = a2aServerGenerator.generate(a2aConfig)
            for ((fileName, content) in a2aFiles) {
                files["$pkg/a2a/$fileName"] = content
            }
        }

        return files
    }

    private fun generatePlannerBuildGradle(): String = buildString {
        val koogVersion = BuildGradleGenerator.KOOG_VERSION
        val kotlinVersion = BuildGradleGenerator.KOTLIN_VERSION
        val springBootVersion = BuildGradleGenerator.SPRING_BOOT_VERSION
        val depMgmtVersion = BuildGradleGenerator.DEPENDENCY_MANAGEMENT_VERSION
        appendLine("""plugins {""")
        appendLine("""    kotlin("jvm") version "$kotlinVersion"""")
        appendLine("""    kotlin("plugin.spring") version "$kotlinVersion"""")
        appendLine("""    id("org.springframework.boot") version "$springBootVersion"""")
        appendLine("""    id("io.spring.dependency-management") version "$depMgmtVersion"""")
        appendLine("""}""")
        appendLine()
        appendLine("""java {""")
        appendLine("""    toolchain {""")
        appendLine("""        languageVersion = JavaLanguageVersion.of(21)""")
        appendLine("""    }""")
        appendLine("""}""")
        appendLine()
        appendLine("""repositories {""")
        appendLine("""    mavenCentral()""")
        appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")""")
        appendLine("""}""")
        appendLine()
        appendLine("""dependencies {""")
        appendLine("""    implementation("org.springframework.boot:spring-boot-starter-webflux")""")
        appendLine("""    implementation("org.jetbrains.kotlin:kotlin-reflect")""")
        appendLine("""    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")""")
        appendLine("""    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")""")
        appendLine("""    implementation("ai.koog:koog-agents:$koogVersion")""")
        appendLine("""}""")
    }

    private fun generatePlannerApplicationProperties(planner: KoogPlannerAgent): String = buildString {
        appendLine("server.port=\${SERVER_PORT:8080}")
        if (planner.modelProvider != null) {
            val providerKey = planner.modelProvider.name.lowercase()
            appendLine("${providerKey}.api-key=\${${planner.modelProvider.name}_API_KEY:}")
        }
    }

    private fun generatePlanController(): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import org.springframework.web.bind.annotation.*")
        appendLine()
        appendLine("@RestController")
        appendLine("@RequestMapping(\"/api\")")
        appendLine("class PlanController {")
        appendLine()
        appendLine("    @PostMapping(\"/plan\")")
        appendLine("    fun executePlan(@RequestBody request: PlanRequest): PlanResponse {")
        appendLine("        // TODO: Integrate with planner agent bean")
        appendLine("        return PlanResponse(status = \"planned\", steps = emptyList())")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("data class PlanRequest(val goalName: String, val state: Map<String, Any> = emptyMap())")
        appendLine("data class PlanResponse(val status: String, val steps: List<String>)")
    }
}
```

- [ ] **Step 2: Compile and verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt
git commit -m "feat: create SpringBootBackend wrapping existing generators"
```

---

### Task 3: Refactor ExportService to Use GeneratorBackend

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

Extract shared file generation into a helper method. Delegate infrastructure generation to the selected backend.

- [ ] **Step 1: Refactor ExportService**

Replace the constructor and `generateFiles()` method. The new version:
1. Keeps shared generators as direct dependencies
2. Injects `List<GeneratorBackend>` via Spring
3. Adds `backendId` parameter to `generateFiles()` (default: `"spring-boot"`)
4. Extracts shared file generation (tools, strategy, code nodes, sub-agents, etc.) into `generateSharedFiles()`
5. Delegates infrastructure generation to the selected backend

Key changes to `generateFiles()`:
- Extract tool generation loop, builtin tool generation, sub-agent generation, MCP server generation, input/output generation, shared code generation into a shared method
- The three agent-type branches (planner/graph/standard) each call shared generation + backend generation
- `generateFilesWithMapping()` also gains the `backendId` parameter

The method `generateFiles(graph, backendId)` becomes:
```kotlin
fun generateFiles(graph: WorkspaceGraph, backendId: String = "spring-boot"): Map<String, String> {
    val validationErrors = validator.validate(graph)
    if (validationErrors.isNotEmpty()) throw ExportValidationException(validationErrors)

    val backend = backends.first { it.id == backendId }
    val agents = adapter.convert(graph)
    val plannerAgents = adapter.convertPlannerAgents(graph)
    val graphAgents = adapter.convertGraphAgents(graph)
    val sharedCodes = adapter.convertSharedCode(graph)
    val pkg = "src/main/kotlin/generated"

    if (plannerAgents.isNotEmpty()) {
        val planner = plannerAgents.first()
        val sharedFiles = generateSharedFilesForPlanner(planner, sharedCodes, pkg)
        val infraFiles = backend.generatePlannerAgentFiles(planner, graph.name, sharedFiles)
        return sharedFiles + infraFiles
    }

    if (graphAgents.isNotEmpty()) {
        val ga = graphAgents.first()
        val (inputClassName, outputClassName) = ioClassNames(ga.name, ga.inputs, ga.outputs)
        val strategyCall = "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()"
        val sharedFiles = generateSharedFilesForGraphAgent(ga, sharedCodes, pkg)
        val infraFiles = backend.generateGraphAgentFiles(ga, graph.name, sharedFiles, strategyCall, inputClassName, outputClassName)
        return sharedFiles + infraFiles
    }

    val agent = agents.first()
    val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
    val sharedFiles = generateSharedFilesForAgent(agent, sharedCodes, pkg)
    val infraFiles = backend.generateAgentFiles(agent, graph.name, sharedFiles, inputClassName, outputClassName)
    return sharedFiles + infraFiles
}
```

The shared file generation methods produce tools, builtins, sub-agents, MCP servers, A2A clients, input/output classes, shared code files, registry, and graph strategy files.

- [ ] **Step 2: Update generateFilesWithMapping()**

Add `backendId` parameter:
```kotlin
fun generateFilesWithMapping(graph: WorkspaceGraph, backendId: String = "spring-boot"): Pair<Map<String, String>, Map<String, String>> {
    val files = generateFiles(graph, backendId)
    // ... existing fileToNodeId mapping logic (unchanged)
    return Pair(files, fileToNodeId)
}
```

- [ ] **Step 3: Update export() and exportWithFiles()**

```kotlin
fun export(graph: WorkspaceGraph, backendId: String = "spring-boot"): ByteArray {
    val files = generateFiles(graph, backendId)
    return zipBuilder.buildZip(files)
}

fun exportWithFiles(graph: WorkspaceGraph, backendId: String = "spring-boot"): Pair<ByteArray, Map<String, String>> {
    val files = generateFiles(graph, backendId)
    val zip = zipBuilder.buildZip(files)
    return Pair(zip, files)
}
```

- [ ] **Step 4: Remove private planner helper methods**

The `generatePlannerBuildGradle()`, `generatePlannerApplicationProperties()`, and `generatePlanController()` methods have moved to `SpringBootBackend`. Delete them from `ExportService`.

- [ ] **Step 5: Compile and verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "refactor: ExportService delegates to GeneratorBackend interface"
```

---

### Task 4: Add Backend Parameter to API Endpoints

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportController.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/build/BuildController.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/build/BuildService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/GeneratorController.kt`

- [ ] **Step 1: Create GeneratorController**

```kotlin
package com.agentwork.modeler.export

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class GeneratorController(
    private val backends: List<GeneratorBackend>
) {
    @GetMapping("/generators")
    fun listGenerators(): List<GeneratorInfo> {
        return backends.map { GeneratorInfo(it.id, it.displayName, it.supportedPlatforms) }
    }
}

data class GeneratorInfo(
    val id: String,
    val displayName: String,
    val supportedPlatforms: List<String>
)
```

- [ ] **Step 2: Update ExportController**

Add `backend` field to the request. Since the current endpoint takes `WorkspaceGraph` directly as body, wrap it in an `ExportRequest`:

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ExportController(private val exportService: ExportService) {

    data class ExportRequest(
        val graph: WorkspaceGraph? = null,
        val backend: String = "spring-boot",
        // Support flat graph for backward compatibility
        val version: Int? = null,
        val name: String? = null,
        val nodes: List<com.agentwork.modeler.export.model.GraphNode>? = null,
        val edges: List<com.agentwork.modeler.export.model.GraphEdge>? = null
    ) {
        fun resolveGraph(): WorkspaceGraph = graph ?: WorkspaceGraph(
            version = version ?: 1,
            name = name ?: "export",
            nodes = nodes ?: emptyList(),
            edges = edges ?: emptyList()
        )
    }

    @PostMapping("/export")
    fun export(@RequestBody request: ExportRequest): ResponseEntity<ByteArray> {
        val graph = request.resolveGraph()
        val zip = exportService.export(graph, request.backend)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${graph.name}.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip)
    }

    @ExceptionHandler(ExportValidationException::class)
    fun handleValidation(ex: ExportValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(mapOf("error" to "Validation failed", "details" to ex.errors))
    }
}
```

- [ ] **Step 3: Update BuildController and BuildService**

Add `backend` field to `BuildRequest`:

In `BuildController.kt`, the `BuildRequest` data class gets a `backend` field:
```kotlin
data class BuildRequest(val graph: WorkspaceGraph, val platform: String, val backend: String = "spring-boot")
```

Pass it through in the controller:
```kotlin
@PostMapping("/build")
fun build(@RequestBody request: BuildRequest): BuildResult {
    return buildService.build(request.graph, request.platform, request.backend)
}
```

In `BuildService.kt`, add `backendId` parameter:
```kotlin
fun build(graph: WorkspaceGraph, platform: String, backendId: String = "spring-boot"): BuildResult {
    val (files, fileToNodeMap) = exportService.generateFilesWithMapping(graph, backendId)
    // ... rest unchanged
}
```

- [ ] **Step 4: Update VFS CodeGenerationService and Controller**

In `CodeGenerationService.kt`:
```kotlin
fun generateForProject(projectName: String, graph: WorkspaceGraph, backendId: String = "spring-boot"): GenerationResult {
    val files = try {
        exportService.generateFiles(graph, backendId)
    } catch (e: ExportValidationException) {
        return GenerationResult(status = GenerationStatus.INVALID, errors = e.errors)
    }
    vfsService.writeFiles(projectName, files)
    return GenerationResult(status = GenerationStatus.VALID, fileCount = files.size, generatedAt = Instant.now().toString())
}
```

In `VirtualFileSystemController.kt`, wrap the request to support `backend` parameter:
```kotlin
data class GenerateRequest(
    val graph: WorkspaceGraph? = null,
    val backend: String = "spring-boot",
    // Flat fields for backward compat
    val version: Int? = null,
    val name: String? = null,
    val nodes: List<GraphNode>? = null,
    val edges: List<GraphEdge>? = null
) {
    fun resolveGraph(): WorkspaceGraph = graph ?: WorkspaceGraph(
        version = version ?: 1, name = name ?: "project",
        nodes = nodes ?: emptyList(), edges = edges ?: emptyList()
    )
}

@PostMapping("/api/vfs/{projectName}/generate")
fun generate(@PathVariable projectName: String, @RequestBody request: GenerateRequest): GenerationResult {
    return codeGenerationService.generateForProject(projectName, request.resolveGraph(), request.backend)
}
```

- [ ] **Step 5: Compile and verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/GeneratorController.kt \
  src/main/kotlin/com/agentwork/modeler/export/ExportController.kt \
  src/main/kotlin/com/agentwork/modeler/build/BuildController.kt \
  src/main/kotlin/com/agentwork/modeler/build/BuildService.kt \
  src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt \
  src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt
git commit -m "feat: add backend parameter to export, build, and VFS API endpoints"
```

---

### Task 5: Create Ktor/Wasm Generators

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorMainGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorControllerGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorRegistryGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/WasmBuildGradleGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/WasmDockerfileGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/WasmSettingsGradleGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/WasmGradlePropertiesGenerator.kt`

- [ ] **Step 1: Create WasmBuildGradleGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class WasmBuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.3.0"
        const val KOOG_VERSION = "0.7.2"
        const val KTOR_VERSION = "3.1.3"
    }

    fun generate(agent: KoogAgent): String = buildString {
        appendLine("""plugins {""")
        appendLine("""    kotlin("multiplatform") version "$KOTLIN_VERSION"""")
        appendLine("""    kotlin("plugin.serialization") version "$KOTLIN_VERSION"""")
        appendLine("""}""")
        appendLine()
        appendLine("""kotlin {""")
        appendLine("""    wasmJs {""")
        appendLine("""        nodejs()""")
        appendLine("""        binaries.executable()""")
        appendLine("""    }""")
        appendLine()
        appendLine("""    sourceSets {""")
        appendLine("""        wasmJsMain.dependencies {""")
        appendLine("""            implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-server-core:$KTOR_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-server-node:$KTOR_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-server-content-negotiation:$KTOR_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-serialization-kotlinx-json:$KTOR_VERSION")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")""")
        if (agent.mcpServers.isNotEmpty()) {
            appendLine("""            implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
        }
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

- [ ] **Step 2: Create WasmSettingsGradleGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmSettingsGradleGenerator {
    fun generate(projectName: String): String = "rootProject.name = \"$projectName\""
}
```

- [ ] **Step 3: Create WasmGradlePropertiesGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmGradlePropertiesGenerator {
    fun generate(): String = buildString {
        appendLine("kotlin.code.style=official")
        appendLine("org.gradle.jvmargs=-Xmx512m")
    }
}
```

- [ ] **Step 4: Create WasmDockerfileGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class WasmDockerfileGenerator {
    fun generate(): String = buildString {
        appendLine("FROM node:22-alpine")
        appendLine("WORKDIR /app")
        appendLine("COPY build/js/packages/agent/kotlin/ .")
        appendLine("EXPOSE 8080")
        appendLine("""CMD ["node", "agent.mjs"]""")
    }
}
```

- [ ] **Step 5: Create KtorMainGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class KtorMainGenerator {

    fun generate(agent: KoogAgent, strategyCall: String? = null): String = buildString {
        val modelRef = mapModelRef(agent.modelProvider, agent.modelId)
        val clientImport = agent.modelProvider.importPackage
        val clientClass = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "OpenAILLMClient"
            ModelProvider.ANTHROPIC -> "AnthropicLLMClient"
            ModelProvider.GOOGLE -> "GoogleLLMClient"
        }
        val clientPackage = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "ai.koog.prompt.executor.clients.openai"
            ModelProvider.ANTHROPIC -> "ai.koog.prompt.executor.clients.anthropic"
            ModelProvider.GOOGLE -> "ai.koog.prompt.executor.clients.google"
        }
        val envKey = "${agent.modelProvider.name}_API_KEY"

        appendLine("package generated")
        appendLine()
        appendLine("import ai.koog.agents.core.agent.AIAgent")
        appendLine("import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor")
        appendLine("import $clientPackage.$clientClass")
        appendLine("import $clientImport")
        appendLine("import io.ktor.server.application.*")
        appendLine("import io.ktor.server.engine.*")
        appendLine("import io.ktor.server.routing.*")
        appendLine("import kotlinx.coroutines.runBlocking")
        appendLine()
        appendLine("fun main() {")
        appendLine("    val apiKey = js(\"process.env.$envKey\").unsafeCast<String?>() ?: error(\"$envKey not set\")")
        appendLine("    val client = $clientClass(apiKey)")
        appendLine("    val executor = SingleLLMPromptExecutor(client)")
        appendLine("    val toolRegistry = createToolRegistry()")
        appendLine()

        val escapedPrompt = agent.systemPrompt.replace("\"", "\\\"").replace("\n", "\\n")
        if (strategyCall != null) {
            appendLine("    val agent = AIAgent(")
            appendLine("        executor = executor,")
            appendLine("        strategy = $strategyCall,")
            appendLine("        systemPrompt = \"$escapedPrompt\",")
            appendLine("        llmModel = $modelRef,")
            appendLine("        installTools = { toolRegistry.also { install(it) } }")
            appendLine("    )")
        } else {
            appendLine("    val agent = AIAgent(")
            appendLine("        executor = executor,")
            appendLine("        systemPrompt = \"$escapedPrompt\",")
            appendLine("        llmModel = $modelRef,")
            appendLine("        installTools = { toolRegistry.also { install(it) } }")
            appendLine("    )")
        }
        appendLine()
        appendLine("    embeddedServer(io.ktor.server.engine.ApplicationEngine, port = 8080) {")
        appendLine("        configureChatRoutes(agent)")
        appendLine("    }.start(wait = true)")
        appendLine("}")
    }

    private fun mapModelRef(provider: ModelProvider, modelId: String?): String {
        if (modelId == null) return provider.defaultModel
        return when (provider) {
            ModelProvider.OPENAI -> when {
                modelId.contains("gpt-4o-mini") -> "OpenAIModels.Chat.GPT4oMini"
                modelId.contains("gpt-4o") -> "OpenAIModels.Chat.GPT4o"
                modelId.contains("gpt-4.1") -> "OpenAIModels.Chat.GPT4o"
                modelId.contains("o3-mini") -> "OpenAIModels.Reasoning.O3Mini"
                else -> provider.defaultModel
            }
            ModelProvider.ANTHROPIC -> when {
                modelId.contains("sonnet") -> "AnthropicModels.Sonnet_4"
                modelId.contains("haiku") -> "AnthropicModels.Haiku_3_5"
                modelId.contains("opus") -> "AnthropicModels.Opus_3"
                else -> provider.defaultModel
            }
            ModelProvider.GOOGLE -> when {
                modelId.contains("flash") -> "GoogleModels.Gemini2Flash"
                modelId.contains("pro") -> "GoogleModels.Gemini2Pro"
                else -> provider.defaultModel
            }
        }
    }
}
```

- [ ] **Step 6: Create KtorControllerGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import org.springframework.stereotype.Component

@Component
class KtorControllerGenerator {

    fun generate(inputClassName: String? = null, outputClassName: String? = null): String = buildString {
        val useCustomInput = inputClassName != null
        val useCustomOutput = outputClassName != null
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"

        appendLine("package generated")
        appendLine()
        appendLine("import ai.koog.agents.core.agent.AIAgent")
        appendLine("import io.ktor.server.application.*")
        appendLine("import io.ktor.server.request.*")
        appendLine("import io.ktor.server.response.*")
        appendLine("import io.ktor.server.routing.*")
        appendLine("import io.ktor.http.*")
        if (useCustomInput || useCustomOutput) {
            appendLine("import kotlinx.serialization.Serializable")
            appendLine("import io.ktor.server.plugins.contentnegotiation.*")
            appendLine("import io.ktor.serialization.kotlinx.json.*")
        }
        appendLine("import kotlinx.coroutines.runBlocking")
        appendLine()
        appendLine("fun Application.configureChatRoutes(agent: AIAgent<$inputType, $outputType>) {")
        if (useCustomInput || useCustomOutput) {
            appendLine("    install(ContentNegotiation) { json() }")
        }
        appendLine("    routing {")
        appendLine("        post(\"/api/chat\") {")
        if (useCustomInput) {
            appendLine("            val request = call.receive<$inputType>()")
            appendLine("            val result = runBlocking { agent.run(request) }")
        } else {
            appendLine("            val message = call.receiveText()")
            appendLine("            val result = runBlocking { agent.run(message) }")
        }
        if (useCustomOutput) {
            appendLine("            call.respond(result)")
        } else {
            appendLine("            call.respondText(result)")
        }
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }
}
```

- [ ] **Step 7: Create KtorRegistryGenerator**

```kotlin
package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class KtorRegistryGenerator {

    fun generate(
        tools: List<KoogTool>,
        builtinTools: List<KoogBuiltinTool> = emptyList(),
        subAgents: List<KoogSubAgent> = emptyList(),
        mcpServers: List<KoogMcpServer> = emptyList()
    ): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        if (tools.isNotEmpty()) appendLine("import generated.tools.*")

        appendLine()
        appendLine("fun createToolRegistry(): ToolRegistry {")
        appendLine("    return ToolRegistry {")
        for (tool in tools) {
            appendLine("        tool(${tool.name}())")
        }
        appendLine("    }")
        appendLine("}")
    }
}
```

- [ ] **Step 8: Compile and verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ktor/
git commit -m "feat: add Ktor/Wasm generators for Main, Controller, Registry, Build, Dockerfile"
```

---

### Task 6: Create KtorWasmBackend

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/backend/KtorWasmBackend.kt`

- [ ] **Step 1: Create KtorWasmBackend**

```kotlin
package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.ktor.*
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent
import org.springframework.stereotype.Component

@Component
class KtorWasmBackend(
    private val mainGenerator: KtorMainGenerator,
    private val controllerGenerator: KtorControllerGenerator,
    private val registryGenerator: KtorRegistryGenerator,
    private val buildGradleGenerator: WasmBuildGradleGenerator,
    private val dockerfileGenerator: WasmDockerfileGenerator,
    private val settingsGradleGenerator: WasmSettingsGradleGenerator,
    private val gradlePropertiesGenerator: WasmGradlePropertiesGenerator
) : GeneratorBackend {

    override val id = "ktor-wasm"
    override val displayName = "Ktor (WasmJS)"
    override val supportedPlatforms = listOf("wasm")

    private val pkg = "src/wasmJsMain/kotlin/generated"

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()

        // Application code
        files["$pkg/Main.kt"] = mainGenerator.generate(agent)
        files["$pkg/ChatRoutes.kt"] = controllerGenerator.generate(inputClassName, outputClassName)
        files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents, agent.mcpServers)

        // Remap shared files from src/main/kotlin/generated/ to src/wasmJsMain/kotlin/generated/
        for ((path, content) in sharedFiles) {
            val wasmPath = path.replace("src/main/kotlin/generated/", "$pkg/")
            files[wasmPath] = content
        }

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
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
            outputs = agent.outputs
        )

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(infraAgent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()

        // Application code
        files["$pkg/Main.kt"] = mainGenerator.generate(infraAgent, strategyCall)
        files["$pkg/ChatRoutes.kt"] = controllerGenerator.generate(inputClassName, outputClassName)
        files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents, agent.mcpServers)

        // Remap shared files
        for ((path, content) in sharedFiles) {
            val wasmPath = path.replace("src/main/kotlin/generated/", "$pkg/")
            files[wasmPath] = content
        }

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String> {
        // Planner agents are not yet supported on WasmJS
        throw UnsupportedOperationException("Planner agents are not supported on the Ktor/WasmJS backend")
    }
}
```

- [ ] **Step 2: Compile and verify**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/backend/KtorWasmBackend.kt
git commit -m "feat: add KtorWasmBackend implementing GeneratorBackend"
```

---

### Task 7: Verify End-to-End with Banking Import

**Files:** None (verification only)

- [ ] **Step 1: Start backend**

Run: `./gradlew bootRun` and wait for startup.

- [ ] **Step 2: Test Spring Boot backend (regression)**

```bash
curl -s -X POST http://localhost:8080/api/vfs/banking-spring/generate \
  -H "Content-Type: application/json" \
  -d @docs/banking-import.json | python3 -m json.tool
```

Expected: `"status": "VALID"` with 13 files.

- [ ] **Step 3: Test generators endpoint**

```bash
curl -s http://localhost:8080/api/generators | python3 -m json.tool
```

Expected: Two entries — `spring-boot` and `ktor-wasm`.

- [ ] **Step 4: Test Ktor/Wasm backend generation**

```bash
curl -s -X POST http://localhost:8080/api/vfs/banking-wasm/generate \
  -H "Content-Type: application/json" \
  -d "$(python3 -c '
import json
graph = json.load(open("docs/banking-import.json"))
print(json.dumps({"graph": graph, "backend": "ktor-wasm"}))
')" | python3 -m json.tool
```

Expected: `"status": "VALID"` with files generated.

- [ ] **Step 5: Verify generated Wasm project structure**

```bash
curl -s http://localhost:8080/api/vfs/banking-wasm/tree | python3 -m json.tool
```

Expected: Files under `src/wasmJsMain/kotlin/generated/` with `Main.kt`, `ChatRoutes.kt`, `ToolRegistry.kt`, tools, and strategy files. `build.gradle.kts` should contain `kotlin("multiplatform")`.

- [ ] **Step 6: Verify generated Main.kt content**

```bash
curl -s "http://localhost:8080/api/vfs/banking-wasm/files?path=src/wasmJsMain/kotlin/generated/Main.kt"
```

Expected: Contains `embeddedServer`, `OpenAILLMClient`, `createBankingAssistantStrategy()`, Ktor imports.

- [ ] **Step 7: Verify Spring Boot export still works**

```bash
curl -s -X POST http://localhost:8080/api/build \
  -H "Content-Type: application/json" \
  -d "$(python3 -c '
import json
graph = json.load(open("docs/banking-import.json"))
print(json.dumps({"graph": graph, "platform": "jvm"}))
')" | python3 -c 'import json,sys; r=json.load(sys.stdin); print("SUCCESS" if r["success"] else "FAIL", len(r["errors"]), "errors")'
```

Expected: `SUCCESS 0 errors` (or only warnings).

- [ ] **Step 8: Commit verification notes if any fixes needed**

If all tests pass, no commit needed. If fixes are needed, fix and commit.
