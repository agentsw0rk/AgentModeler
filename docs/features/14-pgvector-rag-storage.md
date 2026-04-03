# Feature 14: PostgreSQL/pgvector RAG Storage

## Problem

Der RAG-Node unterstuetzt aktuell drei Storage-Typen: InMemory (kein Persistenz), File (lokales Filesystem) und Custom (manuelle FQN-Eingabe). Fuer produktionsreife RAG-Anwendungen fehlt eine persistente, skalierbare Vector-Datenbank. Der User muss aktuell die Custom-Option nutzen und eine eigene `VectorStorage`-Implementierung schreiben.

## Ziel

`PostgreSQL` als vierten Storage-Typ im RAG-Node mit:

1. **Eigener Storage-Typ** `PostgreSQL` im Dropdown — keine FQN-Eingabe noetig
2. **Generierte `PgVectorStorage.kt`** — implementiert `VectorStorage<String>`, wird inline als Teil des Projekts erzeugt
3. **docker-compose.yaml** — wird automatisch generiert mit 2 Services (pgvector DB + App), sofort lauffaehig mit `docker compose up`
4. **Konfiguration per Env-Vars** — `PGVECTOR_HOST`, `PGVECTOR_PORT`, `PGVECTOR_DB`, `PGVECTOR_USER`, `PGVECTOR_PASSWORD` mit sinnvollen Defaults

## Voraussetzungen

| Abhaengigkeit | Status | Blocker? |
|---|---|---|
| RAG-Node (Feature 10) | Implementiert | Nein |
| Koog `VectorStorage<T>` Interface | Verfuegbar in Koog 0.7.2 | Nein |
| `pgvector/pgvector` Docker Image | Offiziell verfuegbar | Nein |
| PostgreSQL JDBC Driver | Standard Maven-Artefakt | Nein |
| pgvector-java Library | Standard Maven-Artefakt | Nein |

## Architektur

### Storage-Typ Enum

```kotlin
// RagModel.kt — neuer Wert
enum class RagStorageType { InMemory, File, Custom, PostgreSQL }
```

### Generierter Code — PgVectorStorage.kt

Wird als eigene Datei unter `src/main/kotlin/generated/rag/PgVectorStorage.kt` generiert:

```kotlin
package generated.rag

import ai.koog.rag.vector.VectorStorage
import ai.koog.rag.vector.DocumentWithPayload
import ai.koog.rag.vector.Vector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PgVectorStorage(
    private val jdbcUrl: String = "jdbc:postgresql://" +
        "${System.getenv("PGVECTOR_HOST") ?: "localhost"}:" +
        "${System.getenv("PGVECTOR_PORT") ?: "5432"}/" +
        "${System.getenv("PGVECTOR_DB") ?: "ragdb"}",
    private val user: String = System.getenv("PGVECTOR_USER") ?: "postgres",
    private val password: String = System.getenv("PGVECTOR_PASSWORD") ?: "postgres",
    private val tableName: String = "rag_documents",
    private val dimensions: Int = 1536
) : VectorStorage<String> {

    private fun connection(): Connection =
        DriverManager.getConnection(jdbcUrl, user, password)

    fun initSchema() {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector")
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS $tableName (
                        id TEXT PRIMARY KEY,
                        content TEXT NOT NULL,
                        embedding vector($dimensions),
                        created_at TIMESTAMPTZ DEFAULT NOW()
                    )
                """.trimIndent())
            }
        }
    }

    override suspend fun store(document: String, data: Vector): String {
        val id = UUID.randomUUID().toString()
        connection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO $tableName (id, content, embedding) VALUES (?, ?, ?::vector)"
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, document)
                stmt.setString(3, data.toList().toString())
                stmt.executeUpdate()
            }
        }
        return id
    }

    override suspend fun read(documentId: String): String? {
        connection().use { conn ->
            conn.prepareStatement("SELECT content FROM $tableName WHERE id = ?").use { stmt ->
                stmt.setString(1, documentId)
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getString("content") else null
            }
        }
    }

    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<String, Vector>? {
        connection().use { conn ->
            conn.prepareStatement("SELECT content, embedding FROM $tableName WHERE id = ?").use { stmt ->
                stmt.setString(1, documentId)
                val rs = stmt.executeQuery()
                if (!rs.next()) return null
                val content = rs.getString("content")
                val embeddingStr = rs.getString("embedding")
                val vector = parseVector(embeddingStr)
                return DocumentWithPayload(content, vector)
            }
        }
    }

    override suspend fun delete(documentId: String): Boolean {
        connection().use { conn ->
            conn.prepareStatement("DELETE FROM $tableName WHERE id = ?").use { stmt ->
                stmt.setString(1, documentId)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun getPayload(documentId: String): Vector? {
        return readWithPayload(documentId)?.payload
    }

    override fun allDocuments(): Flow<String> = flow {
        connection().use { conn ->
            conn.prepareStatement("SELECT content FROM $tableName").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) emit(rs.getString("content"))
            }
        }
    }

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<String, Vector>> = flow {
        connection().use { conn ->
            conn.prepareStatement("SELECT content, embedding FROM $tableName").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val content = rs.getString("content")
                    val vector = parseVector(rs.getString("embedding"))
                    emit(DocumentWithPayload(content, vector))
                }
            }
        }
    }

    private fun parseVector(pgVector: String): Vector {
        return pgVector.trim('[', ']').split(",")
            .map { it.trim().toDouble() }
            .toDoubleArray()
    }
}
```

### Generierter RAG-File Auszug (PostgreSQL)

Im generierten RAG-File (z.B. `MyRag.kt`) aendert sich nur die Storage-Initialisierung:

```kotlin
// Statt:  val vectorStorage = InMemoryVectorStorage<String>()
val vectorStorage = PgVectorStorage(dimensions = 1536)

val documentEmbedder = JVMTextDocumentEmbedder(embedder)
val storage = EmbeddingBasedDocumentStorage(documentEmbedder, vectorStorage)

suspend fun initialize() {
    vectorStorage.initSchema()  // CREATE EXTENSION + CREATE TABLE
    // ... Dokumente laden wie bisher
}
```

Die `dimensions` werden aus dem Embedder-Model abgeleitet:
- OpenAI text-embedding-3-small: 1536
- OpenAI text-embedding-3-large: 3072
- Ollama nomic-embed-text: 768

### docker-compose.yaml

Neuer `DockerComposeGenerator` erzeugt `docker-compose.yaml` wenn mindestens ein RAG-Node `PostgreSQL` als Storage hat:

```yaml
services:
  db:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: ragdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      PGVECTOR_HOST: db
      PGVECTOR_PORT: "5432"
      PGVECTOR_DB: ragdb
      PGVECTOR_USER: postgres
      PGVECTOR_PASSWORD: postgres
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
```

- `pgvector/pgvector:pg17` — offizielles pgvector Docker Image mit PostgreSQL 17
- `app` baut das generierte Dockerfile, wartet auf DB-Healthcheck
- API-Key wird vom Host durchgereicht
- Env-Var fuer den ModelProvider wird dynamisch ergaenzt (OPENAI_API_KEY, ANTHROPIC_API_KEY, etc.)

### Build Dependencies

Wenn ein RAG-Node `PostgreSQL` als Storage hat, werden zur `build.gradle.kts` ergaenzt:

```kotlin
implementation("org.postgresql:postgresql:42.7.4")
implementation("com.pgvector:pgvector:0.1.6")
```

## Betroffene Dateien

### Backend

| Datei | Aenderung |
|---|---|
| `export/model/RagModel.kt` | `PostgreSQL` zu `RagStorageType` |
| `export/generator/RagCodeGenerator.kt` | PostgreSQL Storage-Initialisierung + PgVectorStorage.kt Generierung |
| `export/generator/DockerComposeGenerator.kt` | NEU: docker-compose.yaml mit pgvector + app Services |
| `export/generator/BuildGradleGenerator.kt` | PostgreSQL + pgvector Dependencies |
| `export/generator/lib/LibBuildGradleGenerator.kt` | PostgreSQL + pgvector Dependencies (KMP) |
| `export/backend/SpringBootBackend.kt` | docker-compose.yaml in generierte Files aufnehmen |
| `export/backend/KmpLibraryBackend.kt` | Keine Aenderung (Library hat kein Docker) |

### Frontend

| Datei | Aenderung |
|---|---|
| `types/rag.ts` | `"PostgreSQL"` zu `RagStorageType` |
| `components/panels/RagPropertiesStorageTab.tsx` | PostgreSQL SelectItem |

### Tests

| Datei | Aenderung |
|---|---|
| `RagCodeGeneratorTest.kt` | Test mit PostgreSQL Storage |
| `ExportServiceTest.kt` | Integration-Test mit pgvector RAG |

## Platform-Einschraenkungen

| Backend | PostgreSQL RAG verfuegbar? | Grund |
|---|---|---|
| Spring Boot (JVM) | Ja | JDBC + pgvector-java |
| KMP Library (JVM Target) | Ja | JDBC verfuegbar auf JVM |
| Ktor/Wasm | Nein | Kein JDBC auf WasmJS |

## Akzeptanzkriterien

- [ ] `RagStorageType.PostgreSQL` existiert im Enum
- [ ] RAG Storage Tab zeigt "PostgreSQL / pgvector" als Option
- [ ] Keine zusaetzlichen UI-Felder bei PostgreSQL (alles per Env-Var)
- [ ] Generierter Code enthaelt `PgVectorStorage.kt` mit vollstaendiger `VectorStorage<String>` Implementierung
- [ ] `PgVectorStorage.initSchema()` erstellt Extension + Table automatisch
- [ ] `dimensions` Parameter passt sich dem Embedder-Model an
- [ ] `build.gradle.kts` enthaelt PostgreSQL-Driver + pgvector-java Dependencies
- [ ] `docker-compose.yaml` wird generiert mit `db` (pgvector) + `app` (Agent) Services
- [ ] `docker compose up` startet DB + App, App wartet auf DB-Healthcheck
- [ ] Env-Vars (`PGVECTOR_HOST`, etc.) haben sinnvolle Defaults fuer lokale Entwicklung
- [ ] docker-compose wird NUR generiert wenn mindestens ein RAG-Node PostgreSQL Storage nutzt
- [ ] Bestehende Storage-Typen (InMemory, File, Custom) bleiben unberuehrt
