# Design: Ktor/Wasm Generator Backend

**Date:** 2026-03-30
**Feature:** 08-wasm-codegen
**Scope:** Phase 1 (Backend-Abstraktion) + Phase 2 (Ktor/Wasm Generatoren)

## Goal

Introduce a `GeneratorBackend` interface so the `ExportService` can delegate infrastructure code generation to pluggable backends. Implement `KtorWasmBackend` as the second backend alongside the existing Spring Boot path.

## Key Insight

Koog supports WasmJS (KMP). Tools, Strategy DSL, and SharedCode are target-independent. Only infrastructure differs: Spring DI vs manual wiring, Spring MVC vs Ktor routing, JVM build vs KMP wasmJs build.

## Interface

```kotlin
interface GeneratorBackend {
    val id: String
    val displayName: String
    val supportedPlatforms: List<String>

    fun generateAgentFiles(agent: KoogAgent, graphName: String, sharedFiles: Map<String, String>): Map<String, String>
    fun generateGraphAgentFiles(agent: KoogGraphAgent, graphName: String, sharedFiles: Map<String, String>): Map<String, String>
    fun generatePlannerAgentFiles(agent: KoogPlannerAgent, graphName: String, sharedFiles: Map<String, String>): Map<String, String>
}
```

Three methods mirror the three code paths in the current `ExportService.generateFiles()`.

## Shared Generators (unchanged, reused by all backends)

- `ToolGenerator` — ToolSet with `@Tool` annotations (KMP-compatible)
- `GraphAgentGenerator` — Strategy DSL (KMP-compatible)
- `SharedCodeGenerator` — Domain code (plain Kotlin)
- `SubAgentGenerator`, `BuiltinToolGenerator`, `McpServerGenerator`
- `A2AServerGenerator`, `A2AClientGenerator`, `InputOutputGenerator`

## SpringBootBackend

Wraps existing generators: `AgentGenerator`, `ControllerGenerator`, `RegistryGenerator`, `BuildGradleGenerator`, `ApplicationGenerator`, `ApplicationPropertiesGenerator`, `DockerfileGenerator`, `SettingsGradleGenerator`, `GradlePropertiesGenerator`.

No behavioral change — just moves infrastructure generation behind the interface.

## KtorWasmBackend

New generators in `export/generator/ktor/`:

| Generator | Output |
|---|---|
| `KtorMainGenerator` | `main()` with Ktor `embeddedServer` + agent setup |
| `KtorControllerGenerator` | Ktor `routing { post("/api/chat") { } }` |
| `KtorRegistryGenerator` | Tool registry without Spring `@Bean` |
| `WasmBuildGradleGenerator` | `kotlin("multiplatform")` + wasmJs + Ktor deps |
| `WasmDockerfileGenerator` | Node.js Alpine image |
| `WasmSettingsGradleGenerator` | KMP settings |
| `WasmGradlePropertiesGenerator` | KMP gradle properties |

## ExportService Refactoring

Before:
```
ExportService(agentGenerator, controllerGenerator, buildGradleGenerator, ...)
```

After:
```
ExportService(toolGenerator, graphAgentGenerator, sharedCodeGenerator, ..., backends: List<GeneratorBackend>)
```

The `generateFiles()` method gains an optional `backendId` parameter (default: `"spring-boot"`). Shared file generation is extracted into a helper. Infrastructure generation is delegated to the selected backend.

## API Changes

All endpoints gain an optional `backend` query/body parameter:
- `POST /api/export` — `backend` in request body
- `POST /api/build` — `backend` in request body
- `POST /api/vfs/{name}/generate` — `backend` in request body
- `GET /api/generators` — new endpoint listing available backends

## Generated Project Structure (Ktor/Wasm)

```
build.gradle.kts                    ← kotlin("multiplatform") + wasmJs + Ktor
settings.gradle.kts
src/wasmJsMain/kotlin/generated/
  ├── Main.kt                       ← Ktor embeddedServer + agent wiring
  ├── ChatRoutes.kt                 ← Ktor routing
  ├── ToolRegistry.kt               ← plain function (no @Bean)
  ├── BankingAssistantStrategy.kt   ← identical to Spring output
  ├── tools/                        ← identical to Spring output
  └── code/                         ← identical to Spring output
Dockerfile                           ← Node.js Alpine
```

## Out of Scope

- Build validation for wasm platform (Phase 3)
- Frontend UI changes (Phase 3)
- Dockerfile.builder changes (Phase 3)
- Browser-embedding target
