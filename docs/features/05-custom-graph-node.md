# Feature 05: Custom Node im Graph-Editor

## Problem

Koog erlaubt benutzerdefinierte Nodes mit `node<Input, Output> { ... }`. Damit kann man beliebige Kotlin-Logik in den Graphen einbauen — z.B. Datenvalidierung, Format-Konvertierung, Intent-Klassifikation. Der Graph-Editor bietet das noch nicht.

## Abhängigkeit

Benötigt Feature 01 (Code-Editor) für die Inline-Code-Bearbeitung.

## Lösung

Neuer Node-Typ `node-custom` im Graph-Editor mit konfigurierbarem Input-Typ, Output-Typ und Kotlin-Body.

## Scope

### Frontend — Neuer Node-Typ `node-custom`

**Properties Panel:**

| Feld | Typ | Beschreibung |
|------|-----|-------------|
| Label | String | Name des Nodes (wird zur Kotlin-Variable) |
| Input Type | Select | `String`, `Message.Response`, `ReceivedToolResult`, `List<Message.Response>`, `Any` |
| Output Type | Select | Gleiche Optionen wie Input Type |
| Body | Code-Editor | Kotlin-Lambda-Body: `{ input -> ... }` |

**Beispiel — Intent Classifier:**
```
Label: classifyIntent
Input Type: String
Output Type: String
Body:
  val intent = llm.writeSession {
      appendPrompt { user("Classify: $input") }
      requestLLM()
  }
  intent.content ?: "unknown"
```

### Backend — Code-Generierung

Generierter Code für obiges Beispiel:
```kotlin
val classifyIntent by node<String, String> { input ->
    val intent = llm.writeSession {
        appendPrompt { user("Classify: $input") }
        requestLLM()
    }
    intent.content ?: "unknown"
}
```

**Import:** `import ai.koog.agents.core.dsl.builder.node`

### Edge-Typ-Inferenz

Custom Nodes haben frei wählbare Typen. `autoTypeEdge()` muss erweitert werden:
- Custom → Custom: `onCondition` (default)
- Custom → `node-finish`: basierend auf Output-Typ
- `node-llm-request` → Custom: `onAssistantMessage` wenn Input=String, `onToolCall` wenn Input=Tool.Call

### Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `frontend/lib/strategy-rete-nodes.ts` | `CustomReteNode` Klasse |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | `createReteNode()`, `autoTypeEdge()` |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Properties für Custom Node |
| `frontend/types/graph-agent.ts` | `GraphStrategyNodeData` um inputType/outputType/body erweitern |
| `backend: GraphAgentGenerator.kt` | `node-custom` → `node<I, O> { body }` generieren |
| `backend: GraphAgentModel.kt` | `inputType`, `outputType`, `body` auf `KoogStrategyNode` |
| `backend: GraphToKoogAdapter.kt` | Neue Felder aus Node-Data extrahieren |

## Akzeptanzkriterien

- [ ] Custom Node im Graph-Editor platzierbar
- [ ] Input-Typ und Output-Typ konfigurierbar
- [ ] Kotlin-Body im Code-Editor editierbar
- [ ] Generierter Code erzeugt `val name by node<I, O> { body }`
- [ ] Edge-Typen zwischen Custom Nodes und vordefinierten Nodes korrekt inferiert
- [ ] Generierter Code kompiliert mit Koog 0.7.2
