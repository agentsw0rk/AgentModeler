# Feature 11: KotlinPoet Code Generation

## Problem

Alle 28 Code-Generatoren verwenden `buildString { appendLine(...) }` fuer Kotlin-Code-Erzeugung. Dies fuehrt zu:

- **Manuelles String-Escaping** an 7 Stellen (fehleranfaellig, order-dependent)
- **Manuelle Indentation** ueber ~650 Zeilen (Off-by-one Risiko bei Verschachtelung)
- **Manuelle Import-Listen** mit duplizierter Logik ueber mehrere Generatoren
- **Keine Typ-Sicherheit** — fehlende Klammern oder Imports werden erst beim Build des generierten Codes entdeckt
- **Duplizierter Code** — Model-ID-Mapping, Annotation-Erzeugung und Identifier-Sanitization in 3+ Generatoren kopiert

## Loesung

Migration der 19 Kotlin-Code-Generatoren auf [KotlinPoet](https://square.github.io/kotlinpoet/) (`com.squareup:kotlinpoet:2.2.0`). KotlinPoet ist eine Kotlin/Java-API zur programmatischen Erzeugung von `.kt` Quelldateien mit automatischem Import-Management, String-Escaping und Indentation.

### Was KotlinPoet uebernimmt

| Problem | buildString | KotlinPoet |
|---------|-------------|------------|
| String-Escaping | `replace("\"", "\\\"")` | `%S` Format-Specifier |
| Typ-Imports | Manuelle `appendLine("import ...")` | `%T` mit `ClassName` → Auto-Import |
| Indentation | `"    "` Prefix-Strings | `beginControlFlow`/`endControlFlow` |
| Kommas/Klammern | Manuell mit Index-Check | `ParameterSpec` automatisch |
| Kotlin-Keywords | Nicht beruecksichtigt | Automatisch escaped mit Backticks |

### Was sich nicht aendert

- Generator-Signaturen: `fun generate(...): String` (via `FileSpec.toString()`)
- ExportService/Backend Pipeline: `Map<String, String>` bleibt
- Bestehende Tests: pruefen weiterhin den generierten String-Output
- 9 Infrastruktur-Generatoren bleiben bei buildString (Gradle, Properties, Dockerfile)

## KotlinPoet Kern-API

### FileSpec — Datei-Erzeugung
```kotlin
FileSpec.builder("generated.tools", "CustomerTools")
    .addType(typeSpec)
    .addFunction(topLevelFun)
    .build()
    .toString()  // → vollstaendiger Kotlin-Code als String
```

### TypeSpec — Klassen, Objects, Interfaces
```kotlin
TypeSpec.classBuilder("CustomerTools")
    .addAnnotation(KoogAnnotations.llmDescription("Tools for customer ops"))
    .addSuperinterface(ClassName("ai.koog.agents.core.tools.reflect", "ToolSet"))
    .addFunction(funSpec)
    .build()

TypeSpec.objectBuilder("ProductDocsRag")  // Kotlin object
TypeSpec.classBuilder("Person").addModifiers(KModifier.DATA)  // Data class
```

### FunSpec — Funktionen
```kotlin
FunSpec.builder("searchDocs")
    .addModifiers(KModifier.SUSPEND)
    .addAnnotation(KoogAnnotations.tool())
    .addAnnotation(KoogAnnotations.llmDescription("Search documents"))
    .addParameter("query", String::class)
    .addParameter("count", Int::class)
    .returns(String::class)
    .addStatement("val docs = storage.mostRelevantDocuments(query, count = count)")
    .beginControlFlow("if (docs.isEmpty())")
    .addStatement("return %S", "No documents found")
    .endControlFlow()
    .addStatement("return docs.joinToString(%S)", "\n")
    .build()
```

### Format-Specifier
| Specifier | Beschreibung | Beispiel |
|-----------|-------------|---------|
| `%S` | String mit Escaping | `%S` → `"Hello \"World\""` |
| `%T` | Typ mit Auto-Import | `%T()` → `OpenAILLMClient()` + import |
| `%L` | Literal (raw) | `%L` → `42`, `true` |
| `%N` | Name (keyword-safe) | `%N` → `` `when` `` |
| `%M` | Member-Referenz | `%M()` → `createTaco()` + import |

## Scope

### KotlinPoetUtils.kt — Gemeinsame Utility-Schicht

Eine Datei mit 3 Bereichen die heute duplizierte Logik eliminieren:

**KoogAnnotations** — Factory-Methoden fuer `@Tool`, `@LLMDescription`, `@Serializable`

**KoogModels** — Provider-zu-ClassName Mapping (OpenAI, Ollama, Anthropic → Client, Models, Executor ClassName). Einzige Stelle die Koog-Package-Pfade kennt.

**Identifier-Sanitization** — `sanitizeIdentifier()` und `sanitizeClassName()` als Extension Functions. Heute in 3 Generatoren dupliziert.

### Migrationsphasen

#### Phase 1: Komplexe Generatoren (hoechster Nutzen)

| Generator | Zeilen | Hauptnutzen durch KotlinPoet |
|-----------|--------|------------------------------|
| `AgentGenerator` | 178 | Prompt-Escaping via `%S`, OTel-Bloecke via `beginControlFlow` |
| `GraphAgentGenerator` | 228 | Verschachtelte Subgraph-Indentation, Edge-Routing |
| `RagCodeGenerator` | 258 | 2 Varianten (JVM/Wasm), Escaping, dynamische Imports |
| `ToolGenerator` | 94 | `@Tool`/`@LLMDescription` Annotations, Parameter-Defaults |
| `RegistryGenerator` | 157 | Conditional Tool-Registrierung, Import-Management |
| `BuiltinToolGenerator` | 201 | 5 Tool-Varianten, switch-artige Generierung |

#### Phase 2: Mittlere Generatoren

| Generator | Zeilen | Hauptnutzen |
|-----------|--------|-------------|
| `SubAgentGenerator` | 151 | Prompt-Escaping (dupliziert von Agent) |
| `McpServerGenerator` | 63 | Transport-Typ Branching |
| `A2AServerGenerator` | 120 | Multi-File, Capability-Flags |
| `A2AClientGenerator` | 88 | Request/Response Klassen |
| `PlannerAgentGenerator` | 181 | 3-File Generierung, Goal/Action Mapping |
| `KtorMainGenerator` | 96 | JsFun-Annotations, Prompt-Escaping |
| `KtorControllerGenerator` | 72 | Route-Mapping |
| `KtorRegistryGenerator` | 33 | Wasm-Tool-Registration |
| `WasmToolGenerator` | 123 | SimpleTool-Pattern, `@Serializable` Args |

#### Phase 3: Einfache Generatoren

| Generator | Zeilen | Bemerkung |
|-----------|--------|-----------|
| `ControllerGenerator` | 48 | Spring REST Controller |
| `ApplicationGenerator` | 22 | Boot Main-Klasse |
| `InputOutputGenerator` | 80 | Data Classes — ideal fuer KotlinPoet |
| `SharedCodeGenerator` | 13 | Nur Package + Imports |

#### Nicht migriert (bleiben bei buildString)

`BuildGradleGenerator`, `WasmBuildGradleGenerator`, `SettingsGradleGenerator`, `WasmSettingsGradleGenerator`, `GradlePropertiesGenerator`, `WasmGradlePropertiesGenerator`, `ApplicationPropertiesGenerator`, `DockerfileGenerator`, `WasmDockerfileGenerator` — Gradle-DSL, Properties und Dockerfiles profitieren nicht von KotlinPoet.

### Migrationspattern

Jeder Generator wird einzeln umgestellt. Die Signatur bleibt gleich:

```kotlin
// Vorher:
fun generate(tool: KoogTool): String = buildString { ... }

// Nachher:
fun generate(tool: KoogTool): String {
    val typeSpec = TypeSpec.classBuilder(tool.name)
        .addAnnotation(KoogAnnotations.llmDescription(tool.description))
        // ...
        .build()
    return FileSpec.builder("generated.tools", tool.name)
        .addType(typeSpec)
        .build()
        .toString()
}
```

`FileSpec.toString()` liefert den generierten Code als String. Die Pipeline bleibt unveraendert.

### Dependency

```kotlin
// build.gradle.kts des Modeler-Projekts
implementation("com.squareup:kotlinpoet:2.2.0")
```

Keine transitiven Abhaengigkeiten ausser `kotlin-stdlib`.

### Teststrategie

- Bestehende Tests (`GraphAgentGeneratorTest`, `RagCodeGeneratorTest`, `ExportServiceTest`) bleiben und validieren die Migration
- Akzeptable Output-Unterschiede: Import-Reihenfolge (alphabetisch), Leerzeilen, String-Format
- Nicht akzeptabel: Fehlende Imports, falsche Typen, anderer Code-Flow
- Build-Validation nach jeder Phase: JVM + Wasm Build mit `rag-agent-example.json`

### Betroffene Dateien

| Datei | Aenderung |
|-------|-----------|
| `build.gradle.kts` (Modeler) | KotlinPoet Dependency |
| `KotlinPoetUtils.kt` (neu) | KoogAnnotations, KoogModels, Sanitization |
| 19 Generator-Dateien | buildString → FileSpec/TypeSpec/FunSpec |
| Test-Dateien | Assertions ggf. anpassen fuer Formatierungsunterschiede |

## Akzeptanzkriterien

- [ ] KotlinPoet `2.2.0` Dependency in `build.gradle.kts`
- [ ] `KotlinPoetUtils.kt` mit KoogAnnotations, KoogModels und Identifier-Sanitization
- [ ] Phase 1: 6 komplexe Generatoren migriert (Agent, GraphAgent, Rag, Tool, Registry, BuiltinTool)
- [ ] Phase 2: 9 mittlere Generatoren migriert (SubAgent, MCP, A2A Server/Client, Planner, Ktor Main/Controller/Registry, WasmTool)
- [ ] Phase 3: 4 einfache Generatoren migriert (Controller, Application, InputOutput, SharedCode)
- [ ] 9 Infrastruktur-Generatoren bleiben bei buildString (Gradle, Properties, Dockerfile)
- [ ] Alle bestehenden Tests weiterhin gruen
- [ ] Kein manuelles String-Escaping mehr in migrierten Generatoren
- [ ] Keine manuelle Indentation mehr in migrierten Generatoren
- [ ] Keine manuellen Import-Listen mehr (KotlinPoet Auto-Imports)
- [ ] JVM-Build mit `rag-agent-example.json` erfolgreich
- [ ] Wasm-Build mit `rag-agent-example.json` erfolgreich
- [ ] Generator-Signaturen unveraendert (`fun generate(...): String`)
