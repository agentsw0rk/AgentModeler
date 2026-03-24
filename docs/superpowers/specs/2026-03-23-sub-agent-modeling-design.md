# Sub-Agent-Modellierung — Design Spec

**Datum:** 2026-03-23
**Phase:** Beta
**Status:** Approved
**Abhaengigkeiten:** Keine (baut auf MVP auf)

---

## Uebersicht

Der Koog Agent Editor wird um einen neuen Node-Typ `sub-agent` erweitert. Ein Haupt-Agent kann Aufgaben an spezialisierte Sub-Agents delegieren, die als Agent-Tools im Koog-Framework registriert werden. Sub-Agents koennen eigene Tools haben.

## Design-Entscheidungen

| Entscheidung | Ergebnis | Begruendung |
|---|---|---|
| Delegationsstrategien | UI zeigt `delegationMode`, Export nutzt nur `automatic` | Koog-Pattern unterstuetzt automatic nativ; andere Modi koennen spaeter ergaenzt werden |
| Eigene Tools fuer Sub-Agents | Ja | Ein Sub-Agent ohne Tools ist kaum nuetzlicher als ein Prompt |
| Connection-Richtung | Sub-Agent → Agent (wie Tool → Agent) | Konsistent mit bestehendem Pattern; spiegelt Koog-Realitaet (Sub-Agent = Tool) |
| Haupt-Agent vs. Sub-Agent | Eigenstaendiger Node-Typ `sub-agent` | Klare Trennung, einfache Validierung, unabhaengig erweiterbar |
| Verschachtelung | Keine (flache Hierarchie) | Deckt die meisten Use Cases ab; Lockerung spaeter moeglich ohne Breaking Changes |
| Implementierungsansatz | Neuer Node-Typ durch alle Schichten | Folgt dem Builtin-Tool-Pattern; konsistent mit bestehender Architektur |

## Datenmodell

### Frontend-Typen

**`types/graph.ts`** — Erweiterung NodeType:

```typescript
type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent"
```

**`types/koog.ts`** — Neue Config:

```typescript
interface SubAgentConfig {
  name: string              // required — wird zu agentName im Export
  description: string       // required — wird zu agentDescription
  systemPrompt: string      // required
  modelProvider: string     // "openai" | "anthropic" | "google"
  modelId?: string
  temperature?: number
  delegationMode: string    // "automatic" (aktiv) | "manual" | "fallback" | "specialized" (coming soon)
  inputDescription: string  // required — wird zu inputDescription im createAgentTool
}
```

### Backend-Modell

**`KoogModel.kt`** — Neues Modell:

```kotlin
data class KoogSubAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val delegationMode: String,
    val inputDescription: String,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>
)
```

**`KoogModel.kt`** — Erweiterung KoogAgent:

```kotlin
data class KoogAgent(
    // ... bestehende Felder ...
    val subAgents: List<KoogSubAgent> = emptyList()
)
```

### Adapter

**`GraphToKoogAdapter`** — Erweiterungen:
- Erkennt `sub-agent` Nodes
- Findet Tools, die an den Sub-Agent (nicht an den Haupt-Agent) verbunden sind, ueber Edges
- Baut `KoogSubAgent` mit eigenen Tool-Listen

## Canvas-Layout

### Visuelles Konzept

- **Sub-Agent-Nodes**: Lila (#a855f7), 220x100, Input-Port (fuer Tools) + Output-Port (zum Agent)
- **Connections**: Gestrichelte Linien fuer Delegation (Sub-Agent → Agent), durchgezogene fuer Tool-Bindings
- **Legende**: Agent (blau), Sub-Agent (lila), Tool (gruen), Built-in (orange)

### Connection-Regeln

| Source | Target | Erlaubt |
|---|---|---|
| Tool / Builtin-Tool | Agent | Ja (bestehend) |
| Tool / Builtin-Tool | Sub-Agent | Ja (neu) |
| Sub-Agent | Agent | Ja (neu) |
| Sub-Agent | Sub-Agent | Nein |
| Agent | * | Nein (Agent hat keinen Output) |

### Einschraenkung

- Ein Tool kann nur an EINEN Agent oder Sub-Agent verbunden sein, nicht an beide gleichzeitig.

## Code-Generierung (Export)

### Neuer Generator: SubAgentGenerator.kt

Generiert fuer jeden Sub-Agent eine eigene Kotlin-Datei:

```kotlin
// generated/subagents/ResearchAgentSubAgent.kt
package generated.subagents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.models.AnthropicModels

fun createResearchAgent() = AIAgent(
    promptExecutor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY")),
    systemPrompt = "You are a research specialist...",
    llmModel = AnthropicModels.Sonnet_4,
    toolRegistry = createResearchAgentToolRegistry()
)
```

### RegistryGenerator-Erweiterung

Sub-Agents werden im Haupt-Agent ToolRegistry als Agent-Tools registriert:

```kotlin
// generated/ToolRegistry.kt
fun createToolRegistry(): ToolRegistry = ToolRegistry {
    // Bestehende custom tools
    tools(WeatherToolToolSet())

    // Sub-Agents als Tools
    tool(
        createResearchAgent().asTool(
            agentName = "ResearchAgent",
            agentDescription = "Researches travel destinations and activities",
            inputDescription = "A travel destination or topic to research"
        )
    )
}
```

### Sub-Agent eigene ToolRegistries

Jeder Sub-Agent mit Tools bekommt eine eigene Registry-Datei:

```kotlin
// generated/subagents/ResearchAgentToolRegistry.kt
fun createResearchAgentToolRegistry(): ToolRegistry = ToolRegistry {
    tools(FetchToolToolSet())
}
```

### Export-ZIP Dateistruktur

```
src/main/kotlin/generated/
  AgentConfig.kt                        (unveraendert)
  ToolRegistry.kt                       (erweitert um Sub-Agent-Tools)
  ChatController.kt                     (unveraendert)
  Application.kt                        (unveraendert)
  tools/
    WeatherToolToolSet.kt               (Haupt-Agent Tools)
  subagents/
    ResearchAgentSubAgent.kt            (Sub-Agent Definition)
    ResearchAgentToolRegistry.kt        (Sub-Agent eigene Tools)
    BookingAgentSubAgent.kt
    BookingAgentToolRegistry.kt
```

**Hinweis:** Das Koog-Pattern `AIAgent.asTool()` bzw. `AIAgentService.fromAgent().createAgentTool()` ist der zentrale Mechanismus. Der genaue API-Aufruf muss gegen die aktuelle Koog-Doku verifiziert werden. Dies sollte als fruehe Aufgabe im Implementierungsplan eingeplant werden, bevor der Generator gebaut wird.

## Frontend-Komponenten

### Neue / geaenderte Dateien

| Datei | Aenderung |
|---|---|
| `rete-nodes.ts` | Neue `SubAgentReteNode` Klasse (Input + Output Port) |
| `SubAgentProperties.tsx` | Neues Properties-Panel mit 8 Feldern |
| `Canvas.tsx` | Sync-Logik fuer neuen Node-Typ + gestrichelte Connections |
| `Toolbar.tsx` | "+Sub-Agent" Button |
| `PropertiesPanel.tsx` | Routing auf SubAgentProperties |
| `validation.ts` | Sub-Agent Validierungsregeln |
| `editorStore.ts` | `addNode("sub-agent", ...)` mit Defaults |

### SubAgentReteNode

- Input-Port: `ToolSocket` (empfaengt Tools, wie AgentReteNode)
- Output-Port: `ToolSocket` (verbindet zum Haupt-Agent, wie ToolReteNode)
- Groesse: 220x100 (wie Agent-Node)
- Label: Sub-Agent Name

### SubAgentProperties.tsx

| Feld | Typ | Required | Hinweis |
|---|---|---|---|
| `name` | Input | Ja | |
| `description` | Textarea | Ja | Platzhalter: "Beschreibt was dieser Sub-Agent kann" |
| `systemPrompt` | Textarea | Ja | |
| `inputDescription` | Input | Ja | Platzhalter: "Was erwartet der Sub-Agent als Eingabe?" |
| `modelProvider` | Select | Ja | openai / anthropic / google |
| `modelId` | Input | Nein | |
| `temperature` | Input number | Nein | 0-2 |
| `delegationMode` | Select | Ja | automatic aktiv; manual/fallback/specialized disabled mit "coming soon" |

### Gestrichelte Connections

In `Canvas.tsx`: wenn Source ein `sub-agent` Node ist, wird die Connection mit `stroke-dasharray` gerendert.

## Validierung

### Frontend (`validation.ts`)

| Regel | Severity | Message |
|---|---|---|
| Sub-Agent `name` leer | error | "Sub-Agent requires a name" |
| Sub-Agent `description` leer | error | "Sub-Agent requires a description" |
| Sub-Agent `systemPrompt` leer | error | "Sub-Agent requires a system prompt" |
| Sub-Agent `inputDescription` leer | error | "Sub-Agent requires an input description" |
| Sub-Agent nicht mit Agent verbunden | error | "Sub-Agent must be connected to the main Agent" |
| Sub-Agent mit Sub-Agent verbunden | error | "Sub-Agent cannot delegate to another Sub-Agent" |
| Tool mit Agent UND Sub-Agent verbunden | error | "Tool can only be connected to one Agent or Sub-Agent" |
| Sub-Agent ohne Tools | warning | "Sub-Agent has no tools — consider adding tools for specialization" |

### Backend (`GraphValidator.kt`)

Gleiche Regeln wie Frontend, zusaetzlich:
- Bestehende "Only single agent" Regel bleibt (zaehlt nur `type == "agent"`, nicht `"sub-agent"`)
- Safety-Net Zyklen-Check fuer Sub-Agent-Verbindungen

## Abgrenzung

### In Scope

- Neuer Node-Typ `sub-agent` durch alle Schichten
- Backend: KoogSubAgent Modell, Adapter, SubAgentGenerator, Registry-Erweiterung
- Export: Sub-Agent-Dateien in `subagents/`-Unterordner
- Gestrichelte Connections fuer visuelle Unterscheidung
- Validierung Frontend + Backend

### Out of Scope

- Delegationsstrategien ausser `automatic` (UI vorbereitet, Export nur automatic)
- Verschachtelte Sub-Agents
- Sub-Agent ↔ Sub-Agent Kommunikation
- Live-Preview / Testausfuehrung von Sub-Agent-Setups
- Undo/Redo fuer Sub-Agent-Operationen (kommt mit Undo/Redo Feature)

## Testbarkeit

- **Backend Unit-Tests:** Fuer jeden geaenderten/neuen Generator, Adapter, Validator (folgt bestehendem Pattern)
- **Frontend:** Manuelle Testszenarien (wie bei Builtin-Tools)
- **Integration:** ExportServiceTest mit Sub-Agent Graph → ZIP mit korrekter Dateistruktur
