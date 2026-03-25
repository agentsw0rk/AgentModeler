# OpenTelemetry Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional OpenTelemetry instrumentation to generated Kotlin code, configurable via a workspace-level Settings dialog in the frontend toolbar.

**Architecture:** Frontend adds `OtelConfig` to `WorkspaceGraph`, stored in `editorStore`, configured via a new `WorkspaceSettingsDialog`. Backend receives `OtelConfig`, passes it through the adapter to generators. `AgentGenerator` emits `install(OpenTelemetry) { ... }`, `BuildGradleGenerator` adds OTel dependencies, `ApplicationPropertiesGenerator` adds OTel properties. Koog handles all automatic instrumentation.

**Tech Stack:** Next.js (Base UI + Lucide), Zustand, Spring Boot (Kotlin), Koog OpenTelemetry feature, OpenTelemetry Java SDK

**Spec:** `docs/superpowers/specs/2026-03-25-opentelemetry-support-design.md`

---

### Task 1: Backend Data Model — Add `OtelConfig` to `GraphModel.kt` and `KoogModel.kt`

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`

- [ ] **Step 1: Add `OtelConfig` data class and field to `GraphModel.kt`**

At the end of `GraphModel.kt`, add:

```kotlin
data class OtelConfig(
    val enabled: Boolean = false,
    val exporterType: String = "http",
    val endpoint: String? = null,
    val serviceName: String? = null,
    val serviceVersion: String? = null,
    val authorizationHeader: String? = null,
    val verbose: Boolean = false
)
```

Add `val otelConfig: OtelConfig? = null` as the last parameter of `WorkspaceGraph`.

- [ ] **Step 2: Add `otelConfig` field to `KoogAgent` in `KoogModel.kt`**

Add import at top of `KoogModel.kt`:
```kotlin
// No import needed — OtelConfig is in the same package
```

Add `val otelConfig: OtelConfig? = null` as the last parameter of `KoogAgent`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat(otel): add OtelConfig data class to GraphModel and KoogModel"
```

---

### Task 2: Backend — Adapter passes `otelConfig` through

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `GraphToKoogAdapterTest.kt`:

```kotlin
@Test
fun `passes otelConfig from graph to KoogAgent`() {
    val otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces", serviceName = "test-svc")
    val graph = WorkspaceGraph(
        version = 1, name = "Test",
        nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai"))),
        edges = emptyList(),
        otelConfig = otelConfig
    )
    val agents = adapter.convert(graph)
    assertEquals(otelConfig, agents[0].otelConfig)
}

@Test
fun `otelConfig defaults to null when not provided`() {
    val graph = WorkspaceGraph(
        version = 1, name = "Test",
        nodes = listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai"))),
        edges = emptyList()
    )
    val agents = adapter.convert(graph)
    assertEquals(null, agents[0].otelConfig)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest" --no-daemon`
Expected: FAIL — `otelConfig` not set on `KoogAgent`

- [ ] **Step 3: Implement — pass `otelConfig` in `GraphToKoogAdapter.convert()`**

In `GraphToKoogAdapter.kt`, in the `KoogAgent(...)` constructor call inside the `agentNodes.map` lambda (around line 59-69), add `otelConfig = graph.otelConfig` as the last named argument:

```kotlin
KoogAgent(
    name = name,
    systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
    modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
    modelId = agentNode.data["modelId"] as? String,
    temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
    tools = tools,
    builtinTools = builtinTools,
    subAgents = subAgents,
    mcpServers = mcpServers,
    otelConfig = graph.otelConfig
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest" --no-daemon`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt
git commit -m "feat(otel): pass otelConfig through GraphToKoogAdapter"
```

---

### Task 3: Backend — `BuildGradleGenerator` adds OTel dependencies

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `BuildGradleGeneratorTest.kt`:

```kotlin
@Test
fun `includes OTel dependencies when otelConfig enabled with HTTP exporter`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "http"))
    val code = generator.generate(agent)
    assertContains(code, "opentelemetry-bom")
    assertContains(code, "koog-opentelemetry")
    assertContains(code, "opentelemetry-sdk")
    assertContains(code, "opentelemetry-exporter-otlp")
}

@Test
fun `includes logging exporter when otelConfig logging`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "logging"))
    val code = generator.generate(agent)
    assertContains(code, "opentelemetry-exporter-logging")
    assertFalse(code.contains("opentelemetry-exporter-otlp"))
}

@Test
fun `omits OTel dependencies when otelConfig disabled`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = false))
    val code = generator.generate(agent)
    assertFalse(code.contains("opentelemetry"))
    assertFalse(code.contains("koog-opentelemetry"))
}

@Test
fun `omits OTel dependencies when otelConfig null`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("opentelemetry"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: Implement OTel dependencies in `BuildGradleGenerator.kt`**

Add a companion constant:
```kotlin
const val OTEL_BOM_VERSION = "1.48.0"
```

In `generate()`, after the existing SQLite dependency block (after line 47), before `appendLine("""}""")`, add:

```kotlin
if (agent.otelConfig?.enabled == true) {
    appendLine("""    implementation(platform("io.opentelemetry:opentelemetry-bom:$OTEL_BOM_VERSION"))""")
    appendLine("""    implementation("ai.koog:koog-opentelemetry:$KOOG_VERSION")""")
    appendLine("""    implementation("io.opentelemetry:opentelemetry-sdk")""")
    when (agent.otelConfig?.exporterType) {
        "logging" -> appendLine("""    implementation("io.opentelemetry:opentelemetry-exporter-logging")""")
        else -> appendLine("""    implementation("io.opentelemetry:opentelemetry-exporter-otlp")""")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest" --no-daemon`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt
git commit -m "feat(otel): add OTel dependencies to BuildGradleGenerator"
```

---

### Task 4: Backend — `ApplicationPropertiesGenerator` adds OTel properties

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `ApplicationPropertiesGeneratorTest.kt`:

```kotlin
@Test
fun `generates OTel properties when enabled`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces",
            serviceName = "my-svc", serviceVersion = "1.0.0", verbose = true))
    val code = generator.generate(agent)
    assertContains(code, "otel.endpoint=http://localhost:4318/v1/traces")
    assertContains(code, "otel.service-name=my-svc")
    assertContains(code, "otel.service-version=1.0.0")
    assertContains(code, "otel.verbose=true")
}

@Test
fun `generates OTel properties with defaults when values are null`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true))
    val code = generator.generate(agent)
    assertContains(code, "otel.endpoint=http://localhost:4318/v1/traces")
    assertContains(code, "otel.service-name=ai.koog")
    assertContains(code, "otel.service-version=1.0.0")
}

@Test
fun `omits OTel properties when disabled`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = false))
    val code = generator.generate(agent)
    assertFalse(code.contains("otel."))
}

@Test
fun `omits OTel properties when otelConfig null`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("otel."))
}
```

Import `assertFalse` at top of test file if not already present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.ApplicationPropertiesGeneratorTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: Implement OTel properties in `ApplicationPropertiesGenerator.kt`**

In `generate()`, after the existing `appendLine` for api-key, add:

```kotlin
if (agent.otelConfig?.enabled == true) {
    val otel = agent.otelConfig!!
    appendLine()
    appendLine("# OpenTelemetry")
    if (otel.exporterType != "logging") {
        val defaultEndpoint = if (otel.exporterType == "grpc") "http://localhost:4317" else "http://localhost:4318/v1/traces"
        appendLine("otel.endpoint=${otel.endpoint ?: defaultEndpoint}")
    }
    appendLine("otel.service-name=${otel.serviceName ?: "ai.koog"}")
    appendLine("otel.service-version=${otel.serviceVersion ?: "1.0.0"}")
    otel.authorizationHeader?.let { appendLine("otel.authorization-header=$it") }
    appendLine("otel.verbose=${otel.verbose}")
}
```

Note: Endpoint is omitted for `logging` exporter since it is not used.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.ApplicationPropertiesGeneratorTest" --no-daemon`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGeneratorTest.kt
git commit -m "feat(otel): add OTel properties to ApplicationPropertiesGenerator"
```

---

### Task 5: Backend — `AgentGenerator` emits `install(OpenTelemetry)` block

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `AgentGeneratorTest.kt`:

```kotlin
@Test
fun `generates OTel install block when otelConfig enabled with HTTP exporter`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "http"))
    val code = generator.generate(agent)
    assertContains(code, "install(OpenTelemetry)")
    assertContains(code, "import ai.koog.agents.features.otel.OpenTelemetry")
    assertContains(code, "OtlpHttpSpanExporter")
    assertContains(code, "import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter")
    assertContains(code, "setServiceInfo(otelServiceName, otelServiceVersion)")
    assertContains(code, "@Value(\"\\\${otel.endpoint}\")")
}

@Test
fun `generates OTel install block with gRPC exporter`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "grpc"))
    val code = generator.generate(agent)
    assertContains(code, "install(OpenTelemetry)")
    assertContains(code, "OtlpGrpcSpanExporter")
    assertContains(code, "import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter")
}

@Test
fun `generates OTel install block with Logging exporter`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "logging"))
    val code = generator.generate(agent)
    assertContains(code, "install(OpenTelemetry)")
    assertContains(code, "LoggingSpanExporter")
    assertContains(code, "import io.opentelemetry.exporter.logging.LoggingSpanExporter")
    assertFalse(code.contains("otelEndpoint"))
}

@Test
fun `generates OTel with authorization header when set`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "http", authorizationHeader = "Bearer token123"))
    val code = generator.generate(agent)
    assertContains(code, ".addHeader(\"Authorization\", otelAuthHeader)")
    assertContains(code, "@Value(\"\\\${otel.authorization-header}\")")
}

@Test
fun `omits OTel install block when otelConfig disabled`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        otelConfig = OtelConfig(enabled = false))
    val code = generator.generate(agent)
    assertFalse(code.contains("OpenTelemetry"))
    assertFalse(code.contains("otel"))
}

@Test
fun `omits OTel install block when otelConfig null`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("OpenTelemetry"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: Implement OTel generation in `AgentGenerator.kt`**

The `AgentGenerator.generate()` method needs these changes. **Important:** The generator has TWO code paths for `AIAgent(...)` — one with `runBlocking` (when MCP servers are present, lines 63-73) and one without (lines 74-80). Both paths must be updated.

Extract a helper method `generateOtelInstallBlock()` to avoid duplication. Also extract `generateOtelImports()` and `generateOtelConstructorParams()`.

**a) Add OTel imports** — after existing imports section (around line 36-47), conditionally add OTel imports:

```kotlin
if (agent.otelConfig?.enabled == true) {
    appendLine("import ai.koog.agents.features.otel.OpenTelemetry")
    when (agent.otelConfig?.exporterType) {
        "http" -> appendLine("import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter")
        "grpc" -> appendLine("import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter")
        "logging" -> appendLine("import io.opentelemetry.exporter.logging.LoggingSpanExporter")
    }
    if (agent.otelConfig?.exporterType in listOf("http", "grpc")) {
        appendLine("import java.util.concurrent.TimeUnit")
    }
}
```

**b) Fix constructor parameter comma and add OTel `@Value` fields** — The existing `apiKey` `@Value` line (line 56) does NOT end with a comma. When OTel is enabled, the `apiKey` line must end with `,` to allow additional constructor params. Change the `apiKey` line generation to:

```kotlin
val apiKeyTrailingComma = if (agent.otelConfig?.enabled == true) "," else ""
appendLine("""    @Value("\${'$'}{${agent.modelProvider.name.lowercase()}.api-key}") private val apiKey: String$apiKeyTrailingComma""")
```

Then add the OTel `@Value` fields before the closing `)`:

```kotlin
if (agent.otelConfig?.enabled == true) {
    val otelParams = mutableListOf<String>()
    if (agent.otelConfig?.exporterType != "logging") {
        otelParams.add("""    @Value("\${'$'}{otel.endpoint}") private val otelEndpoint: String""")
    }
    otelParams.add("""    @Value("\${'$'}{otel.service-name}") private val otelServiceName: String""")
    otelParams.add("""    @Value("\${'$'}{otel.service-version}") private val otelServiceVersion: String""")
    if (agent.otelConfig?.authorizationHeader != null) {
        otelParams.add("""    @Value("\${'$'}{otel.authorization-header}") private val otelAuthHeader: String""")
    }
    otelParams.add("""    @Value("\${'$'}{otel.verbose}") private val otelVerbose: Boolean""")
    // Join with commas, last param has no comma
    otelParams.forEachIndexed { i, param ->
        val comma = if (i < otelParams.size - 1) "," else ""
        appendLine("$param$comma")
    }
}
```

**c) Add `installFeatures` trailing lambda to BOTH code paths** — Extract a helper method in `AgentGenerator`:

```kotlin
private fun StringBuilder.appendOtelInstallBlock(agent: KoogAgent) {
    if (agent.otelConfig?.enabled != true) return
    appendLine(" {")
    appendLine("        install(OpenTelemetry) {")
    appendLine("            setServiceInfo(otelServiceName, otelServiceVersion)")
    appendLine("            setVerbose(otelVerbose)")
    when (agent.otelConfig?.exporterType) {
        "http" -> {
            appendLine("            addSpanExporter(")
            appendLine("                OtlpHttpSpanExporter.builder()")
            appendLine("                    .setEndpoint(otelEndpoint)")
            appendLine("                    .setTimeout(30, TimeUnit.SECONDS)")
            if (agent.otelConfig?.authorizationHeader != null) {
                appendLine("                    .addHeader(\"Authorization\", otelAuthHeader)")
            }
            appendLine("                    .build()")
            appendLine("            )")
        }
        "grpc" -> {
            appendLine("            addSpanExporter(")
            appendLine("                OtlpGrpcSpanExporter.builder()")
            appendLine("                    .setEndpoint(otelEndpoint)")
            if (agent.otelConfig?.authorizationHeader != null) {
                appendLine("                    .addHeader(\"Authorization\", otelAuthHeader)")
            }
            appendLine("                    .build()")
            appendLine("            )")
        }
        "logging" -> {
            appendLine("            addSpanExporter(LoggingSpanExporter.create())")
        }
    }
    appendLine("        }")
    appendLine("    }")
}
```

Then in BOTH the `runBlocking` path (MCP servers present) and the non-runBlocking path, call `appendOtelInstallBlock(agent)` after the closing `)` of the `AIAgent(` constructor call.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest" --no-daemon`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat(otel): generate install(OpenTelemetry) block in AgentGenerator"
```

---

### Task 6: Backend — `ExportService` integration test with OTel

**Files:**
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Write the integration test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `export with OTel enabled includes OTel configuration in generated files`() {
    val graph = WorkspaceGraph(1, "OtelProject",
        listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))),
        emptyList(),
        otelConfig = OtelConfig(enabled = true, exporterType = "http", endpoint = "http://localhost:4318/v1/traces", serviceName = "my-svc")
    )
    val (_, files) = service.exportWithFiles(graph)

    // build.gradle.kts has OTel deps
    assertContains(files["build.gradle.kts"]!!, "koog-opentelemetry")
    assertContains(files["build.gradle.kts"]!!, "opentelemetry-bom")

    // AgentConfig.kt has install block
    assertContains(files["src/main/kotlin/generated/AgentConfig.kt"]!!, "install(OpenTelemetry)")
    assertContains(files["src/main/kotlin/generated/AgentConfig.kt"]!!, "OtlpHttpSpanExporter")

    // application.properties has OTel props
    assertContains(files["src/main/resources/application.properties"]!!, "otel.endpoint=http://localhost:4318/v1/traces")
    assertContains(files["src/main/resources/application.properties"]!!, "otel.service-name=my-svc")
}

@Test
fun `export without OTel has no OTel artifacts`() {
    val graph = WorkspaceGraph(1, "NoOtelProject",
        listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))),
        emptyList()
    )
    val (_, files) = service.exportWithFiles(graph)
    assertTrue(!files["build.gradle.kts"]!!.contains("opentelemetry"))
    assertTrue(!files["src/main/kotlin/generated/AgentConfig.kt"]!!.contains("OpenTelemetry"))
    assertTrue(!files["src/main/resources/application.properties"]!!.contains("otel."))
}
```

Note: Use `assertTrue(!expr)` pattern to match existing `ExportServiceTest` style (no `assertFalse` import).

- [ ] **Step 2: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest" --no-daemon`
Expected: ALL PASS (these tests should pass since the generators are already implemented)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "test(otel): add ExportService integration tests for OTel"
```

---

### Task 7: Backend — Run full test suite to verify no regressions

**Files:** None (verification only)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --no-daemon`
Expected: ALL existing tests PASS (the new `otelConfig` field defaults to `null`, so all existing `KoogAgent(...)` constructors still work via default parameter). Note: 2 pre-existing failures in MCP export tests are known and expected.

---

### Task 8: Frontend — Add `OtelConfig` type and `buildWorkspaceGraph` helper

**Files:**
- Modify: `frontend/types/graph.ts`
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Add `OtelConfig` interface to `types/graph.ts`**

Before the `WorkspaceGraph` interface, add:

```ts
export type OtelExporterType = "http" | "grpc" | "logging";

export interface OtelConfig {
  enabled: boolean;
  exporterType: OtelExporterType;
  endpoint?: string;
  serviceName?: string;
  serviceVersion?: string;
  authorizationHeader?: string;
  verbose: boolean;
}
```

Add `otelConfig?: OtelConfig;` as the last field of `WorkspaceGraph`.

- [ ] **Step 2: Add `otelConfig` state and `buildWorkspaceGraph` to `editorStore.ts`**

Add import at top:
```ts
import { OtelConfig } from "@/types/graph";
```

Add to `EditorState` interface:
```ts
otelConfig: OtelConfig;
updateOtelConfig: (partial: Partial<OtelConfig>) => void;
```

Add default config constant before the store:
```ts
const DEFAULT_OTEL_CONFIG: OtelConfig = {
  enabled: false,
  exporterType: "http",
  verbose: false,
};
```

Add to the store's initial state:
```ts
otelConfig: { ...DEFAULT_OTEL_CONFIG },
```

Add action:
```ts
updateOtelConfig(partial) {
  set((state) => ({
    otelConfig: { ...state.otelConfig, ...partial },
  }));
},
```

Update `reset()` to also reset `otelConfig`:
```ts
otelConfig: { ...DEFAULT_OTEL_CONFIG },
```

Add exported helper function **outside** the store:
```ts
export function buildWorkspaceGraph(): WorkspaceGraph {
  const { nodes, edges, workspaceName, otelConfig } = useEditorStore.getState();
  return {
    version: 1,
    name: workspaceName,
    nodes,
    edges,
    otelConfig: otelConfig.enabled ? otelConfig : undefined,
  };
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/types/graph.ts frontend/store/editorStore.ts
git commit -m "feat(otel): add OtelConfig type, store field, and buildWorkspaceGraph helper"
```

---

### Task 9: Frontend — Update all `WorkspaceGraph` construction sites to use `buildWorkspaceGraph`

**Files:**
- Modify: `frontend/components/dialogs/ExportDialog.tsx`
- Modify: `frontend/components/dialogs/SaveLoadDialog.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`
- Modify: `frontend/hooks/useCodeGeneration.ts`

- [ ] **Step 1: Update `ExportDialog.tsx`**

Add import:
```ts
import { buildWorkspaceGraph } from "@/store/editorStore";
```

In `handleExportAndReview()` (line 28), replace:
```ts
const graph: WorkspaceGraph = { version: 1, name: workspaceName, nodes, edges };
```
with:
```ts
const graph = buildWorkspaceGraph();
```

In `handleExport()` (line 77), replace the same pattern with:
```ts
const graph = buildWorkspaceGraph();
```

Remove unused `nodes`, `edges`, `workspaceName` store subscriptions if they are no longer used elsewhere in the component (check first — `workspaceName` is still used for the download filename on line 93/61).

- [ ] **Step 2: Update `SaveLoadDialog.tsx`**

Add import:
```ts
import { buildWorkspaceGraph } from "@/store/editorStore";
```

In `handleSave()` (line 18), replace:
```ts
const graph: WorkspaceGraph = { version: 1, name: workspaceName, nodes, edges };
```
with:
```ts
const graph = buildWorkspaceGraph();
```

In `handleLoad()` (line 31-34), also restore `otelConfig`. Import `DEFAULT_OTEL_CONFIG` from `editorStore`:
```ts
import { useEditorStore, DEFAULT_OTEL_CONFIG } from "@/store/editorStore";
```

Update the `setState` call:
```ts
useEditorStore.setState({
  nodes: graph.nodes,
  edges: graph.edges,
  workspaceName: graph.name,
  otelConfig: graph.otelConfig ?? { ...DEFAULT_OTEL_CONFIG },
});
```

Note: Export `DEFAULT_OTEL_CONFIG` from `editorStore.ts` (make it `export const`).

- [ ] **Step 3: Update `Toolbar.tsx`**

Add import:
```ts
import { buildWorkspaceGraph } from "@/store/editorStore";
```

In `handleValidate()` (line 53), replace:
```ts
const graph: WorkspaceGraph = { version: 1, name: workspaceName, nodes, edges };
```
with:
```ts
const graph = buildWorkspaceGraph();
```

- [ ] **Step 4: Update `useCodeGeneration.ts`**

In the change detection subscription (line 15-18), add `otelConfig`:
```ts
const changed =
  state.nodes !== prev.nodes ||
  state.edges !== prev.edges ||
  state.workspaceName !== prev.workspaceName ||
  state.otelConfig !== prev.otelConfig;
```

In `triggerGeneration()` (line 62-80), change the signature to accept the full state and use `buildWorkspaceGraph`:

Replace the function signature and body. Import `buildWorkspaceGraph`:
```ts
import { useEditorStore, buildWorkspaceGraph } from "@/store/editorStore";
```

Simplify `triggerGeneration` to read from the store directly instead of accepting state as params:

Replace the function signature (line 62-66):
```ts
async function triggerGeneration(
  abortRef: React.MutableRefObject<AbortController | null>,
) {
```

Replace the manual graph construction (lines 75-80):
```ts
const graph = buildWorkspaceGraph();
const workspaceName = useEditorStore.getState().workspaceName;
```

Update both call sites:
- Line 37 (debounce callback): `triggerGeneration(abortRef)` — remove `state.workspaceName, state.nodes, state.edges` args
- Line 54 (panel open handler): `triggerGeneration(abortRef)` — remove `editorState.workspaceName, editorState.nodes, editorState.edges` args

Note: After debounce, `buildWorkspaceGraph()` reads the latest store state via `getState()`. This is intentionally correct — we always want to generate with the most recent state, not the stale snapshot from when the debounce was scheduled.

- [ ] **Step 5: Commit**

```bash
git add frontend/components/dialogs/ExportDialog.tsx frontend/components/dialogs/SaveLoadDialog.tsx frontend/components/panels/Toolbar.tsx frontend/hooks/useCodeGeneration.ts
git commit -m "refactor(otel): use buildWorkspaceGraph at all construction sites"
```

---

### Task 10: Frontend — Create `WorkspaceSettingsDialog` component

**Files:**
- Create: `frontend/components/dialogs/WorkspaceSettingsDialog.tsx`

- [ ] **Step 1: Create the dialog component**

Create `frontend/components/dialogs/WorkspaceSettingsDialog.tsx`:

```tsx
"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { OtelExporterType } from "@/types/graph";
import { SettingsIcon } from "lucide-react";

const ENDPOINT_PLACEHOLDERS: Record<OtelExporterType, string> = {
  http: "http://localhost:4318/v1/traces",
  grpc: "http://localhost:4317",
  logging: "",
};

export function WorkspaceSettingsDialog() {
  const [open, setOpen] = useState(false);
  const otelConfig = useEditorStore((s) => s.otelConfig);
  const updateOtelConfig = useEditorStore((s) => s.updateOtelConfig);

  const showEndpoint = otelConfig.exporterType !== "logging";
  const showAuth = otelConfig.exporterType !== "logging";

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <SettingsIcon className="size-4" />
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Workspace Settings</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <Label htmlFor="otel-enabled">OpenTelemetry</Label>
            <button
              id="otel-enabled"
              role="switch"
              aria-checked={otelConfig.enabled}
              onClick={() => updateOtelConfig({ enabled: !otelConfig.enabled })}
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                otelConfig.enabled ? "bg-blue-600" : "bg-muted"
              }`}
            >
              <span
                className={`inline-block size-4 transform rounded-full bg-white transition-transform ${
                  otelConfig.enabled ? "translate-x-6" : "translate-x-1"
                }`}
              />
            </button>
          </div>

          {otelConfig.enabled && (
            <div className="space-y-4 border-t pt-4">
              <div>
                <Label>Exporter Type</Label>
                <Select
                  value={otelConfig.exporterType}
                  onValueChange={(v) => updateOtelConfig({ exporterType: v as OtelExporterType })}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="http">HTTP (OTLP)</SelectItem>
                    <SelectItem value="grpc">gRPC (OTLP)</SelectItem>
                    <SelectItem value="logging">Logging (Console)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {showEndpoint && (
                <div>
                  <Label htmlFor="otel-endpoint">Endpoint</Label>
                  <Input
                    id="otel-endpoint"
                    value={otelConfig.endpoint ?? ""}
                    onChange={(e) => updateOtelConfig({ endpoint: e.target.value || undefined })}
                    placeholder={ENDPOINT_PLACEHOLDERS[otelConfig.exporterType]}
                  />
                </div>
              )}

              <div>
                <Label htmlFor="otel-service-name">Service Name</Label>
                <Input
                  id="otel-service-name"
                  value={otelConfig.serviceName ?? ""}
                  onChange={(e) => updateOtelConfig({ serviceName: e.target.value || undefined })}
                  placeholder="my-agent-service"
                />
              </div>

              <div>
                <Label htmlFor="otel-service-version">Service Version</Label>
                <Input
                  id="otel-service-version"
                  value={otelConfig.serviceVersion ?? ""}
                  onChange={(e) => updateOtelConfig({ serviceVersion: e.target.value || undefined })}
                  placeholder="1.0.0"
                />
              </div>

              {showAuth && (
                <div>
                  <Label htmlFor="otel-auth">Authorization Header (optional)</Label>
                  <Input
                    id="otel-auth"
                    value={otelConfig.authorizationHeader ?? ""}
                    onChange={(e) => updateOtelConfig({ authorizationHeader: e.target.value || undefined })}
                    placeholder="Bearer ..."
                  />
                </div>
              )}

              <div className="flex items-center justify-between">
                <div>
                  <Label htmlFor="otel-verbose">Verbose Mode</Label>
                  <p className="text-xs text-muted-foreground">Show LLM messages in cleartext</p>
                </div>
                <button
                  id="otel-verbose"
                  role="switch"
                  aria-checked={otelConfig.verbose}
                  onClick={() => updateOtelConfig({ verbose: !otelConfig.verbose })}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    otelConfig.verbose ? "bg-blue-600" : "bg-muted"
                  }`}
                >
                  <span
                    className={`inline-block size-4 transform rounded-full bg-white transition-transform ${
                      otelConfig.verbose ? "translate-x-6" : "translate-x-1"
                    }`}
                  />
                </button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/components/dialogs/WorkspaceSettingsDialog.tsx
git commit -m "feat(otel): add WorkspaceSettingsDialog component"
```

---

### Task 11: Frontend — Add Settings button to Toolbar

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Import and add the dialog**

Add import:
```ts
import { WorkspaceSettingsDialog } from "@/components/dialogs/WorkspaceSettingsDialog";
```

In the JSX, add `<WorkspaceSettingsDialog />` in the right-side button group, between `<SaveLoadDialog />` and `<ExportDialog />` (around line 149-150):

```tsx
<SaveLoadDialog />
<WorkspaceSettingsDialog />
<ExportDialog />
```

- [ ] **Step 2: Verify the frontend compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx
git commit -m "feat(otel): add Settings button to Toolbar"
```

---

### Task 12: Verification — End-to-end check

**Files:** None (verification only)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --no-daemon`
Expected: All tests pass (2 pre-existing MCP failures allowed)

- [ ] **Step 2: Run frontend build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build`
Expected: Build succeeds

- [ ] **Step 3: Manual smoke test (if dev server is available)**

1. Start backend + frontend
2. Open browser, create an agent
3. Click Settings (gear) icon in toolbar
4. Toggle OpenTelemetry on
5. Set exporter to HTTP, endpoint to `http://localhost:4318/v1/traces`
6. Export — verify the ZIP contains `install(OpenTelemetry)` in `AgentConfig.kt`, OTel deps in `build.gradle.kts`, OTel props in `application.properties`
7. Toggle OpenTelemetry off, export again — verify no OTel artifacts
8. Save workspace, reload — verify OTel settings are preserved
