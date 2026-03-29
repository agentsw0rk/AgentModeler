Implementiere ein Feature für: Code-Editor für Tool-Bodies

## Problem

Das aktuelle Body-Feld für Tool-Funktionen ist ein einfaches `<textarea>`. Für die Banking-Tutorial-Tools (`chooseRecipient`, `sendMoney`) mit 10-20 Zeilen Kotlin braucht es einen richtigen Code-Editor.

## Lösung

Ersetze das `<textarea>` für `returnType` und `body` Felder in der Tool-Funktion durch einen Code-Editor mit Syntax-Highlighting.

## Scope

### Frontend

- **Code-Editor-Komponente** basierend auf [CodeMirror 6](https://codemirror.net/) oder [Monaco Editor](https://microsoft.github.io/monaco-editor/) (leichtgewichtiger: CodeMirror)
  - Kotlin Syntax-Highlighting
  - Zeilennummern
  - Auto-Indentation
  - Bracket-Matching
  - Mindesthöhe 120px, resizable
- **Integration** im Properties Panel:
  - `body`-Feld bei Tool-Funktionen ersetzen
  - `condition`-Feld bei Graph-Agent Edge (Vorbereitung für Feature 03)
  - `condition`-Feld bei Condition-Nodes

### Backend

Keine Änderungen nötig — der `body`-String wird bereits 1:1 in den generierten Code übernommen.

## Betroffene Dateien

| Datei | Änderung |
|-------|----------|
| `frontend/package.json` | CodeMirror Dependencies hinzufügen |
| `frontend/components/shared/CodeEditor.tsx` | Neue Komponente |
| `frontend/components/panels/PropertiesPanel.tsx` | `<textarea>` → `<CodeEditor>` für body/condition |

## Akzeptanzkriterien

- [ ] Tool-Body-Feld zeigt Kotlin Syntax-Highlighting
- [ ] Zeilennummern und Bracket-Matching funktionieren
- [ ] Mehrzeiliger Code ist komfortabel editierbar (min 120px Höhe)
- [ ] Bestehende Body-Werte werden korrekt geladen und gespeichert
- [ ] Editor funktioniert im Dark Theme
