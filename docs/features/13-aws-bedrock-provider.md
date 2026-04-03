# Feature 13: AWS Bedrock Model Provider

## Problem

Der Modeler unterstuetzt aktuell drei LLM-Provider: OpenAI, Anthropic und Google. AWS Bedrock als vierten Provider zu unterstuetzen ermoeglicht Zugang zu Modellen mehrerer Familien (Claude, Nova, Llama) ueber eine einzige AWS-Integration mit bestehenden AWS-Credentials.

## Ziel

`BEDROCK` als vierten `ModelProvider` mit:

1. **8 Modelle** aus 3 Familien (Claude, Nova, Llama) im Dropdown
2. **2 Env-Vars** fuer Authentication (`AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_ACCESS_KEY`)
3. **Region per Env-Var** (`AWS_BEDROCK_REGION`, Default `us-west-2`)
4. **BedrockClientSettings** im generierten Code fuer Region-Konfiguration
5. **JVM-only** — verfuegbar fuer Spring Boot und KMP Library (JVM-Target), nicht fuer Ktor/Wasm

## Voraussetzungen

| Abhaengigkeit | Status | Blocker? |
|---|---|---|
| Koog Bedrock Client (`prompt-executor-bedrock-client`) | Verfuegbar in Koog 0.7.2 | Nein |
| `simpleBedrockExecutor` | Verfuegbar in `ai.koog.prompt.executor.llms.all` | Nein |
| `BedrockModels`, `BedrockRegions`, `BedrockClientSettings` | Verfuegbar | Nein |

## Architektur

### ModelProvider Enum

Neuer Wert in `ModelProvider`:

```kotlin
enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash"),
    BEDROCK("ai.koog.prompt.executor.clients.bedrock.BedrockModels", "simpleBedrockExecutor", "BedrockModels.AnthropicClaude4Sonnet")
}
```

### Bedrock-Modelle (mapModelId)

8 Modelle im Dropdown, gemappt auf `BedrockModels`-Konstanten:

| Dropdown-ID | Frontend-Label | BedrockModels Konstante |
|---|---|---|
| `claude-4.5-sonnet` | Claude 4.5 Sonnet | `BedrockModels.AnthropicClaude4_5Sonnet` |
| `claude-4-sonnet` | Claude 4 Sonnet | `BedrockModels.AnthropicClaude4Sonnet` |
| `claude-4-opus` | Claude 4 Opus | `BedrockModels.AnthropicClaude4Opus` |
| `nova-pro` | Nova Pro | `BedrockModels.AmazonNovaPro` |
| `nova-lite` | Nova Lite | `BedrockModels.AmazonNovaLite` |
| `nova-micro` | Nova Micro | `BedrockModels.AmazonNovaMicro` |
| `llama-3.3-70b` | Llama 3.3 70B | `BedrockModels.MetaLlama3_3_70BInstruct` |
| `llama-3.1-405b` | Llama 3.1 405B | `BedrockModels.MetaLlama3_1_405BInstruct` |

### Executor-Erzeugung — Bedrock vs. Andere

Bisherige Provider erzeugen den Executor mit einem API-Key:

```kotlin
promptExecutor = simpleOpenAIExecutor(apiKey)
```

Bedrock braucht 2 Keys + Settings:

```kotlin
promptExecutor = simpleBedrockExecutor(
    awsAccessKeyId = accessKey,
    awsSecretAccessKey = secretKey,
    settings = BedrockClientSettings(
        region = System.getenv("AWS_BEDROCK_REGION") ?: "us-west-2"
    )
)
```

Das betrifft 3 Generatoren: `AgentGenerator` (Spring Boot), `KtorMainGenerator` (Ktor/Wasm — nicht relevant, Bedrock nicht unterstuetzt), `AgentFactoryGenerator` (KMP Library — Consumer bringt eigenen Executor mit, kein Sonder-Code).

Nur der `AgentGenerator` (Spring Boot) braucht die Bedrock-Verzweigung fuer die Executor-Erzeugung.

### Generierter Code — Spring Boot

**AgentConfig.kt:**

```kotlin
@Configuration
class AgentConfig(
    @Value("\${bedrock.access-key}") private val accessKey: String,
    @Value("\${bedrock.secret-key}") private val secretKey: String
) {
    @Bean @Primary
    fun agent(): AIAgent = AIAgent(
        promptExecutor = simpleBedrockExecutor(
            awsAccessKeyId = accessKey,
            awsSecretAccessKey = secretKey,
            settings = BedrockClientSettings(
                region = System.getenv("AWS_BEDROCK_REGION") ?: "us-west-2"
            )
        ),
        systemPrompt = "...",
        llmModel = BedrockModels.AnthropicClaude4_5Sonnet,
        toolRegistry = createToolRegistry()
    )
}
```

**application.properties:**

```
bedrock.access-key=${AWS_BEDROCK_ACCESS_KEY:}
bedrock.secret-key=${AWS_BEDROCK_SECRET_ACCESS_KEY:}
```

**build.gradle.kts** — Bedrock-Client Dependency:

```kotlin
implementation("ai.koog.prompt:prompt-executor-bedrock-client:0.7.2")
```

### Generierter Code — KMP Library

Die `AgentFactory.kt` aendert sich nicht — der Consumer bringt seinen eigenen Executor mit. Die `build.gradle.kts` muss die Bedrock-Client Dependency enthalten wenn der Agent Bedrock nutzt.

## Frontend-Aenderungen

### ModelProvider Type

```typescript
export type ModelProvider = "openai" | "anthropic" | "google" | "bedrock";
```

### Provider-Dropdown

In 4 Komponenten (`AgentProperties`, `SubAgentProperties`, `GraphAgentProperties`, `PlannerProperties`):

```tsx
<SelectItem value="openai">OpenAI</SelectItem>
<SelectItem value="anthropic">Anthropic</SelectItem>
<SelectItem value="google">Google</SelectItem>
<SelectItem value="bedrock">AWS Bedrock</SelectItem>
```

**Einschraenkung:** Bedrock-Option ist nur sichtbar wenn `activeBackend !== "ktor-wasm"`.

### Model-ID Dropdown

Wenn `bedrock` gewaehlt, zeigt das Model-ID-Feld die 8 Modelle als Optionen:

```
claude-4.5-sonnet | claude-4-sonnet | claude-4-opus
nova-pro | nova-lite | nova-micro
llama-3.3-70b | llama-3.1-405b
```

### Client-seitige Validation

`validation.ts` akzeptiert `"bedrock"` als gueltigen `modelProvider`.

## Betroffene Dateien

### Backend

| Datei | Aenderung |
|---|---|
| `export/model/KoogModel.kt` | `BEDROCK` zu `ModelProvider` enum |
| `export/generator/AgentGenerator.kt` | Bedrock-Executor-Erzeugung, `mapModelId()` Case, Bedrock-Imports |
| `export/generator/ktor/KtorMainGenerator.kt` | `mapModelId()` Case (Executor-Erzeugung nicht noetig — Bedrock nicht auf Wasm) |
| `export/generator/lib/AgentFactoryGenerator.kt` | `mapModelId()` Case |
| `export/generator/BuildGradleGenerator.kt` | Bedrock-Client Dependency wenn Provider == BEDROCK |
| `export/generator/ApplicationPropertiesGenerator.kt` | `bedrock.access-key` und `bedrock.secret-key` Properties |
| `export/generator/lib/LibBuildGradleGenerator.kt` | Bedrock-Client Dependency wenn Provider == BEDROCK |

### Frontend

| Datei | Aenderung |
|---|---|
| `types/koog.ts` | `"bedrock"` zu `ModelProvider` Type |
| `components/panels/AgentProperties.tsx` | Bedrock SelectItem + Model-Optionen |
| `components/panels/SubAgentProperties.tsx` | Bedrock SelectItem + Model-Optionen |
| `components/panels/GraphAgentProperties.tsx` | Bedrock SelectItem + Model-Optionen |
| `components/planner/SettingsTab.tsx` | Bedrock SelectItem + Model-Optionen |
| `lib/validation.ts` | `"bedrock"` als gueltig akzeptieren |
| `store/editorStore.ts` | Default-Model-IDs fuer Bedrock |

### Tests

| Datei | Aenderung |
|---|---|
| `ExportServiceTest.kt` | Test mit Bedrock-Agent |
| `AgentGeneratorTest.kt` (falls vorhanden) | Bedrock-Executor-Code pruefen |

## Platform-Einschraenkungen

| Backend | Bedrock verfuegbar? | Grund |
|---|---|---|
| Spring Boot (JVM) | Ja | Bedrock SDK ist JVM |
| Spring Boot (Native) | Ja | GraalVM kompatibel |
| KMP Library (JVM Target) | Ja | JVM Dependency |
| KMP Library (WasmJS/JS/iOS Target) | Nein | Kein Bedrock SDK fuer diese Targets |
| Ktor/Wasm | Nein | Bedrock SDK ist JVM-only |

## Akzeptanzkriterien

- [ ] `ModelProvider.BEDROCK` existiert mit korrektem Import-Package und Executor-Factory
- [ ] `mapModelId()` mappt alle 8 Bedrock-Modelle korrekt
- [ ] Generierter Spring Boot Code nutzt `simpleBedrockExecutor` mit 2 Keys + `BedrockClientSettings`
- [ ] `application.properties` enthaelt `bedrock.access-key` und `bedrock.secret-key`
- [ ] `build.gradle.kts` enthaelt `prompt-executor-bedrock-client` Dependency bei Bedrock
- [ ] Frontend zeigt "AWS Bedrock" im Provider-Dropdown (nicht bei Ktor/Wasm Backend)
- [ ] Model-Dropdown zeigt 8 Bedrock-Modelle wenn Bedrock gewaehlt
- [ ] KMP Library `build.gradle.kts` enthaelt Bedrock-Dependency wenn Provider == BEDROCK
- [ ] Bestehende Provider (OpenAI, Anthropic, Google) bleiben unberuehrt
- [ ] Client-seitige Validation akzeptiert `"bedrock"`
