# Koog RAG mit externer Vector-Datenbank

Koog bietet zwei Wege, eine externe Vector-Datenbank (Qdrant, ChromaDB, pgvector, Pinecone etc.) statt `InMemoryVectorStorage` zu verwenden.

## 1. Custom `VectorStorage<T>` — für RAG Document Retrieval

Das `VectorStorage<T>` Interface ist der zentrale Baustein für die Dokumenten-Suche. Es lässt sich mit jeder externen Vector-DB implementieren.

### Interface

```kotlin
interface VectorStorage<Document> : DocumentStorageWithPayload<Document, Vector> {
    fun allDocuments(): Flow<Document>
    fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Document, Vector>>
    suspend fun store(document: Document, data: Vector): String
    suspend fun read(documentId: String): Document?
    suspend fun readWithPayload(documentId: String): DocumentWithPayload<Document, Vector>?
    suspend fun delete(documentId: String): Boolean
    suspend fun getPayload(documentId: String): Vector?
}
```

### Beispiel: Qdrant als VectorStorage

```kotlin
import ai.koog.rag.vector.VectorStorage
import ai.koog.rag.vector.DocumentWithPayload
import ai.koog.rag.vector.Vector
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Points.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class QdrantVectorStorage(
    private val client: QdrantClient,
    private val collectionName: String
) : VectorStorage<String> {

    override suspend fun store(document: String, data: Vector): String {
        val id = java.util.UUID.randomUUID().toString()
        val point = PointStruct.newBuilder()
            .setId(PointId.newBuilder().setUuid(id))
            .addAllVectors(data.toList().map { it.toFloat() })
            .putPayload("content", Value.newBuilder().setStringValue(document).build())
            .build()
        client.upsertAsync(collectionName, listOf(point)).get()
        return id
    }

    override suspend fun read(documentId: String): String? {
        val points = client.getAsync(collectionName, listOf(
            PointId.newBuilder().setUuid(documentId).build()
        ), true, false, null).get()
        return points.firstOrNull()?.payloadMap?.get("content")?.stringValue
    }

    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<String, Vector>? {
        val points = client.getAsync(collectionName, listOf(
            PointId.newBuilder().setUuid(documentId).build()
        ), true, true, null).get()
        val point = points.firstOrNull() ?: return null
        val doc = point.payloadMap["content"]?.stringValue ?: return null
        val vector = point.vectorsList.map { it.toDouble() }.toDoubleArray()
        return DocumentWithPayload(doc, vector)
    }

    override suspend fun delete(documentId: String): Boolean {
        client.deleteAsync(collectionName, listOf(
            PointId.newBuilder().setUuid(documentId).build()
        )).get()
        return true
    }

    override suspend fun getPayload(documentId: String): Vector? {
        return readWithPayload(documentId)?.payload
    }

    override fun allDocuments(): Flow<String> = flow {
        // Scroll through all points
        var offset: PointId? = null
        do {
            val result = client.scrollAsync(collectionName, null, offset, 100, true, false, null).get()
            for (point in result.pointsList) {
                val content = point.payloadMap["content"]?.stringValue
                if (content != null) emit(content)
            }
            offset = result.nextPageOffset
        } while (offset != null)
    }

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<String, Vector>> = flow {
        var offset: PointId? = null
        do {
            val result = client.scrollAsync(collectionName, null, offset, 100, true, true, null).get()
            for (point in result.pointsList) {
                val content = point.payloadMap["content"]?.stringValue ?: continue
                val vector = point.vectorsList.map { it.toDouble() }.toDoubleArray()
                emit(DocumentWithPayload(content, vector))
            }
            offset = result.nextPageOffset
        } while (offset != null)
    }
}
```

### Verwendung mit EmbeddingBasedDocumentStorage

```kotlin
import ai.koog.rag.vector.EmbeddingBasedDocumentStorage
import ai.koog.rag.embedding.LLMEmbedder
import ai.koog.rag.embedding.JVMTextDocumentEmbedder
import ai.koog.prompt.executor.clients.openai.OpenAIEmbeddingClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels

// 1. Embedder setup (OpenAI oder Ollama)
val embeddingClient = OpenAIEmbeddingClient(System.getenv("OPENAI_API_KEY"))
val embedder = LLMEmbedder(embeddingClient, OpenAIModels.Embeddings.TextEmbedding3Small)
val documentEmbedder = JVMTextDocumentEmbedder(embedder)

// 2. Externe Vector-DB statt InMemoryVectorStorage
val qdrantClient = QdrantClient(
    QdrantGrpcClient.newBuilder("localhost", 6334, false).build()
)
val vectorStorage = QdrantVectorStorage(qdrantClient, "my-documents")

// 3. Kombination: Embedder + Storage = RAG
val ragStorage = EmbeddingBasedDocumentStorage(documentEmbedder, vectorStorage)

// 4. Dokumente speichern
ragStorage.store(Path.of("./docs/product-manual.txt"))
ragStorage.store(Path.of("./docs/faq.txt"))

// 5. Semantische Suche
val results = ragStorage.mostRelevantDocuments(
    query = "How do I reset my password?",
    count = 3,
    similarityThreshold = 0.7
)
results.forEach { println("Found: ${it.toAbsolutePath()}") }
```

## 2. Custom `RetrievalStorage` + `IngestionStorage` — für Long-Term Memory

Für Agent-Memory (statt Dokument-Retrieval) bietet Koog das `RetrievalStorage`/`IngestionStorage` Interface-Paar. Ideal für Agenten die sich Dinge über Sessions hinweg merken sollen.

### Interface

```kotlin
class MyVectorDbStorage : RetrievalStorage, IngestionStorage {
    override suspend fun search(
        request: SearchRequest, namespace: String?
    ): List<SearchResult> {
        // Query your vector DB (z.B. Qdrant, Pinecone, pgvector)
    }

    override suspend fun add(
        records: List<MemoryRecord>, namespace: String?
    ) {
        // Upsert into your vector DB
    }
}
```

### Beispiel: PostgreSQL/pgvector als Memory Storage

```kotlin
import ai.koog.agents.memory.RetrievalStorage
import ai.koog.agents.memory.IngestionStorage
import ai.koog.agents.memory.SearchRequest
import ai.koog.agents.memory.SearchResult
import ai.koog.agents.memory.MemoryRecord
import org.jetbrains.exposed.sql.*

class PgVectorMemoryStorage(
    private val database: Database,
    private val embedder: LLMEmbedder
) : RetrievalStorage, IngestionStorage {

    override suspend fun search(
        request: SearchRequest, namespace: String?
    ): List<SearchResult> {
        val queryVector = embedder.embed(request.query)
        // SQL: SELECT *, embedding <=> $queryVector AS distance
        //      FROM memories WHERE namespace = $namespace
        //      ORDER BY distance LIMIT $request.limit
        return transaction(database) {
            exec("""
                SELECT id, content, metadata, 
                       embedding <=> '${queryVector.toList()}' AS distance
                FROM memories 
                WHERE namespace = ?
                ORDER BY distance 
                LIMIT ?
            """.trimIndent(), listOf(
                StringColumnType() to (namespace ?: "default"),
                IntegerColumnType() to request.limit
            )) { rs ->
                val results = mutableListOf<SearchResult>()
                while (rs.next()) {
                    results.add(SearchResult(
                        id = rs.getString("id"),
                        content = rs.getString("content"),
                        score = 1.0 - rs.getDouble("distance")
                    ))
                }
                results
            } ?: emptyList()
        }
    }

    override suspend fun add(
        records: List<MemoryRecord>, namespace: String?
    ) {
        for (record in records) {
            val vector = embedder.embed(record.content)
            transaction(database) {
                exec("""
                    INSERT INTO memories (id, content, metadata, embedding, namespace)
                    VALUES (?, ?, ?, '${vector.toList()}', ?)
                    ON CONFLICT (id) DO UPDATE SET 
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding
                """.trimIndent(), listOf(
                    StringColumnType() to record.id,
                    StringColumnType() to record.content,
                    StringColumnType() to record.metadata.toString(),
                    StringColumnType() to (namespace ?: "default")
                ))
            }
        }
    }
}
```

### SQL Schema (PostgreSQL + pgvector Extension)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE memories (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    embedding vector(1536),  -- Dimension passend zum Embedding-Modell
    namespace TEXT DEFAULT 'default',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX ON memories USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

## Vergleich der Ansaetze

| | VectorStorage (RAG) | RetrievalStorage (Memory) |
|---|---|---|
| **Use Case** | Dokument-Retrieval, Wissens-Suche | Agent-Memory, Session-uebergreifend |
| **Interface** | `VectorStorage<T>` | `RetrievalStorage` + `IngestionStorage` |
| **Speichert** | Dokumente + Embedding-Vektoren | Memory-Records + Vektoren |
| **Suche** | `mostRelevantDocuments(query, count)` | `search(SearchRequest)` |
| **Koog-Modul** | `rag/vector-storage` | `agents/memory` |
| **Built-in** | `InMemoryVectorStorage` | `InMemoryRecordStorage` |

## Gradle Dependencies

```kotlin
// RAG Document Storage
implementation("ai.koog:koog-agents:0.7.2")  // Enthaelt rag-base + vector-storage

// Fuer Qdrant
implementation("io.qdrant:client:1.12.0")

// Fuer pgvector (mit Exposed ORM)
implementation("org.jetbrains.exposed:exposed-core:0.55.0")
implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
implementation("org.postgresql:postgresql:42.7.4")
implementation("com.pgvector:pgvector:0.1.6")

// Embeddings (OpenAI oder Ollama)
implementation("ai.koog.prompt:prompt-executor-openai-client:0.7.2")
// oder: implementation("ai.koog.prompt:prompt-executor-ollama-client:0.7.2")
```
