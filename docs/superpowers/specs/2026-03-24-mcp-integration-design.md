# MCP Integration — Design Spec

**Datum:** 2026-03-24
**Phase:** Beta
**Status:** Approved
**Abhaengigkeiten:** Keine (baut auf MVP + Sub-Agent auf)

---

## Uebersicht

Der Koog Agent Editor wird um MCP (Model Context Protocol) Integration erweitert. Ein neuer `mcp-server` Node-Typ erlaubt Nutzern, MCP-Server zu konfigurieren, deren verfuegbare Tools per Live-Discovery abzurufen, und die gesamte Tool-Registry im Export als Koog MCP-Registry einzubinden.

## Design-Entscheidungen

| Entscheidung | Ergebnis | Begruendung |
|---|---|---|
| Darstellung | Ein MCP-Server-Node (nicht einzelne Tool-Nodes) | Kompakter, MCP-Server hat viele Tools; Tool-Entdeckung passiert zur Laufzeit |
| Discovery | Backend-Proxy, Live-Abruf | Unterstuetzt STDIO + SSE, keine CORS-Probleme, robust |
| Transports | SSE + STDIO | Deckt alle Koog-Beispiele ab; Streamable HTTP spaeter |
| Export | Registry-Merge (`ToolRegistry + mcpRegistry`) | Natives Koog-Pattern, minimaler Code |
| Credentials | Env-Var-Namen fuer Export, temporaere Werte fuer Discovery | Sicher — Werte nie persistiert oder exportiert |
| Binding | Connection wie Tools (Output → Agent/Sub-Agent) | Konsistenz mit bestehendem Connection-Modell |

## Datenmodell

### Frontend-Typen

**`types/mcp.ts`** — Neue Datei:

```typescript
export type McpTransportType = "sse" | "stdio";

export interface McpEnvVar {
  id: string;
  name: string;       // z.B. "GOOGLE_MAPS_API_KEY" — wird exportiert
  value?: string;      // temporaer fuer Discovery — wird NICHT persistiert/exportiert
}

export interface McpDiscoveredTool {
  name: string;
  description: string;
}

export interface McpServerConfig {
  name: string;                          // required
  transportType: McpTransportType;       // required
  url?: string;                          // fuer SSE
  command?: string;                      // fuer STDIO
  args?: string[];                        // fuer STDIO (zusaetzliche Args, z.B. ["--headless"])
  envVars: McpEnvVar[];                  // Env-Var-Namen + temporaere Werte
  discoveredTools: McpDiscoveredTool[];  // nach Discovery befuellt
  lastDiscoveredAt?: string;             // ISO timestamp
}
```

**`types/graph.ts`** — Erweiterung NodeType:

```typescript
type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server";
```

GraphNode data Union wird um `McpServerConfig` erweitert.

### Backend-Modell

**`KoogModel.kt`** — Neue Typen:

```kotlin
data class KoogMcpServer(
    val name: String,
    val transportType: String,    // "sse" | "stdio"
    val url: String?,             // fuer SSE
    val command: String?,         // fuer STDIO
    val args: List<String>?,            // fuer STDIO
    val envVars: List<McpEnvVarName>
)

data class McpEnvVarName(
    val name: String
)
```

**`KoogAgent`** — Erweiterung:

```kotlin
data class KoogAgent(
    // ... bestehende Felder ...
    val mcpServers: List<KoogMcpServer> = emptyList()
)
```

### Persistierung

- `discoveredTools` und `lastDiscoveredAt` werden im JSON-Projekt persistiert (als Cache/Vorschau)
- `McpEnvVar.value` Felder werden NICHT persistiert — beim Laden leer
- `McpEnvVar.name` Felder werden persistiert und exportiert

## Backend — Discovery Proxy

### Endpoint

`POST /api/mcp/discover`

**Request:**
```json
{
  "transportType": "sse",
  "url": "http://localhost:8931",
  "command": null,
  "args": null,
  "envVars": [
    { "name": "GOOGLE_MAPS_API_KEY", "value": "AIza..." }
  ]
}
```

**Response (success):**
```json
{
  "tools": [
    { "name": "geocode", "description": "Convert address to coordinates" },
    { "name": "directions", "description": "Get directions between locations" }
  ]
}
```

**Response (error):**
```json
{
  "error": "Connection refused: http://localhost:8931"
}
```

### Implementierung

Neue Klassen:
- `McpDiscoveryController` — REST-Endpoint, validiert Request
- `McpDiscoveryService` — MCP-Client-Logik

Service-Verhalten:
- **SSE:** Nutzt Koog `McpToolRegistryProvider.defaultSseTransport(url)`
- **STDIO:** Startet Prozess via `ProcessBuilder(listOf(command) + args)`, setzt Env-Vars auf dem ProcessBuilder, nutzt `McpToolRegistryProvider.defaultStdioTransport(process)`
- Ruft Tool-Liste ab, extrahiert Namen + Beschreibungen
- Timeout: 10 Sekunden
- Verbindung wird nach Discovery sofort geschlossen (kein offener State)
- Env-Var-Werte werden nur fuer den Request verwendet, nirgends persistiert

### Request/Response DTOs

```kotlin
data class McpDiscoveryRequest(
    val transportType: String,
    val url: String?,
    val command: String?,
    val args: List<String>?,
    val envVars: List<McpEnvVarDto>
)

data class McpEnvVarDto(
    val name: String,
    val value: String?
)

data class McpDiscoveryResponse(
    val tools: List<McpToolDto>?,
    val error: String?
)

data class McpToolDto(
    val name: String,
    val description: String
)
```

## Code-Generierung (Export)

### Neuer Generator: McpServerGenerator.kt

Generiert fuer jeden MCP-Server eine Kotlin-Datei.

**API-Wahl:** Die Spec nutzt `McpToolRegistryProvider.fromTransport()` statt Convenience-Methoden wie `fromSseUrl()`, weil STDIO Env-Var-Injection ueber ProcessBuilder benoetigt. Fuer Konsistenz nutzen beide Transports die gleiche `fromTransport`-API. Waehrend der Implementierung sollte geprueft werden ob fuer SSE eine Convenience-Methode verfuegbar und einfacher ist — falls ja, bevorzugt verwenden.

**SSE-Beispiel:**
```kotlin
// generated/mcp/GoogleMapsMcp.kt
package generated.mcp

import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo

suspend fun createGoogleMapsRegistry(): ai.koog.agents.core.tools.ToolRegistry {
    val transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
    return McpToolRegistryProvider.fromTransport(
        transport = transport,
        serverInfo = McpServerInfo(url = "http://localhost:8931"),
        name = "google-maps",
        version = "1.0.0"
    )
}
```

**STDIO-Beispiel:**
```kotlin
// generated/mcp/PlaywrightMcp.kt
package generated.mcp

import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo

// Required environment variables: PLAYWRIGHT_KEY
suspend fun createPlaywrightRegistry(): ai.koog.agents.core.tools.ToolRegistry {
    val process = ProcessBuilder("npx", "@playwright/mcp", "--headless")
        .apply {
            environment()["PLAYWRIGHT_KEY"] = System.getenv("PLAYWRIGHT_KEY")
        }
        .start()
    val transport = McpToolRegistryProvider.defaultStdioTransport(process)
    return McpToolRegistryProvider.fromTransport(
        transport = transport,
        serverInfo = McpServerInfo(command = "npx @playwright/mcp --headless"),
        name = "playwright",
        version = "1.0.0"
    )
}
```

Die `ProcessBuilder`-Argumente werden aus `command` (erster Eintrag) + `args` (weitere Eintraege) zusammengesetzt. Ein Kommentar ueber der Funktion listet die benoetigten Env-Vars auf.

### RegistryGenerator-Erweiterung

**Ohne MCP-Server:** `createToolRegistry()` bleibt non-suspend (keine Aenderung am bestehenden Verhalten).

**Mit MCP-Servern:** Die Funktion wird `suspend` und mergt MCP-Registries per `+` Operator:

```kotlin
suspend fun createToolRegistry(): ToolRegistry {
    val baseRegistry = ToolRegistry {
        tools(WeatherToolToolSet())
    }

    val googleMapsRegistry = createGoogleMapsRegistry()
    val playwrightRegistry = createPlaywrightRegistry()

    return baseRegistry + googleMapsRegistry + playwrightRegistry
}
```

Wenn keine custom/builtin Tools vorhanden sind aber MCP-Server existieren, wird nur die MCP-Registry zurueckgegeben (kein leeres `ToolRegistry {}`).

### AgentGenerator-Anpassung

**Nur wenn MCP-Server vorhanden:** Die Agent-Erstellung wird in `runBlocking` gewrappt, da `createToolRegistry()` suspend ist:

```kotlin
@Configuration
class AgentConfig {
    @Bean
    fun agent(): AIAgent<String, String> = runBlocking {
        AIAgent(
            promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
            systemPrompt = "...",
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = createToolRegistry()
        )
    }
}
```

Der `runBlocking`-Import (`import kotlinx.coroutines.runBlocking`) wird nur bei MCP-Servern hinzugefuegt.

**Ohne MCP-Server:** AgentGenerator bleibt unveraendert (kein `runBlocking`).

### BuildGradleGenerator-Erweiterung

Wenn MCP-Server vorhanden, wird die Koog-MCP-Dependency ergaenzt:
```kotlin
implementation("ai.koog:agents-ext-mcp:$koogVersion")
```

### Export-ZIP Dateistruktur

```
src/main/kotlin/generated/
  AgentConfig.kt                (angepasst fuer suspend)
  ToolRegistry.kt               (suspend + merge)
  mcp/
    GoogleMapsMcp.kt
    PlaywrightMcp.kt
  tools/
    ...
```

## Canvas-Layout

### Visuelles Konzept

- **MCP-Server-Nodes**: Cyan (#0ea5e9), 240x120+, Output-Port zum Agent
- **Node zeigt:** Server-Name, Transport + Endpoint, entdeckte Tools als Tags (max 4-5, dann "+N")
- **Nicht verbundene** Nodes: ausgegraut mit gestricheltem Rand
- **Nach Discovery:** Tool-Tags erscheinen auf dem Node

### Connection-Regeln

| Source | Target | Erlaubt |
|---|---|---|
| MCP-Server | Agent | Ja |
| MCP-Server | Sub-Agent | Ja |
| MCP-Server | MCP-Server | Nein |
| MCP-Server | Tool | Nein |

## Frontend-Komponenten

### Neue / geaenderte Dateien

| Datei | Aenderung |
|---|---|
| `types/mcp.ts` | Neue Datei: McpServerConfig, McpTransportType, etc. |
| `lib/rete-nodes.ts` | Neue `McpServerReteNode` Klasse |
| `components/panels/McpServerProperties.tsx` | Properties Panel mit Config + Discovery + Tool-Liste |
| `lib/mcp-client.ts` | Fetch-Wrapper fuer `/api/mcp/discover` |
| `store/editorStore.ts` | `addNode("mcp-server", ...)` mit Defaults |
| `lib/rete-setup.ts` | Connection-Regeln: MCP → Agent/SubAgent |
| `components/editor/Canvas.tsx` | Sync fuer MCP-Nodes |
| `lib/validation.ts` | MCP-Server Regeln |
| `components/panels/Toolbar.tsx` | "+MCP Server" Button |
| `components/panels/PropertiesPanel.tsx` | Routing auf McpServerProperties |

### McpServerReteNode

- Output-Port: `ToolSocket` (wie Tool/Builtin-Tool/Sub-Agent)
- Kein Input-Port
- Groesse: 240x120 (breiter als Tool-Nodes fuer Tool-Tags)
- Label: Server-Name

### McpServerProperties.tsx

| Feld | Typ | Bedingung | Required |
|---|---|---|---|
| `name` | Input | immer | Ja |
| `transportType` | Select (SSE/STDIO) | immer | Ja |
| `url` | Input | nur bei SSE | Ja (bei SSE) |
| `command` | Input | nur bei STDIO | Ja (bei STDIO) |
| `args` | Input | nur bei STDIO | Nein |
| Env-Vars | Dynamic Key-Value-Liste | immer | Nein |
| "Discover Tools" Button | Button | immer | — |
| Tool-Liste | Read-only Liste | nach Discovery | — |
| "Re-Discover" Button | Button | nach Discovery | — |

### mcp-client.ts

```typescript
export async function discoverMcpTools(config: McpDiscoveryRequest): Promise<McpDiscoveryResponse>
```

Einfacher Fetch-Wrapper mit Error-Handling fuer den Backend-Proxy.

### Persistierung

- `McpServerConfig.envVars[].value` wird NICHT in JSON geschrieben
- `discoveredTools` und `lastDiscoveredAt` werden als Cache persistiert
- Beim Laden: Env-Var-Values leer, Tool-Liste aus Cache sichtbar

## Validierung

### Frontend (`validation.ts`)

| Regel | Severity | Message |
|---|---|---|
| MCP-Server `name` leer | error | "MCP Server requires a name" |
| MCP-Server `transportType` ungueltig | error | "MCP Server has invalid transport type" |
| SSE: `url` leer | error | "MCP Server (SSE) requires a URL" |
| STDIO: `command` leer | error | "MCP Server (STDIO) requires a command" |
| MCP-Server nicht mit Agent verbunden | error | "MCP Server must be connected to an Agent" |
| MCP-Server ohne entdeckte Tools | warning | "MCP Server has no discovered tools — click Discover to fetch tools" |
| MCP-Server mit Sub-Agent verbunden | warning | "MCP Server connected to Sub-Agent will only be exported for the main Agent" |

### Backend (`GraphValidator.kt`)

Gleiche Regeln (ohne Warning fuer nicht-entdeckte Tools). Zusaetzlich:
- "Tool can only be connected to one Agent or Sub-Agent" gilt auch fuer MCP-Server-Nodes

## Abgrenzung

### In Scope

- Neuer Node-Typ `mcp-server` durch alle Schichten
- Backend: Discovery-Proxy mit SSE + STDIO Support
- Backend: KoogMcpServer Modell, Adapter, McpServerGenerator
- Export: MCP-Dateien in `mcp/`-Unterordner, Registry-Merge
- RegistryGenerator suspend + merge, AgentGenerator Anpassung
- BuildGradleGenerator MCP-Dependency
- Env-Var Handling (Namen exportiert, Werte nur fuer Discovery)

### Out of Scope

- MCP-Server-zu-MCP-Server Verbindungen
- Selektiver Tool-Import (alle Tools eines Servers werden registriert)
- Streamable HTTP Transport
- OAuth-Authentifizierung
- Automatische Re-Discovery
- MCP-Server in Sub-Agent-Export (Connection zum Sub-Agent ist im Canvas erlaubt, aber der Export ignoriert es still — Validation emittiert ein Warning: "MCP Server connected to Sub-Agent will only be exported for the main Agent")

## Testbarkeit

- **Backend Unit-Tests:** McpDiscoveryService (mit Mock), McpServerGenerator, RegistryGenerator (mit MCP), Adapter, Validator
- **Backend Integration:** ExportServiceTest mit MCP-Server Graph → ZIP
- **Frontend:** Manuelle Testszenarien
- **Mock-Modus:** Discovery-Endpoint kann Mock-Responses zurueckgeben wenn kein echter MCP-Server erreichbar ist
