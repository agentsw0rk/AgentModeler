# Design: RAG Node

**Datum:** 2026-04-01
**Status:** Approved

## Zusammenfassung

Neuer Canvas-Node-Typ `rag` der RAG-Funktionalitaet (Retrieval-Augmented Generation) visuell mit Agenten verbindet. Der Node wird per Edge mit einem Graph-Agent verbunden und konfiguriert Embedder, Vector Storage, Dokumentquellen und Zugriffsmodus (Context Injection und/oder Tool-basiert).

## Design-Entscheidungen

| Frage | Entscheidung | Begruendung |
|-------|-------------|-------------|
| Zugriffsmodus | Beides (Context Injection + Tool) | Maximale Flexibilitaet, User waehlt per Toggle |
| Canvas-Integration | Separater Canvas-Node mit Edge zu Agent | Konsistent mit Tool/MCP-Pattern, haelt Strategy-Graph sauber |
| Embedder-Provider | OpenAI + Ollama | Cloud + Local abgedeckt, beide nativ in Koog |
| Vector Storage | InMemory + File + Custom | InMemory fuer Tests, File fuer Persistenz, Custom fuer Erweiterbarkeit |
| Dokumentquellen | Dateipfade + URLs | Pfade nativ in Koog, URLs fuer externe Quellen |
| Properties-UI | Tab-basiertes Panel (4 Tabs) | Strukturiert die vielen Optionen, skaliert fuer Erweiterungen |
| Modi-Konfiguration | Ein Node, zwei Toggles | Weniger Canvas-Clutter, beide Modi teilen dieselbe Storage-Instanz |

## Vollstaendige Spezifikation

Siehe [docs/features/10-rag-node.md](../../features/10-rag-node.md) fuer die vollstaendige Feature-Spezifikation mit:
- Canvas-Node Beschreibung
- Properties-Panel (4 Tabs: Embedder, Storage, Documents, Mode)
- Backend-Model (`KoogRagNode`)
- Code-Generierung (3 Bloecke: Setup, Context Injection, Tool)
- Frontend-Integration
- Backend-Integration
- Akzeptanzkriterien (12 Punkte)

## Betroffene Dateien

### Frontend
- `frontend/types/graph-agent.ts` — Neuer Typ `"rag"`, Interface `RagNodeData`
- `frontend/components/flow/FlowCanvas.tsx` — RAG-Node Rendering + Edges
- `frontend/components/rag/RagPropertiesPanel.tsx` — Neues 4-Tab Panel
- `frontend/components/graph-agent/GraphStrategyEditor.tsx` — RAG-Node Erkennung

### Backend
- `GraphAgentModel.kt` — `KoogRagNode` Data Class + `ragNodes` auf `KoogGraphAgent`
- `GraphAgentGenerator.kt` — RAG-Code-Generierung
- `GraphToKoogAdapter.kt` — RAG-Node Extraktion aus Canvas-Edges

## Abhaengigkeiten

- Koog 0.7.2 RAG-API (`ai.koog.agents.ext.rag.*`, `ai.koog.prompt.embedding.*`)
- Bestehende Canvas-Edge-Infrastruktur (Tool-Verbindungs-Pattern)

## Risiken

- Koog RAG-Imports muessen gegen die tatsaechliche 0.7.2 API verifiziert werden
- URL-Dokumentquellen erfordern moeglicherweise einen Download-Schritt im generierten Code
- Custom Storage braucht Validierung dass die angegebene Klasse das richtige Interface implementiert
