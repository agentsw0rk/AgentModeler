# Feature 12: KMP Library Code Generator (drittes Generator-Backend)

## Problem

Der Modeler generiert aktuell ausführbare Anwendungen: Spring Boot (JVM/Native) oder Ktor (WasmJS). Es gibt keine Möglichkeit, den modellierten Agenten als wiederverwendbare Kotlin-Bibliothek zu exportieren, die andere Projekte als Maven-Dependency einbinden können. Wer einen Agenten in eine bestehende App integrieren will, muss den generierten Code manuell extrahieren und umstrukturieren.

## Ziel

Ein drittes `GeneratorBackend` (`kmp-library`), das ein Kotlin Multiplatform Library-Projekt generiert:

1. **KMP-Projekt** mit auswählbaren Targets (JVM, WasmJS, JS, iOS)
2. **Public API** als Factory-Funktion — Consumer bringt eigenen LLM-Executor mit
3. **maven-publish** Setup mit sinnvollen Defaults (`com.example:agent-name:0.1.0`)
4. **Smoke-Test** in `commonTest` für Instanziierbarkeits-Check
5. **Publish to Maven Local** als Build-Aktion im Frontend

## Voraussetzungen

| Abhängigkeit | Status | Blocker? |
|---|---|---|
| `GeneratorBackend`-Interface | Implementiert (Feature 08) | Nein |
| Backend-Selector UI | Implementiert (Feature 09) | Nein |
| Koog KMP-Artefakte | JVM, JS, WasmJS, iOS verfügbar | Nein |
| `maven-publish` Plugin | Kotlin/Gradle Standard | Nein |

## Architektur

### Generiertes Projekt-Layout

```
build.gradle.kts                          <- kotlin("multiplatform") + maven-publish
settings.gradle.kts
gradle.properties
src/commonMain/kotlin/generated/
  ├── AgentFactory.kt                     <- fun createXAgent(executor): AIAgent
  ├── ToolRegistry.kt                     <- fun createToolRegistry(): ToolRegistry
  ├── tools/
  │   ├── MoneyTransferTools.kt           <- ToolSet mit @Tool Annotations
  │   └── AnalyticsTools.kt
  ├── subagents/                          <- optional
  ├── mcp/                                <- optional
  ├── a2a/                                <- optional
  ├── rag/                                <- optional
  ├── code/
  │   └── Domain.kt                       <- Shared Code
  └── XStrategy.kt                        <- nur bei Graph-Agents
src/wasmJsMain/kotlin/generated/          <- nur wenn WasmJS Target gewählt
  └── tools/
      ├── MoneyTransferTools.kt           <- SimpleTool statt ToolSet
      └── AnalyticsTools.kt
src/commonTest/kotlin/generated/
  └── AgentSmokeTest.kt                   <- Instanziierbarkeits-Check
```

Alles unter `commonMain` — der Code nutzt nur Koog KMP-APIs. Kein plattformspezifischer Split nötig.

**WasmJS-Sonderfall:** ToolSets nutzen `@Tool`-Annotations, die auf allen Koog-KMP-Targets funktionieren — **außer WasmJS**, wo `SimpleTool` nötig ist. Wenn WasmJS als Target gewählt wird, generiert das Backend zusätzliche Tool-Files in `wasmJsMain` mit `SimpleTool` (identisch zum Ansatz im `KtorWasmBackend`).

### Public API — AgentFactory

Die generierte Facade:

```kotlin
package generated

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMModel

fun createBankingAssistantAgent(
    executor: SingleLLMPromptExecutor,
    model: LLMModel = OpenAIModels.Chat.GPT4o
): AIAgent = AIAgent(
    executor = executor,
    strategy = createBankingAssistantStrategy(),
    systemPrompt = "You are a banking assistant...",
    llmModel = model,
    installToolSets = { createToolRegistry().installAll(this) }
)
```

- Consumer bringt eigenen `SingleLLMPromptExecutor` mit (kein API-Key in der Library)
- Default-Model aus der Node-Konfiguration im Editor
- Bei Graph-Agents: referenziert die generierte Strategy-Funktion
- Bei Planner-Agents: analog `createXPlanner(executor)`

Consumer-Nutzung:

```kotlin
val client = OpenAILLMClient(apiKey)
val executor = SingleLLMPromptExecutor(client)
val agent = createBankingAssistantAgent(executor)
val result = agent.run("Transfer 100 to Alice")
```

### build.gradle.kts

Targets werden dynamisch aus der Frontend-Auswahl generiert:

```kotlin
plugins {
    kotlin("multiplatform") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("maven-publish")
}

group = "com.example"
version = "0.1.0"

kotlin {
    // Dynamisch je nach Target-Auswahl:
    jvm()
    wasmJs { nodejs() }
    js(IR) { nodejs() }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("ai.koog:koog-agents:0.7.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}
```

### Smoke-Test

```kotlin
package generated

import kotlin.test.Test
import kotlin.test.assertNotNull

class AgentSmokeTest {

    @Test
    fun toolRegistryIsCreatable() {
        val registry = createToolRegistry()
        assertNotNull(registry)
    }
}
```

Bei Graph-Agents zusätzlich:

```kotlin
@Test
fun strategyIsCreatable() {
    val strategy = createBankingAssistantStrategy()
    assertNotNull(strategy)
}
```

## Backend-Implementierung

### KmpLibraryBackend

Neues `@Component` unter `export/backend/KmpLibraryBackend.kt`:

```
KmpLibraryBackend : GeneratorBackend
  ├── id = "kmp-library"
  ├── displayName = "KMP Library"
  ├── supportedPlatforms = ["jvm", "wasmJs", "js", "ios"]
  ├── remapsSharedFiles = true
  │
  ├── Eigene Generatoren (neu):
  │   ├── LibBuildGradleGenerator       <- KMP targets + maven-publish
  │   ├── LibSettingsGradleGenerator
  │   ├── LibGradlePropertiesGenerator
  │   ├── AgentFactoryGenerator         <- createXAgent() Facade
  │   └── SmokeTestGenerator            <- commonTest
  │
  ├── Wiederverwendet:
  │   └── WasmToolGenerator             <- SimpleTool fuer wasmJsMain
  │
  └── Shared Files Remapping:
      src/main/kotlin/generated/ -> src/commonMain/kotlin/generated/
```

### BackendConfig

Neues Config-Objekt fuer Backend-spezifische Parameter:

```kotlin
data class BackendConfig(
    val targets: List<String> = emptyList()
)
```

Wird in `ExportService.generateFiles()` gebaut und an die Backend-Methoden weitergegeben. `SpringBootBackend` und `KtorWasmBackend` ignorieren es. Das `GeneratorBackend`-Interface bekommt einen optionalen `config`-Parameter in den `generate*`-Methoden (Default `BackendConfig()`).

### ExportRequest-Erweiterung

```kotlin
data class ExportRequest(
    val graph: WorkspaceGraph? = null,
    val backend: String = "spring-boot",
    val targets: List<String>? = null,   // NEU -- nur relevant fuer kmp-library
    // ... rest bleibt
)
```

## Frontend-Aenderungen

### Backend-Selector mit Target-Auswahl

Im Tools-Menu (Feature 09) erscheint bei aktivem `kmp-library`-Backend eine Target-Checkbox-Gruppe:

```
+---------------------------------+
| Tools                           |
+---------------------------------+
| (gear) Backend                  |
|    [KMP Library            v]   |
|                                 |
|    Targets:                     |
|    [x] JVM                      |
|    [x] WasmJS                   |
|    [ ] JS                       |
|    [ ] iOS                      |
+---------------------------------+
| (check) Validate (Local)        |
| (box)   Publish to Maven Local  |
+---------------------------------+
| (disk)  Save / Load             |
| (gear)  Workspace Settings      |
| (pkg)   Export                   |
+---------------------------------+
```

Default-Selektion: JVM + WasmJS.

### Publish to Maven Local

Statt der Build-Buttons (JVM/Native/Wasm) zeigt das Menu bei `kmp-library` einen **"Publish to Maven Local"**-Button.

**Backend:** `BuildService` erkennt `backend: "kmp-library"` und fuehrt `publishToMavenLocal` als Gradle-Task aus (statt `build` oder `wasmJsBrowserProductionWebpack`).

**Erfolgsausgabe:** Toast-Notification mit Maven-Koordinaten:
> `com.example:my-agent:0.1.0` published to ~/.m2/repository

**Error-Handling:** Gradle-Compile-Errors werden wie bei den anderen Backends geparst und auf Nodes gemappt.

### EditorStore-Erweiterung

```typescript
interface EditorStore {
    // ... existing
    activeBackend: string
    libraryTargets: string[]              // NEU: ["jvm", "wasmJs", "js", "ios"]
    setLibraryTargets: (targets: string[]) => void
}
```

`libraryTargets` wird bei Export, VFS-Generate und Publish mitgeschickt.

### API-Clients

Alle betroffenen Clients (`export-client.ts`, `vfs-client.ts`, `build-client.ts`) schicken `targets` mit, wenn `activeBackend === "kmp-library"`.

## Wiederverwendung

| Komponente | Shared? | Anmerkung |
|---|---|---|
| `GraphToKoogAdapter` | 100% shared | Backend-unabhaengig |
| `ToolGenerator` | 100% shared | ToolSet ist KMP (commonMain) |
| `GraphAgentGenerator` | 100% shared | Strategy DSL ist KMP |
| `SharedCodeGenerator` | 100% shared | Plain Kotlin |
| `SubAgentGenerator` | 100% shared | KMP-kompatibel |
| `BuiltinToolGenerator` | 100% shared | KMP-kompatibel |
| `InputOutputGenerator` | 100% shared | Data classes |
| `RagCodeGenerator` | 100% shared | KMP-kompatibel |
| `McpServerGenerator` | 100% shared | KMP-kompatibel |
| `A2AClientGenerator` | 100% shared | KMP-kompatibel |
| `KtorRegistryGenerator` | wiederverwendet | Plain-function Registry (ohne Spring @Bean), bereits vom KtorWasmBackend genutzt |
| `WasmToolGenerator` | wiederverwendet | Fuer wasmJsMain SimpleTool Override |
| `AgentFactoryGenerator` | NEU | createXAgent() Facade |
| `SmokeTestGenerator` | NEU | commonTest |
| `LibBuildGradleGenerator` | NEU | KMP + maven-publish |
| `LibSettingsGradleGenerator` | NEU | Analog zu bestehenden |
| `LibGradlePropertiesGenerator` | NEU | Analog zu bestehenden |

**~80% des Codegen-Codes bleibt shared.** Nur 5 neue Generatoren noetig.

## Scope

### Phase 1: Backend + Generatoren

| Datei | Aenderung |
|---|---|
| `export/backend/KmpLibraryBackend.kt` | NEU: implementiert `GeneratorBackend` |
| `export/generator/lib/LibBuildGradleGenerator.kt` | NEU: KMP targets + maven-publish |
| `export/generator/lib/LibSettingsGradleGenerator.kt` | NEU |
| `export/generator/lib/LibGradlePropertiesGenerator.kt` | NEU |
| `export/generator/lib/AgentFactoryGenerator.kt` | NEU: createXAgent() Facade |
| `export/generator/lib/SmokeTestGenerator.kt` | NEU: commonTest |
| `export/GeneratorBackend.kt` | BackendConfig Parameter ergaenzen |
| `export/ExportService.kt` | BackendConfig bauen und durchreichen |
| `export/ExportController.kt` | `targets` Feld in ExportRequest |

### Phase 2: Build-Integration (Publish)

| Datei | Aenderung |
|---|---|
| `build/BuildService.kt` | `publishToMavenLocal` Task fuer kmp-library |
| `build/BuildController.kt` | backend-Parameter durchreichen |

### Phase 3: Frontend

| Datei | Aenderung |
|---|---|
| `store/editorStore.ts` | `libraryTargets` State |
| `components/dialogs/ToolsMenuDialog.tsx` | Target-Checkboxen bei kmp-library, Publish-Button |
| `lib/export-client.ts` | `targets` Parameter |
| `lib/vfs-client.ts` | `targets` Parameter |
| `lib/build-client.ts` | `targets` + Publish-Action |

### Phase 4: Tests

| Datei | Aenderung |
|---|---|
| `KmpLibraryBackendTest.kt` | NEU: generierte Dateien pruefen |
| `AgentFactoryGeneratorTest.kt` | NEU: Factory-Code pruefen |
| `LibBuildGradleGeneratorTest.kt` | NEU: Target-Kombinationen pruefen |
| `ExportServiceTest.kt` | Bestehende Tests + kmp-library Backend |

## Akzeptanzkriterien

### Phase 1 (Backend + Generatoren)
- [ ] `KmpLibraryBackend` implementiert `GeneratorBackend` mit id `kmp-library`
- [ ] Generiertes Projekt hat `commonMain` Source-Layout
- [ ] `AgentFactory.kt` exponiert `createXAgent(executor)` Funktion
- [ ] `build.gradle.kts` enthaelt nur die gewaehlten Targets
- [ ] `maven-publish` Plugin mit `group = "com.example"`, `version = "0.1.0"`
- [ ] Smoke-Test in `commonTest` prueft ToolRegistry-Instanziierung
- [ ] Wenn WasmJS Target: `wasmJsMain/tools/` mit SimpleTool-Overrides
- [ ] Bestehende Backends (`spring-boot`, `ktor-wasm`) bleiben unberuehrt

### Phase 2 (Publish)
- [ ] `POST /api/build` mit `backend: "kmp-library"` fuehrt `publishToMavenLocal` aus
- [ ] Compile-Errors werden geparst und auf Nodes gemappt
- [ ] Erfolgs-Output enthaelt Maven-Koordinaten

### Phase 3 (Frontend)
- [ ] Backend-Dropdown zeigt "KMP Library" als Option
- [ ] Bei Auswahl erscheinen Target-Checkboxen (JVM, WasmJS, JS, iOS)
- [ ] Default-Targets: JVM + WasmJS
- [ ] "Publish to Maven Local"-Button statt Build-Buttons
- [ ] Export und VFS-Generate schicken `targets` mit

### Phase 4 (Tests)
- [ ] Unit-Tests fuer alle neuen Generatoren
- [ ] Integration-Test: vollstaendiger Export mit kmp-library Backend
- [ ] Target-Kombinationen (nur JVM, alle 4, etc.) getestet
