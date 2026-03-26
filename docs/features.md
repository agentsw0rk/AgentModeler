# Koog Agent Editor — Aktuelle Funktionen (Phase 1 / MVP)

## Übersicht

Der Koog Agent Editor ist ein visueller Editor zum Modellieren von JetBrains Koog-Agenten. Entwickler können Agenten und Tools auf einem Canvas platzieren, konfigurieren, verbinden und als kompilierbaren Kotlin-Code exportieren.

---

## Frontend (Next.js + Rete.js)

### Canvas Engine
- Interaktive Zeichenfläche mit Rete.js 2
- Drag & Drop von Nodes auf dem Canvas
- Zoom und Pan
- Nodes per Toolbar-Button oder Rechtsklick-Kontextmenü erstellen
- Tool-Dropdown in der Toolbar: Auswahl zwischen "Custom Tool" und "Built-in Tool" (mit Untermenü für Tool-Typ)
- Verbindungen zwischen Tool-Output und Agent-Input per Drag ziehen
- Nur gültige Verbindungen erlaubt (Tool → Agent, Built-in Tool → Agent)
- Node-Selektion per Klick, Mehrfachselektion mit Ctrl+Klick

### Agent Node

Ein Agent Node repräsentiert einen KI-Agenten, der auf einem LLM basiert. Er ist das zentrale Element im Editor — jeder Graph braucht mindestens einen Agenten.

**Was kann man damit tun?**

- Einen Agenten erstellen und ihm einen Namen geben (z.B. `TravelPlanner`)
- Dem Agenten eine Persönlichkeit und Aufgabe über den System Prompt zuweisen
- Den LLM-Provider und das Modell wählen (OpenAI GPT-4o, Anthropic Claude, Google Gemini)
- Die Kreativität über Temperature steuern (0.0 = deterministisch, 1.0+ = kreativ)
- Beliebig viele Tools mit dem Agenten verbinden, die er zur Laufzeit nutzen kann

**Konfigurierbare Felder im Properties Panel:**

| Feld | Pflicht | Beschreibung |
|------|---------|-------------|
| Name | Ja | Kotlin-Klassenname des Agenten (z.B. `TravelPlanner`) |
| System Prompt | Ja | Instruktionen, die das Verhalten des Agenten definieren |
| Model Provider | Ja | OpenAI, Anthropic oder Google |
| Model ID | Nein | Spezifisches Modell (z.B. `gpt-4o`, `claude-sonnet-4`) |
| Temperature | Nein | Kreativitätsparameter (0.0–2.0) |

**Beispiel — Reiseplaner-Agent:**

1. Klicke "+ Agent" in der Toolbar
2. Klicke auf den Agent Node auf dem Canvas
3. Im Properties Panel rechts konfigurieren:
   - **Name:** `TravelPlanner`
   - **System Prompt:** `Du bist ein erfahrener Reiseplaner. Hilf dem Nutzer, eine Reise zu planen. Nutze die verfügbaren Tools, um Flüge zu suchen und das Wetter zu prüfen.`
   - **Model Provider:** OpenAI
   - **Model ID:** `gpt-4o`
   - **Temperature:** `0.7`

### Tool Node (Custom)

Ein Tool Node repräsentiert eine selbst definierte Fähigkeit, die ein Agent zur Laufzeit aufrufen kann. Tools erweitern den Agenten um konkrete Aktionen — z.B. Web-Suche, Datenbankabfrage, API-Aufruf oder Berechnungen.

**Was kann man damit tun?**

- Ein Tool definieren, das der Agent bei Bedarf aufrufen kann
- Dem Tool einen Namen und eine Beschreibung geben (das LLM liest die Beschreibung, um zu entscheiden, wann es das Tool nutzt)
- Typisierte Parameter definieren, die das Tool als Input erwartet
- Das Tool per Drag-Verbindung mit einem Agenten verknüpfen
- Mehrere Tools an denselben Agenten anhängen

**Konfigurierbare Felder im Properties Panel:**

| Feld | Pflicht | Beschreibung |
|------|---------|-------------|
| Name | Ja | Funktionsname des Tools (z.B. `searchFlights`) |
| Description | Nein | Beschreibung für das LLM, wann/wie das Tool zu nutzen ist |
| Parameters | Nein | Liste typisierter Eingabeparameter |

Jeder Parameter hat:

| Feld | Beschreibung |
|------|-------------|
| Name | Parametername (z.B. `destination`) |
| Type | String, Int, Float oder Boolean |
| Description | Beschreibung für das LLM |
| Required | Ob der Parameter Pflicht ist |

**Beispiel — Flugsuche-Tool:**

1. Klicke "+ Tool" in der Toolbar
2. Klicke auf den Tool Node auf dem Canvas
3. Im Properties Panel konfigurieren:
   - **Name:** `searchFlights`
   - **Description:** `Sucht verfügbare Flüge zwischen zwei Städten an einem bestimmten Datum`
4. Klicke "+ Parameter" dreimal und konfiguriere:
   - `origin` (String) — `Abflugstadt, z.B. Berlin`
   - `destination` (String) — `Zielstadt, z.B. Barcelona`
   - `date` (String) — `Reisedatum im Format YYYY-MM-DD`
5. Ziehe eine Verbindung vom Tool-Output zum Agent-Input

**Beispiel — Wetter-Tool:**

1. Erstelle einen weiteren Tool Node
2. Konfiguriere:
   - **Name:** `getWeather`
   - **Description:** `Gibt die aktuelle Wettervorhersage für eine Stadt zurück`
3. Ein Parameter:
   - `city` (String) — `Name der Stadt`
4. Verbinde auch dieses Tool mit dem TravelPlanner-Agenten

### Built-in Tool Node

Ein Built-in Tool Node repräsentiert ein vorgefertigtes Koog-Framework-Tool, das ohne eigene Implementierung genutzt werden kann. Im Gegensatz zu Custom Tools muss hier kein Code geschrieben werden — der Editor generiert den passenden Kotlin-Code automatisch.

**Was kann man damit tun?**

- Ein Built-in Tool aus dem Dropdown in der Toolbar auswählen (Button "+ Built-in")
- Das Tool per Drag-Verbindung mit einem Agenten verknüpfen
- Optional tool-spezifische Konfiguration im Properties Panel anpassen

**Verfügbare Built-in Tools:**

| Tool | Beschreibung | Konfiguration |
|------|-------------|---------------|
| Think | Fügt eine Chain-of-Thought-Anweisung zum System Prompt hinzu | — |
| Shell | Shell-Befehle im Agenten-Umfeld ausführen | Working Directory |
| Filesystem | Dateien und Verzeichnisse lesen, schreiben und auflisten (Koog-eigene File-Tools) | Root Directory |
| Fetch | HTTP-Requests an externe APIs und Webservices senden | Base URL |
| Memory | Persistenter Key-Value-Speicher auf SQLite-Basis | Database Path |
| Todo | In-Memory-Aufgabenliste für komplexe Workflows | — |
| User Prompt | Nutzer interaktiv Fragen stellen und Eingaben einsammeln (Koog-eigene Tools) | — |

**Wie werden Built-in Tools exportiert?**

| Tool | Export-Strategie |
|------|-----------------|
| Think | Kein eigenes ToolSet — erweitert den System Prompt um `"Think step by step before answering."` |
| Shell | Generiert `ShellToolSet.kt` mit `executeCommand()`-Funktion |
| Filesystem | Nutzt Koog-Framework-Klassen `ReadFileTool`, `WriteFileTool`, `ListDirectoryTool` direkt |
| Fetch | Generiert `FetchToolSet.kt` mit `fetch(url, method)`-Funktion |
| Memory | Generiert `MemoryToolSet.kt` mit `store()`, `retrieve()`, `listKeys()` |
| Todo | Generiert `TodoToolSet.kt` mit `addTask()`, `completeTask()`, `listTasks()`, `removeTask()` |
| User Prompt | Nutzt Koog-Framework-Tools `AskUser` und `SayToUser` direkt |

**Beispiel — Coding-Assistent mit Shell + Filesystem:**

1. Erstelle einen Agent Node und konfiguriere:
   - **Name:** `CodingAssistant`
   - **System Prompt:** `Du bist ein Coding-Assistent. Nutze Shell-Befehle und Dateizugriff, um dem Nutzer bei Programmieraufgaben zu helfen.`
   - **Model Provider:** Anthropic
   - **Model ID:** `claude-sonnet-4`
2. Klicke "+ Built-in" in der Toolbar → wähle **Shell**
3. Im Properties Panel: **Working Directory:** `.` (oder ein Projektpfad)
4. Verbinde den Shell-Node mit dem Agent
5. Klicke "+ Built-in" → wähle **Filesystem**
6. Im Properties Panel: **Root Directory:** `.`
7. Verbinde den Filesystem-Node mit dem Agent

**Beispiel — Recherche-Agent mit Fetch + Memory + Think:**




### Zusammenspiel: Agent + Tools → Kotlin-Export

Nach dem Modellieren des Beispiels oben (1 Agent + 2 Tools) erzeugt der Export folgende Kotlin-Dateien:

**`src/TravelPlanner.kt`** — Der Agent:
```kotlin
class TravelPlanner {
    suspend fun run(userMessage: String): String {
        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
            systemPrompt = "Du bist ein erfahrener Reiseplaner...",
            llmModel = OpenAIModels.Chat.GPT4o,
            temperature = 0.7,
            toolRegistry = createToolRegistry()
        )
        return agent.run(userMessage)
    }
}
```

**`src/tools/SearchFlightsToolSet.kt`** — Das Flugsuche-Tool:
```kotlin
@LLMDescription("Sucht verfügbare Flüge zwischen zwei Städten an einem bestimmten Datum")
class SearchFlightsToolSet : ToolSet {
    @Tool
    @LLMDescription("Sucht verfügbare Flüge zwischen zwei Städten an einem bestimmten Datum")
    fun searchFlights(
        @LLMDescription("Abflugstadt, z.B. Berlin") origin: String,
        @LLMDescription("Zielstadt, z.B. Barcelona") destination: String,
        @LLMDescription("Reisedatum im Format YYYY-MM-DD") date: String
    ): String {
        // TODO: Implement tool logic
        return "Not implemented"
    }
}
```

**`src/tools/GetWeatherToolSet.kt`** — Das Wetter-Tool:
```kotlin
@LLMDescription("Gibt die aktuelle Wettervorhersage für eine Stadt zurück")
class GetWeatherToolSet : ToolSet {
    @Tool
    @LLMDescription("Gibt die aktuelle Wettervorhersage für eine Stadt zurück")
    fun getWeather(
        @LLMDescription("Name der Stadt") city: String
    ): String {
        // TODO: Implement tool logic
        return "Not implemented"
    }
}
```

**`src/ToolRegistry.kt`** — Registriert beide Tools:
```kotlin
fun createToolRegistry(): ToolRegistry {
    val searchFlightsTools = SearchFlightsToolSet()
    val getWeatherTools = GetWeatherToolSet()

    return ToolRegistry {
        tools(searchFlightsTools)
        tools(getWeatherTools)
    }
}
```

**`src/Main.kt`** — Einstiegspunkt:
```kotlin
fun main() = runBlocking {
    val agent = TravelPlanner()
    val result = agent.run("Hello! How can you help me?")
    println(result)
}
```

Der generierte Code ist sofort kompilierbar — die `// TODO`-Stellen in den Tools müssen mit der eigentlichen Logik (API-Aufrufe, Datenbankabfragen etc.) befüllt werden.

### Properties Panel
- Rechte Seitenleiste, reagiert auf Node-Selektion
- Zeigt Agent-, Custom-Tool- oder Built-in-Tool-Formular je nach Node-Typ
- Built-in-Tool-Panel zeigt Typ, Beschreibung und tool-spezifische Konfigurationsfelder
- Änderungen werden sofort im Zustand gespeichert
- Node-Label auf dem Canvas aktualisiert sich bei Namensänderung

### Graph-Validierung
- Validierung per "Validate"-Button in der Toolbar
- Validierungsregeln:
  - Agent ohne Name → Fehler
  - Agent ohne System Prompt → Fehler
  - Custom Tool ohne Name → Fehler
  - Custom Tool ohne verbundenen Agent → Fehler
  - Parameter ohne Name → Fehler
  - Built-in Tool mit ungültigem Typ → Fehler
  - Built-in Tool ohne verbundenen Agent → Fehler
- Fehler werden im Validation Panel am unteren Rand angezeigt
- Export wird bei Validierungsfehlern blockiert

### Lokale Persistenz
- Projekt als JSON-Datei speichern (Download)
- JSON-Datei laden und Canvas-Zustand wiederherstellen
- Versioniertes JSON-Format (Version 1)
- Fehlerbehandlung bei ungültigen Dateien

### Export Dialog
- Export-Button in der Toolbar öffnet Dialog
- Validierung wird vor Export automatisch ausgeführt
- Bei Erfolg: ZIP-Datei mit Kotlin-Code wird heruntergeladen
- Fehlermeldungen bei Validierungsfehlern oder API-Fehlern

---

## Backend (Spring Boot + Kotlin)

### REST API

#### `POST /api/export`
- Empfängt den Graph als JSON (ProjectGraph)
- Validiert den Graph serverseitig
- Konvertiert Graph → Koog-Modell → Kotlin-Code
- Liefert eine ZIP-Datei mit generierten Kotlin-Dateien

**Request Body:**
```json
{
  "version": 1,
  "name": "MyProject",
  "nodes": [
    {
      "id": "a1",
      "type": "agent",
      "position": { "x": 0, "y": 0 },
      "data": {
        "name": "MyAgent",
        "systemPrompt": "You are helpful.",
        "modelProvider": "openai",
        "modelId": "gpt-4o",
        "temperature": 0.7
      }
    },
    {
      "id": "t1",
      "type": "tool",
      "position": { "x": 200, "y": 0 },
      "data": {
        "name": "searchTool",
        "description": "Searches the web",
        "parameters": [
          { "id": "p1", "name": "query", "type": "String", "description": "Search query", "required": true }
        ]
      }
    },
    {
      "id": "bt1",
      "type": "builtin-tool",
      "position": { "x": 200, "y": 200 },
      "data": {
        "toolType": "shell",
        "workingDir": "."
      }
    }
  ],
  "edges": [
    { "id": "e1", "source": "t1", "target": "a1" },
    { "id": "e2", "source": "bt1", "target": "a1" }
  ]
}
```

**Erfolg (200):** ZIP-Datei als `application/octet-stream`

**Validierungsfehler (400):**
```json
{
  "error": "Validation failed",
  "details": ["[a1] Agent requires a name", "[t1] Tool must be connected to an Agent"]
}
```

### Kotlin-Code-Generierung

Die ZIP-Datei enthält:

| Datei | Inhalt |
|-------|--------|
| `src/MyAgent.kt` | Agent-Klasse mit AIAgent-Konfiguration, Provider-spezifischem Executor |
| `src/tools/SearchToolToolSet.kt` | Annotation-basiertes ToolSet mit @Tool und @LLMDescription |
| `src/tools/ShellToolSet.kt` | Generiertes Built-in ToolSet (nur bei Shell, Fetch, Memory, Todo) |
| `src/ToolRegistry.kt` | ToolRegistry-Factory mit allen Custom- und Built-in-Tools |
| `src/Main.kt` | Runnable main()-Funktion mit runBlocking |

> **Hinweis:** Built-in Tools wie Think, Filesystem und User Prompt generieren kein eigenes ToolSet — sie nutzen entweder System-Prompt-Erweiterung oder Koog-Framework-Klassen direkt.

### Unterstützte Model Provider

| Provider | Executor | Standard-Modell |
|----------|----------|-----------------|
| OpenAI | `simpleOpenAIExecutor` | GPT-4o |
| Anthropic | `simpleAnthropicExecutor` | Sonnet 4 |
| Google | `simpleGoogleExecutor` | Gemini 2 Flash |

### Serverseitige Validierung
- Agent ohne Name oder System Prompt → Fehler
- Custom Tool ohne Name → Fehler
- Custom Tool ohne verbundenen Agent → Fehler
- Parameter ohne Name → Fehler
- Built-in Tool mit ungültigem Typ → Fehler
- Built-in Tool ohne verbundenen Agent → Fehler

### CORS
- Konfiguriert für `http://localhost:3000` (Frontend)
- Erlaubte Methoden: GET, POST, OPTIONS

---

## Bestehende Endpoints

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| POST | `/api/export` | Graph → Kotlin-ZIP Export |
| POST | `/ask` | Koog Agent Chat (MyAgentService) |

---

## Starten

```bash
./start.sh
```

Startet Backend (`:8080`) und Frontend (`:3000`) parallel.

Einzeln:
```bash
# Backend
./gradlew bootRun

# Frontend
cd frontend && npm run dev
```

---

## Nicht im Scope (Phase 1)

- Workflow-Loops, Branches, Fallbacks
- Sub-Agents
- Input/Output Nodes (geplant als nächste Erweiterung)
- MCP-Integration
- Live-Preview / Runtime-Ausführung
- Memory / RAG
- Kollaboration
- IntelliJ-Plugin
- Observability Dashboard
