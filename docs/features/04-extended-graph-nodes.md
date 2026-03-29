# Feature 04: Erweiterte Graph-Node-Typen

## Problem

Der Graph-Editor unterstützt nur 6 Node-Typen. Koog bietet weitere vordefinierte Nodes, die für realistische Strategien wichtig sind.

## Lösung

Schrittweise neue Node-Typen im Graph-Editor hinzufügen.

## Neue Node-Typen

### Phase A: Wichtig für Tool-Loops

| Node-Typ | Koog-Funktion | Input → Output | Beschreibung |
|----------|---------------|----------------|-------------|
| `node-execute-multiple-tools` | `nodeExecuteMultipleTools()` | `List<Tool.Call> → List<ReceivedToolResult>` | Führt mehrere Tools parallel aus |
| `node-send-multiple-results` | `nodeLLMSendMultipleToolResults()` | `List<ReceivedToolResult> → Message.Response` | Sendet mehrere Tool-Ergebnisse ans LLM |

### Phase B: History & Streaming

| Node-Typ | Koog-Funktion | Input → Output | Beschreibung |
|----------|---------------|----------------|-------------|
| `node-compress-history` | `nodeLLMCompressHistory()` | `T → T` | Komprimiert die Prompt-History bei langen Konversationen |
| `node-llm-streaming` | `nodeLLMRequestStreaming()` | `String → Flow<StreamFrame>` | LLM-Request mit Streaming-Response |

### Phase C: Structured Output

| Node-Typ | Koog-Funktion | Input → Output | Beschreibung |
|----------|---------------|----------------|-------------|
| `node-llm-structured` | `nodeLLMRequestStructured()` | `String → StructuredResponse<T>` | LLM-Request mit typisiertem Output |
| `node-do-nothing` | `nodeDoNothing()` | `T → T` | Pass-through, nützlich als Platzhalter |

## Scope pro Node-Typ

Jeder neue Node-Typ braucht:

### Frontend
- Rete.js Node-Klasse in `frontend/lib/strategy-rete-nodes.ts`
- Registrierung in `GraphStrategyCanvas.tsx` → `createReteNode()`
- Visuelles Styling (Icon/Farbe) im Strategy-Editor
- Toolbar-Button oder Dropdown-Eintrag zum Hinzufügen

### Backend
- Neuer `type`-String in `KoogStrategyNode`
- Handling in `GraphAgentGenerator.kt`:
  - Node-Declaration: `val name by nodeXyz()`
  - Import-Statement: `import ai.koog.agents.core.dsl.extension.nodeXyz`
- Optional: Properties (z.B. Compression-Strategy für `node-compress-history`)

### Edge-Typ-Inferenz
- `autoTypeEdge()` in `GraphStrategyCanvas.tsx` erweitern für neue Source/Target-Kombinationen
- Neue Edge-Typen falls nötig: `onMultipleToolCalls`, `onToolResult`

## Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `frontend/lib/strategy-rete-nodes.ts` | Neue Rete-Node-Klassen |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | `createReteNode()`, `autoTypeEdge()` |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Toolbar-Buttons für neue Nodes |
| `backend: GraphAgentGenerator.kt` | Node-Declaration + Imports |
| `backend: GraphAgentModel.kt` | Ggf. neue Properties auf `KoogStrategyNode` |

## Akzeptanzkriterien pro Phase

### Phase A
- [ ] `nodeExecuteMultipleTools` und `nodeLLMSendMultipleToolResults` im Graph-Editor
- [ ] Edges mit `onMultipleToolCalls` automatisch erkannt
- [ ] Generierter Code kompiliert mit Koog 0.7.2

### Phase B
- [ ] `nodeLLMCompressHistory` mit konfigurierbarer Strategie
- [ ] `nodeLLMRequestStreaming` Node

### Phase C
- [ ] `nodeLLMRequestStructured` mit Schema-Konfiguration
- [ ] `nodeDoNothing` als Pass-through
