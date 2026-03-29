# Feature 06: Subgraphen im Graph-Editor

## Problem

Das Banking Tutorial nutzt Subgraphen um verschiedene Intent-Pfade zu isolieren (Transfer vs. Analytics). Jeder Subgraph hat eigene Nodes, Edges und optional ein eigenes Tool-Set. Der Modeler unterstützt nur flache Graphen.

## Abhängigkeiten

- Feature 04 (Erweiterte Node-Typen) — für reichhaltigere Subgraph-Inhalte
- Feature 05 (Custom Nodes) — für Intent-Classifier innerhalb von Subgraphen

## Lösung

Neuer Node-Typ `node-subgraph` im Graph-Editor. Ein Subgraph-Node enthält einen verschachtelten Strategy-Graphen (eigene Nodes + Edges) und wird visuell als zusammengeklappter Block dargestellt. Per Doppelklick öffnet sich der innere Graph.

## Koog-Konzept

```kotlin
val transferSubgraph by subgraph<String, String>(
    name = "transfer",
    tools = listOf(sendMoneyTool)
) {
    val llm by nodeLLMRequest()
    val exec by nodeExecuteTool()
    val send by nodeLLMSendToolResult()

    edge(nodeStart forwardTo llm)
    edge(llm forwardTo exec onToolCall { true })
    edge(llm forwardTo nodeFinish onAssistantMessage { true })
    edge(exec forwardTo send)
    edge(send forwardTo exec onToolCall { true })
    edge(send forwardTo nodeFinish onAssistantMessage { true })
}

// Im Hauptgraphen:
edge(classifyIntent forwardTo transferSubgraph onCondition { it == "transfer" })
edge(classifyIntent forwardTo analyticsSubgraph onCondition { it == "analytics" })
edge(transferSubgraph forwardTo nodeFinish)
edge(analyticsSubgraph forwardTo nodeFinish)
```

## Scope

### Frontend — Neuer Node-Typ `node-subgraph`

**Canvas-Darstellung:**
- Zusammengeklappter Block mit Name und Zusammenfassung (Anzahl innere Nodes)
- Doppelklick → öffnet inneren Graph-Editor (wie der bestehende Graph-Agent-Editor)
- Breadcrumb-Navigation: `Hauptgraph > Subgraph: Transfer`

**Properties Panel:**
| Feld | Typ | Beschreibung |
|------|-----|-------------|
| Name | String | Subgraph-Name (Kotlin-Variable) |
| Input Type | Select | Eingangstyp (`String`, `Message.Response`, etc.) |
| Output Type | Select | Ausgangstyp |
| Tool Selection | Select | `ALL` (alle Agent-Tools), `SPECIFIC` (nur bestimmte), `NONE` |
| Tools | Multi-Select | Nur bei `SPECIFIC`: welche Tools verfügbar sind |

**Innerer Graph:**
- Eigene `nodeStart` und `nodeFinish` Nodes
- Gleiche Node-Typen wie Hauptgraph (LLM Request, Execute Tool, etc.)
- Eigenständige Edge-Verwaltung

### Backend — Code-Generierung

| Datei | Änderung |
|-------|----------|
| `GraphAgentModel.kt` | `KoogSubgraphNode` mit innerem `strategyNodes`/`strategyEdges`, inputType, outputType, toolSelection |
| `GraphAgentGenerator.kt` | `subgraph<I, O>("name", toolSelectionStrategy) { ... }` generieren mit inneren Nodes/Edges |
| `GraphToKoogAdapter.kt` | Verschachtelte Subgraph-Daten aus Node-Data extrahieren |

**Generierter Code:**
```kotlin
val transfer by subgraph<String, String>(
    name = "transfer",
    toolSelectionStrategy = ToolSelectionStrategy.Tools(listOf(sendMoneyTool.descriptor))
) {
    val llm by nodeLLMRequest()
    // ... innere Edges
}
```

### Edge-Typ-Inferenz

Subgraph-Nodes verhalten sich wie reguläre Nodes im Hauptgraphen:
- Eingehende Edges: basierend auf Input-Typ des Subgraphen
- Ausgehende Edges: basierend auf Output-Typ des Subgraphen

### Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `frontend/types/graph-agent.ts` | `GraphStrategyNode` um Subgraph-Daten erweitern |
| `frontend/lib/strategy-rete-nodes.ts` | `SubgraphReteNode` Klasse |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | Subgraph-Rendering, Doppelklick-Navigation |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Breadcrumb, Subgraph-Properties, innerer Editor |
| `backend: GraphAgentModel.kt` | Subgraph-Model |
| `backend: GraphAgentGenerator.kt` | Subgraph-Codegen |
| `backend: GraphToKoogAdapter.kt` | Subgraph-Konvertierung |

## Akzeptanzkriterien

- [ ] Subgraph-Node im Graph-Editor platzierbar
- [ ] Doppelklick öffnet inneren Graph-Editor
- [ ] Breadcrumb-Navigation zwischen Haupt- und Subgraph
- [ ] Input/Output-Typ und Tool-Selection konfigurierbar
- [ ] Generierter Code erzeugt `subgraph<I, O> { ... }` mit inneren Nodes/Edges
- [ ] Edges zwischen Subgraph und anderen Nodes im Hauptgraphen funktionieren
- [ ] Banking Tutorial Intent-Routing (Classify → Transfer/Analytics Subgraphen) nachbaubar
- [ ] Generierter Code kompiliert mit Koog 0.7.2
