# OpenTelemetry Support for Koog Agent Modeler

## Overview

Add optional OpenTelemetry instrumentation to the generated Kotlin code. Users configure OTel settings at workspace level via a Settings dialog in the toolbar. When enabled, the generated agent code includes Koog's `install(OpenTelemetry)` feature with the selected exporter, service info, and verbose mode. When disabled, the generated code is unchanged.

## Data Model

### Frontend (`types/graph.ts`)

```ts
export interface OtelConfig {
  enabled: boolean;
  exporterType: "http" | "grpc" | "logging";
  endpoint?: string;
  serviceName?: string;
  serviceVersion?: string;
  authorizationHeader?: string;
  verbose: boolean;
}

export interface WorkspaceGraph {
  version: 1;
  name: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  otelConfig?: OtelConfig;
}
```

Default values: `{ enabled: false, exporterType: "http", verbose: false }`.

### Single definition of `OtelConfig`

`OtelConfig` is defined once in `GraphModel.kt` on the backend and once in `types/graph.ts` on the frontend. Both `WorkspaceGraph` and `KoogAgent` reference the same backend `OtelConfig` class (no duplication). The adapter copies `graph.otelConfig` to every `KoogAgent` in the list (in practice there is always one agent, but this keeps it consistent).

### Backend (`export/model/GraphModel.kt`)

```kotlin
data class OtelConfig(
    val enabled: Boolean = false,
    val exporterType: String = "http",   // "http" | "grpc" | "logging"
    val endpoint: String? = null,
    val serviceName: String? = null,
    val serviceVersion: String? = null,
    val authorizationHeader: String? = null,
    val verbose: Boolean = false
)
```

Added as optional field `otelConfig: OtelConfig? = null` to `WorkspaceGraph`.

### Koog Model (`export/model/KoogModel.kt`)

`KoogAgent` gets a new field `otelConfig: OtelConfig? = null` (importing from `GraphModel.kt`). The `GraphToKoogAdapter` copies `graph.otelConfig` to each `KoogAgent`.

## Frontend UI

### `buildWorkspaceGraph()` Helper

To avoid silently dropping `otelConfig` at the 5+ sites that manually construct `WorkspaceGraph`, introduce a shared helper in `store/editorStore.ts`:

```ts
export function buildWorkspaceGraph(state: EditorState): WorkspaceGraph {
  return {
    version: 1,
    name: state.workspaceName,
    nodes: state.nodes,
    edges: state.edges,
    otelConfig: state.otelConfig,
  };
}
```

All existing construction sites must be updated to use this helper:

| File | Location |
|------|----------|
| `components/dialogs/ExportDialog.tsx` | `handleExport` and `handleExportAndReview` |
| `components/dialogs/SaveLoadDialog.tsx` | save handler |
| `components/panels/Toolbar.tsx` | `handleValidate` |
| `hooks/useCodeGeneration.ts` | `triggerGeneration` |

### Settings Dialog (`WorkspaceSettingsDialog.tsx`) — New File

- Opened via a gear icon button in the Toolbar
- Contains an "OpenTelemetry" section with:
  - **Enabled** toggle — when off, remaining fields are hidden
  - **Exporter Type** dropdown — `HTTP (OTLP)`, `gRPC (OTLP)`, `Logging (Console)`
  - **Endpoint** text input — dynamic placeholder based on exporter type:
    - HTTP: `http://localhost:4318/v1/traces`
    - gRPC: `http://localhost:4317`
    - Logging: hidden (not needed)
  - **Service Name** text input — placeholder `my-agent-service`
  - **Service Version** text input — placeholder `1.0.0`
  - **Authorization Header** text input (optional) — placeholder `Basic ...` or `Bearer ...`. Hidden for Logging exporter.
  - **Verbose Mode** toggle — shows LLM messages in cleartext instead of masked

### Editor Store Changes (`store/editorStore.ts`)

- New state field: `otelConfig: OtelConfig` (default: `{ enabled: false, exporterType: "http", verbose: false }`)
- New action: `updateOtelConfig(partial: Partial<OtelConfig>)`
- `reset()` also resets `otelConfig` to defaults
- New helper: `buildWorkspaceGraph()` (see above)

### Toolbar Changes (`components/panels/Toolbar.tsx`)

- Add a Settings (gear) icon button that opens `WorkspaceSettingsDialog`

### History Store

OTel config changes are **not** tracked in undo/redo history. This is intentional: OTel settings are workspace-level configuration, not canvas operations. The `historyStore` `Snapshot` type is not modified.

### Code Generation Hook (`hooks/useCodeGeneration.ts`)

The subscription change detection must include `otelConfig`:
```ts
const changed =
  state.nodes !== prev.nodes ||
  state.edges !== prev.edges ||
  state.workspaceName !== prev.workspaceName ||
  state.otelConfig !== prev.otelConfig;
```

### Persistence

`otelConfig` is part of `WorkspaceGraph`, so it is automatically persisted with save/load via `buildWorkspaceGraph()`.

## Backend Code Generation

### AgentGenerator

When `otelConfig?.enabled == true`, the generated `AgentConfig.kt` includes:

1. Additional imports for OTel classes
2. `@Value` fields for OTel properties from `application.properties`
3. An `installFeatures` block on the `AIAgent` constructor with `install(OpenTelemetry) { ... }`

The install block configures:
- `setServiceInfo(serviceName, serviceVersion)`
- `setVerbose(verbose)`
- `addSpanExporter(...)` based on exporter type

Exporter selection:
- `http`: `OtlpHttpSpanExporter.builder().setEndpoint(...).setTimeout(30, SECONDS).build()`
- `grpc`: `OtlpGrpcSpanExporter.builder().setEndpoint(...).build()`
- `logging`: `LoggingSpanExporter.create()`

If `authorizationHeader` is set (HTTP/gRPC), `.addHeader("Authorization", ...)` is included.

When `otelConfig` is null or `enabled == false`, no OTel code is generated.

### BuildGradleGenerator

When OTel enabled, add dependencies using the OpenTelemetry BOM for version alignment:

```kotlin
implementation(platform("io.opentelemetry:opentelemetry-bom:1.48.0"))
implementation("ai.koog:koog-opentelemetry:$KOOG_VERSION")
implementation("io.opentelemetry:opentelemetry-sdk")
```

Exporter-specific:
- HTTP: `implementation("io.opentelemetry:opentelemetry-exporter-otlp")`
- gRPC: `implementation("io.opentelemetry:opentelemetry-exporter-otlp")` (same artifact, gRPC transport included)
- Logging: `implementation("io.opentelemetry:opentelemetry-exporter-logging")`

### ApplicationPropertiesGenerator

When OTel enabled, generate properties with user-configured values or sensible defaults:
```properties
# OpenTelemetry
otel.endpoint=http://localhost:4318/v1/traces
otel.service-name=my-agent-service
otel.service-version=1.0.0
otel.authorization-header=
otel.verbose=false
```

## Instrumentation Coverage

Koog's OpenTelemetry feature automatically instruments:
- Agent execution start/end (`CreateAgentSpan`, `InvokeAgentSpan`)
- Strategy execution (`StrategySpan`)
- Node execution (`NodeExecuteSpan`)
- Subgraph execution (`SubgraphExecuteSpan`)
- LLM calls (`InferenceSpan`)
- Tool calls (`ExecuteToolSpan`)
- MCP operations (`McpClientSpan`)

No manual instrumentation in generated classes is needed. All spans, attributes, and events are handled by the Koog OTel feature.

## Tests

| Test File | Scenario |
|-----------|----------|
| `AgentGeneratorTest` | OTel enabled: output contains `install(OpenTelemetry)`, imports, exporter config. OTel disabled: none of these present. |
| `BuildGradleGeneratorTest` | OTel enabled: OTel dependencies + BOM present. Disabled: absent. |
| `ApplicationPropertiesGeneratorTest` | OTel enabled: OTel properties generated. Disabled: absent. |
| `GraphToKoogAdapterTest` | `otelConfig` passed through from graph to `KoogAgent`. |
| `ExportServiceTest` | Full export with OTel produces correct file set. |

## Files Changed

| Layer | File | Change |
|-------|------|--------|
| Frontend Types | `types/graph.ts` | Add `OtelConfig` interface, add `otelConfig?` to `WorkspaceGraph` |
| Frontend Store | `store/editorStore.ts` | Add `otelConfig` state, `updateOtelConfig` action, `buildWorkspaceGraph()` helper |
| Frontend UI | `components/dialogs/WorkspaceSettingsDialog.tsx` | **New** — Settings dialog with OTel config |
| Frontend UI | `components/panels/Toolbar.tsx` | Add Settings button |
| Frontend UI | `components/dialogs/ExportDialog.tsx` | Use `buildWorkspaceGraph()` |
| Frontend UI | `components/dialogs/SaveLoadDialog.tsx` | Use `buildWorkspaceGraph()` |
| Frontend Hook | `hooks/useCodeGeneration.ts` | Use `buildWorkspaceGraph()`, watch `otelConfig` changes |
| Backend Model | `export/model/GraphModel.kt` | Add `OtelConfig` data class, add to `WorkspaceGraph` |
| Backend Model | `export/model/KoogModel.kt` | Add `otelConfig: OtelConfig?` to `KoogAgent` |
| Backend Adapter | `export/adapter/GraphToKoogAdapter.kt` | Copy `graph.otelConfig` to each `KoogAgent` |
| Backend Generator | `export/generator/AgentGenerator.kt` | Generate OTel install block |
| Backend Generator | `export/generator/BuildGradleGenerator.kt` | Add OTel dependencies + BOM |
| Backend Generator | `export/generator/ApplicationPropertiesGenerator.kt` | Add OTel properties |
| Backend Tests | 5 test files | OTel enabled/disabled scenarios |
