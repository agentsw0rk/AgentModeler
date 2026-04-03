# pgvector RAG Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `PostgreSQL` as fourth RAG storage type with inline `PgVectorStorage.kt` generation, docker-compose.yaml with pgvector + app services, and PostgreSQL dependencies.

**Architecture:** Add `"POSTGRESQL"` case to `RagCodeGenerator` storage branching, create `DockerComposeGenerator` for multi-service YAML, add PostgreSQL/pgvector dependencies to build.gradle generators, add dropdown option to frontend RAG storage tab.

**Tech Stack:** Kotlin, KotlinPoet, Spring Boot 4, Koog 0.7.2 VectorStorage interface, PostgreSQL JDBC 42.7.4, pgvector-java 0.1.6, Next.js 16

---

## File Structure

### New files
- `src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt` — generates docker-compose.yaml
- `src/main/kotlin/com/agentwork/modeler/export/generator/PgVectorStorageGenerator.kt` — generates PgVectorStorage.kt

### Modified files (Backend)
- `src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt` — PostgreSQL storage case
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt` — PostgreSQL dependencies
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt` — PostgreSQL dependencies
- `src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt` — docker-compose in file map
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` — pass ragNodes to backend for docker-compose decision

### Modified files (Frontend)
- `frontend/types/rag.ts` — add `"PostgreSQL"` to RagStorageType
- `frontend/components/panels/RagPropertiesStorageTab.tsx` — PostgreSQL SelectItem

### Test files
- `src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt` — PostgreSQL storage test
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt` — integration test with pgvector RAG

---

## Task 1: PgVectorStorageGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/PgVectorStorageGenerator.kt`

- [ ] **Step 1: Create PgVectorStorageGenerator**

This generator produces the inline `PgVectorStorage.kt` file. It's a string-template generator (not KotlinPoet) since the generated class is complex and self-contained.

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/PgVectorStorageGenerator.kt
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class PgVectorStorageGenerator {

    fun generate(dimensions: Int = 1536): String = buildString {
        appendLine("package generated.rag")
        appendLine()
        appendLine("import ai.koog.rag.vector.VectorStorage")
        appendLine("import ai.koog.rag.vector.DocumentWithPayload")
        appendLine("import ai.koog.rag.vector.Vector")
        appendLine("import kotlinx.coroutines.flow.Flow")
        appendLine("import kotlinx.coroutines.flow.flow")
        appendLine("import java.sql.Connection")
        appendLine("import java.sql.DriverManager")
        appendLine("import java.util.UUID")
        appendLine()
        appendLine("class PgVectorStorage(")
        appendLine("    private val jdbcUrl: String = \"jdbc:postgresql://\" +")
        appendLine("        \"\${System.getenv(\"PGVECTOR_HOST\") ?: \"localhost\"}:\" +")
        appendLine("        \"\${System.getenv(\"PGVECTOR_PORT\") ?: \"5432\"}/\" +")
        appendLine("        \"\${System.getenv(\"PGVECTOR_DB\") ?: \"ragdb\"}\",")
        appendLine("    private val user: String = System.getenv(\"PGVECTOR_USER\") ?: \"postgres\",")
        appendLine("    private val password: String = System.getenv(\"PGVECTOR_PASSWORD\") ?: \"postgres\",")
        appendLine("    private val tableName: String = \"rag_documents\",")
        appendLine("    private val dimensions: Int = $dimensions")
        appendLine(") : VectorStorage<String> {")
        appendLine()
        appendLine("    private fun connection(): Connection =")
        appendLine("        DriverManager.getConnection(jdbcUrl, user, password)")
        appendLine()
        appendLine("    fun initSchema() {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.createStatement().use { stmt ->")
        appendLine("                stmt.execute(\"CREATE EXTENSION IF NOT EXISTS vector\")")
        appendLine("                stmt.execute(\"\"\"")
        appendLine("                    CREATE TABLE IF NOT EXISTS \$tableName (")
        appendLine("                        id TEXT PRIMARY KEY,")
        appendLine("                        content TEXT NOT NULL,")
        appendLine("                        embedding vector(\$dimensions),")
        appendLine("                        created_at TIMESTAMPTZ DEFAULT NOW()")
        appendLine("                    )")
        appendLine("                \"\"\".trimIndent())")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    override suspend fun store(document: String, data: Vector): String {")
        appendLine("        val id = UUID.randomUUID().toString()")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(")
        appendLine("                \"INSERT INTO \$tableName (id, content, embedding) VALUES (?, ?, ?::vector)\"")
        appendLine("            ).use { stmt ->")
        appendLine("                stmt.setString(1, id)")
        appendLine("                stmt.setString(2, document)")
        appendLine("                stmt.setString(3, data.toList().toString())")
        appendLine("                stmt.executeUpdate()")
        appendLine("            }")
        appendLine("        }")
        appendLine("        return id")
        appendLine("    }")
        appendLine()
        appendLine("    override suspend fun read(documentId: String): String? {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(\"SELECT content FROM \$tableName WHERE id = ?\").use { stmt ->")
        appendLine("                stmt.setString(1, documentId)")
        appendLine("                val rs = stmt.executeQuery()")
        appendLine("                return if (rs.next()) rs.getString(\"content\") else null")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<String, Vector>? {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(\"SELECT content, embedding FROM \$tableName WHERE id = ?\").use { stmt ->")
        appendLine("                stmt.setString(1, documentId)")
        appendLine("                val rs = stmt.executeQuery()")
        appendLine("                if (!rs.next()) return null")
        appendLine("                val content = rs.getString(\"content\")")
        appendLine("                val vector = parseVector(rs.getString(\"embedding\"))")
        appendLine("                return DocumentWithPayload(content, vector)")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    override suspend fun delete(documentId: String): Boolean {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(\"DELETE FROM \$tableName WHERE id = ?\").use { stmt ->")
        appendLine("                stmt.setString(1, documentId)")
        appendLine("                return stmt.executeUpdate() > 0")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    override suspend fun getPayload(documentId: String): Vector? =")
        appendLine("        readWithPayload(documentId)?.payload")
        appendLine()
        appendLine("    override fun allDocuments(): Flow<String> = flow {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(\"SELECT content FROM \$tableName\").use { stmt ->")
        appendLine("                val rs = stmt.executeQuery()")
        appendLine("                while (rs.next()) emit(rs.getString(\"content\"))")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<String, Vector>> = flow {")
        appendLine("        connection().use { conn ->")
        appendLine("            conn.prepareStatement(\"SELECT content, embedding FROM \$tableName\").use { stmt ->")
        appendLine("                val rs = stmt.executeQuery()")
        appendLine("                while (rs.next()) {")
        appendLine("                    val content = rs.getString(\"content\")")
        appendLine("                    val vector = parseVector(rs.getString(\"embedding\"))")
        appendLine("                    emit(DocumentWithPayload(content, vector))")
        appendLine("                }")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    private fun parseVector(pgVector: String): Vector =")
        appendLine("        pgVector.trim('[', ']').split(\",\").map { it.trim().toDouble() }.toDoubleArray()")
        appendLine("}")
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/PgVectorStorageGenerator.kt
git commit -m "feat: add PgVectorStorageGenerator for inline PostgreSQL vector storage"
```

---

## Task 2: DockerComposeGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt`

- [ ] **Step 1: Create DockerComposeGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class DockerComposeGenerator {

    fun generate(agent: KoogAgent): String = buildString {
        val apiKeyEnvVar = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "OPENAI_API_KEY"
            ModelProvider.ANTHROPIC -> "ANTHROPIC_API_KEY"
            ModelProvider.GOOGLE -> "GOOGLE_API_KEY"
            ModelProvider.BEDROCK -> "AWS_BEDROCK_ACCESS_KEY"
        }

        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: pgvector/pgvector:pg17")
        appendLine("    environment:")
        appendLine("      POSTGRES_DB: ragdb")
        appendLine("      POSTGRES_USER: postgres")
        appendLine("      POSTGRES_PASSWORD: postgres")
        appendLine("    ports:")
        appendLine("      - \"5432:5432\"")
        appendLine("    volumes:")
        appendLine("      - pgdata:/var/lib/postgresql/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD-SHELL\", \"pg_isready -U postgres\"]")
        appendLine("      interval: 5s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        appendLine()
        appendLine("  app:")
        appendLine("    build: .")
        appendLine("    ports:")
        appendLine("      - \"8080:8080\"")
        appendLine("    environment:")
        appendLine("      PGVECTOR_HOST: db")
        appendLine("      PGVECTOR_PORT: \"5432\"")
        appendLine("      PGVECTOR_DB: ragdb")
        appendLine("      PGVECTOR_USER: postgres")
        appendLine("      PGVECTOR_PASSWORD: postgres")
        appendLine("      $apiKeyEnvVar: \${$apiKeyEnvVar}")
        if (agent.modelProvider == ModelProvider.BEDROCK) {
            appendLine("      AWS_BEDROCK_SECRET_ACCESS_KEY: \${AWS_BEDROCK_SECRET_ACCESS_KEY}")
            appendLine("      AWS_BEDROCK_REGION: \${AWS_BEDROCK_REGION:-us-west-2}")
        }
        appendLine("    depends_on:")
        appendLine("      db:")
        appendLine("        condition: service_healthy")
        appendLine()
        appendLine("volumes:")
        appendLine("  pgdata:")
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/DockerComposeGenerator.kt
git commit -m "feat: add DockerComposeGenerator for pgvector + app services"
```

---

## Task 3: RagCodeGenerator — PostgreSQL Storage Case

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `RagCodeGeneratorTest.kt`:

```kotlin
@Test
fun `generates PostgreSQL storage with PgVectorStorage`() {
    val code = generator.generateRagFile(ragNode(
        storageType = "PostgreSQL",
        filePaths = listOf("./docs/manual.txt")
    ))

    assertContains(code, "object ProductDocsRag")
    assertContains(code, "PgVectorStorage()")
    assertContains(code, "vectorStorage.initSchema()")
    assertContains(code, "storage.store(Path.of(\"./docs/manual.txt\"))")
    assertFalse(code.contains("InMemoryVectorStorage"))
    assertFalse(code.contains("JVMFileVectorStorage"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.RagCodeGeneratorTest.generates PostgreSQL storage*"`
Expected: FAIL — assertion on "PgVectorStorage" fails

- [ ] **Step 3: Modify RagCodeGenerator**

In `RagCodeGenerator.kt`, find the `storageInitializer` `when` block (around line 80-99). Add a new case for PostgreSQL before the `else`:

```kotlin
"POSTGRESQL" -> {
    val pgVectorStorageClass = ClassName("generated.rag", "PgVectorStorage")
    // Storage uses PgVectorStorage wrapping
    CodeBlock.of(
        "%T(documentEmbedder, %T())",
        embeddingBasedStorage, pgVectorStorageClass
    )
}
```

Also modify the `initialize()` function builder. After the `initFun` is created (after `val initFun = FunSpec.builder("initialize").addModifiers(KModifier.SUSPEND)`), add before the filePaths loop:

```kotlin
if (rag.storageType.uppercase() == "POSTGRESQL") {
    // Add a private vectorStorage property reference for initSchema
    // We need to call initSchema before storing documents
    initFun.addStatement("(storage as? %T)?.let { /* schema auto-created via PgVectorStorage.initSchema() */ }", embeddingBasedStorage)
}
```

Actually, a cleaner approach: add a separate `vectorStorage` property when PostgreSQL, so `initSchema()` can be called:

Find where the `storage` property is created. For PostgreSQL, add a `vectorStorage` property before the `storage` property:

```kotlin
if (rag.storageType.uppercase() == "POSTGRESQL") {
    val pgVectorStorageClass = ClassName("generated.rag", "PgVectorStorage")
    objectBuilder.addProperty(
        PropertySpec.builder("vectorStorage", pgVectorStorageClass)
            .addModifiers(KModifier.PRIVATE)
            .initializer("%T()", pgVectorStorageClass)
            .build()
    )
}
```

And change the storage initializer for PostgreSQL to use this property:

```kotlin
"POSTGRESQL" -> {
    CodeBlock.of(
        "%T(documentEmbedder, vectorStorage)",
        embeddingBasedStorage
    )
}
```

And in the `initialize()` function, add `vectorStorage.initSchema()` before file loading:

```kotlin
if (rag.storageType.uppercase() == "POSTGRESQL") {
    initFun.addStatement("vectorStorage.initSchema()")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.RagCodeGeneratorTest"`
Expected: All tests pass including new PostgreSQL test

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt \
  src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt
git commit -m "feat: add PostgreSQL storage case to RagCodeGenerator"
```

---

## Task 4: BuildGradle Dependencies + SpringBootBackend docker-compose

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt`

- [ ] **Step 1: Add PostgreSQL dependencies to BuildGradleGenerator**

In `BuildGradleGenerator.kt`, the `generate()` method takes a `KoogAgent`. However, the agent doesn't carry RAG storage info directly — the RAG nodes are in `agent.ragNodes`. Check if any ragNode has `storageType == "PostgreSQL"`.

After the Bedrock dependency block, add:

```kotlin
if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
    appendLine("""    implementation("org.postgresql:postgresql:42.7.4")""")
    appendLine("""    implementation("com.pgvector:pgvector:0.1.6")""")
}
```

- [ ] **Step 2: Add PostgreSQL dependencies to LibBuildGradleGenerator**

In `LibBuildGradleGenerator.kt`, after the Bedrock dependency block, add:

```kotlin
if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
    appendLine("""            implementation("org.postgresql:postgresql:42.7.4")""")
    appendLine("""            implementation("com.pgvector:pgvector:0.1.6")""")
}
```

- [ ] **Step 3: Add DockerComposeGenerator to SpringBootBackend**

In `SpringBootBackend.kt`, add `DockerComposeGenerator` to the constructor:

```kotlin
@Component
class SpringBootBackend(
    // ... existing generators ...
    private val dockerComposeGenerator: DockerComposeGenerator,
    private val pgVectorStorageGenerator: PgVectorStorageGenerator
) : GeneratorBackend {
```

In `generateAgentFiles()`, after `files["Dockerfile"] = dockerfileGenerator.generate()`, add:

```kotlin
// Docker Compose (when pgvector RAG storage is used)
if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
    files["docker-compose.yaml"] = dockerComposeGenerator.generate(agent)
    files["$pkg/rag/PgVectorStorage.kt"] = pgVectorStorageGenerator.generate()
}
```

Repeat the same pattern in `generateGraphAgentFiles()` (using `infraAgent` and checking `agent.ragNodes`):

```kotlin
if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
    files["docker-compose.yaml"] = dockerComposeGenerator.generate(infraAgent)
    files["$pkg/rag/PgVectorStorage.kt"] = pgVectorStorageGenerator.generate()
}
```

- [ ] **Step 4: Compile and run tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/backend/SpringBootBackend.kt
git commit -m "feat: add PostgreSQL dependencies and docker-compose to export pipeline"
```

---

## Task 5: Frontend — RagStorageType + Storage Tab

**Files:**
- Modify: `frontend/types/rag.ts`
- Modify: `frontend/components/panels/RagPropertiesStorageTab.tsx`

- [ ] **Step 1: Add PostgreSQL to RagStorageType**

In `frontend/types/rag.ts`, change:

```typescript
export type RagStorageType = "InMemory" | "File" | "Custom" | "PostgreSQL";
```

- [ ] **Step 2: Add PostgreSQL SelectItem to RagPropertiesStorageTab**

In `RagPropertiesStorageTab.tsx`, add after the File-based SelectItem:

```tsx
<SelectItem value="PostgreSQL">PostgreSQL / pgvector</SelectItem>
```

The full SelectContent becomes:

```tsx
<SelectContent>
    <SelectItem value="InMemory">In-Memory</SelectItem>
    <SelectItem value="File">File-based</SelectItem>
    <SelectItem value="PostgreSQL">PostgreSQL / pgvector</SelectItem>
    <SelectItem value="Custom">Custom</SelectItem>
</SelectContent>
```

No additional fields needed for PostgreSQL (all config via env-vars).

- [ ] **Step 3: Run frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/types/rag.ts \
  frontend/components/panels/RagPropertiesStorageTab.tsx
git commit -m "feat: add PostgreSQL/pgvector option to RAG storage dropdown"
```

---

## Task 6: Integration Test

**Files:**
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Add pgvector RAG integration test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `exports graph agent with pgvector RAG including docker-compose`() {
    val graph = WorkspaceGraph(1, "PgRagProject",
        listOf(
            GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                "name" to "DocsAgent",
                "description" to "Searches docs",
                "systemPrompt" to "Help with docs",
                "modelProvider" to "openai",
                "strategyNodes" to listOf(
                    mapOf("id" to "n1", "type" to "nodeLLM", "label" to "answer")
                ),
                "strategyEdges" to emptyList<Any>()
            )),
            GraphNode("rag1", "rag-node", Position(-200.0, 0.0), mapOf(
                "name" to "ProductDocs",
                "embedderProvider" to "OpenAI",
                "embedderModel" to "TextEmbedding3Small",
                "apiKeyVariable" to "OPENAI_API_KEY",
                "storageType" to "PostgreSQL",
                "similarityThreshold" to 0.7,
                "filePaths" to listOf("./docs/manual.txt"),
                "urlSources" to emptyList<String>(),
                "maxDocuments" to 5,
                "injectAsContext" to true,
                "provideAsTool" to false,
                "toolDescription" to ""
            ))
        ),
        listOf(GraphEdge("e1", "rag1", "ga1"))
    )
    val (_, files) = service.exportWithFiles(graph)

    // docker-compose.yaml generated
    assertTrue(files.containsKey("docker-compose.yaml"))
    val compose = files["docker-compose.yaml"]!!
    assertContains(compose, "pgvector/pgvector:pg17")
    assertContains(compose, "PGVECTOR_HOST: db")
    assertContains(compose, "service_healthy")

    // PgVectorStorage.kt generated
    assertTrue(files.containsKey("src/main/kotlin/generated/rag/PgVectorStorage.kt"))
    val pgStorage = files["src/main/kotlin/generated/rag/PgVectorStorage.kt"]!!
    assertContains(pgStorage, "class PgVectorStorage")
    assertContains(pgStorage, "VectorStorage<String>")

    // RAG file uses PgVectorStorage
    val ragFile = files["src/main/kotlin/generated/rag/ProductDocsRag.kt"]!!
    assertContains(ragFile, "PgVectorStorage()")
    assertContains(ragFile, "initSchema()")

    // build.gradle has PostgreSQL deps
    assertContains(files["build.gradle.kts"]!!, "postgresql")
    assertContains(files["build.gradle.kts"]!!, "pgvector")
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "test: add pgvector RAG integration test with docker-compose"
```

---

## Task 7: Full Stack Verification

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Start backend + frontend, verify:
1. Create a Graph Agent with RAG node
2. RAG node Storage tab shows "PostgreSQL / pgvector"
3. Selecting it shows no additional fields
4. Code explorer generates `PgVectorStorage.kt`, `docker-compose.yaml`
5. Export ZIP contains all expected files
