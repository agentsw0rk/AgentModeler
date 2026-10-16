# Feature 16: Parallel Node im Graph-Editor

## Problem

Koog kann mehrere Nodes gleichzeitig ausfuehren und ihre Ergebnisse zusammenfuehren (`parallel(...) { ... }`, siehe `docs/koog/docs/parallel-node-execution.md`). Der Graph-Editor kennt dafuer keinen Node-Typ. Muster wie "drei Modelle beantworten dieselbe Frage, das beste Ergebnis gewinnt" lassen sich nicht modellieren.

## Ziel

Neuer Node-Typ `node-parallel`, der mehrere bestehende Nodes parallel startet und ihre Ergebnisse mit einer Merge-Strategie zu einem Wert zusammenfuehrt.

1. **Node-Typ `node-parallel`**: im Toolbar, Canvas und Properties Panel des Graph-Editors
2. **Codegen**: `GraphAgentGenerator` erzeugt `val x by parallel<I, O>(a, b, c) { <merge> }`
3. **Validierung**: Frontend und `GraphValidator` pruefen Branches und Merge-Strategie

## Voraussetzungen

| Abhaengigkeit | Status | Blocker? |
|---|---|---|
| Graph-Agent mit Strategy-Editor | Implementiert | Nein |
| Feature 05: Custom Node (`node-custom`, Input-/Output-Typen) | Implementiert | Nein |
| Koog `ai.koog.agents.core.dsl.builder.parallel` (Koog 1.3.0) | Verfuegbar | Nein |

## Architektur

### Datenmodell

`GraphStrategyNodeData` (Frontend) und `KoogStrategyNode` (Backend) bekommen vier optionale Felder. `inputType` und `outputType` gibt es schon (vom Custom Node) und werden wiederverwendet.

```ts
parallelBranches?: string[];   // IDs der Nodes, die parallel laufen (mind. 2)
mergeStrategy?: "selectBy" | "selectByMax" | "selectByIndex" | "fold";
mergeExpression?: string;      // Lambda-Body, z.B. "it.length"
foldInitial?: string;          // nur fuer fold: Kotlin-Ausdruck des Startwerts
```

| Merge-Strategie | Ausdruck (`mergeExpression`) | Generiert |
|---|---|---|
| `selectBy` | Praedikat auf `it` | `selectBy { it.contains("x") }` |
| `selectByMax` | Vergleichswert aus `it` | `selectByMax { it.length }` |
| `selectByIndex` | Index aus `it` (Liste der Ergebnisse) | `selectByIndex { 0 }` |
| `fold` | Body mit `acc, value` | `fold("Start") { acc, value -> acc + value }` |

Die Branches werden per Referenz (Node-IDs) angegeben, nicht ueber Kanten. Kanten in Koog bedeuten "danach ausfuehren". Eine parallele Gabelung ist etwas anderes, und eine eigene Kantenart dafuer waere mehr Code fuer dasselbe Ergebnis.

### Generierter Code

Branch-Nodes werden wie bisher deklariert. Der Parallel-Node wird erst danach deklariert, weil er die Variablen der Branches braucht:

```kotlin
val askOpenAI by node<String, String> { input -> /* ... */ }
val askClaude by node<String, String> { input -> /* ... */ }
val askGemini by node<String, String> { input -> /* ... */ }

val bestAnswer by parallel<String, String>(
    askOpenAI, askClaude, askGemini,
) {
    selectByMax { it.length }
}

edge(nodeStart forwardTo bestAnswer)
edge(bestAnswer forwardTo nodeFinish)
```

Import: `ai.koog.agents.core.dsl.builder.parallel` (nur wenn ein `node-parallel` vorkommt, wie bei den anderen Node-Imports in `GraphAgentGenerator`).

### Frontend

- Toolbar: Eintrag "Parallel" neben "Custom" und "Subgraph"
- Properties Panel: Input/Output-Typ (dieselben Selects wie beim Custom Node), Mehrfachauswahl der Branch-Nodes aus den Nodes des aktuellen Graphen, Select fuer die Merge-Strategie, Code-Editor fuer `mergeExpression`, Textfeld `foldInitial` (nur bei `fold` sichtbar)
- Canvas: gestrichelte Linie vom Parallel-Node zu jedem Branch (nur Anzeige, keine Kante im Modell)

### Validierung

Fehler, wenn:
- weniger als 2 Branches gewaehlt sind
- eine Branch-ID nicht existiert oder auf `node-start`, `node-finish` oder den Parallel-Node selbst zeigt
- ein Branch-Node eigene ein- oder ausgehende Kanten hat (er wird nur ueber `parallel` ausgefuehrt)
- `mergeStrategy` oder `mergeExpression` leer ist, oder bei `fold` `foldInitial` fehlt

## Betroffene Dateien

### Backend

| Datei | Aenderung |
|---|---|
| `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt` | Felder an `KoogStrategyNode` |
| `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt` | Neue Felder durchreichen |
| `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt` | `node-parallel` in `emitNodeDeclaration`, Deklaration nach den Branches, Import |
| `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` | Regeln aus "Validierung" |

### Frontend

| Datei | Aenderung |
|---|---|
| `frontend/types/graph-agent.ts` | `"node-parallel"` in `GraphStrategyNodeType`, neue Felder |
| `frontend/components/graph-agent/GraphStrategyToolbar.tsx` | Eintrag "Parallel" |
| `frontend/components/graph-agent/StrategyNodeProperties.tsx` | Properties-Formular |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | Rendering inkl. gestrichelter Branch-Linien |
| `frontend/lib/graph-agent-validation.ts` | Regeln aus "Validierung" |

### Tests

| Datei | Aenderung |
|---|---|
| `src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt` | Je Merge-Strategie ein Test; Test, dass `parallel` nach den Branches steht |
| `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt` | Ungueltige Branches und fehlende Merge-Felder |

## Platform-Einschraenkungen

| Backend | Verfuegbar? | Grund |
|---|---|---|
| Spring Boot (JVM) | Ja | |
| KMP Library | Ja | `parallel` ist Teil von Koog-Core (multiplatform) |
| Ktor/Wasm | Wie bestehende Graph-Agents | Keine eigene Einschraenkung durch `parallel` |

## Nicht im Scope

- `node-parallel` innerhalb von Subgraphen (`emitSubgraph` hat eine eigene Node-Schleife). Kann als Folge-Feature kommen.
- Zugriff auf den Kontext der Branches (`NodeExecutionResult.context`) im Merge-Ausdruck
- Live-Preview-Visualisierung der parallelen Ausfuehrung

## Akzeptanzkriterien

- [ ] Ein Graph mit `node-parallel` (3 Branches, `selectByMax`) exportiert Code, der kompiliert (Gradle Build Validation, Feature 07)
- [ ] Alle vier Merge-Strategien erzeugen den Koog-DSL-Aufruf aus der Tabelle oben
- [ ] Der `parallel`-Aufruf steht im generierten Code nach den Deklarationen aller Branches
- [ ] Jede Regel aus "Validierung" liefert einen Fehler im Editor und in `GraphValidator`
- [ ] Bestehende Graph-Agents exportieren unveraendert
