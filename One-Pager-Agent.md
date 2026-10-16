# Agent Modeler

Ein visueller Editor zum Erstellen, Konfigurieren und Exportieren von KI-Agenten auf Basis des [Koog Agent Framework](https://github.com/JetBrains/koog).

## Was ist das?

Der Agent Modeler ist eine Web-Anwendung, mit der sich KI-Agenten per Drag & Drop zusammenbauen lassen — ohne manuell Kotlin-Code schreiben zu müssen. Der fertige Agent wird als kompilierbares Spring Boot-Projekt exportiert.

## Kernfunktionen

**Graph Editor** — Agenten, Tools, Sub-Agenten und MCP-Server als Knoten auf einer Arbeitsfläche platzieren und miteinander verbinden. Jeder Knoten hat ein Properties-Panel zur Konfiguration (Name, System-Prompt, Modell, Parameter).

**Live Code Explorer** — Eine IDE-ähnliche Sidebar zeigt in Echtzeit den generierten Kotlin-Code an. Bei jeder gültigen Änderung im Graph wird der Code automatisch neu generiert (800ms Debounce). Dateien lassen sich in einem Modal mit Syntax Highlighting (Shiki) betrachten.

**Validierung** — Der Graph wird fortlaufend geprüft: fehlende Pflichtfelder, ungültige Verbindungen, strukturelle Fehler. Fehler werden pro Knoten angezeigt.

**Export** — Ein-Klick-Export als ZIP mit vollständigem Kotlin-Projekt: `build.gradle.kts`, `Dockerfile`, `Application.kt`, `AgentConfig.kt`, Tool-Implementierungen, MCP-Konfigurationen und Spring Boot Starter.

**LLM Code Review** — Optionaler Review-Schritt beim Export: Ein LLM prüft den generierten Code auf Fehler, Wiring-Probleme und Best Practices. Ergebnisse werden nach Severity (Critical/Warning/Suggestion) gruppiert angezeigt.

**Agent Preview** — Generierte Agenten können direkt in der App getestet werden. Eine Chat-Oberfläche mit Execution-Timeline zeigt LLM-Calls, Tool-Aufrufe und Antworten in Echtzeit.

**MCP Discovery** — MCP-Server (SSE oder STDIO) können live verbunden werden, um verfügbare Tools automatisch zu erkennen.

## Unterstützte Bausteine

| Typ | Beschreibung |
|-----|-------------|
| Agent | Haupt-Agent mit System-Prompt, Modell (OpenAI/Anthropic/Google) und Temperatur |
| Custom Tool | Eigene Tools mit Name, Beschreibung und Parametern |
| Builtin Tools | Think, Shell, Filesystem, Fetch, Memory, Todo, User Prompt |
| Sub-Agent | Delegierbare Unter-Agenten mit eigenem Prompt und Tools |
| MCP Server | Externe Tool-Server via SSE oder STDIO |

## Techstack

| Schicht | Technologie |
|---------|------------|
| Frontend | Next.js 16, React 19, Rete.js (Graph), Zustand (State), Tailwind CSS, Shiki |
| Backend | Spring Boot 4, Kotlin, jimfs (virtuelles Dateisystem) |
| Agent Framework | Koog 0.7.2 (JetBrains) |
| Code-Generierung | Kotlin-Generatoren für jeden Dateityp, ZIP-Builder |

## Architektur

```
Browser (Next.js)          Backend 
┌──────────────────┐       ┌──────────────────────┐
│ Graph Editor     │──API──│ Validation           │
│ Code Explorer    │       │ Graph → Agent Adapter │
│ Preview Chat     │       │ Code Generators      │
│ Export + Review  │       │ VFS für den Code     │
└──────────────────┘       │ Preview Runtime      │
                           │ LLM Code Review      │
                           └──────────────────────┘
```

Der Nutzer baut den Agenten visuell → der Graph wird validiert und in das Koog-Datenmodell konvertiert → 13 Generatoren erzeugen die Projektdateien → Export als ZIP oder Live-Ansicht im Code Explorer.
