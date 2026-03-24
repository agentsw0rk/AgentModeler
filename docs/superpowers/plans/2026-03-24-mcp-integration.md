# MCP Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add MCP server integration to the Koog Agent Editor — users can configure MCP servers, discover tools via backend proxy, and export agents with MCP tool registries.

**Architecture:** New `mcp-server` NodeType through all layers (frontend types, Rete node, properties panel, store, validation, Canvas sync). New backend discovery proxy (`POST /api/mcp/discover`) using Koog's `McpToolRegistryProvider`. New `McpServerGenerator` produces MCP connection code. `RegistryGenerator` gains `suspend` variant with registry merge. `AgentGenerator` wraps in `runBlocking` when MCP is present.

**Tech Stack:** TypeScript, Next.js, Rete.js, Zustand, shadcn/ui (frontend); Kotlin, Spring Boot, Koog MCP SDK, JUnit 5 (backend)

**Spec:** `docs/superpowers/specs/2026-03-24-mcp-integration-design.md`

---

## File Map

### New Files
- `frontend/types/mcp.ts` — McpServerConfig, McpTransportType, McpEnvVar, McpDiscoveredTool
- `frontend/lib/mcp-client.ts` — Fetch wrapper for `/api/mcp/discover`
- `frontend/components/panels/McpServerProperties.tsx` — Properties panel with config + discovery + tool list
- `src/main/kotlin/com/agentwork/modeler/export/model/McpModel.kt` — KoogMcpServer, McpEnvVarName
- `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryController.kt` — REST endpoint
- `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryService.kt` — MCP client logic
- `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryDto.kt` — Request/Response DTOs
- `src/main/kotlin/com/agentwork/modeler/export/generator/McpServerGenerator.kt` — Code generator
- `src/test/kotlin/com/agentwork/modeler/export/generator/McpServerGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/mcp/McpDiscoveryControllerTest.kt`

### Modified Files
- `frontend/types/graph.ts` — Add `"mcp-server"` to NodeType, McpServerConfig to data union
- `frontend/store/editorStore.ts` — Default data for mcp-server
- `frontend/lib/rete-nodes.ts` — McpServerReteNode class, AppNode union
- `frontend/lib/rete-setup.ts` — Connection validation: MCP → Agent/SubAgent
- `frontend/lib/validation.ts` — MCP server validation rules
- `frontend/components/editor/Canvas.tsx` — Sync for MCP nodes
- `frontend/components/panels/Toolbar.tsx` — "+MCP Server" button
- `frontend/components/panels/PropertiesPanel.tsx` — Route to McpServerProperties
- `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt` — mcpServers field on KoogAgent
- `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt` — Parse MCP nodes
- `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` — MCP rules
- `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt` — suspend + MCP merge
- `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt` — runBlocking for MCP
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt` — MCP dependency
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` — Wire McpServerGenerator
- `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

---

## Task 1: Backend Model — KoogMcpServer

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/McpModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`

- [ ] **Step 1: Create McpModel.kt**

```kotlin
package com.agentwork.modeler.export.model

data class KoogMcpServer(
    val name: String,
    val transportType: String,
    val url: String?,
    val command: String?,
    val args: List<String>?,
    val envVars: List<McpEnvVarName>
)

data class McpEnvVarName(
    val name: String
)
```

- [ ] **Step 2: Add mcpServers to KoogAgent**

In `KoogModel.kt`, add after `subAgents`:

```kotlin
data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList(),
    val mcpServers: List<KoogMcpServer> = emptyList()
)
```

- [ ] **Step 3: Run tests to verify nothing breaks**

Run: `./gradlew test`

Expected: ALL tests pass (default `emptyList()` — no call-site changes).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/McpModel.kt \
        src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat(model): add KoogMcpServer and mcpServers field to KoogAgent"
```

---

## Task 2: Backend Validation — MCP Server Rules

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `GraphValidatorTest.kt`:

```kotlin
@Test
fun `valid mcp-server connected to agent passes validation`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Google Maps", "transportType" to "sse",
                "url" to "http://localhost:8931"
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertTrue(validator.validate(graph).isEmpty())
}

@Test
fun `reports mcp-server without name`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "", "transportType" to "sse",
                "url" to "http://localhost:8931"
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("mcp1") })
}

@Test
fun `reports mcp-server with invalid transport type`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Server", "transportType" to "invalid",
                "url" to "http://localhost:8931"
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("transport") })
}

@Test
fun `reports sse mcp-server without url`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Server", "transportType" to "sse", "url" to ""
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("URL") })
}

@Test
fun `reports stdio mcp-server without command`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Server", "transportType" to "stdio", "command" to ""
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("command") })
}

@Test
fun `reports mcp-server not connected to agent`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Server", "transportType" to "sse", "url" to "http://localhost"
            ))
        ),
        emptyList())
    assertTrue(validator.validate(graph).any { it.contains("connected") })
}

@Test
fun `mcp-server does not count toward single-agent limit`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Server", "transportType" to "sse", "url" to "http://localhost"
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1")))
    assertFalse(validator.validate(graph).any { it.contains("single-agent") || it.contains("Only single") })
}
```

- [ ] **Step 2: Run tests — expect failures**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`

- [ ] **Step 3: Implement MCP validation**

In `GraphValidator.kt`, add a new `when` branch after `"sub-agent"`:

```kotlin
"mcp-server" -> {
    val name = node.data["name"] as? String ?: ""
    val transportType = node.data["transportType"] as? String ?: ""
    if (name.isBlank()) errors.add("[${node.id}] MCP Server requires a name")

    val validTransports = setOf("sse", "stdio")
    if (transportType !in validTransports) {
        errors.add("[${node.id}] MCP Server has invalid transport type")
    } else if (transportType == "sse") {
        val url = node.data["url"] as? String ?: ""
        if (url.isBlank()) errors.add("[${node.id}] MCP Server (SSE) requires a URL")
    } else if (transportType == "stdio") {
        val command = node.data["command"] as? String ?: ""
        if (command.isBlank()) errors.add("[${node.id}] MCP Server (STDIO) requires a command")
    }

    val isConnected = graph.edges.any { it.source == node.id }
    if (!isConnected) errors.add("[${node.id}] MCP Server must be connected to an Agent")

    // Warning: MCP connected to sub-agent (export only supports main agent)
    val targets = graph.edges.filter { it.source == node.id }.map { edge ->
        graph.nodes.find { it.id == edge.target }
    }
    for (target in targets) {
        if (target?.type == "sub-agent") {
            errors.add("[${node.id}] MCP Server connected to Sub-Agent will only be exported for the main Agent")
        }
    }
}
```

Also update the multi-connection check to include `mcp-server`:

Change `if (node.type === "tool" || node.type === "builtin-tool")` to:
```kotlin
if (node.type == "tool" || node.type == "builtin-tool" || node.type == "mcp-server")
```

- [ ] **Step 4: Run tests — all pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt \
        src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt
git commit -m "feat(validation): add MCP server validation rules"
```

---

## Task 3: Backend Adapter — Parse MCP Server Nodes

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `GraphToKoogAdapterTest.kt`:

```kotlin
@Test
fun `converts mcp-server nodes to KoogMcpServer list`() {
    val graph = ProjectGraph(
        version = 1, name = "Test",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
            )),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Google Maps", "transportType" to "sse",
                "url" to "http://localhost:8931",
                "envVars" to listOf(mapOf("id" to "1", "name" to "GOOGLE_MAPS_API_KEY"))
            ))
        ),
        edges = listOf(GraphEdge("e1", "mcp1", "a1"))
    )

    val agents = adapter.convert(graph)
    assertEquals(1, agents[0].mcpServers.size)
    val mcp = agents[0].mcpServers[0]
    assertEquals("Google Maps", mcp.name)
    assertEquals("sse", mcp.transportType)
    assertEquals("http://localhost:8931", mcp.url)
    assertEquals(1, mcp.envVars.size)
    assertEquals("GOOGLE_MAPS_API_KEY", mcp.envVars[0].name)
}

@Test
fun `converts stdio mcp-server with command and args`() {
    val graph = ProjectGraph(
        version = 1, name = "Test",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "Agent", "systemPrompt" to "Help", "modelProvider" to "openai"
            )),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Playwright", "transportType" to "stdio",
                "command" to "npx", "args" to listOf("@playwright/mcp", "--headless"),
                "envVars" to emptyList<Any>()
            ))
        ),
        edges = listOf(GraphEdge("e1", "mcp1", "a1"))
    )

    val agents = adapter.convert(graph)
    val mcp = agents[0].mcpServers[0]
    assertEquals("stdio", mcp.transportType)
    assertEquals("npx", mcp.command)
    assertEquals(listOf("@playwright/mcp", "--headless"), mcp.args)
}
```

- [ ] **Step 2: Run tests — expect failures**

- [ ] **Step 3: Implement MCP parsing**

In `GraphToKoogAdapter.kt`:

1. Add at the top of `convert()`:
```kotlin
val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
```

2. Inside the `agentNodes.map` block, after sub-agents:
```kotlin
val mcpServers = mcpServerNodes
    .filter { it.id in connectedToAgent }
    .map { convertMcpServer(it) }
```

3. Add `mcpServers = mcpServers` to the `KoogAgent` constructor call.

4. Add the conversion method:
```kotlin
private fun convertMcpServer(node: GraphNode): KoogMcpServer {
    val envVars = (node.data["envVars"] as? List<*>)?.mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        val name = map["name"] as? String ?: return@mapNotNull null
        McpEnvVarName(name = name)
    } ?: emptyList()

    val args = (node.data["args"] as? List<*>)?.mapNotNull { it as? String }

    return KoogMcpServer(
        name = node.data["name"] as? String ?: "",
        transportType = node.data["transportType"] as? String ?: "sse",
        url = node.data["url"] as? String,
        command = node.data["command"] as? String,
        args = args,
        envVars = envVars
    )
}
```

Add imports: `import com.agentwork.modeler.export.model.KoogMcpServer` and `import com.agentwork.modeler.export.model.McpEnvVarName`

- [ ] **Step 4: Run tests — all pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt \
        src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt
git commit -m "feat(adapter): parse MCP server nodes"
```

---

## Task 4: McpServerGenerator — Code Generation

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/McpServerGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/McpServerGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Create `McpServerGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.McpEnvVarName
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class McpServerGeneratorTest {

    private val generator = McpServerGenerator()

    @Test
    fun `generates SSE MCP server code`() {
        val mcp = KoogMcpServer(
            name = "Google Maps", transportType = "sse",
            url = "http://localhost:8931", command = null, args = null,
            envVars = emptyList()
        )
        val code = generator.generate(mcp)
        assertContains(code, "package generated.mcp")
        assertContains(code, "suspend fun createGoogleMapsRegistry()")
        assertContains(code, "defaultSseTransport")
        assertContains(code, "http://localhost:8931")
        assertContains(code, "McpToolRegistryProvider")
    }

    @Test
    fun `generates STDIO MCP server code with command and args`() {
        val mcp = KoogMcpServer(
            name = "Playwright", transportType = "stdio",
            url = null, command = "npx", args = listOf("@playwright/mcp", "--headless"),
            envVars = listOf(McpEnvVarName("PLAYWRIGHT_KEY"))
        )
        val code = generator.generate(mcp)
        assertContains(code, "suspend fun createPlaywrightRegistry()")
        assertContains(code, "ProcessBuilder")
        assertContains(code, "\"npx\"")
        assertContains(code, "\"@playwright/mcp\"")
        assertContains(code, "\"--headless\"")
        assertContains(code, "PLAYWRIGHT_KEY")
        assertContains(code, "System.getenv")
        assertContains(code, "defaultStdioTransport")
    }

    @Test
    fun `generates env var comment when env vars present`() {
        val mcp = KoogMcpServer(
            name = "Test", transportType = "sse",
            url = "http://localhost", command = null, args = null,
            envVars = listOf(McpEnvVarName("API_KEY"), McpEnvVarName("SECRET"))
        )
        val code = generator.generate(mcp)
        assertContains(code, "// Required environment variables: API_KEY, SECRET")
    }

    @Test
    fun `no env var comment when no env vars`() {
        val mcp = KoogMcpServer(
            name = "Test", transportType = "sse",
            url = "http://localhost", command = null, args = null,
            envVars = emptyList()
        )
        val code = generator.generate(mcp)
        assertFalse(code.contains("Required environment variables"))
    }
}
```

- [ ] **Step 2: Run tests — expect failures**

- [ ] **Step 3: Implement McpServerGenerator**

Create `McpServerGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogMcpServer
import org.springframework.stereotype.Component

@Component
class McpServerGenerator {

    fun generate(mcp: KoogMcpServer): String {
        val funcName = "create${mcp.name.replace(" ", "").replaceFirstChar { it.uppercase() }}Registry"
        val safeName = mcp.name.lowercase().replace(" ", "-")

        return buildString {
            appendLine("package generated.mcp")
            appendLine()
            appendLine("import ai.koog.agents.mcp.McpToolRegistryProvider")
            appendLine("import ai.koog.agents.mcp.metadata.McpServerInfo")
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine()
            if (mcp.envVars.isNotEmpty()) {
                val envNames = mcp.envVars.joinToString(", ") { it.name }
                appendLine("// Required environment variables: $envNames")
            }
            appendLine("suspend fun $funcName(): ToolRegistry {")

            when (mcp.transportType) {
                "sse" -> {
                    appendLine("    val transport = McpToolRegistryProvider.defaultSseTransport(\"${mcp.url}\")")
                    appendLine("    return McpToolRegistryProvider.fromTransport(")
                    appendLine("        transport = transport,")
                    appendLine("        serverInfo = McpServerInfo(url = \"${mcp.url}\"),")
                    appendLine("        name = \"$safeName\",")
                    appendLine("        version = \"1.0.0\"")
                    appendLine("    )")
                }
                "stdio" -> {
                    val allArgs = mutableListOf("\"${mcp.command}\"")
                    mcp.args?.forEach { allArgs.add("\"$it\"") }
                    val pbArgs = allArgs.joinToString(", ")
                    appendLine("    val process = ProcessBuilder($pbArgs)")
                    if (mcp.envVars.isNotEmpty()) {
                        appendLine("        .apply {")
                        for (env in mcp.envVars) {
                            appendLine("            environment()[\"${env.name}\"] = System.getenv(\"${env.name}\")")
                        }
                        appendLine("        }")
                    }
                    appendLine("        .start()")
                    val cmdStr = listOfNotNull(mcp.command).plus(mcp.args ?: emptyList()).joinToString(" ")
                    appendLine("    val transport = McpToolRegistryProvider.defaultStdioTransport(process)")
                    appendLine("    return McpToolRegistryProvider.fromTransport(")
                    appendLine("        transport = transport,")
                    appendLine("        serverInfo = McpServerInfo(command = \"$cmdStr\"),")
                    appendLine("        name = \"$safeName\",")
                    appendLine("        version = \"1.0.0\"")
                    appendLine("    )")
                }
            }

            appendLine("}")
        }
    }
}
```

- [ ] **Step 4: Run tests — all pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.McpServerGeneratorTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/McpServerGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/McpServerGeneratorTest.kt
git commit -m "feat(generator): add McpServerGenerator for SSE and STDIO"
```

---

## Task 5: RegistryGenerator — Suspend + MCP Merge

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `RegistryGeneratorTest.kt`:

```kotlin
@Test
fun `generates suspend registry with MCP merge`() {
    val mcpServers = listOf(
        KoogMcpServer("Google Maps", "sse", "http://localhost:8931", null, null, emptyList())
    )
    val code = generator.generate(emptyList(), emptyList(), emptyList(), mcpServers)
    assertContains(code, "suspend fun createToolRegistry()")
    assertContains(code, "import generated.mcp.*")
    assertContains(code, "createGoogleMapsRegistry()")
    assertContains(code, "baseRegistry +")
}

@Test
fun `generates non-suspend registry without MCP`() {
    val tools = listOf(KoogTool("searchTool", "Searches", emptyList(), null))
    val code = generator.generate(tools)
    assertFalse(code.contains("suspend"))
    assertFalse(code.contains("import generated.mcp"))
}

@Test
fun `generates suspend registry with tools and MCP`() {
    val tools = listOf(KoogTool("weatherTool", "Weather", emptyList(), null))
    val mcpServers = listOf(
        KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())
    )
    val code = generator.generate(tools, emptyList(), emptyList(), mcpServers)
    assertContains(code, "suspend fun createToolRegistry()")
    assertContains(code, "WeatherToolToolSet")
    assertContains(code, "createMapsRegistry()")
    assertContains(code, "baseRegistry +")
}
```

Add import: `import com.agentwork.modeler.export.model.KoogMcpServer`

- [ ] **Step 2: Run tests — expect failures**

- [ ] **Step 3: Extend RegistryGenerator**

Change method signature to accept MCP servers:

```kotlin
fun generate(
    tools: List<KoogTool>,
    builtinTools: List<KoogBuiltinTool> = emptyList(),
    subAgents: List<KoogSubAgent> = emptyList(),
    mcpServers: List<KoogMcpServer> = emptyList()
): String {
```

Add import: `import com.agentwork.modeler.export.model.KoogMcpServer`

Inside the `buildString`:

1. Add MCP import after sub-agent imports:
```kotlin
if (mcpServers.isNotEmpty()) {
    appendLine("import generated.mcp.*")
}
```

2. Change function declaration to be conditionally suspend:
```kotlin
val suspendPrefix = if (mcpServers.isNotEmpty()) "suspend " else ""
appendLine("${suspendPrefix}fun createToolRegistry(): ToolRegistry {")
```

3. When MCP servers are present, change the return pattern. Replace the closing `appendLine("    }")` / `appendLine("}")` with:

If `mcpServers.isNotEmpty()`:
```kotlin
appendLine("    }")
appendLine()
appendLine("    val baseRegistry = ToolRegistry {")
// ... move all tool registrations here ...
appendLine("    }")
appendLine()
// MCP registries
for (mcp in mcpServers) {
    val funcName = "create${mcp.name.replace(" ", "").replaceFirstChar { it.uppercase() }}Registry"
    appendLine("    val ${mcp.name.replace(" ", "").replaceFirstChar { it.lowercase() }}Registry = $funcName()")
}
appendLine()
val allRegistries = mcpServers.joinToString(" + ") {
    "${it.name.replace(" ", "").replaceFirstChar { c -> c.lowercase() }}Registry"
}
appendLine("    return baseRegistry + $allRegistries")
appendLine("}")
```

**Implementation note:** This is the most complex change in the plan. The key insight is: when MCP servers are present, the function wraps all existing registrations in a `val baseRegistry = ToolRegistry { ... }` block, then merges with MCP registries via `+`. When no MCP servers are present, the existing pattern (`return ToolRegistry { ... }`) is unchanged.

**Edge case — MCP-only (no custom/builtin tools):** When there are zero custom tools, zero builtin tools, and zero sub-agents, but MCP servers exist: skip the `baseRegistry` entirely. Just create and merge the MCP registries directly. If there's only one MCP server, return it directly without `+`. The implementer should handle this case explicitly to avoid generating an empty `ToolRegistry { }`.

- [ ] **Step 4: Run tests — all pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt
git commit -m "feat(registry): suspend + MCP registry merge"
```

---

## Task 6: AgentGenerator — runBlocking for MCP

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `AgentGeneratorTest.kt`:

```kotlin
@Test
fun `wraps agent in runBlocking when MCP servers present`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        mcpServers = listOf(KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())))
    val code = generator.generate(agent)
    assertContains(code, "runBlocking")
    assertContains(code, "import kotlinx.coroutines.runBlocking")
    assertContains(code, "createToolRegistry()")
}

@Test
fun `no runBlocking without MCP servers`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("runBlocking"))
}
```

Add imports: `import com.agentwork.modeler.export.model.KoogMcpServer`

- [ ] **Step 2: Run tests — expect failures**

- [ ] **Step 3: Implement runBlocking conditional**

In `AgentGenerator.kt`:

1. Update the `toolRegistry` condition to include MCP:
```kotlin
if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
    params.add("toolRegistry = createToolRegistry()")
}
```

2. Add `runBlocking` import when MCP is present. After the existing imports:
```kotlin
if (agent.mcpServers.isNotEmpty()) {
    appendLine("import kotlinx.coroutines.runBlocking")
}
```

3. Wrap the `@Bean` method when MCP is present. Change the agent creation block:

When `agent.mcpServers.isNotEmpty()`:
```kotlin
appendLine("    @Bean")
appendLine("    fun agent(): AIAgent<String, String> = runBlocking {")
appendLine("        AIAgent(")
appendLine("            $paramBlock")
appendLine("        )")
appendLine("    }")
```

When no MCP servers (existing pattern):
```kotlin
appendLine("    @Bean")
appendLine("    fun agent(): AIAgent<String, String> = AIAgent(")
appendLine("            $paramBlock")
appendLine("    )")
```

- [ ] **Step 4: Run tests — all pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat(agent): add runBlocking wrapper for MCP suspend support"
```

---

## Task 7: BuildGradleGenerator — MCP Dependency

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

Add to `BuildGradleGeneratorTest.kt`:

```kotlin
@Test
fun `adds MCP dependency when MCP servers present`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList(),
        mcpServers = listOf(KoogMcpServer("Maps", "sse", "http://localhost", null, null, emptyList())))
    val code = generator.generate(agent)
    assertContains(code, "agents-ext-mcp")
}

@Test
fun `no MCP dependency without MCP servers`() {
    val agent = KoogAgent("Agent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertFalse(code.contains("agents-ext-mcp"))
}
```

Add imports as needed.

- [ ] **Step 2: Run tests — expect first to fail**

- [ ] **Step 3: Add MCP dependency conditionally**

In `BuildGradleGenerator.kt`, after the SQLite dependency block:

```kotlin
if (agent.mcpServers.isNotEmpty()) {
    appendLine("""    implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
}
```

- [ ] **Step 4: Run tests — all pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt
git commit -m "feat(gradle): add MCP dependency when MCP servers present"
```

---

## Task 8: ExportService — Wire McpServerGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Write failing integration test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `exports graph with MCP server`() {
    val graph = ProjectGraph(1, "TestProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("mcp1", "mcp-server", Position(200.0, 0.0), mapOf(
                "name" to "Google Maps", "transportType" to "sse",
                "url" to "http://localhost:8931",
                "envVars" to listOf(mapOf("id" to "1", "name" to "GOOGLE_MAPS_API_KEY"))
            ))
        ),
        listOf(GraphEdge("e1", "mcp1", "a1"))
    )
    val zip = service.export(graph)
    val entries = readZipEntries(zip)

    assertTrue(entries.contains("src/main/kotlin/generated/mcp/GoogleMapsMcp.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))

    val mcpContent = readZipFileContent(zip, "src/main/kotlin/generated/mcp/GoogleMapsMcp.kt")
    assertContains(mcpContent, "defaultSseTransport")
    assertContains(mcpContent, "http://localhost:8931")

    val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
    assertContains(registryContent, "suspend")
    assertContains(registryContent, "createGoogleMapsRegistry()")

    val agentContent = readZipFileContent(zip, "src/main/kotlin/generated/AgentConfig.kt")
    assertContains(agentContent, "runBlocking")

    val buildContent = readZipFileContent(zip, "build.gradle.kts")
    assertContains(buildContent, "agents-ext-mcp")
}
```

- [ ] **Step 2: Run test — expect failure**

- [ ] **Step 3: Wire McpServerGenerator**

In `ExportService.kt`:

1. Add constructor parameter:
```kotlin
private val mcpServerGenerator: McpServerGenerator,
```

2. After the sub-agents block, add:
```kotlin
// MCP servers
for (mcpServer in agent.mcpServers) {
    val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
    files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
}
```

3. Update registry generation condition:
```kotlin
if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
    files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents, agent.mcpServers)
}
```

4. Update `ExportServiceTest` constructor to include `mcpServerGenerator = McpServerGenerator()`.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt \
        src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat(export): wire McpServerGenerator into export pipeline"
```

---

## Task 9: Discovery Proxy — DTOs, Service, Controller

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryDto.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryService.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/mcp/McpDiscoveryController.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/mcp/McpDiscoveryControllerTest.kt`

This is the largest task. It creates the backend discovery proxy.

- [ ] **Step 1: Create DTOs**

Create `McpDiscoveryDto.kt`:

```kotlin
package com.agentwork.modeler.mcp

data class McpDiscoveryRequest(
    val transportType: String,
    val url: String?,
    val command: String?,
    val args: List<String>?,
    val envVars: List<McpEnvVarDto>?
)

data class McpEnvVarDto(
    val name: String,
    val value: String?
)

data class McpDiscoveryResponse(
    val tools: List<McpToolDto>? = null,
    val error: String? = null
)

data class McpToolDto(
    val name: String,
    val description: String
)
```

- [ ] **Step 2: Create McpDiscoveryService**

Create `McpDiscoveryService.kt`:

```kotlin
package com.agentwork.modeler.mcp

import org.springframework.stereotype.Service

@Service
class McpDiscoveryService {

    fun discover(request: McpDiscoveryRequest): McpDiscoveryResponse {
        return try {
            when (request.transportType) {
                "sse" -> discoverSse(request)
                "stdio" -> discoverStdio(request)
                else -> McpDiscoveryResponse(error = "Unsupported transport type: ${request.transportType}")
            }
        } catch (e: Exception) {
            McpDiscoveryResponse(error = "Discovery failed: ${e.message}")
        }
    }

    private fun discoverSse(request: McpDiscoveryRequest): McpDiscoveryResponse {
        val url = request.url ?: return McpDiscoveryResponse(error = "URL is required for SSE transport")
        // TODO: Implement real MCP SSE discovery using Koog SDK
        // For now, return a mock response to enable frontend development
        return McpDiscoveryResponse(
            tools = listOf(
                McpToolDto("mock-tool-1", "Mock tool from SSE server at $url"),
                McpToolDto("mock-tool-2", "Another mock tool")
            )
        )
    }

    private fun discoverStdio(request: McpDiscoveryRequest): McpDiscoveryResponse {
        val command = request.command ?: return McpDiscoveryResponse(error = "Command is required for STDIO transport")
        // TODO: Implement real MCP STDIO discovery using Koog SDK
        // For now, return a mock response to enable frontend development
        return McpDiscoveryResponse(
            tools = listOf(
                McpToolDto("mock-tool-1", "Mock tool from STDIO command: $command"),
                McpToolDto("mock-tool-2", "Another mock tool")
            )
        )
    }
}
```

**Deliberate scope decision — mock discovery for this milestone:** The real Koog MCP SDK integration (`McpToolRegistryProvider.fromTransport()`) is deferred to a follow-up task. Reasons: (1) adding `ai.koog:agents-ext-mcp` as a backend runtime dependency pulls in coroutine and MCP SDK dependencies that may conflict with the current Spring Boot setup, (2) the mock responses enable full end-to-end development and testing of the complete pipeline (frontend → backend → export), (3) the real SDK integration is a contained change inside `McpDiscoveryService` that doesn't affect any other component. The mock service returns realistic tool lists that exercise the full data flow. **Follow-up task:** Replace mock implementations in `discoverSse()` and `discoverStdio()` with real Koog `McpToolRegistryProvider` calls.

- [ ] **Step 3: Create McpDiscoveryController**

Create `McpDiscoveryController.kt`:

```kotlin
package com.agentwork.modeler.mcp

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class McpDiscoveryController(private val service: McpDiscoveryService) {

    @PostMapping("/api/mcp/discover")
    fun discover(@RequestBody request: McpDiscoveryRequest): ResponseEntity<McpDiscoveryResponse> {
        val response = service.discover(request)
        return if (response.error != null) {
            ResponseEntity.badRequest().body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }
}
```

- [ ] **Step 4: Write controller test**

Create `McpDiscoveryControllerTest.kt`:

```kotlin
package com.agentwork.modeler.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class McpDiscoveryControllerTest {

    private val service = McpDiscoveryService()

    @Test
    fun `discovers tools via SSE`() {
        val request = McpDiscoveryRequest("sse", "http://localhost:8931", null, null, null)
        val response = service.discover(request)
        assertNull(response.error)
        assertNotNull(response.tools)
        assertEquals(2, response.tools!!.size)
    }

    @Test
    fun `discovers tools via STDIO`() {
        val request = McpDiscoveryRequest("stdio", null, "npx", listOf("@playwright/mcp"), null)
        val response = service.discover(request)
        assertNull(response.error)
        assertNotNull(response.tools)
    }

    @Test
    fun `returns error for SSE without URL`() {
        val request = McpDiscoveryRequest("sse", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
        assertNull(response.tools)
    }

    @Test
    fun `returns error for STDIO without command`() {
        val request = McpDiscoveryRequest("stdio", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
    }

    @Test
    fun `returns error for unsupported transport`() {
        val request = McpDiscoveryRequest("websocket", null, null, null, null)
        val response = service.discover(request)
        assertNotNull(response.error)
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/mcp/ \
        src/test/kotlin/com/agentwork/modeler/mcp/
git commit -m "feat(mcp): add discovery proxy with mock responses"
```

---

## Task 10: Frontend Types — McpServerConfig

**Files:**
- Create: `frontend/types/mcp.ts`
- Modify: `frontend/types/graph.ts`

- [ ] **Step 1: Create mcp.ts**

```typescript
export type McpTransportType = "sse" | "stdio";

export interface McpEnvVar {
  id: string;
  name: string;
  value?: string;
}

export interface McpDiscoveredTool {
  name: string;
  description: string;
}

export interface McpServerConfig {
  name: string;
  transportType: McpTransportType;
  url?: string;
  command?: string;
  args?: string[];
  envVars: McpEnvVar[];
  discoveredTools: McpDiscoveredTool[];
  lastDiscoveredAt?: string;
}
```

- [ ] **Step 2: Extend NodeType and GraphNode**

In `frontend/types/graph.ts`:

```typescript
import { McpServerConfig } from "./mcp";

export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server";

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/types/mcp.ts frontend/types/graph.ts
git commit -m "feat(types): add McpServerConfig and extend NodeType"
```

---

## Task 11: Frontend — Store, Rete Node, mcp-client

**Files:**
- Modify: `frontend/store/editorStore.ts`
- Modify: `frontend/lib/rete-nodes.ts`
- Create: `frontend/lib/mcp-client.ts`

- [ ] **Step 1: Add MCP defaults to store**

In `editorStore.ts`, add import and default:

```typescript
import { McpServerConfig } from "@/types/mcp";
```

Update `updateNodeData` type union to include `McpServerConfig`.

Add default:
```typescript
const defaultMcpServerData: McpServerConfig = {
  name: "",
  transportType: "sse",
  envVars: [],
  discoveredTools: [],
};
```

Add `else if` in `addNode`:
```typescript
} else if (type === "mcp-server") {
  data = { ...defaultMcpServerData };
}
```

Update the `let data` type annotation to include `McpServerConfig`.

- [ ] **Step 2: Add McpServerReteNode**

In `rete-nodes.ts`, add after `SubAgentReteNode`:

```typescript
export class McpServerReteNode extends ClassicPreset.Node {
  width = 240;
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "MCP Server");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}
```

Update `AppNode`:
```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode;
```

- [ ] **Step 3: Create mcp-client.ts**

```typescript
const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface McpDiscoveryRequest {
  transportType: string;
  url?: string;
  command?: string;
  args?: string[];
  envVars?: { name: string; value?: string }[];
}

export interface McpDiscoveryResponse {
  tools?: { name: string; description: string }[];
  error?: string;
}

export async function discoverMcpTools(config: McpDiscoveryRequest): Promise<McpDiscoveryResponse> {
  const response = await fetch(`${API_BASE}/api/mcp/discover`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  });

  const data = await response.json();

  if (!response.ok) {
    return { error: data.error || "Discovery failed" };
  }

  return data;
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/store/editorStore.ts frontend/lib/rete-nodes.ts frontend/lib/mcp-client.ts
git commit -m "feat(frontend): add MCP store defaults, Rete node, and discovery client"
```

---

## Task 12: Frontend — Connection Rules, Canvas Sync, Validation

**Files:**
- Modify: `frontend/lib/rete-setup.ts`
- Modify: `frontend/components/editor/Canvas.tsx`
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Update connection rules**

In `rete-setup.ts`, add `McpServerReteNode` to imports and update the validation pipe:

```typescript
import { ..., McpServerReteNode } from "./rete-nodes";
```

Add to the connection validation:
```typescript
const isMcpSource = sourceNode instanceof McpServerReteNode;

// Tool/Builtin -> Agent or SubAgent: OK
if (isToolSource && (isAgentTarget || isSubAgentTarget)) {
  return context;
}
// SubAgent -> Agent: OK
if (isSubAgentSource && isAgentTarget) {
  return context;
}
// MCP -> Agent or SubAgent: OK
if (isMcpSource && (isAgentTarget || isSubAgentTarget)) {
  return context;
}
// Everything else: blocked
return undefined;
```

- [ ] **Step 2: Update Canvas sync**

In `Canvas.tsx`, add `McpServerReteNode` to imports.

Add MCP node creation in the Zustand → Rete sync:
```typescript
} else if (node.type === "mcp-server") {
  reteNode = new McpServerReteNode(node.id, (node.data as any).name || "MCP Server");
}
```

Update `connectioncreated` and `connectionremoved` pipes to include `McpServerReteNode`:
```typescript
const isMcpSource = sourceNode instanceof McpServerReteNode;
// Update condition: (isToolSource || isSubAgentSource || isMcpSource) && (isAgentTarget || isSubAgentTarget)
```

- [ ] **Step 3: Add MCP validation**

In `validation.ts`, add import and validation block:

```typescript
import { McpServerConfig } from "@/types/mcp";
```

After the sub-agent block:
```typescript
if (node.type === "mcp-server") {
  const data = node.data as McpServerConfig;
  if (!data.name.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "MCP Server requires a name" });
  }
  const validTransports = ["sse", "stdio"];
  if (!validTransports.includes(data.transportType)) {
    errors.push({ nodeId: node.id, severity: "error", message: "MCP Server has invalid transport type" });
  } else if (data.transportType === "sse" && !(data.url || "").trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "MCP Server (SSE) requires a URL" });
  } else if (data.transportType === "stdio" && !(data.command || "").trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "MCP Server (STDIO) requires a command" });
  }

  const isConnected = graph.edges.some((e) => e.source === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "MCP Server must be connected to an Agent" });
  }

  if (data.discoveredTools.length === 0) {
    errors.push({ nodeId: node.id, severity: "warning", message: "MCP Server has no discovered tools — click Discover to fetch tools" });
  }

  // Warning: MCP connected to sub-agent (export only supports main agent)
  const mcpTargets = graph.edges
    .filter((e) => e.source === node.id)
    .map((e) => graph.nodes.find((n) => n.id === e.target));
  for (const target of mcpTargets) {
    if (target?.type === "sub-agent") {
      errors.push({ nodeId: node.id, severity: "warning", message: "MCP Server connected to Sub-Agent will only be exported for the main Agent" });
    }
  }
}
```

Update the multi-connection check to include `mcp-server`:
```typescript
if (node.type === "tool" || node.type === "builtin-tool" || node.type === "mcp-server") {
```

- [ ] **Step 4: Commit**

```bash
git add frontend/lib/rete-setup.ts frontend/components/editor/Canvas.tsx frontend/lib/validation.ts
git commit -m "feat(frontend): add MCP connection rules, canvas sync, and validation"
```

---

## Task 13: McpServerProperties Panel

**Files:**
- Create: `frontend/components/panels/McpServerProperties.tsx`

- [ ] **Step 1: Create the component**

```tsx
"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { discoverMcpTools } from "@/lib/mcp-client";
import type { McpServerConfig, McpTransportType, McpEnvVar } from "@/types/mcp";

export function McpServerProperties({ nodeId, data }: { nodeId: string; data: McpServerConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<McpServerConfig>) => updateNodeData(nodeId, field);

  const [discovering, setDiscovering] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDiscover = async () => {
    setDiscovering(true);
    setError(null);
    try {
      const result = await discoverMcpTools({
        transportType: data.transportType,
        url: data.url,
        command: data.command,
        args: data.args,
        envVars: data.envVars.map((v) => ({ name: v.name, value: v.value })),
      });
      if (result.error) {
        setError(result.error);
      } else if (result.tools) {
        update({
          discoveredTools: result.tools,
          lastDiscoveredAt: new Date().toISOString(),
        });
      }
    } catch (e: any) {
      setError(e.message || "Discovery failed");
    } finally {
      setDiscovering(false);
    }
  };

  const addEnvVar = () => {
    update({ envVars: [...data.envVars, { id: `env-${Date.now()}`, name: "", value: "" }] });
  };

  const updateEnvVar = (id: string, field: Partial<McpEnvVar>) => {
    update({ envVars: data.envVars.map((v) => (v.id === id ? { ...v, ...field } : v)) });
  };

  const removeEnvVar = (id: string) => {
    update({ envVars: data.envVars.filter((v) => v.id !== id) });
  };

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="mcp-name">Server Name</Label>
        <Input id="mcp-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="Google Maps" />
      </div>
      <div>
        <Label>Transport</Label>
        <Select value={data.transportType} onValueChange={(v) => update({ transportType: v as McpTransportType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="sse">SSE</SelectItem>
            <SelectItem value="stdio">STDIO</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {data.transportType === "sse" && (
        <div>
          <Label htmlFor="mcp-url">URL</Label>
          <Input id="mcp-url" value={data.url || ""} onChange={(e) => update({ url: e.target.value })} placeholder="http://localhost:8931" />
        </div>
      )}

      {data.transportType === "stdio" && (
        <>
          <div>
            <Label htmlFor="mcp-command">Command</Label>
            <Input id="mcp-command" value={data.command || ""} onChange={(e) => update({ command: e.target.value })} placeholder="npx" />
          </div>
          <div>
            <Label htmlFor="mcp-args">Arguments (comma-separated)</Label>
            <Input
              id="mcp-args"
              value={(data.args || []).join(", ")}
              onChange={(e) => update({ args: e.target.value.split(",").map((s) => s.trim()).filter(Boolean) })}
              placeholder="@playwright/mcp, --headless"
            />
          </div>
        </>
      )}

      <div>
        <Label className="text-xs text-muted-foreground">Environment Variables</Label>
        <div className="space-y-2 mt-1">
          {data.envVars.map((v) => (
            <div key={v.id} className="flex gap-2 items-center">
              <Input
                value={v.name}
                onChange={(e) => updateEnvVar(v.id, { name: e.target.value })}
                placeholder="KEY"
                className="flex-1 text-xs"
              />
              <Input
                type="password"
                value={v.value || ""}
                onChange={(e) => updateEnvVar(v.id, { value: e.target.value })}
                placeholder="value (for discovery only)"
                className="flex-1 text-xs"
              />
              <Button variant="ghost" size="sm" onClick={() => removeEnvVar(v.id)} className="text-xs px-2">x</Button>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={addEnvVar} className="text-xs">+ Add Variable</Button>
        </div>
      </div>

      <Button onClick={handleDiscover} disabled={discovering} className="w-full" size="sm">
        {discovering ? "Discovering..." : data.discoveredTools.length > 0 ? "Re-Discover Tools" : "Discover Tools"}
      </Button>

      {error && (
        <div className="text-sm text-destructive bg-destructive/10 rounded p-2">{error}</div>
      )}

      {data.discoveredTools.length > 0 && (
        <div>
          <Label className="text-xs text-muted-foreground">Discovered Tools ({data.discoveredTools.length})</Label>
          <div className="mt-1 space-y-1 bg-muted/50 rounded p-2">
            {data.discoveredTools.map((tool) => (
              <div key={tool.name}>
                <div className="text-xs font-medium">{tool.name}</div>
                {tool.description && <div className="text-xs text-muted-foreground">{tool.description}</div>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/components/panels/McpServerProperties.tsx
git commit -m "feat(ui): add McpServerProperties panel with discovery"
```

---

## Task 14: PropertiesPanel Routing + Toolbar Button

**Files:**
- Modify: `frontend/components/panels/PropertiesPanel.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Update PropertiesPanel**

Add imports:
```typescript
import { McpServerProperties } from "./McpServerProperties";
import { McpServerConfig } from "@/types/mcp";
```

Update title:
```tsx
{selectedNode.type === "mcp-server" ? "MCP Server" :
 selectedNode.type === "builtin-tool" ? "Built-in Tool" :
 selectedNode.type === "sub-agent" ? "Sub-Agent" :
 selectedNode.type} Properties
```

Add rendering branch:
```tsx
{selectedNode.type === "mcp-server" && (
  <McpServerProperties nodeId={selectedNode.id} data={selectedNode.data as McpServerConfig} />
)}
```

- [ ] **Step 2: Update Toolbar**

Add a "+MCP Server" button after the "+Sub-Agent" button:

```tsx
<Button variant="outline" size="sm" onClick={() => addNode("mcp-server", { x: 100, y: 300 })}>
  + MCP Server
</Button>
```

- [ ] **Step 3: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/PropertiesPanel.tsx \
        frontend/components/panels/Toolbar.tsx
git commit -m "feat(ui): wire MCP properties panel and toolbar button"
```

---

## Task 15: Final — All Tests + Type Check

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`

Expected: ALL tests pass.

- [ ] **Step 2: Run frontend type check**

Run: `cd frontend && npx tsc --noEmit`

Expected: No errors.

- [ ] **Step 3: Fix any issues and commit**

If any fixes needed:
```bash
git add -A
git commit -m "fix: address issues found during final verification"
```
