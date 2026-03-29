# Feature 03: Edge-Transformation im UI

## Problem

Der `GraphAgentGenerator` unterstützt bereits `transformed { ... }` auf Edges, aber das UI bietet kein Feld dafür. Koog nutzt Transformationen z.B. um Typen zu konvertieren: `transformed { it.uppercase() }`.

## Lösung

Wenn eine Edge im Graph-Editor selektiert wird, zeigt das Properties Panel Felder für **Condition** und **Transformation** an.

## Scope

### Frontend

- **Edge-Selektion** im Graph Strategy Editor:
  - Klick auf eine Edge selektiert sie (wie bei Nodes)
  - Properties Panel zeigt Edge-Properties:
    - **Edge Type** (readonly): `onToolCall`, `onAssistantMessage`, `onCondition`
    - **Condition** (Code-Editor, nur bei `onCondition`): Lambda-Body
    - **Transformation** (Code-Editor, optional): Lambda-Body für `transformed { ... }`

- **Datenmodell** — `GraphStrategyEdge` hat bereits `condition?` und `transformation?` Felder in `frontend/types/graph-agent.ts`

### Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Edge-Selection-Handler, Edge-Properties im Panel |
| `frontend/components/graph-agent/GraphStrategyCanvas.tsx` | Edge-Click-Event an Parent weiterleiten |
| `frontend/components/panels/PropertiesPanel.tsx` | Edge-Properties rendern (Type, Condition, Transformation) |

### Backend

Keine Änderungen — `GraphAgentGenerator.formatEdgeSuffix()` unterstützt `transformation` bereits.

## Akzeptanzkriterien

- [ ] Edge im Graph-Editor per Klick selektierbar
- [ ] Properties Panel zeigt Edge-Typ, Condition und Transformation
- [ ] Condition editierbar bei `onCondition`-Edges
- [ ] Transformation editierbar bei allen Edge-Typen
- [ ] Generierter Code enthält `onCondition { ... } transformed { ... }` korrekt
