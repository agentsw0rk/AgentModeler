---
name: doc-scout
description: "Durchsucht das docs/-Verzeichnis nach relevanten Markdown- und Textdateien, bevor ein Feature implementiert wird. Nutze diesen Skill immer wenn du per /doc-scout aufgerufen wirst. Er hilft Claude, Architektur-Entscheidungen, Konventionen, API-Beschreibungen und bestehende Spezifikationen zu verstehen, bevor Code geschrieben wird. Funktioniert als Recherche-Schritt vor oder während der Superpowers-Planungsphase (brainstorm/write-plan)."
---

# Doc Scout — Projekt-Dokumentation durchsuchen

## Zweck

Bevor du ein Feature implementierst, durchsuche die Projektdokumentation im `docs/`-Verzeichnis nach relevanten Informationen. Ziel ist es, Architektur, Konventionen, bestehende Spezifikationen und Abhängigkeiten zu verstehen, damit die Implementierung zum Projekt passt.

## Wann diesen Skill nutzen

- Der User ruft `/doc-scout` auf, gefolgt von einer Feature-Beschreibung
- Typischer Aufruf: `/doc-scout User-Authentifizierung mit OAuth2`
- Nutze ihn **vor** dem Coden — idealerweise als Input für Superpowers `/brainstorm` oder `/write-plan`

## Integration mit Superpowers

Dieser Skill ergänzt den Superpowers-Workflow. Der empfohlene Ablauf ist:

1. `/doc-scout <Feature-Beschreibung>` — Relevante Doku finden und zusammenfassen
2. `/superpowers:brainstorm` — Mit dem Doku-Kontext den Ansatz durchdenken
3. `/superpowers:write-plan` — Plan schreiben, der die gefundenen Konventionen berücksichtigt
4. `/superpowers:execute-plan` — Implementieren

Du kannst Doc Scout auch mitten in der Planung nutzen, wenn neue Fragen auftauchen.

## Ablauf

### Schritt 1: Feature-Beschreibung verstehen

Lies die Feature-Beschreibung des Users. Extrahiere daraus Schlüsselbegriffe:
- Fachbegriffe (z.B. "OAuth", "REST", "WebSocket")
- Architektur-Komponenten (z.B. "API", "Middleware", "Database")
- Modulnamen oder Dateinamen die erwähnt werden

### Schritt 2: Dokumentation durchsuchen

Durchsuche das `docs/`-Verzeichnis systematisch:

```bash
# Alle .md und .txt Dateien im docs/-Verzeichnis auflisten
find docs/ -type f \( -name "*.md" -o -name "*.txt" \) 2>/dev/null

# Nach Schlüsselbegriffen in den Dateinamen suchen
find docs/ -type f \( -name "*.md" -o -name "*.txt" \) | grep -i "<begriff>"

# Inhalte nach Schlüsselbegriffen durchsuchen
grep -ril "<begriff>" docs/ --include="*.md" --include="*.txt"
```

Suche gezielt nach:
- **Architektur-Docs**: Dateien mit "architecture", "design", "overview", "structure" im Namen
- **API-Docs**: Dateien mit "api", "endpoint", "route", "schema" im Namen
- **Konventionen**: Dateien mit "convention", "guide", "style", "standard" im Namen
- **Feature-spezifische Docs**: Dateien die thematisch zum gewünschten Feature passen

### Schritt 3: Relevante Dokumente lesen

Lies die gefundenen Dokumente. Priorisiere dabei:
1. Dokumente die direkt zum Feature passen (höchste Priorität)
2. Architektur-Überblicke und Konventionen (hohe Priorität)
3. Verwandte Feature-Dokumentation (mittlere Priorität)

Überspringe Dokumente die offensichtlich irrelevant sind — nicht alles lesen, nur was hilft.

### Schritt 4: Zusammenfassung ausgeben

Gib dem User eine kompakte Zusammenfassung mit diesen Abschnitten:

**Gefundene relevante Dokumente:**
Liste der Dateien die relevant sind, jeweils mit einem Satz was drinsteht.

**Wichtigste Erkenntnisse für das Feature:**
Die konkreten Informationen aus der Doku, die für die Implementierung relevant sind — Konventionen, bestehende Patterns, API-Verträge, Abhängigkeiten.

**Offene Fragen:**
Was in der Doku nicht beantwortet wird und vor der Implementierung geklärt werden sollte.

**Empfehlung:**
Ein kurzer Vorschlag, wie das Feature basierend auf der Doku umgesetzt werden sollte — oder der Hinweis, dass jetzt ein guter Zeitpunkt für `/superpowers:brainstorm` ist.

## Hinweise

- Wenn kein `docs/`-Verzeichnis existiert, sag das dem User und schlag vor, die README.md und andere Root-Level-Dokumente zu prüfen.
- Lies keine riesigen auto-generierten Dateien (z.B. API-Referenzen mit 5000+ Zeilen) komplett — scanne sie nach relevanten Abschnitten.
- Halte die Zusammenfassung auf das Feature fokussiert. Kein allgemeiner Projekt-Überblick, sondern das was für die anstehende Arbeit zählt.
