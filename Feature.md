# Feature.md — Phase 1 (MVP)

![Phase](https://img.shields.io/badge/Phase-MVP-blue)
![Status](https://img.shields.io/badge/Status-Planned-orange)
![Frontend](https://img.shields.io/badge/Frontend-Next.js-black)
![UI](https://img.shields.io/badge/UI-shadcn%2Fui-111827)
![Canvas](https://img.shields.io/badge/Canvas-Rete.js-6d28d9)
![Backend](https://img.shields.io/badge/Backend-Kotlin-7f52ff)
![Framework](https://img.shields.io/badge/Agent_Framework-JetBrains_Koog-00a3ff)
![Export](https://img.shields.io/badge/Export-Kotlin_Code-success)
![Persistence](https://img.shields.io/badge/Persistence-JSON-lightgrey)

# Koog Agent Editor — Phase 1 (MVP)

Phase 1 liefert den ersten nutzbaren Produktkern:  
Ein Entwickler modelliert einen einfachen Koog-Agenten visuell im Canvas, verbindet Tools mit dem Agenten, validiert den Graphen und exportiert daraus kompilierbaren Kotlin-Code.

Der MVP-Fokus in der Roadmap liegt auf genau diesen Bausteinen: **Canvas Engine**, **Agent-Node**, **Tool-Node**, **Kotlin-Export**, **Graph-Validierung**, **lokale Persistenz** und **UI-/Export-Polish**. Die geplante Umsetzung erstreckt sich über **Wochen 1–6**.

---

## Technische Leitplanken

## Frontend
- Next.js App Router
- TypeScript
- shadcn/ui
- Rete.js
- Zustand oder Redux Toolkit für Editor-State
- Zod für Form-/Schema-Validierung

## Backend / Export
- Kotlin-Modul für Codegenerierung
- Koog-kompatible Templates / Generatorlogik
- JSON-Schema für Austauschformat

## Architekturprinzipien
- UI-Modell ist unabhängig vom Kotlin-Exportmodell
- Adapter-Layer zwischen Canvas-Graph und Koog-DSL
- Exportlogik testbar und deterministisch
- Projektformat versionieren

---

## Ziel von Phase 1

Der MVP ist erfolgreich, wenn ein Entwickler in wenigen Minuten:

1. ein neues Projekt anlegt,
2. einen Agenten auf das Canvas zieht,
3. ein oder mehrere Tools anlegt und verbindet,
4. Konfigurationsfehler direkt sieht,
5. und per Export lauffähigen Kotlin-Code erhält.

Das Roadmap-Ziel formuliert es so: Ein Entwickler soll einen Agenten mit zwei Tools modellieren und in unter fünf Minuten exportieren können.  

---

## Scope

### In Scope
- Node-basierte Canvas-Oberfläche
- Agent-Node
- Tool-Node
- Input-/Output-Grundmodell
- Properties Panel für Node-Konfiguration
- Validierung von Verbindungen und Pflichtfeldern
- Kotlin-Code-Export für Agent + ToolRegistry
- JSON-basierte lokale Persistenz
- Export-Dialog
- Basis-UI mit Next.js + shadcn/ui + Rete.js

### Out of Scope
- Workflow-Loops, Branches, Fallbacks
- Sub-Agents
- MCP-Integration
- Live-Preview / Runtime-Ausführung
- Memory / RAG
- Kollaboration
- IntelliJ-Plugin
- Observability Dashboard

Diese Punkte liegen laut Roadmap in späteren Phasen ab Beta bzw. Production.

---

## Kern-Features

## 1. Canvas Engine
Interaktive graphbasierte Zeichenfläche mit:
- Drag & Drop für Nodes
- Verbindungen zwischen Ports
- Selektion von Nodes und Edges
- Verschieben und Löschen von Nodes
- Zoom / Pan
- Raster oder klarer Arbeitsbereich
- rechte Seitenleiste für Properties

**Technologieentscheidung für dieses Projekt:**
- **Next.js** für App-Struktur und Routing
- **shadcn/ui** für Layout, Panels, Dialoge und Formulare
- **Rete.js** für Node-/Edge-Editor

Die Roadmap fordert in Woche 1–2 genau das Aufsetzen der Canvas Engine mit Node/Edge Rendering und Drag & Drop.

### Akzeptanzkriterien
- Agent-, Tool-, Input- und Output-Nodes können erstellt werden
- Nodes lassen sich frei platzieren
- Verbindungen können per UI erstellt und entfernt werden
- Canvas-Zustand bleibt im Frontend konsistent
- Properties Panel reagiert auf Selektion

---

## 2. Agent Node
Ein Agent-Node repräsentiert einen exportierbaren Koog-Agenten.

### Konfigurierbare Felder
- `name`
- `systemPrompt`
- `modelProvider` (`openai`, `anthropic`, `google`)
- optional `modelId`
- optional `temperature`

Die Roadmap nennt Name, System-Prompt und LLM-Modell explizit als Kern des Agent-Nodes.  

### Akzeptanzkriterien
- Agent kann im Properties Panel bearbeitet werden
- Pflichtfelder werden validiert
- Provider-Auswahl beeinflusst spätere Exportlogik
- Es kann mindestens ein Tool mit dem Agenten verbunden werden

---

## 3. Tool Node
Ein Tool-Node repräsentiert ein Tool, das durch den Agenten genutzt werden kann.

### Konfigurierbare Felder
- `name`
- `description`
- `parameters[]`
    - `name`
    - `type`
    - `description`
    - `required`
- optional `handlerStubName`

Die Roadmap nennt Tool-Name sowie Parameter mit Typ und Beschreibung als MVP-Bestandteil.  

### Akzeptanzkriterien
- Tool-Node kann erstellt, bearbeitet und gelöscht werden
- Parameter können hinzugefügt und entfernt werden
- Tool kann mit genau einem Agenten verbunden werden
- Ungültige oder unvollständige Tools werden markiert

---

## 4. Graph-Validierung
Der MVP braucht frühe, verständliche Fehlerhinweise.

### Validierungsregeln
- Tool ohne verbundenen Agenten ist ungültig
- Agent ohne Namen ist ungültig
- Agent ohne Prompt ist ungültig
- Tool ohne Namen ist ungültig
- Parameter ohne Namen oder Typ sind ungültig
- Ungültige Verbindungstypen sind nicht erlaubt
- Optional: genau ein primärer Agent pro Projekt

Die Roadmap nennt Fehlermeldungen bei ungültigen Verbindungen explizit als MVP-Feature und ordnet Graph-Validierung Woche 4–5 zu.

### Akzeptanzkriterien
- Fehler werden unmittelbar oder nach Trigger im UI angezeigt
- Export ist bei kritischen Fehlern blockiert
- Fehlermeldungen sind verständlich und konkreten Nodes zuordenbar

---

## 5. Kotlin Export
Herzstück des MVP ist der Export in kompilierbaren Kotlin-Code.

### Export-Ziel
Generiere ein minimales Koog-Projekt bzw. exportierbare Kotlin-Dateien für:
- Agent-Definition
- ToolRegistry / Tool-Definitionen
- Provider-spezifische Grundkonfiguration
- Basisskelett für Tool-Handler

Die Roadmap beschreibt den Kotlin-Export als One-Click-Export für vollständigen Koog-Kotlin-Code inklusive ToolRegistry.  

### Export-Artefakte
- `AgentDefinition.kt`
- `ToolRegistry.kt`
- `Main.kt` oder `SampleUsage.kt`
- optional `project.json`

### Akzeptanzkriterien
- Export erzeugt deterministische Dateien
- Export schlägt bei Validierungsfehlern fehl
- Export berücksichtigt Provider-Auswahl
- Export enthält mindestens Agent und angebundene Tools

---

## 6. Lokale Persistenz
Projektzustand muss speicherbar und wiederherstellbar sein.

### Speicherformat
JSON mit:
- Metadaten
- Node-Liste
- Edge-Liste
- View-State optional
- Versionierung des Schemas

Die Roadmap nennt lokale Persistenz via JSON als Phase-1-Baustein in Woche 5–6.  

### Akzeptanzkriterien
- Projekt kann lokal gespeichert werden
- Projekt kann wieder geladen werden
- JSON-Version ist explizit versioniert
- Laden ungültiger Dateien liefert nachvollziehbare Fehler

---

## 7. Export Dialog & UI Polish
Zum Abschluss des MVP:
- Export-Dialog
- klare CTA-Buttons
- Fehler-/Statusmeldungen
- leere Zustände
- Demo-Projekt / Seed
- konsistente UI mit shadcn/ui

Die Roadmap ordnet diese Abschlussarbeiten ebenfalls Woche 5–6 zu.  

---

## Features at a Glance

| Feature | Beschreibung | Priorität |
|---|---|---|
| Canvas Engine | Node-/Edge-Rendering, Drag & Drop, Navigation | Kritisch |
| Agent Node | Name, Prompt, Provider-Konfiguration | Kritisch |
| Tool Node | Tool-Definition inkl. Parameter | Kritisch |
| Kotlin Export | Export von Agent + Tools in Kotlin | Kritisch |
| Graph Validation | Fehlererkennung und Export-Blocker | Hoch |
| Local Persistence | JSON speichern und laden | Hoch |
| Export Dialog / UI Polish | UX-Abschluss für MVP | Hoch |

Die Priorisierung folgt direkt dem MVP-Scope im Roadmap-Dokument. 

---

## Wochenplan

| Woche | Fokus | Ergebnis |
|---|---|---|
| 1–2 | Canvas Engine | Arbeitsfähiger Editor mit Nodes und Verbindungen |
| 2–3 | Agent + Tool Node | Konfigurierbare Nodes mit Properties Panel |
| 3–4 | Kotlin Export | Erster funktionaler Kotlin-Codegenerator |
| 4–5 | Validierung | Fehlermeldungen und Export-Schutz |
| 5–6 | Persistenz + Polish | JSON Save/Load, Export Dialog, UX-Verfeinerung |


