# Gap Analysis: Banking Assistant Tutorial vs. Modeler

Vergleich des [Koog Banking Assistant Tutorial](https://github.com/JetBrains/koog/blob/develop/docs/docs/examples/notebooks/Banking.ipynb) mit den aktuellen Fähigkeiten des Koog Agent Modelers.

## Bewertung nach Tutorial-Abschnitt

### 1. Setup & Dependencies
**Status: VOLLSTÄNDIG UNTERSTÜTZT**

| Feature | Tutorial | Modeler |
|---------|----------|---------|
| Gradle-Projekt mit Koog-Dependency | `%use koog` | `BuildGradleGenerator` erzeugt `build.gradle.kts` mit Koog 0.7.2 |
| API-Key-Konfiguration | `System.getenv("OPENAI_API_KEY")` | `ApplicationPropertiesGenerator` + `AgentGenerator` |
| LLM Executor | `simpleOpenAIExecutor(apiKey)` | `AgentGenerator` erzeugt Executor nach Provider (OpenAI, Anthropic, Google) |

### 2. System Prompt
**Status: VOLLSTÄNDIG UNTERSTÜTZT**

Der System Prompt wird im Agent-Node konfiguriert und direkt in `AgentConfig.kt` generiert.

### 3. Domain Model & Sample Data
**Status: NICHT UNTERSTÜTZT**

| Feature | Tutorial | Modeler |
|---------|----------|---------|
| `@Serializable data class Contact(...)` | Benutzerdefinierte Datenklassen | Nicht möglich |
| `val contactList = listOf(...)` | Beispieldaten / Companion-Code | Nicht möglich |
| `val contactById = contactList.associateBy(...)` | Hilfsvariablen | Nicht möglich |

**Gap:** Der Modeler hat kein Konzept für "Domain Models" — benutzerdefinierte Datenklassen, die von Tools referenziert werden. Es gibt Input/Output-Klassen für den Agenten, aber keine frei definierbaren Domain-Klassen.

**Auswirkung:** Tools können keinen gemeinsamen Datenkontext referenzieren. Im Tutorial greifen `getContacts`, `chooseRecipient` und `sendMoney` alle auf `contactList` und `contactById` zu — im Modeler müsste diese Logik komplett in die Tool-Body-Felder geschrieben werden, ohne geteilte Referenzen.

### 4. Tool-Funktionen (MoneyTransferTools)
**Status: GRÖSSTENTEILS UNTERSTÜTZT, mit Einschränkungen**

| Feature | Tutorial | Modeler | Status |
|---------|----------|---------|--------|
| `class MoneyTransferTools : ToolSet` | ToolSet-Klasse | Tool-Node im Editor | ✅ |
| `@LLMDescription` auf Klasse | Klassenbeschreibung | ToolSet Description Feld | ✅ |
| `@Tool` + `@LLMDescription` auf Funktion | Funktionsbeschreibung | Funktion mit Description | ✅ |
| Parameter mit `@LLMDescription` | Pro Parameter | Pro Parameter im Editor | ✅ |
| Parametertypen: `Int`, `Double`, `String`, `Boolean` | Gemischt | Unterstützt | ✅ |
| Optionale Parameter mit Default | `confirmed: Boolean = false` | Required-Flag + Default-Feld | ✅ |
| Return Type `String` | Alle Tools geben String zurück | String/Unit unterstützt | ✅ |
| **Komplexer Funktions-Body** | `buildString { ... }`, `when`, `filter`, `format` | Body-Textfeld vorhanden | ⚠️ |
| **Referenz auf externe Variablen** | `contactList`, `contactById` | Nicht möglich (kein Shared State) | ❌ |
| **String-Interpolation mit Expressions** | `"${c.id}: ${c.name}"` | Im Body-Feld möglich aber ungeprüft | ⚠️ |

**Gap bei komplexen Bodies:** Das Body-Feld existiert, aber der Editor bietet keinen Code-Editor mit Syntax-Highlighting, Autovervollständigung oder Validierung. Für triviale Einzeiler reicht es, für die Banking-Tutorial-Tools (10-20 Zeilen mit Kontrollfluss) ist es unpraktisch.

### 5. Agent-Erstellung (AIAgentService)
**Status: VOLLSTÄNDIG UNTERSTÜTZT**

| Feature | Tutorial | Modeler |
|---------|----------|---------|
| `AIAgentService(executor, llmModel, ...)` | Agent-Service | `AgentGenerator` erzeugt Spring Bean |
| `toolRegistry = ToolRegistry { ... }` | Tool-Registry | `RegistryGenerator` kombiniert alle verbundenen Tools |
| `tool(AskUser)` | Builtin UserPrompt | `user_prompt` Builtin-Tool-Node |
| `tools(MoneyTransferTools().asTools())` | Custom Tools | Custom Tool-Nodes |
| `temperature = 0.0` | Temperatur | Temperature-Slider im Properties Panel |
| `createAgentAndRun(message)` | Ausführung | `ChatController` mit REST-Endpoint |

### 6. Graph/Subgraph Strategy
**Status: TEILWEISE UNTERSTÜTZT**

| Feature | Tutorial | Modeler | Status |
|---------|----------|---------|--------|
| `strategy<String, String>("name") { ... }` | Strategy-Builder | `GraphAgentGenerator` | ✅ |
| `nodeLLMRequest()` | LLM Request Node | Graph-Editor Node | ✅ |
| `nodeExecuteTool()` | Tool Execution Node | Graph-Editor Node | ✅ |
| `nodeLLMSendToolResult()` | Send Result Node | Graph-Editor Node | ✅ |
| `edge(A forwardTo B onCondition { ... })` | Edge mit Condition | Edge-Editor | ✅ |
| `onToolCall`, `onAssistantMessage` | Edge-Typen | Automatisch per `autoTypeEdge()` | ✅ |
| **`subgraph { ... }`** | Verschachtelte Subgraphen | **Nicht unterstützt** | ❌ |
| **`nodeLLMCompressHistory()`** | History Compression | **Nicht unterstützt** | ❌ |
| **`nodeLLMRequestStreaming()`** | Streaming Request | **Nicht unterstützt** | ❌ |
| **`nodeLLMRequestStructured()`** | Structured Output | **Nicht unterstützt** | ❌ |
| **`nodeExecuteMultipleTools()`** | Multi-Tool Execution | **Nicht unterstützt** | ❌ |
| **`parallel { ... }`** | Parallele Ausführung | **Nicht unterstützt** | ❌ |
| **Custom `node<I, O> { ... }`** | Benutzerdefinierte Nodes | **Nicht unterstützt** | ❌ |
| **`transformed { ... }`** | Edge-Transformation | ✅ im Generator, ❌ im UI (kein Feld) | ⚠️ |

**Gap bei Subgraphen:** Das Tutorial zeigt einen Intent-Classifier, der zu verschiedenen Subgraphen routet (Transfer vs. Analytics). Subgraphen ermöglichen isolierte Tool-Sets und Strategien innerhalb eines Graphen. Der Modeler unterstützt zwar Sub-Agents als separate Nodes, aber keine verschachtelten Subgraphen innerhalb eines Graph-Agents.

### 7. "Agents as Tools" Pattern
**Status: UNTERSTÜTZT (über Sub-Agent-Nodes)**

| Feature | Tutorial | Modeler |
|---------|----------|---------|
| Agent als Tool in anderem Agent | `createAgentTool<I,O>()` | Sub-Agent-Node → wird als Tool registriert |
| Eigenes Tool-Registry pro Sub-Agent | Separate Tools | Sub-Agent hat eigene Tool-Verbindungen |
| Input/Output-Typen | Typisierte I/O | Input/Output-Nodes am Sub-Agent |

---

## Zusammenfassung: Feature-Lücken

### Kritische Lücken (ohne diese kann das Tutorial nicht nachgebaut werden)

| # | Feature | Beschreibung | Aufwand |
|---|---------|--------------|---------|
| 1 | **Domain Models** | Benutzerdefinierte `@Serializable data class` Definitionen mit Feldern, die von Tools referenziert werden können | Mittel |
| 2 | **Shared Code / Companion-Dateien** | Möglichkeit, Hilfscode zu definieren (Variablen, Listen, Maps), der von mehreren Tools genutzt wird | Mittel |

### Wichtige Lücken (schränken die Flexibilität signifikant ein)

| # | Feature | Beschreibung | Aufwand |
|---|---------|--------------|---------|
| 3 | **Subgraphen** | Verschachtelte Strategy-Graphen innerhalb eines Graph-Agents für modulare Workflows | Hoch |
| 4 | **Custom Nodes** | `node<Input, Output> { ... }` — benutzerdefinierte Verarbeitungsschritte im Graphen | Mittel |
| 5 | **Code-Editor für Tool-Bodies** | Syntax-Highlighting, mehrzeiliger Editor statt einfachem Textfeld für komplexe Tool-Implementierungen | Mittel |
| 6 | **Edge-Transformation im UI** | `transformed { ... }` — UI-Feld zum Bearbeiten der Transformation auf Edges | Klein |

### Nice-to-Have Lücken (erweiterte Koog-Features)

| # | Feature | Beschreibung | Aufwand |
|---|---------|--------------|---------|
| 7 | **Weitere Node-Typen** | `nodeLLMCompressHistory`, `nodeLLMRequestStreaming`, `nodeLLMRequestStructured`, `nodeExecuteMultipleTools` | Mittel pro Node |
| 8 | **Parallel-Ausführung** | `parallel { ... }` Node mit Merge-Strategie | Hoch |
| 9 | **Intent-Classifier-Node** | Dedizierter Node für LLM-basierte Intent-Klassifikation mit Routing zu Subgraphen | Mittel |

---

## Was JETZT schon funktioniert

Der Modeler kann **heute** einen vereinfachten Banking Assistant erzeugen:

```
[Agent: Banking Assistant]
  ├── systemPrompt: "You are a banking assistant..."
  ├── modelProvider: OPENAI
  ├── modelId: gpt-4o-mini
  ├── temperature: 0.0
  │
  ├── [Tool: MoneyTransferTools]
  │     ├── getContacts(userId: Int): String
  │     ├── getBalance(userId: Int): String
  │     ├── getDefaultCurrency(userId: Int): String
  │     ├── getExchangeRate(from: String, to: String): String
  │     ├── chooseRecipient(confusingRecipientName: String): String
  │     └── sendMoney(senderId: Int, amount: Double, recipientId: Int, purpose: String, confirmed: Boolean): String
  │
  └── [Builtin: UserPrompt (AskUser)]
```

**Was fehlt für die volle Tutorial-Abbildung:**
1. Die `contactList`/`contactById`-Daten müssten in die Tool-Bodies hardcodiert werden (statt als geteilter Domain-Code)
2. Der komplexe Body von `chooseRecipient` und `sendMoney` muss komplett im Body-Textfeld geschrieben werden
3. Der Graph/Subgraph-Teil (Intent-Classifier → Transfer/Analytics-Routing) ist nicht abbildbar

**Geschätzte Abdeckung: ~70%** — Der einfache Agent mit Tools funktioniert vollständig, aber der fortgeschrittene Graph-basierte Orchestrierungsteil fehlt.

---

## Empfohlene Priorisierung

1. **Domain Models + Shared Code** → Ermöglicht realistische Tool-Implementierungen
2. **Code-Editor für Tool-Bodies** → Macht komplexe Tools praktikabel
3. **Subgraphen im Graph-Editor** → Ermöglicht modulare Multi-Step-Workflows
4. **Custom Nodes** → Maximale Flexibilität in Graphen
5. **Weitere Node-Typen** → Inkrementell erweiterbar
