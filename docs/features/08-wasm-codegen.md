# Feature 08: Ktor/Wasm Code Generator (zweiter Generator-Backend)

## Problem

Der Modeler hat aktuell genau einen Codegen-Pfad: **Spring Boot + JVM**. Alle Generatoren (`AgentGenerator`, `ControllerGenerator`, `BuildGradleGenerator`, etc.) sind direkt im `ExportService` verdrahtet und erzeugen ausschließlich Spring-Boot-Code. Ein zweites Target (z.B. WasmJS) erfordert alternative Generatoren für Infrastruktur-Code, aber die bestehende Architektur bietet keine Schnittstelle dafür.

Zusätzlich erzeugen JVM/Native-Targets große Container-Images (200-400 MB) mit langsamen Kaltstarts. Für Edge-Deployments, Serverless und Browser-Embedding ist das ungeeignet.

## Ziel

1. **Generator-Backend-Abstraktion** — eine Schnittstelle, über die verschiedene Codegen-Backends (Spring Boot, Ktor/Wasm, zukünftig weitere) angesprochen werden können
2. **Ktor/WasmJS-Backend** — ein zweiter Generator, der Ktor als HTTP-Framework nutzt und auf Kotlin/WasmJS kompiliert

## Voraussetzungen

| Abhängigkeit | Status | Blocker? |
|---|---|---|
| Kotlin/Wasm (WasmJS) | Stable seit Kotlin 2.0 | Nein |
| Koog WasmJS-Target | **Supported** (JVM, JS, WasmJS, iOS) | Nein |
| Ktor Server WasmJS | Supported (experimentell) | Nein |
| Ktor Client WasmJS | Supported | Nein |
| kotlinx-coroutines Wasm | Supported | Nein |
| kotlinx-serialization Wasm | Supported | Nein |

**Kein Blocker:** Koog ist ein Kotlin Multiplatform Projekt mit WasmJS-Artefakten. Koog Agent DSL, ToolSet, Strategy-Builder und LLM-Clients sind direkt auf WasmJS nutzbar.

## Architektur

### Generator-Backend-Schnittstelle

Heute ist der `ExportService` monolithisch — er kennt jeden einzelnen Generator direkt:

```
ExportService → AgentGenerator, ToolGenerator, ControllerGenerator, BuildGradleGenerator, ...
```

**Neu:** Eine `GeneratorBackend`-Abstraktion kapselt die infrastrukturspezifischen Generatoren. Geteilte Generatoren (Tools, Strategy, SharedCode) bleiben zentral.

```
ExportService
  ├── Shared Generators (wiederverwendbar)
  │   ├── ToolGenerator            ← ToolSet-Code ist KMP-kompatibel
  │   ├── GraphAgentGenerator      ← Strategy DSL ist KMP-kompatibel
  │   ├── SharedCodeGenerator      ← Domain-Code ist plain Kotlin
  │   ├── SubAgentGenerator
  │   ├── BuiltinToolGenerator
  │   ├── McpServerGenerator
  │   ├── A2AServerGenerator
  │   ├── A2AClientGenerator
  │   └── InputOutputGenerator
  │
  └── GeneratorBackend (Interface)
      ├── SpringBootBackend (bestehend, default)
      │   ├── AgentGenerator         ← @Bean, @Configuration
      │   ├── ControllerGenerator    ← @RestController, @PostMapping
      │   ├── RegistryGenerator      ← Spring @Bean
      │   ├── BuildGradleGenerator   ← kotlin("jvm"), Spring Boot Plugin
      │   ├── ApplicationGenerator   ← @SpringBootApplication
      │   ├── ApplicationPropertiesGenerator
      │   ├── DockerfileGenerator    ← JDK 21 Base Image
      │   └── SettingsGradleGenerator
      │
      └── KtorWasmBackend (neu)
          ├── KtorAgentGenerator     ← manuelles Wiring, kein DI
          ├── KtorControllerGenerator← Ktor routing { post("/api/chat") }
          ├── KtorRegistryGenerator  ← ohne Spring @Bean
          ├── WasmBuildGradleGenerator ← kotlin("multiplatform") + wasmJs
          ├── WasmMainGenerator      ← fun main() + Ktor embeddedServer
          ├── WasmDockerfileGenerator← Node.js Alpine
          └── WasmSettingsGradleGenerator
```

### Interface-Definition

```kotlin
interface GeneratorBackend {
    val id: String                          // "spring-boot" | "ktor-wasm"
    val displayName: String                 // "Spring Boot (JVM)" | "Ktor (WasmJS)"
    val supportedPlatforms: List<String>    // ["jvm"] | ["wasm"] | ["jvm", "wasm"]

    fun generateInfrastructure(
        agent: KoogAgent,           // oder KoogGraphAgent / KoogPlannerAgent
        graphName: String,
        sharedFiles: Map<String, String>  // bereits generierte Tool/Strategy-Dateien
    ): Map<String, String>                // Pfad → Dateiinhalt
}
```

Der `ExportService` wird refactored:

```kotlin
@Service
class ExportService(
    // Shared generators (unchanged)
    private val toolGenerator: ToolGenerator,
    private val graphAgentGenerator: GraphAgentGenerator,
    private val sharedCodeGenerator: SharedCodeGenerator,
    // ...

    // Backend selection
    private val backends: List<GeneratorBackend>  // Spring-injected
) {
    fun generateFiles(graph: WorkspaceGraph, backendId: String = "spring-boot"): Map<String, String> {
        val backend = backends.first { it.id == backendId }
        val sharedFiles = generateSharedFiles(graph)  // Tools, Strategy, Domain
        val infraFiles = backend.generateInfrastructure(agent, graph.name, sharedFiles)
        return sharedFiles + infraFiles
    }
}
```

### API-Erweiterung

```
POST /api/export          → Body: { graph, backend: "spring-boot" | "ktor-wasm" }
POST /api/build           → Body: { graph, platform: "jvm" | "native" | "wasm", backend: "ktor-wasm" }
GET  /api/generators      → Liste verfügbarer Backends mit Metadaten
POST /api/vfs/{name}/generate → Body: { graph, backend: "ktor-wasm" }
```

### Warum Ktor statt rohes Node.js

| | Raw Node.js `require("http")` | Ktor Server |
|---|---|---|
| Typsicherheit | `dynamic` — kein Compiler-Check | Voll typisiert |
| Routing | Manuell | `routing { post { } }` DSL |
| Content Negotiation | Manuell (JSON parse/serialize) | Plugin: `ContentNegotiation` |
| KMP-Kompatibilität | Nur WasmJS/JS | JVM + WasmJS + Native |
| Wiederverwendung | Keiner der JVM-Controller-Logik | Controller-Logik portierbar |
| Testbarkeit | Schwer | `testApplication { }` API |

Mit Ktor als HTTP-Framework könnte langfristig auch der JVM-Pfad von Spring auf Ktor migriert werden — dann gäbe es **einen** Controller-Generator für alle Targets.

## Was sich gegenüber JVM ändert

Da Koog auf WasmJS läuft, bleibt der generierte Agent-Code **nahezu identisch**. Nur die Infrastruktur-Schicht ändert sich:

| Spring Boot (JVM) | Ktor (WasmJS) |
|---|---|
| Spring Boot WebFlux | Ktor `embeddedServer(Node, 8080)` |
| Spring DI (`@Bean`, `@Configuration`) | `main()` mit manuellem Wiring |
| `@RestController` + `@PostMapping` | `routing { post("/api/chat") { } }` |
| `koog-spring-boot-starter` | `koog-agents` direkt (KMP-Artefakt) |
| `application.properties` | Umgebungsvariablen |
| JDK 21 Runtime (~200 MB) | Node.js (~50 MB) oder wasmtime (~10 MB) |

### Was gleich bleibt (Shared Generators)

- **ToolSet mit `@Tool` Annotations** — identisch
- **Strategy DSL** (`strategy<>`, `subgraph`, `node`, `edge`) — identisch
- **LLM-Clients** — Koog OpenAI/Anthropic/Google sind KMP-kompatibel
- **ToolRegistry-Logik** — gleiche Registrierung (nur ohne `@Bean` Wrapper)
- **Shared Code (Domain.kt etc.)** — unverändert

## Generierter Code (Ktor/WasmJS)

### Main.kt

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.node.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun main() {
    val client = OpenAILLMClient(getEnv("OPENAI_API_KEY"))
    val executor = SingleLLMPromptExecutor(client)
    val toolRegistry = createToolRegistry()

    val agent = AIAgent(
        executor = executor,
        strategy = createBankingAssistantStrategy(),
        systemPrompt = "You are a banking assistant...",
        llmModel = OpenAIModels.Chat.GPT4o,
        installToolSets = { toolRegistry.installAll(this) }
    )

    embeddedServer(Node, port = 8080) {
        routing {
            post("/api/chat") {
                val input = call.receiveText()
                val result = agent.run(input)
                call.respondText(result)
            }
        }
    }.start(wait = true)
}
```

### build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

kotlin {
    wasmJs {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation("ai.koog:koog-agents:0.7.2")
            implementation("io.ktor:ktor-server-core:3.1.3")
            implementation("io.ktor:ktor-server-node:3.1.3")
            implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}
```

### Dockerfile

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY build/js/packages/agent/kotlin/ .
EXPOSE 8080
CMD ["node", "agent.mjs"]
```

### Projektstruktur

```
build.gradle.kts              ← kotlin("multiplatform") + wasmJs + Ktor
settings.gradle.kts
src/wasmJsMain/kotlin/
  ├── Main.kt                 ← Ktor embeddedServer + Agent-Setup
  ├── ToolRegistry.kt         ← Tool-Registrierung (ohne @Bean)
  ├── BankingAssistantStrategy.kt  ← identisch zum Spring-Output
  ├── tools/
  │   ├── MoneyTransferTools.kt    ← identisch zum Spring-Output
  │   └── AnalyticsTools.kt       ← identisch zum Spring-Output
  └── code/
      └── Domain.kt               ← identisch zum Spring-Output
Dockerfile                     ← Node.js Alpine
```

## Scope

### Phase 1: Generator-Backend-Abstraktion

Refactoring des `ExportService` zur Einführung der `GeneratorBackend`-Schnittstelle.

| Datei | Änderung |
|---|---|
| `export/GeneratorBackend.kt` | Neues Interface |
| `export/backend/SpringBootBackend.kt` | Bestehende Generatoren kapseln |
| `export/ExportService.kt` | Refactoring: Backend über Interface ansprechen |
| `export/ExportController.kt` | `backend`-Parameter im Request |
| `build/BuildService.kt` | `backend`-Parameter durchreichen |
| `build/BuildController.kt` | `backend`-Parameter im Request |
| `vfs/CodeGenerationController.kt` | `backend`-Parameter im Request |

**Ergebnis:** Bestehende Funktionalität bleibt unverändert (Spring Boot als Default), aber die Architektur erlaubt weitere Backends.

### Phase 2: Ktor/WasmJS-Backend

| Datei | Beschreibung |
|---|---|
| `export/backend/KtorWasmBackend.kt` | Implementiert `GeneratorBackend` |
| `export/generator/ktor/KtorMainGenerator.kt` | `main()` mit Ktor `embeddedServer` |
| `export/generator/ktor/KtorControllerGenerator.kt` | Ktor `routing { }` statt `@RestController` |
| `export/generator/ktor/KtorAgentConfigGenerator.kt` | Agent-Setup ohne Spring DI |
| `export/generator/ktor/KtorRegistryGenerator.kt` | Tool-Registry ohne `@Bean` |
| `export/generator/ktor/WasmBuildGradleGenerator.kt` | KMP `wasmJs` Target + Ktor Deps |
| `export/generator/ktor/WasmDockerfileGenerator.kt` | Node.js Alpine Image |

### Phase 3: Build-System + Frontend

| Datei | Änderung |
|---|---|
| `Dockerfile.builder` | Node.js hinzufügen für WasmJS-Kompilierung |
| `BuildService.kt` | Gradle Task `wasmJsBrowserProductionWebpack` oder `wasmJsNodeProductionRun` |
| `ToolsMenuDialog.tsx` | "Build (Wasm)" Button |
| `build-client.ts` | `"wasm"` Platform + `"ktor-wasm"` Backend |
| `ExportDialog.tsx` | Backend-Auswahl (Dropdown: Spring Boot / Ktor Wasm) |

### API-Endpunkte (neu/geändert)

```
GET  /api/generators                          → [{ id, displayName, platforms }]
POST /api/export        + backend Parameter   → ZIP
POST /api/build         + backend Parameter   → BuildResult
POST /api/vfs/{n}/generate + backend Parameter → GenerationResult
```

## Deployment-Optionen (Ktor/WasmJS)

| Ziel | Runtime | Image-Größe | Kaltstart |
|---|---|---|---|
| Docker (Node.js Alpine) | Node.js + WasmJS | ~50-80 MB | <500ms |
| Kubernetes | Standard Pod | ~50-80 MB | <500ms |
| Cloudflare Workers | V8 Wasm | ~1 MB | <10ms |
| Browser | Direkt via JS-Interop | 0 (inline) | Instant |
| Deno Deploy | Deno + Wasm | ~1 MB | <50ms |

## Wiederverwendung

| Komponente | Shared? | Anmerkung |
|---|---|---|
| `GraphToKoogAdapter` | 100% shared | Backend-unabhängig |
| `ToolGenerator` | 100% shared | ToolSet ist KMP |
| `GraphAgentGenerator` | 100% shared | Strategy DSL ist KMP |
| `SharedCodeGenerator` | 100% shared | Plain Kotlin |
| `SubAgentGenerator` | 100% shared | KMP-kompatibel |
| `BuiltinToolGenerator` | 100% shared | KMP-kompatibel |
| `InputOutputGenerator` | 100% shared | Data classes |
| `RegistryGenerator` | ~80% shared | Spring `@Bean` → plain function |
| `BuildGradleGenerator` | Backend-spezifisch | JVM vs. KMP |
| `ApplicationGenerator` | Backend-spezifisch | Spring Boot vs. Ktor |
| `ControllerGenerator` | Backend-spezifisch | Spring MVC vs. Ktor Routing |
| `DockerfileGenerator` | Backend-spezifisch | JDK vs. Node.js |

**~75% des Codegen-Codes bleibt shared.** Nur Infrastruktur-Generatoren sind Backend-spezifisch.

## Einschränkungen (Ktor/WasmJS)

- **WasmJS (nicht WASI)** — Koog targetted `wasmJs`, braucht Node.js oder Browser als Host
- **Keine JVM-Libraries** — kein SQLite JDBC → Memory-Tool braucht In-Memory oder IndexedDB
- **MCP** — HTTP-SSE Transport (kein stdio in WasmJS)
- **File System** — nur via Node.js `fs` API (nicht im Browser)
- **Ktor Server auf WasmJS** — experimentell, ggf. auf Node.js HTTP-Modul zurückfallen

## Zukunft: Weitere Backends

Die `GeneratorBackend`-Schnittstelle ermöglicht zukünftig:

| Backend | Target | HTTP-Framework | Use Case |
|---|---|---|---|
| `spring-boot` | JVM | Spring Boot WebFlux | Enterprise, bestehende Infrastruktur |
| `ktor-wasm` | WasmJS | Ktor | Edge, Serverless, Browser |
| `ktor-jvm` | JVM | Ktor | Leichtgewichtiger als Spring |
| `ktor-native` | Native | Ktor | CLI-Agents, Embedded |
| `cli` | JVM/Native | Kein Server | Standalone-Agent ohne HTTP |

## Akzeptanzkriterien

### Phase 1 (Abstraktion)
- [ ] `GeneratorBackend`-Interface existiert
- [ ] `SpringBootBackend` kapselt alle bestehenden Generatoren
- [ ] `ExportService` nutzt Backend über Interface
- [ ] `GET /api/generators` liefert Liste der verfügbaren Backends
- [ ] Bestehende API-Aufrufe ohne `backend`-Parameter funktionieren wie bisher (Default: `spring-boot`)

### Phase 2 (Ktor/Wasm)
- [ ] `KtorWasmBackend` implementiert `GeneratorBackend`
- [ ] Generierter Code nutzt Ktor für HTTP + Koog DSL für Agent-Logik
- [ ] ToolSet-Code und Strategy-Code sind identisch zum Spring-Output
- [ ] `POST /api/vfs/{name}/generate` mit `backend: "ktor-wasm"` generiert korrekte Projektstruktur

### Phase 3 (Build + UI)
- [ ] `POST /api/build` mit `platform: "wasm"` kompiliert erfolgreich
- [ ] Wasm-Binary ist lauffähig mit Node.js
- [ ] UI bietet Backend-Auswahl (Export-Dialog) und "Build (Wasm)" Button
- [ ] Compile-Errors werden wie bei JVM/Native geparst und auf Nodes gemappt
