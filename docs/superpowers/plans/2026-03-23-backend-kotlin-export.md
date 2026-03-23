# Backend Kotlin Export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin backend service that receives a graph model (JSON) from the frontend editor and generates compilable Koog agent Kotlin source files as a ZIP download.

**Architecture:** Spring Boot REST endpoint receives the ProjectGraph JSON, maps it through an adapter layer to a Koog export model, then uses template-based code generation to produce Kotlin source files. The export is deterministic and testable without the frontend.

**Tech Stack:** Kotlin, Spring Boot 4, Jackson, Koog SDK (as reference for generated code patterns)

---

## File Structure

```
src/main/kotlin/com/agentwork/modeler/
├── ModelerApplication.kt              # (existing)
├── MyAgentService.kt                   # (existing)
├── IndexController.kt                  # (existing)
├── export/
│   ├── ExportController.kt            # POST /api/export endpoint
│   ├── ExportService.kt               # Orchestrates validation → generation → ZIP
│   ├── model/
│   │   ├── GraphModel.kt              # Incoming JSON model (ProjectGraph)
│   │   └── KoogModel.kt               # Internal Koog export model
│   ├── adapter/
│   │   └── GraphToKoogAdapter.kt      # Maps GraphModel → KoogModel
│   ├── generator/
│   │   ├── AgentGenerator.kt          # Generates AgentDefinition.kt content
│   │   ├── ToolGenerator.kt           # Generates Tool class content
│   │   ├── RegistryGenerator.kt       # Generates ToolRegistry setup
│   │   └── MainGenerator.kt           # Generates Main.kt / runner
│   ├── validation/
│   │   └── GraphValidator.kt          # Validates graph before export
│   └── zip/
│       └── ZipBuilder.kt              # Assembles files into ZIP

src/test/kotlin/com/agentwork/modeler/
├── export/
│   ├── adapter/
│   │   └── GraphToKoogAdapterTest.kt
│   ├── validation/
│   │   └── GraphValidatorTest.kt
│   ├── generator/
│   │   ├── AgentGeneratorTest.kt
│   │   ├── ToolGeneratorTest.kt
│   │   ├── RegistryGeneratorTest.kt
│   │   └── MainGeneratorTest.kt
│   ├── zip/
│   │   └── ZipBuilderTest.kt
│   └── ExportServiceTest.kt
```

---

### Task 1: Graph Model (Incoming JSON)

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt`

- [ ] **Step 1: Define data classes for the incoming graph JSON**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt

package com.agentwork.modeler.export.model

data class ProjectGraph(
    val version: Int,
    val name: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNode(
    val id: String,
    val type: String, // "agent" or "tool"
    val position: Position,
    val data: Map<String, Any?>
)

data class Position(val x: Double, val y: Double)

data class GraphEdge(
    val id: String,
    val source: String, // tool node id
    val target: String  // agent node id
)
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt
git commit -m "feat: add GraphModel data classes for incoming JSON"
```

---

### Task 2: Koog Export Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`

- [ ] **Step 1: Define internal Koog export model**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt

package com.agentwork.modeler.export.model

data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>
)

enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash")
}

data class KoogTool(
    val name: String,
    val description: String,
    val parameters: List<KoogToolParameter>,
    val handlerStubName: String?
)

data class KoogToolParameter(
    val name: String,
    val type: KoogParamType,
    val description: String,
    val required: Boolean
)

enum class KoogParamType(val kotlinType: String) {
    STRING("String"),
    INT("Int"),
    FLOAT("Float"),
    BOOLEAN("Boolean")
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat: add KoogModel with Agent, Tool, Parameter definitions"
```

---

### Task 3: Graph-to-Koog Adapter

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt

package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GraphToKoogAdapterTest {

    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts a valid graph with one agent and one tool`() {
        val graph = ProjectGraph(
            version = 1,
            name = "TestProject",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyAgent",
                    "systemPrompt" to "You are helpful.",
                    "modelProvider" to "openai",
                    "modelId" to "gpt-4o",
                    "temperature" to 0.7
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool",
                    "description" to "Searches the web",
                    "parameters" to listOf(
                        mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "Search query", "required" to true)
                    )
                ))
            ),
            edges = listOf(GraphEdge("e1", "t1", "a1"))
        )

        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals("MyAgent", agents[0].name)
        assertEquals(ModelProvider.OPENAI, agents[0].modelProvider)
        assertEquals(1, agents[0].tools.size)
        assertEquals("searchTool", agents[0].tools[0].name)
        assertEquals(1, agents[0].tools[0].parameters.size)
        assertEquals(KoogParamType.STRING, agents[0].tools[0].parameters[0].type)
    }

    @Test
    fun `throws on agent without name`() {
        val graph = ProjectGraph(
            version = 1,
            name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "",
                    "systemPrompt" to "Prompt",
                    "modelProvider" to "openai"
                ))
            ),
            edges = emptyList()
        )
        assertThrows<IllegalArgumentException> { adapter.convert(graph) }
    }

    @Test
    fun `agent with no connected tools gets empty tool list`() {
        val graph = ProjectGraph(
            version = 1,
            name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "Agent1",
                    "systemPrompt" to "Prompt",
                    "modelProvider" to "openai"
                ))
            ),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertEquals(0, agents[0].tools.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.GraphToKoogAdapterTest"
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement adapter**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt

package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class GraphToKoogAdapter {

    fun convert(graph: ProjectGraph): List<KoogAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val agentNodes = graph.nodes.filter { it.type == "agent" }

        return agentNodes.map { agentNode ->
            val name = agentNode.data["name"] as? String ?: ""
            require(name.isNotBlank()) { "Agent requires a name" }

            val connectedToolIds = graph.edges
                .filter { it.target == agentNode.id }
                .map { it.source }

            val tools = toolNodes
                .filter { it.id in connectedToolIds }
                .map { convertTool(it) }

            KoogAgent(
                name = name,
                systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
                modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
                modelId = agentNode.data["modelId"] as? String,
                temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
                tools = tools
            )
        }
    }

    private fun convertTool(node: GraphNode): KoogTool {
        val params = (node.data["parameters"] as? List<*>)?.map { raw ->
            val map = raw as Map<*, *>
            KoogToolParameter(
                name = map["name"] as? String ?: "",
                type = parseParamType(map["type"] as? String ?: "String"),
                description = map["description"] as? String ?: "",
                required = map["required"] as? Boolean ?: true
            )
        } ?: emptyList()

        return KoogTool(
            name = node.data["name"] as? String ?: "",
            description = node.data["description"] as? String ?: "",
            parameters = params,
            handlerStubName = node.data["handlerStubName"] as? String
        )
    }

    private fun parseProvider(value: String): ModelProvider = when (value.lowercase()) {
        "openai" -> ModelProvider.OPENAI
        "anthropic" -> ModelProvider.ANTHROPIC
        "google" -> ModelProvider.GOOGLE
        else -> ModelProvider.OPENAI
    }

    private fun parseParamType(value: String): KoogParamType = when (value) {
        "Int" -> KoogParamType.INT
        "Float" -> KoogParamType.FLOAT
        "Boolean" -> KoogParamType.BOOLEAN
        else -> KoogParamType.STRING
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.GraphToKoogAdapterTest"
```
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/ src/test/kotlin/com/agentwork/modeler/export/adapter/
git commit -m "feat: add GraphToKoog adapter with tests"
```

---

### Task 4: Agent Code Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class AgentGeneratorTest {

    private val generator = AgentGenerator()

    @Test
    fun `generates agent definition with correct model and prompt`() {
        val agent = KoogAgent(
            name = "MyAgent",
            systemPrompt = "You are a helpful assistant.",
            modelProvider = ModelProvider.OPENAI,
            modelId = "gpt-4o",
            temperature = 0.7,
            tools = emptyList()
        )

        val code = generator.generate(agent)

        assertContains(code, "class MyAgent")
        assertContains(code, "simpleOpenAIExecutor")
        assertContains(code, "You are a helpful assistant.")
        assertContains(code, "GPT4o")
        assertContains(code, "temperature = 0.7")
    }

    @Test
    fun `generates agent with tool registry reference`() {
        val agent = KoogAgent(
            name = "AgentWithTools",
            systemPrompt = "Help",
            modelProvider = ModelProvider.ANTHROPIC,
            modelId = null,
            temperature = null,
            tools = listOf(
                KoogTool("searchTool", "Searches", emptyList(), null)
            )
        )

        val code = generator.generate(agent)

        assertContains(code, "toolRegistry")
        assertContains(code, "AnthropicModels")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.AgentGeneratorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement AgentGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class AgentGenerator {

    fun generate(agent: KoogAgent): String {
        val modelImport = agent.modelProvider.importPackage
        val modelRef = agent.modelId?.let { mapModelId(agent.modelProvider, it) } ?: agent.modelProvider.defaultModel

        val params = mutableListOf(
            """promptExecutor = ${agent.modelProvider.executorFactory}(System.getenv("${agent.modelProvider.name}_API_KEY"))""",
            """systemPrompt = "${agent.systemPrompt.replace("\"", "\\\"")}"""",
            "llmModel = $modelRef"
        )
        agent.temperature?.let { params.add("temperature = $it") }
        if (agent.tools.isNotEmpty()) {
            params.add("toolRegistry = createToolRegistry()")
        }

        val paramBlock = params.joinToString(",\n            ")

        return """
            |package generated
            |
            |import ai.koog.agents.core.agent.AIAgent
            |import ai.koog.agents.core.tools.ToolRegistry
            |import ai.koog.prompt.executor.llms.all.${agent.modelProvider.executorFactory}
            |import $modelImport
            |
            |class ${agent.name} {
            |
            |    suspend fun run(userMessage: String): String {
            |        val agent = AIAgent(
            |            $paramBlock
            |        )
            |
            |        return agent.run(userMessage)
            |    }
            |}
        """.trimMargin()
    }

    private fun mapModelId(provider: ModelProvider, id: String): String = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
            "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
            "gpt-4-1" -> "OpenAIModels.Chat.GPT4_1"
            else -> provider.defaultModel
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
            "claude-opus-4" -> "AnthropicModels.Opus_4"
            "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
            else -> provider.defaultModel
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
            "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
            else -> provider.defaultModel
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.AgentGeneratorTest"
```
Expected: All 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat: add AgentGenerator for Koog agent Kotlin code"
```

---

### Task 5: Tool Code Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ToolGeneratorTest {

    private val generator = ToolGenerator()

    @Test
    fun `generates tool class with parameters`() {
        val tool = KoogTool(
            name = "searchTool",
            description = "Searches the web for information",
            parameters = listOf(
                KoogToolParameter("query", KoogParamType.STRING, "The search query", true),
                KoogToolParameter("maxResults", KoogParamType.INT, "Maximum results", false)
            ),
            handlerStubName = null
        )

        val code = generator.generate(tool)

        assertContains(code, "SearchToolToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "@LLMDescription")
        assertContains(code, "query: String")
        assertContains(code, "maxResults: Int")
        assertContains(code, "Searches the web for information")
    }

    @Test
    fun `generates tool with no parameters`() {
        val tool = KoogTool("pingTool", "Pings the server", emptyList(), null)

        val code = generator.generate(tool)

        assertContains(code, "PingToolToolSet")
        assertContains(code, "@Tool")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ToolGeneratorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement ToolGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String {
        val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
        val params = tool.parameters.joinToString(",\n        ") { param ->
            """@LLMDescription("${param.description}") ${param.name}: ${param.type.kotlinType}"""
        }
        val paramList = if (params.isNotEmpty()) "\n        $params\n    " else ""

        return """
            |package generated.tools
            |
            |import ai.koog.agents.core.tools.annotations.Tool
            |import ai.koog.agents.core.tools.annotations.LLMDescription
            |import ai.koog.agents.core.tools.reflect.ToolSet
            |
            |@LLMDescription("${tool.description}")
            |class $className : ToolSet {
            |
            |    @Tool
            |    @LLMDescription("${tool.description}")
            |    fun ${tool.name}($paramList): String {
            |        // TODO: Implement tool logic
            |        return "Not implemented"
            |    }
            |}
        """.trimMargin()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ToolGeneratorTest"
```
Expected: All 2 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/ToolGeneratorTest.kt
git commit -m "feat: add ToolGenerator for annotation-based Koog tool code"
```

---

### Task 6: Registry & Main Generators

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/MainGeneratorTest.kt`

- [ ] **Step 1: Write failing tests for RegistryGenerator**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class RegistryGeneratorTest {

    private val generator = RegistryGenerator()

    @Test
    fun `generates registry with multiple tools`() {
        val tools = listOf(
            KoogTool("searchTool", "Searches", emptyList(), null),
            KoogTool("calcTool", "Calculates", emptyList(), null)
        )

        val code = generator.generate(tools)

        assertContains(code, "ToolRegistry")
        assertContains(code, "SearchToolToolSet")
        assertContains(code, "CalcToolToolSet")
        assertContains(code, "tools(")
    }
}
```

- [ ] **Step 2: Write failing tests for MainGenerator**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/generator/MainGeneratorTest.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class MainGeneratorTest {

    private val generator = MainGenerator()

    @Test
    fun `generates runnable main function`() {
        val agent = KoogAgent(
            name = "MyAgent",
            systemPrompt = "Help",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            tools = emptyList()
        )

        val code = generator.generate(agent)

        assertContains(code, "fun main()")
        assertContains(code, "runBlocking")
        assertContains(code, "MyAgent")
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.RegistryGeneratorTest" --tests "*.MainGeneratorTest"
```
Expected: FAIL

- [ ] **Step 4: Implement RegistryGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogTool
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {

    fun generate(tools: List<KoogTool>): String {
        val toolInstances = tools.joinToString("\n    ") { tool ->
            val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
            "val ${tool.name}Tools = $className()"
        }

        val toolRegistrations = tools.joinToString("\n        ") { tool ->
            "tools(${tool.name}Tools)"
        }

        return """
            |package generated
            |
            |import ai.koog.agents.core.tools.ToolRegistry
            |import ai.koog.agents.core.tools.reflect.tools
            |import generated.tools.*
            |
            |fun createToolRegistry(): ToolRegistry {
            |    $toolInstances
            |
            |    return ToolRegistry {
            |        $toolRegistrations
            |    }
            |}
        """.trimMargin()
    }
}
```

- [ ] **Step 5: Implement MainGenerator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt

package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class MainGenerator {

    fun generate(agent: KoogAgent): String {
        return """
            |package generated
            |
            |import kotlinx.coroutines.runBlocking
            |
            |fun main() = runBlocking {
            |    val agent = ${agent.name}()
            |    val result = agent.run("Hello! How can you help me?")
            |    println(result)
            |}
        """.trimMargin()
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.RegistryGeneratorTest" --tests "*.MainGeneratorTest"
```
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/
git commit -m "feat: add RegistryGenerator and MainGenerator"
```

---

### Task 7: ZIP Builder

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/zip/ZipBuilder.kt`

- [ ] **Step 1: Write failing test for ZipBuilder**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/zip/ZipBuilderTest.kt

package com.agentwork.modeler.export.zip

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals

class ZipBuilderTest {

    private val builder = ZipBuilder()

    @Test
    fun `creates ZIP with expected entries and content`() {
        val files = mapOf(
            "src/Hello.kt" to "fun main() {}",
            "src/tools/MyTool.kt" to "class MyTool"
        )

        val zip = builder.buildZip(files)
        val entries = mutableMapOf<String, String>()

        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = String(zis.readAllBytes())
                entry = zis.nextEntry
            }
        }

        assertEquals(2, entries.size)
        assertEquals("fun main() {}", entries["src/Hello.kt"])
        assertEquals("class MyTool", entries["src/tools/MyTool.kt"])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ZipBuilderTest"
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement ZipBuilder**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/zip/ZipBuilder.kt

package com.agentwork.modeler.export.zip

import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class ZipBuilder {

    fun buildZip(files: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            for ((path, content) in files) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ZipBuilderTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/zip/ src/test/kotlin/com/agentwork/modeler/export/zip/
git commit -m "feat: add ZipBuilder with tests"
```

---

### Task 8: Graph Validator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt

package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphValidatorTest {

    private val validator = GraphValidator()

    @Test
    fun `valid graph passes validation`() {
        val graph = ProjectGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to listOf(mapOf("id" to "p1", "name" to "q", "type" to "String", "description" to "d", "required" to true))))
            ),
            edges = listOf(GraphEdge("e1", "t1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `reports agent without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "Help", "modelProvider" to "openai"))),
            emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("a1") })
    }

    @Test
    fun `reports agent without system prompt`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("prompt") })
    }

    @Test
    fun `reports tool without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("t1") })
    }

    @Test
    fun `reports tool without connected agent`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to emptyList<Any>()))),
            emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") })
    }

    @Test
    fun `reports parameter without name`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("t1", "tool", Position(0.0, 0.0), mapOf("name" to "Tool1", "description" to "Desc", "parameters" to listOf(mapOf("id" to "p1", "name" to "", "type" to "String", "description" to "", "required" to true))))),
            listOf(GraphEdge("e1", "t1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Parameter") })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.GraphValidatorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement GraphValidator**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt

package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.ProjectGraph
import org.springframework.stereotype.Component

@Component
class GraphValidator {

    fun validate(graph: ProjectGraph): List<String> {
        val errors = mutableListOf<String>()

        for (node in graph.nodes) {
            when (node.type) {
                "agent" -> {
                    val name = node.data["name"] as? String ?: ""
                    val prompt = node.data["systemPrompt"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Agent requires a name")
                    if (prompt.isBlank()) errors.add("[${node.id}] Agent requires a system prompt")
                }
                "tool" -> {
                    val name = node.data["name"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Tool requires a name")

                    val isConnected = graph.edges.any { it.source == node.id }
                    if (!isConnected) errors.add("[${node.id}] Tool must be connected to an Agent")

                    val params = node.data["parameters"] as? List<*> ?: emptyList<Any>()
                    for (param in params) {
                        val map = param as? Map<*, *> ?: continue
                        val paramName = map["name"] as? String ?: ""
                        if (paramName.isBlank()) errors.add("[${node.id}] Parameter requires a name")
                    }
                }
            }
        }

        return errors
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.GraphValidatorTest"
```
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/ src/test/kotlin/com/agentwork/modeler/export/validation/
git commit -m "feat: add GraphValidator with full validation rules"
```

---

### Task 9: Export Service

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt

package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.AgentGenerator
import com.agentwork.modeler.export.generator.MainGenerator
import com.agentwork.modeler.export.generator.RegistryGenerator
import com.agentwork.modeler.export.generator.ToolGenerator
import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.zip.ZipBuilder
import org.junit.jupiter.api.Test
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue

class ExportServiceTest {

    private val service = ExportService(
        validator = GraphValidator(),
        adapter = GraphToKoogAdapter(),
        agentGenerator = AgentGenerator(),
        toolGenerator = ToolGenerator(),
        registryGenerator = RegistryGenerator(),
        mainGenerator = MainGenerator(),
        zipBuilder = ZipBuilder()
    )

    @Test
    fun `exports a valid graph as ZIP with expected files`() {
        val graph = ProjectGraph(
            version = 1,
            name = "TestProject",
            nodes = listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyAgent",
                    "systemPrompt" to "Help",
                    "modelProvider" to "openai"
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool",
                    "description" to "Searches",
                    "parameters" to listOf(
                        mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "query", "required" to true)
                    )
                ))
            ),
            edges = listOf(GraphEdge("e1", "t1", "a1"))
        )

        val zip = service.export(graph)
        val entries = readZipEntries(zip)

        assertTrue(entries.any { it.contains("MyAgent.kt") })
        assertTrue(entries.any { it.contains("SearchToolToolSet.kt") })
        assertTrue(entries.any { it.contains("ToolRegistry.kt") })
        assertTrue(entries.any { it.contains("Main.kt") })
    }

    private fun readZipEntries(data: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        return entries
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ExportServiceTest"
```
Expected: FAIL

- [ ] **Step 3: Implement ExportService**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/ExportService.kt

package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.AgentGenerator
import com.agentwork.modeler.export.generator.MainGenerator
import com.agentwork.modeler.export.generator.RegistryGenerator
import com.agentwork.modeler.export.generator.ToolGenerator
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.export.zip.ZipBuilder
import org.springframework.stereotype.Service

class ExportValidationException(val errors: List<String>) : RuntimeException("Validation failed: ${errors.joinToString("; ")}")

@Service
class ExportService(
    private val validator: GraphValidator,
    private val adapter: GraphToKoogAdapter,
    private val agentGenerator: AgentGenerator,
    private val toolGenerator: ToolGenerator,
    private val registryGenerator: RegistryGenerator,
    private val mainGenerator: MainGenerator,
    private val zipBuilder: ZipBuilder
) {

    fun export(graph: ProjectGraph): ByteArray {
        val validationErrors = validator.validate(graph)
        if (validationErrors.isNotEmpty()) {
            throw ExportValidationException(validationErrors)
        }

        val agents = adapter.convert(graph)

        val files = mutableMapOf<String, String>()

        for (agent in agents) {
            files["src/${agent.name}.kt"] = agentGenerator.generate(agent)

            for (tool in agent.tools) {
                val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                files["src/tools/${className}.kt"] = toolGenerator.generate(tool)
            }

            if (agent.tools.isNotEmpty()) {
                files["src/ToolRegistry.kt"] = registryGenerator.generate(agent.tools)
            }

            files["src/Main.kt"] = mainGenerator.generate(agent)
        }

        return zipBuilder.buildZip(files)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*.ExportServiceTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat: add ExportService orchestrating graph-to-ZIP pipeline"
```

---

### Task 10: Export REST Controller & CORS

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/ExportController.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt`

- [ ] **Step 1: Implement ExportController with error handling**

```kotlin
// src/main/kotlin/com/agentwork/modeler/export/ExportController.kt

package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.ProjectGraph
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ExportController(private val exportService: ExportService) {

    @PostMapping("/api/export")
    fun export(@RequestBody graph: ProjectGraph): ResponseEntity<ByteArray> {
        val zip = exportService.export(graph)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${graph.name}.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip)
    }

    @ExceptionHandler(ExportValidationException::class)
    fun handleValidation(ex: ExportValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            mapOf("error" to "Validation failed", "details" to ex.errors)
        )
    }
}
```

- [ ] **Step 2: Add CORS configuration**

```kotlin
// src/main/kotlin/com/agentwork/modeler/config/CorsConfig.kt

package com.agentwork.modeler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
        }
    }
}
```
```

- [ ] **Step 2: Test with curl**

```bash
curl -X POST http://localhost:8080/api/export \
  -H "Content-Type: application/json" \
  -d '{"version":1,"name":"TestProject","nodes":[{"id":"a1","type":"agent","position":{"x":0,"y":0},"data":{"name":"MyAgent","systemPrompt":"Help","modelProvider":"openai"}},{"id":"t1","type":"tool","position":{"x":200,"y":0},"data":{"name":"searchTool","description":"Searches","parameters":[{"id":"p1","name":"query","type":"String","description":"query","required":true}]}}],"edges":[{"id":"e1","source":"t1","target":"a1"}]}' \
  --output test-export.zip
```
Expected: Downloads a valid ZIP file

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportController.kt
git commit -m "feat: add POST /api/export endpoint returning ZIP"
```
