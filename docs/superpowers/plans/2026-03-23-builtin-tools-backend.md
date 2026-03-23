# Built-in Tools Backend — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend support for 7 built-in tool types (Think, Shell, Filesystem, Fetch, Memory, Todo, User Prompt) that export as Koog built-in registrations or ready-to-use Kotlin ToolSet classes.

**Architecture:** Extend KoogModel with `BuiltinToolType` enum and `KoogBuiltinTool` data class. Add `builtinTools` field to `KoogAgent`. Extend adapter/validator for `"builtin-tool"` graph nodes. New `BuiltinToolGenerator` produces Kotlin ToolSet code for Shell/Fetch/Memory/Todo, returns null for Think/Filesystem/UserPrompt (Koog built-ins). Extend RegistryGenerator, AgentGenerator, BuildGradleGenerator, and ExportService.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-23-builtin-tools-design.md`

---

## File Map

### New Files
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt`

### Modified Files
- `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

---

## Task 1: Extend KoogModel with BuiltinToolType and KoogBuiltinTool

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: All test files that construct `KoogAgent` (add `builtinTools = emptyList()`)

**IMPORTANT:** Adding `builtinTools` to `KoogAgent` breaks every existing call site. All tests constructing `KoogAgent` must add the new parameter. This task handles that migration.

- [ ] **Step 1: Add new types and extend KoogAgent**

In `KoogModel.kt`, add after the `KoogParamType` enum:

```kotlin
enum class BuiltinToolType {
    THINK, SHELL, FILESYSTEM, FETCH, MEMORY, TODO, USER_PROMPT;

    fun toFileName(): String? = when (this) {
        SHELL -> "ShellToolSet.kt"
        FETCH -> "FetchToolSet.kt"
        MEMORY -> "MemoryToolSet.kt"
        TODO -> "TodoToolSet.kt"
        else -> null
    }
}

data class KoogBuiltinTool(
    val type: BuiltinToolType,
    val config: Map<String, Any?> = emptyMap()
)
```

And add `builtinTools` to `KoogAgent`:

```kotlin
data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool> = emptyList()
)
```

Note: Using default `= emptyList()` means existing production code (adapter) still compiles without change. But test files that use positional args need updating.

- [ ] **Step 2: Update all test files that construct KoogAgent**

Every test that uses `KoogAgent(...)` with positional parameters must work. Since we added a default parameter at the end, positional calls with 6 args still compile. Verify by running:

Run: `./gradlew test`
Expected: ALL PASS (default parameter means no breakage)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat: add BuiltinToolType enum, KoogBuiltinTool, and builtinTools to KoogAgent"
```

---

## Task 2: Extend GraphToKoogAdapter for builtin-tool nodes

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `GraphToKoogAdapterTest.kt`:

```kotlin
@Test
fun `converts builtin-tool nodes to KoogBuiltinTool list`() {
    val graph = ProjectGraph(
        version = 1, name = "Test",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell", "workingDir" to "/app")),
            GraphNode("bt2", "builtin-tool", Position(400.0, 0.0), mapOf("toolType" to "think"))
        ),
        edges = listOf(GraphEdge("e1", "bt1", "a1"), GraphEdge("e2", "bt2", "a1"))
    )
    val agents = adapter.convert(graph)
    assertEquals(2, agents[0].builtinTools.size)
    assertEquals(BuiltinToolType.SHELL, agents[0].builtinTools[0].type)
    assertEquals("/app", agents[0].builtinTools[0].config["workingDir"])
    assertEquals(BuiltinToolType.THINK, agents[0].builtinTools[1].type)
}
```

Also add the import at the top: `import com.agentwork.modeler.export.model.BuiltinToolType`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest.converts builtin-tool nodes to KoogBuiltinTool list"`
Expected: FAIL — `builtinTools` is always empty.

- [ ] **Step 3: Implement the adapter extension**

In `GraphToKoogAdapter.kt`, add a `builtinToolNodes` filter at the top of `convert()` alongside the existing filters:

```kotlin
val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
```

Inside the `agentNodes.map` block, after the `tools` computation, add:

```kotlin
val builtinTools = builtinToolNodes
    .filter { it.id in connectedToolIds }
    .map { convertBuiltinTool(it) }
```

And pass it to the `KoogAgent` constructor:

```kotlin
KoogAgent(
    name = name,
    systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
    modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
    modelId = agentNode.data["modelId"] as? String,
    temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
    tools = tools,
    builtinTools = builtinTools
)
```

Add the `convertBuiltinTool` private method:

```kotlin
private fun convertBuiltinTool(node: GraphNode): KoogBuiltinTool {
    val typeStr = node.data["toolType"] as? String ?: ""
    val type = BuiltinToolType.valueOf(typeStr.uppercase())
    val config = node.data.filterKeys { it != "toolType" }
    return KoogBuiltinTool(type = type, config = config)
}
```

Add the import: `import com.agentwork.modeler.export.model.KoogBuiltinTool` and `import com.agentwork.modeler.export.model.BuiltinToolType`

- [ ] **Step 4: Run all adapter tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt
git commit -m "feat: extend GraphToKoogAdapter to convert builtin-tool nodes"
```

---

## Task 3: Extend GraphValidator for builtin-tool nodes

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `GraphValidatorTest.kt`:

```kotlin
@Test
fun `valid builtin-tool passes validation`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell"))
        ),
        listOf(GraphEdge("e1", "bt1", "a1")))
    assertTrue(validator.validate(graph).isEmpty())
}

@Test
fun `reports builtin-tool without toolType`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf<String, Any?>())
        ),
        listOf(GraphEdge("e1", "bt1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("toolType") })
}

@Test
fun `reports builtin-tool with invalid toolType`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "invalid"))
        ),
        listOf(GraphEdge("e1", "bt1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("toolType") })
}

@Test
fun `reports builtin-tool not connected to agent`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell"))
        ),
        emptyList())
    assertTrue(validator.validate(graph).any { it.contains("connected") })
}
```

- [ ] **Step 2: Run tests to verify failures**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`
Expected: First test passes (no validation = no error), others fail or pass incorrectly.

- [ ] **Step 3: Add builtin-tool validation branch**

In `GraphValidator.kt`, add a new branch in the `when (node.type)` block:

```kotlin
"builtin-tool" -> {
    val toolType = node.data["toolType"] as? String ?: ""
    val validTypes = setOf("think", "shell", "filesystem", "fetch", "memory", "todo", "user_prompt")
    if (toolType.isBlank()) {
        errors.add("[${node.id}] Built-in tool requires a toolType")
    } else if (toolType !in validTypes) {
        errors.add("[${node.id}] Invalid toolType: $toolType")
    }
    val isConnected = graph.edges.any { it.source == node.id }
    if (!isConnected) errors.add("[${node.id}] Built-in tool must be connected to an Agent")
}
```

- [ ] **Step 4: Run all validator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt
git commit -m "feat: add builtin-tool validation (toolType, connection check)"
```

---

## Task 4: BuiltinToolGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class BuiltinToolGeneratorTest {
    private val generator = BuiltinToolGenerator()

    @Test
    fun `returns null for THINK`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.THINK)))
    }

    @Test
    fun `returns null for USER_PROMPT`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.USER_PROMPT)))
    }

    @Test
    fun `returns null for FILESYSTEM`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.FILESYSTEM)))
    }

    @Test
    fun `generates ShellToolSet with workingDir config`() {
        val tool = KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app"))
        val code = generator.generate(tool)!!
        assertContains(code, "class ShellToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "ProcessBuilder")
        assertContains(code, "workingDir")
        assertContains(code, "waitFor")
    }

    @Test
    fun `generates FetchToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.FETCH))!!
        assertContains(code, "class FetchToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "HttpClient")
    }

    @Test
    fun `generates MemoryToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.MEMORY))!!
        assertContains(code, "class MemoryToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "store")
        assertContains(code, "retrieve")
        assertContains(code, "listKeys")
    }

    @Test
    fun `generates TodoToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.TODO))!!
        assertContains(code, "class TodoToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "addTask")
        assertContains(code, "completeTask")
        assertContains(code, "listTasks")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuiltinToolGeneratorTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement BuiltinToolGenerator**

Create `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import org.springframework.stereotype.Component

@Component
class BuiltinToolGenerator {

    fun generate(tool: KoogBuiltinTool): String? = when (tool.type) {
        BuiltinToolType.THINK -> null
        BuiltinToolType.USER_PROMPT -> null
        BuiltinToolType.FILESYSTEM -> null
        BuiltinToolType.SHELL -> generateShell(tool)
        BuiltinToolType.FETCH -> generateFetch(tool)
        BuiltinToolType.MEMORY -> generateMemory(tool)
        BuiltinToolType.TODO -> generateTodo()
    }

    private fun generateShell(tool: KoogBuiltinTool): String {
        val workingDir = tool.config["workingDir"] as? String ?: "."
        return buildString {
            appendLine("package generated.tools")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.annotations.Tool")
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            appendLine("import java.io.File")
            appendLine()
            appendLine("""@LLMDescription("Tools for executing shell commands")""")
            appendLine("""class ShellToolSet(private val workingDir: String = "$workingDir") : ToolSet {""")
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Execute a shell command and return its output")""")
            appendLine("    fun executeCommand(")
            appendLine("""        @LLMDescription("The shell command to execute") command: String""")
            appendLine("    ): String {")
            appendLine("""        val process = ProcessBuilder("sh", "-c", command)""")
            appendLine("            .directory(File(workingDir))")
            appendLine("            .redirectErrorStream(true)")
            appendLine("            .start()")
            appendLine("        val output = process.inputStream.bufferedReader().readText()")
            appendLine("        process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)")
            appendLine("        return output.take(100_000)")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateFetch(tool: KoogBuiltinTool): String {
        val baseUrl = tool.config["baseUrl"] as? String ?: ""
        return buildString {
            appendLine("package generated.tools")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.annotations.Tool")
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            appendLine("import java.net.URI")
            appendLine("import java.net.http.HttpClient")
            appendLine("import java.net.http.HttpRequest")
            appendLine("import java.net.http.HttpResponse")
            appendLine()
            appendLine("""@LLMDescription("Tools for making HTTP requests")""")
            appendLine("""class FetchToolSet(private val baseUrl: String = "$baseUrl") : ToolSet {""")
            appendLine("    private val client = HttpClient.newHttpClient()")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Make an HTTP request and return the response body")""")
            appendLine("    fun fetch(")
            appendLine("""        @LLMDescription("The URL to fetch") url: String,""")
            appendLine("""        @LLMDescription("HTTP method (GET, POST, PUT, DELETE)") method: String""")
            appendLine("    ): String {")
            appendLine("""        val fullUrl = if (baseUrl.isNotEmpty()) "${'$'}baseUrl${'$'}url" else url""")
            appendLine("        val request = HttpRequest.newBuilder()")
            appendLine("            .uri(URI.create(fullUrl))")
            appendLine("            .method(method.uppercase(), HttpRequest.BodyPublishers.noBody())")
            appendLine("            .build()")
            appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
            appendLine("""        return "HTTP ${'$'}{response.statusCode()}\n${'$'}{response.body()}"""")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateMemory(tool: KoogBuiltinTool): String {
        val path = tool.config["path"] as? String ?: "memory.db"
        return buildString {
            appendLine("package generated.tools")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.annotations.Tool")
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            appendLine("import java.sql.DriverManager")
            appendLine()
            appendLine("""@LLMDescription("Persistent key-value memory storage")""")
            appendLine("""class MemoryToolSet(private val path: String = "$path") : ToolSet {""")
            appendLine("    init {")
            appendLine("""        DriverManager.getConnection("jdbc:sqlite:${'$'}path").use { conn ->""")
            appendLine("""            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS memory (key TEXT PRIMARY KEY, value TEXT)")""")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("""    private fun connection() = DriverManager.getConnection("jdbc:sqlite:${'$'}path")""")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Store a value with a key")""")
            appendLine("    fun store(")
            appendLine("""        @LLMDescription("The key") key: String,""")
            appendLine("""        @LLMDescription("The value to store") value: String""")
            appendLine("    ): String {")
            appendLine("        connection().use { conn ->")
            appendLine("""            conn.prepareStatement("INSERT OR REPLACE INTO memory (key, value) VALUES (?, ?)").use { stmt ->""")
            appendLine("                stmt.setString(1, key)")
            appendLine("                stmt.setString(2, value)")
            appendLine("                stmt.executeUpdate()")
            appendLine("            }")
            appendLine("        }")
            appendLine("""        return "Stored key: ${'$'}key"""")
            appendLine("    }")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Retrieve a value by key")""")
            appendLine("""    fun retrieve(@LLMDescription("The key to look up") key: String): String {""")
            appendLine("        connection().use { conn ->")
            appendLine("""            conn.prepareStatement("SELECT value FROM memory WHERE key = ?").use { stmt ->""")
            appendLine("                stmt.setString(1, key)")
            appendLine("                val rs = stmt.executeQuery()")
            appendLine("""                return if (rs.next()) rs.getString("value") else "Key not found: ${'$'}key"""")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("List all stored keys")""")
            appendLine("    fun listKeys(): String {")
            appendLine("        val keys = mutableListOf<String>()")
            appendLine("        connection().use { conn ->")
            appendLine("""            conn.createStatement().executeQuery("SELECT key FROM memory").use { rs ->""")
            appendLine("                while (rs.next()) keys.add(rs.getString(\"key\"))")
            appendLine("            }")
            appendLine("        }")
            appendLine("""        return if (keys.isEmpty()) "No keys stored" else keys.joinToString("\n")""")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateTodo(): String {
        return buildString {
            appendLine("package generated.tools")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.annotations.Tool")
            appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
            appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
            appendLine()
            appendLine("""@LLMDescription("Task list management tools")""")
            appendLine("class TodoToolSet : ToolSet {")
            appendLine("    data class Task(val id: Int, val description: String, var completed: Boolean = false)")
            appendLine("    private val tasks = mutableListOf<Task>()")
            appendLine("    private var nextId = 1")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Add a new task to the list")""")
            appendLine("""    fun addTask(@LLMDescription("Task description") description: String): String {""")
            appendLine("        val task = Task(nextId++, description)")
            appendLine("        tasks.add(task)")
            appendLine("""        return "Added task #${'$'}{task.id}: ${'$'}{task.description}"""")
            appendLine("    }")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Mark a task as completed")""")
            appendLine("""    fun completeTask(@LLMDescription("Task ID") id: Int): String {""")
            appendLine("""        val task = tasks.find { it.id == id } ?: return "Task #${'$'}id not found"""")
            appendLine("        task.completed = true")
            appendLine("""        return "Completed task #${'$'}id: ${'$'}{task.description}"""")
            appendLine("    }")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("List all tasks")""")
            appendLine("    fun listTasks(): String {")
            appendLine("""        if (tasks.isEmpty()) return "No tasks"""")
            appendLine("""        return tasks.joinToString("\n") { "#${'$'}{it.id} [${"\$"}{if (it.completed) "x" else " "}] ${'$'}{it.description}" }""")
            appendLine("    }")
            appendLine()
            appendLine("    @Tool")
            appendLine("""    @LLMDescription("Remove a task from the list")""")
            appendLine("""    fun removeTask(@LLMDescription("Task ID") id: Int): String {""")
            appendLine("""        val removed = tasks.removeIf { it.id == id }""")
            appendLine("""        return if (removed) "Removed task #${'$'}id" else "Task #${'$'}id not found"""")
            appendLine("    }")
            appendLine("}")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuiltinToolGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGeneratorTest.kt
git commit -m "feat: add BuiltinToolGenerator with Shell, Fetch, Memory, Todo implementations"
```

---

## Task 5: Extend AgentGenerator for Think prompt

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `AgentGeneratorTest.kt`:

```kotlin
@Test
fun `appends think instruction to system prompt when THINK builtin tool present`() {
    val agent = KoogAgent("Agent1", "You are helpful.", ModelProvider.OPENAI, null, null, emptyList(),
        listOf(KoogBuiltinTool(BuiltinToolType.THINK)))
    val code = generator.generate(agent)
    assertContains(code, "Think step by step")
}

@Test
fun `does not append think instruction when no THINK builtin tool`() {
    val agent = KoogAgent("Agent1", "You are helpful.", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("Think step by step"))
}
```

Add imports: `import com.agentwork.modeler.export.model.KoogBuiltinTool` and `import com.agentwork.modeler.export.model.BuiltinToolType`

- [ ] **Step 2: Run tests to verify first one fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest"`
Expected: `appends think instruction` FAILS — prompt not modified.

- [ ] **Step 3: Implement Think prompt extension**

In `AgentGenerator.kt`, at the start of the `generate` method, modify the system prompt:

```kotlin
val hasThink = agent.builtinTools.any { it.type == BuiltinToolType.THINK }
val effectivePrompt = if (hasThink) {
    agent.systemPrompt + "\nThink step by step before answering."
} else {
    agent.systemPrompt
}
```

Add import: `import com.agentwork.modeler.export.model.BuiltinToolType`

Then replace `agent.systemPrompt` with `effectivePrompt` in the params list:

```kotlin
"""systemPrompt = "${effectivePrompt.replace("\"", "\\\"")}""""
```

Note: The `toolRegistry` condition in AgentGenerator is NOT updated here — it remains checking `agent.tools.isNotEmpty()`. It will be updated in Task 8 alongside ExportService to avoid a logical inconsistency window where AgentConfig references `createToolRegistry()` but no ToolRegistry.kt is generated.

- [ ] **Step 4: Run all agent generator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat: AgentGenerator appends Think instruction for builtin THINK tool"
```

---

## Task 6: Extend RegistryGenerator for built-in tools

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Replace `RegistryGeneratorTest.kt` content:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RegistryGeneratorTest {
    private val generator = RegistryGenerator()

    @Test
    fun `generates registry with custom tools only`() {
        val tools = listOf(
            KoogTool("searchTool", "Searches", emptyList(), null),
            KoogTool("calcTool", "Calculates", emptyList(), null)
        )
        val code = generator.generate(tools, emptyList())
        assertContains(code, "ToolRegistry")
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "CalcToolToolSet")
    }

    @Test
    fun `generates registry with builtin shell tool`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app")))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "ShellToolSet")
        assertContains(code, "/app")
    }

    @Test
    fun `generates registry with user_prompt using Koog built-ins`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.USER_PROMPT))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "tool(AskUser)")
        assertContains(code, "tool(SayToUser)")
        assertContains(code, "import ai.koog.agents.ext.tool.AskUser")
        assertContains(code, "import ai.koog.agents.ext.tool.SayToUser")
    }

    @Test
    fun `generates registry with filesystem using Koog built-ins`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.FILESYSTEM))
        val code = generator.generate(emptyList(), builtinTools)
        assertContains(code, "ReadFileTool")
        assertContains(code, "WriteFileTool")
        assertContains(code, "ListDirectoryTool")
        assertContains(code, "JVMFileSystemProvider")
    }

    @Test
    fun `skips THINK in registry`() {
        val builtinTools = listOf(KoogBuiltinTool(BuiltinToolType.THINK))
        val code = generator.generate(emptyList(), builtinTools)
        assertFalse(code.contains("Think"))
    }

    @Test
    fun `generates mixed custom and builtin tools`() {
        val tools = listOf(KoogTool("searchTool", "Searches", emptyList(), null))
        val builtinTools = listOf(
            KoogBuiltinTool(BuiltinToolType.SHELL),
            KoogBuiltinTool(BuiltinToolType.USER_PROMPT)
        )
        val code = generator.generate(tools, builtinTools)
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "ShellToolSet")
        assertContains(code, "tool(AskUser)")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest"`
Expected: FAIL — method signature mismatch (old `generate(List<KoogTool>)` vs new `generate(List<KoogTool>, List<KoogBuiltinTool>)`).

- [ ] **Step 3: Rewrite RegistryGenerator**

Replace `RegistryGenerator.kt` content:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {
    fun generate(tools: List<KoogTool>, builtinTools: List<KoogBuiltinTool> = emptyList()): String {
        val hasUserPrompt = builtinTools.any { it.type == BuiltinToolType.USER_PROMPT }
        val hasFilesystem = builtinTools.any { it.type == BuiltinToolType.FILESYSTEM }
        val customBuiltins = builtinTools.filter {
            it.type in setOf(BuiltinToolType.SHELL, BuiltinToolType.FETCH, BuiltinToolType.MEMORY, BuiltinToolType.TODO)
        }

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.agents.core.tools.reflect.tools")
            if (tools.isNotEmpty() || customBuiltins.isNotEmpty()) {
                appendLine("import generated.tools.*")
            }
            if (hasUserPrompt) {
                appendLine("import ai.koog.agents.ext.tool.AskUser")
                appendLine("import ai.koog.agents.ext.tool.SayToUser")
            }
            if (hasFilesystem) {
                appendLine("import ai.koog.agents.ext.tool.file.ReadFileTool")
                appendLine("import ai.koog.agents.ext.tool.file.WriteFileTool")
                appendLine("import ai.koog.agents.ext.tool.file.ListDirectoryTool")
                appendLine("import ai.koog.rag.base.files.JVMFileSystemProvider")
            }
            appendLine()
            appendLine("fun createToolRegistry(): ToolRegistry {")

            // Custom tool instances
            if (tools.isNotEmpty()) {
                val toolInstances = tools.joinToString("\n    ") { tool ->
                    val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                    "val ${tool.name}Tools = $className()"
                }
                appendLine("    $toolInstances")
                appendLine()
            }

            appendLine("    return ToolRegistry {")

            // Custom tools
            for (tool in tools) {
                appendLine("        tools(${tool.name}Tools)")
            }

            // Built-in custom ToolSets
            for (bt in customBuiltins) {
                val registration = when (bt.type) {
                    BuiltinToolType.SHELL -> {
                        val wd = bt.config["workingDir"] as? String ?: "."
                        """tools(ShellToolSet(workingDir = "$wd"))"""
                    }
                    BuiltinToolType.FETCH -> {
                        val bu = bt.config["baseUrl"] as? String ?: ""
                        """tools(FetchToolSet(baseUrl = "$bu"))"""
                    }
                    BuiltinToolType.MEMORY -> {
                        val p = bt.config["path"] as? String ?: "memory.db"
                        """tools(MemoryToolSet(path = "$p"))"""
                    }
                    BuiltinToolType.TODO -> "tools(TodoToolSet())"
                    else -> null
                }
                registration?.let { appendLine("        $it") }
            }

            // Koog framework built-ins
            if (hasUserPrompt) {
                appendLine("        tool(AskUser)")
                appendLine("        tool(SayToUser)")
            }
            if (hasFilesystem) {
                appendLine("        tool(ReadFileTool(JVMFileSystemProvider.ReadWrite))")
                appendLine("        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))")
                appendLine("        tool(ListDirectoryTool(JVMFileSystemProvider.ReadWrite))")
            }

            appendLine("    }")
            appendLine("}")
        }
    }
}
```

- [ ] **Step 4: Run all registry generator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt
git commit -m "feat: RegistryGenerator supports mixed custom + built-in tool registration"
```

---

## Task 7: Extend BuildGradleGenerator for SQLite dependency

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `BuildGradleGeneratorTest.kt`:

```kotlin
@Test
fun `includes SQLite dependency when Memory tool present`() {
    val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        listOf(KoogBuiltinTool(BuiltinToolType.MEMORY)))
    val code = generator.generate(agent)
    assertContains(code, "sqlite-jdbc")
}

@Test
fun `omits SQLite dependency when no Memory tool`() {
    val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        listOf(KoogBuiltinTool(BuiltinToolType.SHELL)))
    val code = generator.generate(agent)
    assertFalse(code.contains("sqlite-jdbc"))
}
```

Add imports: `import com.agentwork.modeler.export.model.KoogBuiltinTool`, `import com.agentwork.modeler.export.model.BuiltinToolType`, `import kotlin.test.assertFalse`

- [ ] **Step 2: Run tests to verify first one fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest"`
Expected: `includes SQLite dependency` FAILS.

- [ ] **Step 3: Add conditional SQLite dependency**

In `BuildGradleGenerator.kt`, add to companion object:

```kotlin
const val SQLITE_VERSION = "3.47.2.0"
```

Add import: `import com.agentwork.modeler.export.model.BuiltinToolType`

In the `generate` method, after the `koog-agents` dependency line, add:

```kotlin
if (agent.builtinTools.any { it.type == BuiltinToolType.MEMORY }) {
    appendLine("""    implementation("org.xerial:sqlite-jdbc:$SQLITE_VERSION")""")
}
```

- [ ] **Step 4: Run all build gradle generator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt
git commit -m "feat: BuildGradleGenerator adds SQLite dependency for Memory built-in tool"
```

---

## Task 8: Rewire ExportService and AgentGenerator toolRegistry condition

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

Note: This task also updates AgentGenerator's toolRegistry condition from `agent.tools.isNotEmpty()` to `agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty()` (and same for the import). This was deferred from Task 5 to keep the ExportService and AgentGenerator changes atomic.

- [ ] **Step 1: Write the failing tests**

Add to `ExportServiceTest.kt` (after existing tests):

```kotlin
@Test
fun `exports graph with builtin shell tool`() {
    val graph = ProjectGraph(1, "TestProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "shell", "workingDir" to "/app"))
        ),
        listOf(GraphEdge("e1", "bt1", "a1"))
    )
    val zip = service.export(graph)
    val entries = readZipEntries(zip)
    assertTrue(entries.contains("src/main/kotlin/generated/tools/ShellToolSet.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))

    val shellContent = readZipFileContent(zip, "src/main/kotlin/generated/tools/ShellToolSet.kt")
    assertContains(shellContent, "ProcessBuilder")
}

@Test
fun `exports graph with filesystem builtin creates no file but has registry`() {
    val graph = ProjectGraph(1, "TestProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("bt1", "builtin-tool", Position(200.0, 0.0), mapOf("toolType" to "filesystem"))
        ),
        listOf(GraphEdge("e1", "bt1", "a1"))
    )
    val zip = service.export(graph)
    val entries = readZipEntries(zip)
    assertTrue(!entries.any { it.contains("FilesystemToolSet") })
    assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))

    val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
    assertContains(registryContent, "ReadFileTool")
}
```

Also update the `ExportService` construction in the test to add `builtinToolGenerator`:

```kotlin
private val service = ExportService(
    // ... existing params ...
    builtinToolGenerator = BuiltinToolGenerator(),
    zipBuilder = ZipBuilder()
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: FAIL — constructor mismatch (missing `builtinToolGenerator`).

- [ ] **Step 3: Extend ExportService**

In `ExportService.kt`, add the new dependency:

```kotlin
private val builtinToolGenerator: BuiltinToolGenerator,
```

In the `export` method, after the custom tools loop, add:

```kotlin
// Built-in tools
for (builtinTool in agent.builtinTools) {
    val code = builtinToolGenerator.generate(builtinTool)
    val fileName = builtinTool.type.toFileName()
    if (code != null && fileName != null) {
        files["$pkg/tools/$fileName"] = code
    }
}
```

Update the registry generation condition:

```kotlin
if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty()) {
    files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools)
}
```

- [ ] **Step 4: Run all ExportService tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: ALL PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat: ExportService generates built-in tool files, AgentGenerator refs registry for builtins"
```
