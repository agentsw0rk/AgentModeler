# Koog Agent Editor

Visueller Editor zum Modellieren, Konfigurieren und Exportieren von **Koog-basierten AI-Agenten** als **Kotlin-Code**.

## Überblick

Der **Koog Agent Editor** ist ein visuelles Tool für Entwickler, die AI-Agenten mit dem JetBrains-Framework **Koog** erstellen möchten.  
Statt Agenten, Tools, Workflows und Sub-Agents manuell im Code zusammenzubauen, können diese Bausteine in einem graphbasierten Editor modelliert und anschließend als ausführbarer Kotlin-Code exportiert werden.

Der Fokus liegt darauf, den Einstieg zu beschleunigen, Boilerplate zu reduzieren und komplexere Agent-Architekturen besser verständlich und wartbar zu machen. Grundlage dafür sind die im Roadmap-Dokument beschriebenen Produktziele, MVP-Phasen und Architekturideen.  [oai_citation:0‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## Vision

Ein Entwickler soll in der Lage sein, einen Koog-Agenten visuell zusammenzustellen, zu validieren und mit einem Klick als kompilierbaren Kotlin-Code zu exportieren.

Ziel ist ein Editor, der sowohl einfache Single-Agent-Setups als auch komplexere Multi-Agent-Architekturen mit Tools, Memory, Delegation und späteren Runtime-Integrationen unterstützt.  [oai_citation:1‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## Kernfunktionen

- **Visuelle Modellierung von Agenten** über ein Canvas mit Nodes und Verbindungen
- **Konfiguration von Agenten** inklusive Name, Prompt und Modellanbieter
- **Definition von Tools** mit Parametern und Metadaten
- **Live- oder On-Demand-Codegenerierung** für Kotlin / Koog
- **Validierung des Graphen** zur Erkennung fehlerhafter Konfigurationen
- **Persistenz von Projekten** über JSON
- **Spätere Erweiterung** um Workflows, Sub-Agents, MCP, Memory und Observability  [oai_citation:2‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## Tech Stack

### Frontend
- **Next.js**
- **React**
- **shadcn/ui**
- **Rete.js**

### Backend / Runtime
- **Kotlin**
- **JetBrains Koog**
- Optional: **Spring Boot** oder **Ktor** für Export, Preview und Integrationen

### Weitere Bausteine
- **JSON / kotlinx.serialization** für Persistenz
- **OpenTelemetry / Langfuse** für Observability
- **OpenAI / Anthropic / Google** als unterstützte LLM-Provider laut Zielbild  [oai_citation:3‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## Warum Next.js, shadcn/ui und Rete.js?

Für den visuellen Editor ist die Kombination aus **Next.js**, **shadcn/ui** und **Rete.js** besonders geeignet:

- **Next.js** liefert die stabile Basis für eine moderne Web-Anwendung
- **shadcn/ui** ermöglicht eine konsistente und gut anpassbare UI
- **Rete.js** eignet sich als Kern für einen flexiblen Node-/Graph-Editor

Damit lässt sich der Canvas nicht als proprietärer Eigenbau entwickeln, sondern auf einem passenden Ökosystem aufsetzen, was Risiko und Implementierungsaufwand reduziert. Das unterstützt direkt die Roadmap-Anforderung, früh einen funktionierenden graphbasierten Editor bereitzustellen.  [oai_citation:4‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## MVP

Das MVP fokussiert sich bewusst auf den Kernnutzen:

1. Agent auf dem Canvas anlegen
2. Tools hinzufügen und verbinden
3. Graph validieren
4. Kotlin-Code exportieren

### Geplante MVP-Features

- Canvas Engine mit Node-/Edge-Rendering
- Agent-Node
- Tool-Node
- Input-/Output-Grundmodell
- Kotlin-Export
- Basis-Validierung
- Lokale JSON-Persistenz
- Auswahl des LLM-Providers  

### MVP-Ziel

Ein Entwickler soll in weniger als 5 Minuten einen einfachen Agenten mit mehreren Tools modellieren und als lauffähigen Kotlin-Code exportieren können.

---

## Roadmap

### Phase 1 — MVP
- Canvas Engine aufbauen
- Agent- und Tool-Nodes implementieren
- Properties Panel ergänzen
- Kotlin-Codegenerator erstellen
- Graph-Validierung integrieren
- JSON-Speicherung und Export-Dialog hinzufügen  

### Phase 2 — Beta
- Workflow-Editor mit Branches, Loops und Fallbacks
- Sub-Agent-Modellierung
- MCP-Integration
- Live-Preview und Testausführung
- Undo/Redo sowie Copy/Paste für Nodes 

### Phase 3 — Production
- Memory- und RAG-Nodes
- Prompt-Template-Editor
- Integrations-Export für Spring Boot / Ktor
- Observability-Konfiguration
- Template-Bibliothek für typische Agent-Patterns  

### Phase 4 — Advanced
- A2A-Kommunikation
- Kollaborativer Multi-User-Modus
- IntelliJ-Plugin
- CI/CD-Integration
- AI-gestützte Vorschläge im Editor  

---

## Beispiel-Workflow

Ein typischer Ablauf im Editor könnte so aussehen:

1. Neuer Agent wird auf dem Canvas angelegt
2. System-Prompt und Modellanbieter werden konfiguriert
3. Zwei oder mehr Tools werden ergänzt
4. Der Graph wird verbunden und validiert
5. Der Editor generiert Kotlin-Code für:
    - Agent-Definition
    - ToolRegistry
    - Basis-Konfiguration für den gewählten Provider

So entsteht aus einer visuellen Modellierung direkt eine Grundlage für ein ausführbares Koog-Projekt.  

---

## Risiken

Zu den wichtigsten Risiken zählen:

- Änderungen an der Koog-API
- unterschätzte Komplexität im Canvas-/Graph-Editor
- Performance bei großen Graphen
- Scope Creep durch zu viele Features zu früh

Die Roadmap empfiehlt deshalb eine klare MVP-Grenze und einen Architekturansatz mit Adapter-Layer zwischen Editor-Modell und Koog-spezifischer DSL.  

---

## Erfolgskriterien

Der Erfolg des MVP lässt sich daran messen, ob neue Entwickler:

- schnell einen ersten Agenten bauen können
- ohne viel Boilerplate zu nutzbarem Kotlin-Code kommen
- typische Modellierungsfehler früh erkennen
- den visuellen Editor als produktivere Alternative zur rein manuellen Implementierung wahrnehmen  [oai_citation:13‡Koog Agent Editor Roadmap.docx](sediment://file_00000000b758724698d7ca090f240a7b)

---

## Status

**Stand:** März 2026  
**Phase:** Konzept / MVP-Planung  

---

## Nächste sinnvolle Schritte

- Domain-Modell für Nodes, Ports, Edges und Projektdaten definieren
- UI-Grundgerüst in Next.js mit shadcn/ui aufsetzen
- Rete.js-basierte Canvas Engine integrieren
- Erste Codegenerierung für Agent + ToolRegistry implementieren
- JSON-Speicherformat festlegen
- Validierungsregeln für MVP definieren

---

## Lizenz

Aktuell offen / noch festzulegen.

---
