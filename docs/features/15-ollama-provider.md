# Feature 15: Ollama Model Provider

## Problem

Der Modeler unterstuetzt aktuell vier LLM-Provider (OpenAI, Anthropic, Google, AWS Bedrock), die alle einen Cloud-Account und API-Keys brauchen. Koog unterstuetzt laut [LLM-Provider-Uebersicht](https://docs.koog.ai/llm-providers/) zusaetzlich **Ollama** fuer lokal laufende Modelle — ohne API-Kosten, offline-faehig und ohne dass Daten das eigene Netz verlassen. Agents lassen sich im Editor bisher nicht mit einem lokalen Modell modellieren, in der Live-Preview testen oder exportieren.

## Ziel

`OLLAMA` als fuenfter `ModelProvider`, waehlbar bei allen Agent-Typen:

1. **Provider im Dropdown** — "Ollama (local)" in Agent, Sub-Agent, Graph-Agent und Planner-Settings
2. **Kein API-Key** — stattdessen Base-URL per Env-Var `OLLAMA_BASE_URL` (Default `http://localhost:11434`)
3. **Kuratierte Modelle + freie Eingabe** — 6 vordefinierte Modelle (gemappt auf `OllamaModels`-Konstanten), zusaetzlich beliebiger Ollama-Modellname (z. B. `mistral:7b`) als Custom-Modell
4. **Live-Preview** — Agents mit Ollama laufen in der Preview gegen die lokale Ollama-Instanz
5. **Export** — generierter Code fuer Spring Boot, KMP Library und Ktor/Wasm nutzt `simpleOllamaAIExecutor(baseUrl)`; Docker-Compose erreicht Ollama auf dem Host
6. **Lokale Modelle entdecken (Phase 2)** — Backend-Endpoint listet die in Ollama installierten Modelle inkl. Tool-Faehigkeit fuer das Dropdown

## Voraussetzungen

| Abhaengigkeit | Status | Blocker? |
|---|---|---|
| Koog Ollama Client (`prompt-executor-ollama-client`) | Verfuegbar in Koog 1.3.0, transitiv in `koog-agents` | Nein |
| `simpleOllamaAIExecutor(baseUrl: String = "http://localhost:11434")` | Verfuegbar in `ai.koog.prompt.executor.llms.all` (`prompt-executor-llms-all` 1.3.0-beta, kommt ueber den Spring-Starter) | Nein |
| `OllamaModels` (`Meta`, `Alibaba`, `OpenAI`, `DeepSeek`, `Groq`, `Granite`, `Embeddings`) | Verfuegbar in `ai.koog.prompt.executor.ollama.client` | Nein |
| `OllamaClient.getModels()` / `OllamaModelCard.toLLModel()` | Verfuegbar (ruft Ollama `api/tags` + `api/show`) | Nein (nur Phase 2) |
| Laufende Ollama-Instanz mit gepulltem Modell | Lokal beim Anwender | Nein (nur fuer Preview/Laufzeit) |

Alle Signaturen und Konstanten oben sind gegen die Koog-1.3.0-JARs geprueft.

## Architektur

### ModelProvider Enum

Neuer Wert in `export/model/KoogModel.kt`:

```kotlin
enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash"),
    BEDROCK("ai.koog.prompt.executor.clients.bedrock.BedrockModels", "simpleBedrockExecutor", "BedrockModels.AnthropicClaude4Sonnet"),
    OLLAMA("ai.koog.prompt.executor.ollama.client.OllamaModels", "simpleOllamaAIExecutor", "OllamaModels.Meta.LLAMA_3_2")
}
```

`GraphToKoogAdapter` mappt `"ollama" -> ModelProvider.OLLAMA`.

Da mehrere Stellen per `when (provider)` exhaustiv verzweigen, erzwingt der Compiler die Ergaenzung in allen betroffenen Generatoren (siehe "Betroffene Dateien").

### Ollama-Modelle (mapModelId)

Die Dropdown-IDs sind die **echten Ollama-Tags** — dieselben Namen, die der Anwender mit `ollama pull` verwendet:

| Dropdown-ID (Ollama-Tag) | Frontend-Label | OllamaModels Konstante |
|---|---|---|
| `llama3.2:latest` | Llama 3.2 | `OllamaModels.Meta.LLAMA_3_2` |
| `llama3.2:3b` | Llama 3.2 3B | `OllamaModels.Meta.LLAMA_3_2_3B` |
| `qwen3.5:9b` | Qwen 3.5 9B | `OllamaModels.Alibaba.QWEN_3_5_9B` |
| `qwen2.5-coder:32b` | Qwen 2.5 Coder 32B | `OllamaModels.Alibaba.QWEN_CODER_2_5_32B` |
| `gpt-oss:20b` | GPT-OSS 20B | `OllamaModels.OpenAI.GPT_OSS_20B` |
| `llama3-groq-tool-use:8b` | Llama 3 Groq Tool-Use 8B | `OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B` |

**Custom-Modelle:** Jede andere ID wird nicht auf `defaultModel` zurueckgesetzt (anders als bei den Cloud-Providern), sondern als eigenes `LLModel` erzeugt — Ollama-Anwender nutzen haeufig Modelle ausserhalb jeder vordefinierten Liste:

```kotlin
LLModel(
    provider = LLMProvider.Ollama,
    id = "mistral:7b",
    capabilities = listOf(LLMCapability.Temperature, LLMCapability.Schema.JSON.Standard, LLMCapability.Tools),
)
```

Damit braucht `mapModelId()` fuer Ollama einen anderen Fallback:

```kotlin
ModelProvider.OLLAMA -> when (id.lowercase()) {
    "llama3.2:latest", "llama3.2" -> "OllamaModels.Meta.LLAMA_3_2"
    "llama3.2:3b" -> "OllamaModels.Meta.LLAMA_3_2_3B"
    "qwen3.5:9b" -> "OllamaModels.Alibaba.QWEN_3_5_9B"
    "qwen2.5-coder:32b" -> "OllamaModels.Alibaba.QWEN_CODER_2_5_32B"
    "gpt-oss:20b" -> "OllamaModels.OpenAI.GPT_OSS_20B"
    "llama3-groq-tool-use:8b" -> "OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B"
    else -> customOllamaModel(id)   // erzeugt den LLModel(...)-Ausdruck aus dem Beispiel oben
}
```

> **Hinweis Tool-Calling:** Nicht jedes Ollama-Modell kann Tools aufrufen. Agents mit Tools, MCP-Servern oder Sub-Agents brauchen ein Modell mit `LLMCapability.Tools`. Die kuratierte Liste enthaelt bewusst tool-faehige Modelle; fuer Custom-Modelle zeigt die Validation eine Warnung (siehe Frontend), in Phase 2 wird die Faehigkeit aus `OllamaModelCard.capabilities` gelesen.

### Executor-Erzeugung — Ollama vs. Andere

Cloud-Provider erzeugen den Executor mit API-Key, Bedrock mit zwei Keys + Region. Ollama braucht **keinen Key**, nur die Base-URL:

```kotlin
promptExecutor = simpleOllamaAIExecutor(baseUrl)
```

Konsequenzen:

- Keine `validateApiKey()`-Pruefung im generierten Code, kein `*_API_KEY` in Properties/Compose
- Base-URL immer aus `OLLAMA_BASE_URL`, Default `http://localhost:11434` (identisch mit dem Default von Koog)

### Live-Preview (`PreviewAgentBuilder`)

Aktuell liest `build()` **vor** der Provider-Verzweigung zwingend `${provider}_API_KEY` und wirft sonst `PreviewException`. Fuer Ollama muss die Key-Pruefung in die Verzweigung wandern:

```kotlin
val executor = when (agent.modelProvider) {
    ModelProvider.OLLAMA -> simpleOllamaAIExecutor(
        System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
    )
    ModelProvider.OPENAI -> simpleOpenAIExecutor(requireApiKey(agent.modelProvider))
    ModelProvider.ANTHROPIC -> simpleAnthropicExecutor(requireApiKey(agent.modelProvider))
    ModelProvider.GOOGLE -> simpleGoogleAIExecutor(requireApiKey(agent.modelProvider))
    ModelProvider.BEDROCK -> throw PreviewException("Bedrock executor not yet implemented")
}
```

`resolveModel()` bekommt den OLLAMA-Case mit den Konstanten bzw. `LLModel(LLMProvider.Ollama, id, ...)` fuer Custom-Modelle; `resolveDefaultModel()` liefert `OllamaModels.Meta.LLAMA_3_2`.

Fehlerfall: Ist Ollama nicht erreichbar oder das Modell nicht gepullt, soll die Preview eine verstaendliche Meldung zeigen ("Ollama unter http://localhost:11434 nicht erreichbar" bzw. "Modell `xyz` nicht installiert — `ollama pull xyz`"), nicht den rohen Stacktrace.

> Der Koog-Spring-Starter bringt zusaetzlich eine `OllamaLLMAutoConfiguration` (`ai.koog.ollama.enabled=false`, `ai.koog.ollama.base-url=http://127.0.0.1:11434`). Die Preview nutzt sie **nicht**, sondern erzeugt den Executor pro Agent selbst — konsistent zu den anderen Providern.

### Generierter Code — Spring Boot

**AgentConfig.kt:**

```kotlin
@Configuration
class AgentConfig(
    @Value("\${ollama.base-url}") private val baseUrl: String
) {
    @Bean @Primary
    fun agent(): AIAgent = AIAgent(
        promptExecutor = simpleOllamaAIExecutor(baseUrl),
        systemPrompt = "...",
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        toolRegistry = createToolRegistry()
    )
}
```

**application.properties:**

```
ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
```

**build.gradle.kts** — keine zusaetzliche Dependency: `prompt-executor-ollama-client` ist bereits Teil von `koog-agents`, `simpleOllamaAIExecutor` kommt ueber `koog-spring-boot-starter`.

**docker-compose.yml** — die App laeuft im Container, Ollama typischerweise auf dem Host. `localhost` zeigt im Container auf den Container selbst, deshalb:

```yaml
  app:
    environment:
      OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://host.docker.internal:11434}
    extra_hosts:
      - "host.docker.internal:host-gateway"   # noetig unter Linux, auf Mac/Windows harmlos
```

Kein `*_API_KEY`-Eintrag fuer Ollama.

### Generierter Code — KMP Library

`AgentFactory.kt` aendert sich nicht (Consumer bringt seinen Executor mit), nur `mapModelId()` braucht den OLLAMA-Case. `LibBuildGradleGenerator` braucht keine Extra-Dependency — der Ollama-Client ist fuer `jvm`, `wasm-js`, `js` und `iosarm64` veroeffentlicht (geprueft auf Maven Central).

### Generierter Code — Ktor/Wasm

`KtorMainGenerator` erzeugt einen Ktor-Server, der als WasmJS unter Node.js laeuft, und liest heute `${PROVIDER}_API_KEY` per `@JsFun` aus `process.env` (Pflichtwert). Fuer Ollama wird stattdessen `OLLAMA_BASE_URL` gelesen — optional, mit Default:

```kotlin
@JsFun("() => { try { return process.env.OLLAMA_BASE_URL || '' } catch(e) { return '' } }")
private external fun readOllamaBaseUrl(): JsString

private val envOllamaBaseUrl: String = readOllamaBaseUrl().toString().ifEmpty { "http://localhost:11434" }

// in main():
promptExecutor = simpleOllamaAIExecutor(envOllamaBaseUrl)
```

Da der Agent serverseitig unter Node laeuft (nicht im Browser), ist keine CORS-Konfiguration (`OLLAMA_ORIGINS`) noetig.

### Phase 2: Lokale Modelle entdecken

Neuer Endpoint nach dem Muster von `mcp/McpDiscoveryController`:

```
GET /api/ollama/models
→ { "models": [{ "id": "qwen3:0.6b", "label": "qwen3:0.6b (751.6M, Q4_K_M)", "tools": true }, ...], "error": null }
```

Der Endpoint nutzt dieselbe `OLLAMA_BASE_URL` wie die Preview und nimmt bewusst **keine** URL aus dem Request entgegen — sonst liesse sich der Server beliebige Hosts abfragen (SSRF). Implementierung ueber `OllamaClient(baseUrl).getModels()` → `OllamaModelCard` (`name`, `parameterCount`, `quantizationLevel`, `capabilities`). Ist Ollama nicht erreichbar, liefert der Endpoint eine leere Liste mit Hinweis statt eines Fehlers. Das Frontend kombiniert die installierten Modelle mit der kuratierten Liste und markiert Modelle ohne Tool-Support.

## Frontend-Aenderungen

### ModelProvider Type und Modell-Optionen

```typescript
export type ModelProvider = "openai" | "anthropic" | "google" | "bedrock" | "ollama";

export const MODEL_OPTIONS: Record<ModelProvider, { id: string; label: string }[]> = {
  // ...bestehende Provider
  ollama: [
    { id: "llama3.2:latest", label: "Llama 3.2" },
    { id: "llama3.2:3b", label: "Llama 3.2 3B" },
    { id: "qwen3.5:9b", label: "Qwen 3.5 9B" },
    { id: "qwen2.5-coder:32b", label: "Qwen 2.5 Coder 32B" },
    { id: "gpt-oss:20b", label: "GPT-OSS 20B" },
    { id: "llama3-groq-tool-use:8b", label: "Llama 3 Groq Tool-Use 8B" },
  ],
};
```

### Provider-Dropdown

In allen 4 Komponenten (`AgentProperties`, `SubAgentProperties`, `GraphAgentProperties`, `planner/SettingsTab`):

```tsx
<SelectItem value="ollama">Ollama (local)</SelectItem>
```

### Model-Auswahl mit freier Eingabe

Bei `ollama` zeigt das Model-Feld zuerst die in Ollama installierten Modelle (Phase 2), dann die kuratierten Vorschlaege **plus** einen Eintrag "Other model…", der ein Textfeld fuer einen beliebigen Ollama-Tag einblendet (z. B. `mistral:7b`). Fuer nicht installierte Modelle nennt ein Hinweis den passenden Befehl: `ollama pull <modell>`. Modelle ohne Tool-Support sind in der Liste markiert.

Provider- und Modell-Auswahl liegen in der gemeinsamen Komponente `components/shared/ModelFields.tsx`, weil sie in allen 4 Panels identisch waren. Ein Provider-Wechsel setzt die `modelId` zurueck — sonst wuerde z. B. `gpt-4o` nach dem Wechsel zu Ollama als Custom-Ollama-Modell interpretiert.

### Client-seitige Validation

- `validation.ts` akzeptiert `"ollama"` als gueltigen `modelProvider`
- Warnung (kein Fehler), wenn ein Ollama-Agent Tools/MCP-Server/Sub-Agents nutzt und ein Custom-Modell gewaehlt ist: "Pruefe, ob das Modell Tool-Calling unterstuetzt"

## Betroffene Dateien

### Backend

| Datei | Aenderung |
|---|---|
| `export/model/KoogModel.kt` | `OLLAMA` zu `ModelProvider` enum |
| `export/adapter/GraphToKoogAdapter.kt` | `"ollama" -> ModelProvider.OLLAMA` |
| `export/generator/AgentGenerator.kt` | Ollama-Executor mit `baseUrl`, keine Key-Validierung, `mapModelId()` Case inkl. Custom-`LLModel`, Imports (`OllamaModels`, ggf. `LLModel`/`LLMProvider`/`LLMCapability`) |
| `export/generator/SubAgentGenerator.kt` | `mapModelId()` Case |
| `export/generator/PlannerAgentGenerator.kt` | `mapModelId()` Case |
| `export/generator/ApplicationPropertiesGenerator.kt` | `ollama.base-url` statt `*.api-key` |
| `export/generator/DockerComposeGenerator.kt` | `OLLAMA_BASE_URL` mit `host.docker.internal`, `extra_hosts`, kein API-Key-Eintrag (exhaustives `when` ergaenzen) |
| `export/generator/ktor/KtorMainGenerator.kt` | Ollama-Executor mit optionaler `OLLAMA_BASE_URL` statt Pflicht-`envApiKey`, `mapModelId()` Case |
| `export/generator/lib/AgentFactoryGenerator.kt` | `mapModelId()` Case |
| `preview/PreviewAgentBuilder.kt` | Key-Pruefung pro Provider, Ollama-Executor, `resolveModel()`/`resolveDefaultModel()` Cases |
| `preview/PreviewErrorMessages.kt` | NEU: Ollama-Rohfehler → `ollama pull <modell>` / `ollama serve` |
| `export/generator/OllamaModelMapping.kt` | NEU: gemeinsames Tag→Code-Mapping fuer alle Generatoren |
| `ollama/OllamaDiscoveryController.kt`, `OllamaDiscoveryService.kt` | NEU (Phase 2): `GET /api/ollama/models` |

`BuildGradleGenerator` und `LibBuildGradleGenerator` brauchen **keine** Aenderung (Ollama-Client ist in `koog-agents` enthalten).

### Frontend

| Datei | Aenderung |
|---|---|
| `types/koog.ts` | `"ollama"` zu `ModelProvider`, Ollama-Eintraege in `MODEL_OPTIONS` |
| `components/shared/ModelFields.tsx` | NEU: Provider + Modell, "Other model…"-Freitext, installierte Ollama-Modelle |
| `components/panels/AgentProperties.tsx` | `ModelFields` verwenden |
| `components/panels/SubAgentProperties.tsx` | `ModelFields` verwenden |
| `components/panels/GraphAgentProperties.tsx` | `ModelFields` verwenden |
| `components/planner/SettingsTab.tsx` | `ModelFields` verwenden |
| `lib/validation.ts` | `"ollama"` akzeptieren, Tool-Calling-Warnung fuer Custom-Modelle |
| `lib/ollama-client.ts` | NEU (Phase 2): `fetchOllamaModels()` gegen `/api/ollama/models` (einmal pro Seitenaufruf) |

### Tests

| Datei | Aenderung |
|---|---|
| `AgentGeneratorTest.kt` | Ollama-Agent erzeugt `simpleOllamaAIExecutor(baseUrl)`, keine `validateApiKey`, Custom-Modell erzeugt `LLModel(LLMProvider.Ollama, ...)` |
| `ApplicationPropertiesGeneratorTest.kt` | `ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}`, kein `api-key` |
| `SubAgentGeneratorTest.kt`, `PlannerAgentGeneratorTest.kt` | Mapping kuratierter Modelle auf `OllamaModels`-Konstanten |
| `lib/`-Tests (`AgentFactoryGenerator`) | Mapping-Case |
| DockerCompose-Test | `OLLAMA_BASE_URL` + `extra_hosts`, kein `*_API_KEY` |
| `ExportServiceTest.kt` | End-to-End-Export eines Ollama-Agents (Spring Boot) |
| Preview-Test (neu) | Ollama-Agent ohne gesetzten API-Key wirft **keine** `Missing environment variable`-Exception |

## Platform-Einschraenkungen

| Backend | Ollama verfuegbar? | Grund |
|---|---|---|
| Spring Boot (JVM) | Ja | Ollama-Client fuer JVM verfuegbar |
| Spring Boot (Native) | Ja | Reiner HTTP-Client, keine native Abhaengigkeit |
| KMP Library (JVM / WasmJS / JS / iOS) | Ja | Client fuer alle Targets auf Maven Central veroeffentlicht |
| Ktor/Wasm | Ja | Agent laeuft serverseitig unter Node.js, Ollama-Client fuer `wasm-js` verfuegbar |

Allgemein: Zur Laufzeit muss eine Ollama-Instanz unter `OLLAMA_BASE_URL` erreichbar sein und das gewaehlte Modell muss gepullt sein (`ollama pull <modell>`). Im Docker-Export zeigt der Default auf `host.docker.internal`.

## Akzeptanzkriterien

- [x] `ModelProvider.OLLAMA` existiert mit `OllamaModels`-Import und `simpleOllamaAIExecutor` als Executor-Factory
- [x] `mapModelId()` mappt alle 6 kuratierten Modelle auf die richtigen `OllamaModels`-Konstanten
- [x] Unbekannte Ollama-IDs erzeugen ein `LLModel(LLMProvider.Ollama, <id>, ...)` statt auf das Default-Modell zurueckzufallen
- [x] Generierter Spring-Boot-Code nutzt `simpleOllamaAIExecutor(baseUrl)` ohne API-Key-Validierung
- [x] `application.properties` enthaelt `ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}` und keinen `api-key`
- [x] `docker-compose.yml` setzt `OLLAMA_BASE_URL` auf `host.docker.internal` inkl. `extra_hosts`
- [x] Live-Preview startet einen Ollama-Agent **ohne** gesetzten `*_API_KEY` und antwortet ueber die lokale Ollama-Instanz
- [x] Preview zeigt verstaendliche Meldungen, wenn Ollama nicht laeuft oder das Modell fehlt
- [x] Frontend zeigt "Ollama (local)" im Provider-Dropdown aller 4 Agent-Typen
- [x] Model-Feld bietet die 6 kuratierten Modelle und freie Eingabe eines Ollama-Tags
- [x] Validation warnt bei Tool-nutzenden Agents mit Custom-Ollama-Modell
- [x] Ein exportiertes Spring-Boot-Projekt mit Ollama-Agent und Shell-Tool kompiliert und beantwortet eine Anfrage gegen ein lokales Modell — automatisiert in `OllamaShellToolE2ETest` (`./gradlew ollamaE2eTest`)
- [x] (Phase 2) `GET /api/ollama/models` listet installierte Modelle inkl. Tool-Faehigkeit; bei nicht erreichbarem Ollama leere Liste statt Fehler
- [x] Bestehende Provider (OpenAI, Anthropic, Google, Bedrock) bleiben unberuehrt
