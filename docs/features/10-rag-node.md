# Feature 10: RAG Node

## Problem

Koog bietet ein vollstaendiges RAG-Framework (Embedder, Vector Storage, Document Ranking), aber der Modeler hat keine Moeglichkeit, RAG-Funktionalitaet visuell mit Agenten zu verbinden. Der User muss RAG-Code manuell schreiben.

## Loesung

Neuer Canvas-Node-Typ `rag` auf dem Haupt-Canvas. Der RAG-Node wird per Edge mit einem Graph-Agent verbunden (wie Tool-Nodes) und konfiguriert Embedder, Storage, Dokumentquellen und Zugriffsmodus ueber ein Tab-basiertes Properties-Panel.

## Koog-Konzept

Koog unterstuetzt zwei Wege, RAG in Agenten einzubinden:

### Context Injection
Dokumente werden vor dem Agent-Run abgerufen und als Prompt-Kontext injiziert:
```kotlin
val relevantDocs = storage.mostRelevantDocuments(query, count = 5)
prompt("rag-context") {
    user {
        +"Relevant context:"
        relevantDocs.forEach { file(it.pathString, "text/plain") }
    }
}
```

### Tool-basiert
Der Agent bekommt ein Search-Tool und entscheidet selbst, wann er sucht:
```kotlin
@Tool
@LLMDescription("Search for relevant documents")
suspend fun searchDocuments(
    @LLMDescription("Query to search relevant documents about") query: String,
    @LLMDescription("Maximum number of documents") count: Int
): String { ... }
```

Beide Modi koennen gleichzeitig aktiv sein.

## Scope

### Canvas-Node

- Neuer Node-Typ `rag` auf dem Haupt-Canvas (gleiche Ebene wie `tool`, `builtin-tool`, `mcp-server`)
- Edge-Verbindung: `rag` → `graph-agent` (gerichtet, wie bei Tools)
- Ein Agent kann mehrere RAG-Nodes haben
- Visuell: DB/Dokument-Icon, eigene Akzentfarbe (Teal), Label vom User benennbar
- Badge zeigt aktive Modi ("Context", "Tool", oder beides)

### Properties-Panel (4 Tabs)

#### Tab "Embedder"

| Feld | Typ | Optionen | Default |
|------|-----|----------|---------|
| Provider | Select | `OpenAI`, `Ollama` | `OpenAI` |
| Model | Select/Text | OpenAI: `TextEmbeddingAda3Large`, `TextEmbeddingAda3Small`; Ollama: `nomic-embed-text` + Freitext | Provider-abhaengig |
| API Key Variable | Text | Env-Variable fuer API Key (z.B. `OPENAI_API_KEY`) | `OPENAI_API_KEY` |

#### Tab "Storage"

| Feld | Typ | Optionen | Default |
|------|-----|----------|---------|
| Storage Type | Select | `InMemory`, `File`, `Custom` | `InMemory` |
| Storage Path | Text | Nur bei `File`: Pfad zum Speicherort | `./vector-storage` |
| Custom Class | Text | Nur bei `Custom`: Fully-qualified Klassenname | — |
| Similarity Threshold | Slider | `0.0` – `1.0` | `0.7` |

#### Tab "Documents"

| Feld | Typ | Beschreibung |
|------|-----|-------------|
| File Paths | List\<Text\> | Pfade zu Dateien/Ordnern (z.B. `./docs/`, `./data/faq.txt`) |
| URL Sources | List\<Text\> | URLs als Dokumentquellen |
| Max Documents | Number | Max. Anzahl relevanter Dokumente pro Query (Default: `5`) |

#### Tab "Mode"

| Feld | Typ | Beschreibung |
|------|-----|-------------|
| Inject as Context | Toggle | Dokumente vor Agent-Run in den Prompt injizieren |
| Provide as Tool | Toggle | `searchDocuments`-Tool fuer den Agent registrieren |
| Tool Description | Text | Nur bei Tool-Modus: LLM-Beschreibung des Search-Tools (Default: `"Search for relevant documents"`) |

Mindestens ein Toggle muss aktiv sein.

### Backend-Model

Neues Datenmodell in `GraphAgentModel.kt`:

```kotlin
data class KoogRagNode(
    val id: String,
    val name: String,
    // Embedder
    val embedderProvider: String,       // "OpenAI" | "Ollama"
    val embedderModel: String,
    val apiKeyVariable: String,
    // Storage
    val storageType: String,            // "InMemory" | "File" | "Custom"
    val storagePath: String?,
    val customStorageClass: String?,
    val similarityThreshold: Double,
    // Documents
    val filePaths: List<String>,
    val urlSources: List<String>,
    val maxDocuments: Int,
    // Mode
    val injectAsContext: Boolean,
    val provideAsTool: Boolean,
    val toolDescription: String?
)
```

`KoogGraphAgent` bekommt ein neues Feld:
```kotlin
val ragNodes: List<KoogRagNode>
```

RAG-Nodes werden ueber Canvas-Edges gefiltert und dem Agent zugeordnet.

### Code-Generierung

Der `GraphAgentGenerator` erzeugt aus jedem `KoogRagNode` drei Code-Bloecke:

#### Block 1: Embedder + Storage Setup (vor dem Agent)
```kotlin
// RAG: Product Docs
val productDocsEmbedder = LLMEmbedder(
    OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
    OpenAIModels.Embeddings.TextEmbeddingAda3Large
)
val productDocsDocumentEmbedder = JVMTextDocumentEmbedder(productDocsEmbedder)
val productDocsStorage = EmbeddingBasedDocumentStorage(
    productDocsDocumentEmbedder, InMemoryVectorStorage()
)

// Store file documents
productDocsStorage.store(Path.of("./docs/products"))
productDocsStorage.store(Path.of("./docs/faq.txt"))

// Download and store URL documents
val urlDoc0 = downloadToTempFile("https://example.com/api-docs")
productDocsStorage.store(urlDoc0)
```

> **Hinweis:** URL-Quellen werden im generierten Code ueber eine `downloadToTempFile()`-Hilfsfunktion heruntergeladen und als temporaere Dateien gespeichert, bevor sie in den Storage aufgenommen werden. Diese Funktion wird als Teil des generierten Codes erzeugt.

#### Block 2a: Context Injection (wenn Toggle aktiv)
```kotlin
val productDocsContext = productDocsStorage
    .mostRelevantDocuments(query, count = 5, similarityThreshold = 0.7)

prompt("rag-context") {
    user {
        +"Relevant context:"
        productDocsContext.forEach { file(it.pathString, "text/plain") }
    }
}
```

#### Block 2b: Tool-Registrierung (wenn Toggle aktiv)
```kotlin
@Tool
@LLMDescription("Search for relevant documents")
suspend fun searchProductDocs(
    @LLMDescription("Query to search relevant documents about") query: String,
    @LLMDescription("Maximum number of documents") count: Int
): String {
    val docs = productDocsStorage
        .mostRelevantDocuments(query, count = count, similarityThreshold = 0.7)
        .toList()
    if (docs.isEmpty()) return "No relevant documents found for: $query"
    return buildString {
        docs.forEachIndexed { i, doc ->
            append("Document ${i + 1}: ${doc.fileName}\n")
            append("Content: ${Files.readString(doc)}\n\n")
        }
    }
}

// Registered in ToolRegistry:
tool(::searchProductDocs.asTool())
```

#### Imports (automatisch basierend auf Konfiguration)
```kotlin
import ai.koog.agents.ext.rag.*
import ai.koog.agents.core.tools.*
import ai.koog.prompt.embedding.*
// + provider-spezifische Imports (OpenAI/Ollama)
```

Bei mehreren RAG-Nodes werden alle Bloecke hintereinander generiert, mit eindeutigen Variablennamen basierend auf dem Node-Namen.

### Frontend-Integration

| Datei | Aenderung |
|-------|-----------|
| `frontend/types/graph-agent.ts` | Neuer Canvas-Node-Typ `"rag"`, Interface `RagNodeData` |
| `frontend/components/flow/FlowCanvas.tsx` | RAG-Node Rendering, Edge-Erstellung zu Graph-Agents |
| `frontend/components/rag/RagPropertiesPanel.tsx` | Neues Panel mit 4 Tabs (Embedder, Storage, Documents, Mode) |
| `frontend/components/graph-agent/GraphStrategyEditor.tsx` | Connected RAG-Nodes ueber Edge-Filterung erkennen |

### Backend-Integration

| Datei | Aenderung |
|-------|-----------|
| `GraphAgentModel.kt` | `KoogRagNode` Data Class, `ragNodes` Feld auf `KoogGraphAgent` |
| `GraphAgentGenerator.kt` | RAG-Code-Generierung (Embedder, Storage, Context Injection, Tool) |
| `GraphToKoogAdapter.kt` | RAG-Nodes aus Canvas-Edges extrahieren und mappen |

## Akzeptanzkriterien

- [ ] RAG-Node auf dem Haupt-Canvas platzierbar und per Edge mit Graph-Agent verbindbar
- [ ] Properties-Panel mit 4 Tabs (Embedder, Storage, Documents, Mode) vollstaendig konfigurierbar
- [ ] OpenAI und Ollama als Embedder-Provider waehlbar
- [ ] Storage-Typen InMemory, File und Custom waehlbar
- [ ] Dateipfade und URLs als Dokumentquellen konfigurierbar
- [ ] Context Injection und Tool-Modus einzeln oder kombiniert aktivierbar
- [ ] Mehrere RAG-Nodes pro Agent moeglich
- [ ] Generierter Code erzeugt korrektes Embedder/Storage-Setup
- [ ] Context Injection generiert Prompt-Erweiterung mit `mostRelevantDocuments()`
- [ ] Tool-Modus generiert `@Tool`-annotierte Suchfunktion + ToolRegistry-Eintrag
- [ ] Generierter Code kompiliert mit Koog 0.7.2
- [ ] Variablennamen werden aus dem RAG-Node-Namen abgeleitet (eindeutig bei mehreren Nodes)
