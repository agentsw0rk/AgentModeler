# Sub-Agent Modeling — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `sub-agent` node type to the Koog Agent Editor so a main agent can delegate to specialized sub-agents with their own tools, exported as Koog agent-tools.

**Architecture:** New `sub-agent` NodeType through all layers — frontend types, Zustand store, Rete node class, properties panel, Canvas sync, validation (frontend + backend), adapter, new SubAgentGenerator, RegistryGenerator extension, and ExportService wiring. Sub-agents connect to the main agent like tools (output → input) and are rendered with purple styling and dashed connections.

**Tech Stack:** TypeScript, Next.js, Rete.js, Zustand, shadcn/ui (frontend); Kotlin, Spring Boot, JUnit 5 (backend)

**Spec:** `docs/superpowers/specs/2026-03-23-sub-agent-modeling-design.md`

---

## File Map

### New Files
- `frontend/types/subagent.ts` — SubAgentConfig interface
- `frontend/components/panels/SubAgentProperties.tsx` — Properties panel
- `src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt` — Code generator
- `src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt` — Generator tests

### Modified Files
- `frontend/types/graph.ts` — Add `"sub-agent"` to NodeType, SubAgentConfig to data union
- `frontend/types/koog.ts` — Re-export SubAgentConfig
- `frontend/store/editorStore.ts` — Default data for sub-agent, type union update
- `frontend/lib/rete-nodes.ts` — SubAgentReteNode class, AppNode union
- `frontend/lib/rete-setup.ts` — Connection validation rules
- `frontend/lib/validation.ts` — Sub-agent validation rules
- `frontend/components/editor/Canvas.tsx` — Sync logic for sub-agent nodes + connections
- `frontend/components/panels/Toolbar.tsx` — +Sub-Agent button
- `frontend/components/panels/PropertiesPanel.tsx` — Route to SubAgentProperties
- `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt` — KoogSubAgent data class
- `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt` — Parse sub-agent nodes
- `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` — Sub-agent rules
- `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt` — Sub-agent tool registration
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` — Wire SubAgentGenerator
- `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

---

## Task 1: Verify Koog Sub-Agent API

**Files:**
- Read: `docs/koog/examples/code-agent/step-04-add-subagent/src/main/kotlin/`

Before building generators, verify the exact Koog API for registering sub-agents as tools.

- [ ] **Step 1: Read the Koog sub-agent example**

Read `docs/koog/examples/code-agent/step-04-add-subagent/src/main/kotlin/Main.kt` and `FindAgent.kt`. Note:
- How sub-agents are created (AIAgent constructor vs. builder)
- How they are registered as tools (`createAgentTool`, `.asTool()`, or similar)
- What parameters the registration takes (`agentName`, `agentDescription`, `inputDescription`)
- What imports are needed

- [ ] **Step 2: Search for additional patterns**

Run: `grep -r "createAgentTool\|asTool\|agentAsATool" docs/koog/ --include="*.kt" -l`

Document the exact API call and imports. This informs the code in Tasks 7 and 8.

- [ ] **Step 3: Document findings**

Write a brief note at the top of this plan (or in the spec) with the verified API pattern. If the API differs from the spec's assumption, update the spec accordingly.

---

## Task 2: Backend Model — KoogSubAgent

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`

- [ ] **Step 1: Add KoogSubAgent data class**

Add after `KoogBuiltinTool` in `KoogModel.kt`:

```kotlin
data class KoogSubAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val delegationMode: String,
    val inputDescription: String,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>
)
```

- [ ] **Step 2: Add subAgents field to KoogAgent**

Change `KoogAgent` to:

```kotlin
data class KoogAgent(
    val name: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList()
)
```

- [ ] **Step 3: Verify existing tests still compile**

Run: `./gradlew test`

Expected: All tests pass (default `emptyList()` means no call-site changes needed).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt
git commit -m "feat(model): add KoogSubAgent data class and subAgents field to KoogAgent"
```

---

## Task 3: Backend Validation — Sub-Agent Rules

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

- [ ] **Step 1: Write failing tests for sub-agent validation**

Add to `GraphValidatorTest.kt`:

```kotlin
@Test
fun `valid sub-agent connected to agent passes validation`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "ResearchAgent", "description" to "Researches topics",
                "systemPrompt" to "You research.", "inputDescription" to "A topic",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    assertTrue(validator.validate(graph).isEmpty())
}

@Test
fun `reports sub-agent without name`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("name") && it.contains("sa1") })
}

@Test
fun `reports sub-agent without description`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA", "description" to "",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("description") })
}

@Test
fun `reports sub-agent without system prompt`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA", "description" to "Desc",
                "systemPrompt" to "", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("prompt") })
}

@Test
fun `reports sub-agent without input description`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    assertTrue(validator.validate(graph).any { it.contains("input description") })
}

@Test
fun `reports sub-agent not connected to agent`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        emptyList())
    assertTrue(validator.validate(graph).any { it.contains("connected") })
}

@Test
fun `reports sub-agent connected to another sub-agent`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA1", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            )),
            GraphNode("sa2", "sub-agent", Position(400.0, 0.0), mapOf(
                "name" to "SA2", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1"), GraphEdge("e2", "sa2", "sa1")))
    assertTrue(validator.validate(graph).any { it.contains("cannot delegate") || it.contains("Sub-Agent") })
}

@Test
fun `sub-agent does not count toward single-agent limit`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA1", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai"
            ))
        ),
        listOf(GraphEdge("e1", "sa1", "a1")))
    val errors = validator.validate(graph)
    assertFalse(errors.any { it.contains("single-agent") || it.contains("Only single") })
}
```

Add `import kotlin.test.assertFalse` to the imports if not already present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`

Expected: New tests FAIL (validator ignores `"sub-agent"` nodes).

- [ ] **Step 3: Implement sub-agent validation**

In `GraphValidator.kt`, add a new `when` branch inside the `for (node in graph.nodes)` loop, after the `"builtin-tool"` branch:

```kotlin
"sub-agent" -> {
    val name = node.data["name"] as? String ?: ""
    val description = node.data["description"] as? String ?: ""
    val prompt = node.data["systemPrompt"] as? String ?: ""
    val inputDesc = node.data["inputDescription"] as? String ?: ""
    if (name.isBlank()) errors.add("[${node.id}] Sub-Agent requires a name")
    if (description.isBlank()) errors.add("[${node.id}] Sub-Agent requires a description")
    if (prompt.isBlank()) errors.add("[${node.id}] Sub-Agent requires a system prompt")
    if (inputDesc.isBlank()) errors.add("[${node.id}] Sub-Agent requires an input description")

    val isConnected = graph.edges.any { it.source == node.id }
    if (!isConnected) errors.add("[${node.id}] Sub-Agent must be connected to the main Agent")

    // Check target is an agent, not another sub-agent
    val targets = graph.edges.filter { it.source == node.id }.map { edge ->
        graph.nodes.find { it.id == edge.target }
    }
    for (target in targets) {
        if (target?.type == "sub-agent") {
            errors.add("[${node.id}] Sub-Agent cannot delegate to another Sub-Agent")
        }
    }
}
```

Also extend the existing `"tool"` and `"builtin-tool"` branches to check for multi-connection. After the `isConnected` check in both branches, add:

```kotlin
val connectedAgentCount = graph.edges
    .filter { it.source == node.id }
    .map { edge -> graph.nodes.find { it.id == edge.target } }
    .count { it?.type == "agent" || it?.type == "sub-agent" }
if (connectedAgentCount > 1) {
    errors.add("[${node.id}] Tool can only be connected to one Agent or Sub-Agent")
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`

Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt \
        src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt
git commit -m "feat(validation): add sub-agent validation rules"
```

---

## Task 4: Backend Adapter — Parse Sub-Agent Nodes

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `GraphToKoogAdapterTest.kt`:

```kotlin
@Test
fun `converts sub-agent nodes with their own tools`() {
    val graph = ProjectGraph(
        version = 1, name = "Test",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
            )),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "ResearchAgent", "description" to "Researches topics",
                "systemPrompt" to "You research.", "inputDescription" to "A topic",
                "modelProvider" to "anthropic", "modelId" to "claude-sonnet-4", "temperature" to 0.5,
                "delegationMode" to "automatic"
            )),
            GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                "name" to "fetchTool", "description" to "Fetches data",
                "parameters" to emptyList<Any>()
            ))
        ),
        edges = listOf(
            GraphEdge("e1", "sa1", "a1"),
            GraphEdge("e2", "t1", "sa1")
        )
    )

    val agents = adapter.convert(graph)
    assertEquals(1, agents.size)
    assertEquals("MainAgent", agents[0].name)
    assertEquals(1, agents[0].subAgents.size)

    val sa = agents[0].subAgents[0]
    assertEquals("ResearchAgent", sa.name)
    assertEquals("Researches topics", sa.description)
    assertEquals("You research.", sa.systemPrompt)
    assertEquals("A topic", sa.inputDescription)
    assertEquals(ModelProvider.ANTHROPIC, sa.modelProvider)
    assertEquals("claude-sonnet-4", sa.modelId)
    assertEquals(0.5, sa.temperature)
    assertEquals("automatic", sa.delegationMode)
    assertEquals(1, sa.tools.size)
    assertEquals("fetchTool", sa.tools[0].name)
}

@Test
fun `sub-agent tools are not included in main agent tools`() {
    val graph = ProjectGraph(
        version = 1, name = "Test",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai"
            )),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "SA", "description" to "Desc",
                "systemPrompt" to "Prompt", "inputDescription" to "Input",
                "modelProvider" to "openai", "delegationMode" to "automatic"
            )),
            GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                "name" to "subTool", "description" to "SubTool",
                "parameters" to emptyList<Any>()
            )),
            GraphNode("t2", "tool", Position(400.0, 0.0), mapOf(
                "name" to "mainTool", "description" to "MainTool",
                "parameters" to emptyList<Any>()
            ))
        ),
        edges = listOf(
            GraphEdge("e1", "sa1", "a1"),
            GraphEdge("e2", "t1", "sa1"),
            GraphEdge("e3", "t2", "a1")
        )
    )

    val agents = adapter.convert(graph)
    assertEquals(1, agents[0].tools.size)
    assertEquals("mainTool", agents[0].tools[0].name)
    assertEquals(1, agents[0].subAgents[0].tools.size)
    assertEquals("subTool", agents[0].subAgents[0].tools[0].name)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest"`

Expected: FAIL — `subAgents` list is empty.

- [ ] **Step 3: Implement sub-agent parsing in adapter**

Replace the `convert` method in `GraphToKoogAdapter.kt`:

```kotlin
fun convert(graph: ProjectGraph): List<KoogAgent> {
    val toolNodes = graph.nodes.filter { it.type == "tool" }
    val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
    val agentNodes = graph.nodes.filter { it.type == "agent" }
    val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }

    return agentNodes.map { agentNode ->
        val name = agentNode.data["name"] as? String ?: ""
        require(name.isNotBlank()) { "Agent requires a name" }

        val connectedToAgent = graph.edges
            .filter { it.target == agentNode.id }
            .map { it.source }

        // Sub-agent IDs connected to this agent
        val subAgentIds = subAgentNodes
            .filter { it.id in connectedToAgent }
            .map { it.id }
            .toSet()

        // Tool/builtin IDs connected to sub-agents (not main agent)
        val connectedToSubAgents = graph.edges
            .filter { it.target in subAgentIds }
            .map { it.source }
            .toSet()

        // Main agent tools: connected to agent, NOT connected to a sub-agent
        val tools = toolNodes
            .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
            .map { convertTool(it) }

        val builtinTools = builtinToolNodes
            .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
            .map { convertBuiltinTool(it) }

        // Sub-agents with their own tools
        val subAgents = subAgentNodes
            .filter { it.id in connectedToAgent }
            .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes) }

        KoogAgent(
            name = name,
            systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
            modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
            modelId = agentNode.data["modelId"] as? String,
            temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
            tools = tools,
            builtinTools = builtinTools,
            subAgents = subAgents
        )
    }
}

private fun convertSubAgent(
    saNode: GraphNode,
    graph: ProjectGraph,
    toolNodes: List<GraphNode>,
    builtinToolNodes: List<GraphNode>
): KoogSubAgent {
    val connectedIds = graph.edges
        .filter { it.target == saNode.id }
        .map { it.source }

    val tools = toolNodes
        .filter { it.id in connectedIds }
        .map { convertTool(it) }

    val builtinTools = builtinToolNodes
        .filter { it.id in connectedIds }
        .map { convertBuiltinTool(it) }

    return KoogSubAgent(
        name = saNode.data["name"] as? String ?: "",
        description = saNode.data["description"] as? String ?: "",
        systemPrompt = saNode.data["systemPrompt"] as? String ?: "",
        modelProvider = parseProvider(saNode.data["modelProvider"] as? String ?: "openai"),
        modelId = saNode.data["modelId"] as? String,
        temperature = (saNode.data["temperature"] as? Number)?.toDouble(),
        delegationMode = saNode.data["delegationMode"] as? String ?: "automatic",
        inputDescription = saNode.data["inputDescription"] as? String ?: "",
        tools = tools,
        builtinTools = builtinTools
    )
}
```

Add import at top: `import com.agentwork.modeler.export.model.KoogSubAgent`

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterTest"`

Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt \
        src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterTest.kt
git commit -m "feat(adapter): parse sub-agent nodes with their own tools"
```

---

## Task 5: SubAgentGenerator — Code Generation

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Create `SubAgentGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SubAgentGeneratorTest {

    private val generator = SubAgentGenerator()

    @Test
    fun `generates sub-agent function with correct provider and model`() {
        val subAgent = KoogSubAgent(
            name = "ResearchAgent", description = "Researches topics",
            systemPrompt = "You are a research specialist.",
            modelProvider = ModelProvider.ANTHROPIC, modelId = "claude-sonnet-4",
            temperature = 0.5, delegationMode = "automatic",
            inputDescription = "A topic to research",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "package generated.subagents")
        assertContains(code, "fun createResearchAgent()")
        assertContains(code, "simpleAnthropicExecutor")
        assertContains(code, "ANTHROPIC_API_KEY")
        assertContains(code, "You are a research specialist.")
        assertContains(code, "AnthropicModels")
    }

    @Test
    fun `generates sub-agent with temperature`() {
        val subAgent = KoogSubAgent(
            name = "SA", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = 0.7, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "temperature = 0.7")
    }

    @Test
    fun `generates sub-agent with tool registry when tools exist`() {
        val subAgent = KoogSubAgent(
            name = "ToolAgent", description = "Has tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList(), null)),
            builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertContains(code, "toolRegistry = createToolAgentToolRegistry()")
    }

    @Test
    fun `omits tool registry when no tools`() {
        val subAgent = KoogSubAgent(
            name = "SimpleAgent", description = "No tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val code = generator.generate(subAgent)
        assertFalse(code.contains("toolRegistry"))
    }

    @Test
    fun `generates sub-agent registry file when tools exist`() {
        val subAgent = KoogSubAgent(
            name = "ToolAgent", description = "Has tools",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = listOf(KoogTool("fetchTool", "Fetches", emptyList(), null)),
            builtinTools = emptyList()
        )
        val code = generator.generateRegistry(subAgent)!!
        assertContains(code, "package generated.subagents")
        assertContains(code, "fun createToolAgentToolRegistry()")
        assertContains(code, "FetchToolToolSet")
    }

    @Test
    fun `returns null registry when no tools`() {
        val subAgent = KoogSubAgent(
            name = "SA", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
        val result = generator.generateRegistry(subAgent)
        assert(result == null)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SubAgentGeneratorTest"`

Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement SubAgentGenerator**

**Note:** The `mapModelId` method duplicates logic from `AgentGenerator`. This is intentional for now to keep generators independent. Consider extracting to a shared utility in a later cleanup if more generators need it.

Create `SubAgentGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class SubAgentGenerator {

    fun generate(subAgent: KoogSubAgent): String {
        val modelImport = subAgent.modelProvider.importPackage
        val modelRef = subAgent.modelId?.let { mapModelId(subAgent.modelProvider, it) }
            ?: subAgent.modelProvider.defaultModel
        val funcName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}"
        val hasTools = subAgent.tools.isNotEmpty() || subAgent.builtinTools.isNotEmpty()
        val registryFuncName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"

        val params = mutableListOf(
            """promptExecutor = ${subAgent.modelProvider.executorFactory}(System.getenv("${subAgent.modelProvider.name}_API_KEY"))""",
            """systemPrompt = "${subAgent.systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")}"""",
            "llmModel = $modelRef"
        )
        subAgent.temperature?.let { params.add("temperature = $it") }
        if (hasTools) {
            params.add("toolRegistry = $registryFuncName()")
        }

        val paramBlock = params.joinToString(",\n        ")

        return buildString {
            appendLine("package generated.subagents")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            if (hasTools) {
                appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            }
            appendLine("import ai.koog.prompt.executor.llms.all.${subAgent.modelProvider.executorFactory}")
            appendLine("import $modelImport")
            appendLine()
            appendLine("fun $funcName() = AIAgent(")
            appendLine("        $paramBlock")
            appendLine(")")
        }
    }

    fun generateRegistry(subAgent: KoogSubAgent): String? {
        if (subAgent.tools.isEmpty() && subAgent.builtinTools.isEmpty()) return null

        val funcName = "create${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"
        val hasUserPrompt = subAgent.builtinTools.any { it.type == BuiltinToolType.USER_PROMPT }
        val hasFilesystem = subAgent.builtinTools.any { it.type == BuiltinToolType.FILESYSTEM }
        val customBuiltins = subAgent.builtinTools.filter {
            it.type in setOf(BuiltinToolType.SHELL, BuiltinToolType.FETCH, BuiltinToolType.MEMORY, BuiltinToolType.TODO)
        }

        return buildString {
            appendLine("package generated.subagents")
            appendLine()
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            appendLine("import ai.koog.agents.core.tools.reflect.tools")
            if (subAgent.tools.isNotEmpty() || customBuiltins.isNotEmpty()) {
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
            appendLine("fun $funcName(): ToolRegistry {")
            appendLine("    return ToolRegistry {")

            for (tool in subAgent.tools) {
                val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                appendLine("        tools($className())")
            }
            for (bt in customBuiltins) {
                val reg = when (bt.type) {
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
                reg?.let { appendLine("        $it") }
            }
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

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SubAgentGeneratorTest"`

Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt
git commit -m "feat(generator): add SubAgentGenerator for sub-agent code generation"
```

---

## Task 6: RegistryGenerator — Sub-Agent Tool Registration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

Add to `RegistryGeneratorTest.kt`:

```kotlin
@Test
fun `generates registry with sub-agent as tool`() {
    val subAgents = listOf(
        KoogSubAgent(
            name = "ResearchAgent", description = "Researches topics",
            systemPrompt = "Research.", modelProvider = ModelProvider.ANTHROPIC,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "A topic to research",
            tools = emptyList(), builtinTools = emptyList()
        )
    )
    val code = generator.generate(emptyList(), emptyList(), subAgents)
    assertContains(code, "createResearchAgent()")
    assertContains(code, "asTool")
    assertContains(code, "ResearchAgent")
    assertContains(code, "Researches topics")
    assertContains(code, "A topic to research")
    assertContains(code, "import generated.subagents.*")
}

@Test
fun `generates registry with mixed tools and sub-agents`() {
    val tools = listOf(KoogTool("weatherTool", "Weather", emptyList(), null))
    val subAgents = listOf(
        KoogSubAgent(
            name = "SA", description = "Desc",
            systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
            modelId = null, temperature = null, delegationMode = "automatic",
            inputDescription = "Input",
            tools = emptyList(), builtinTools = emptyList()
        )
    )
    val code = generator.generate(tools, emptyList(), subAgents)
    assertContains(code, "WeatherToolToolSet")
    assertContains(code, "createSA()")
}
```

Add import `import com.agentwork.modeler.export.model.KoogSubAgent` to imports.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest"`

Expected: FAIL — `generate` doesn't accept `subAgents` parameter.

- [ ] **Step 3: Extend RegistryGenerator**

Change the `generate` method signature to:

```kotlin
fun generate(tools: List<KoogTool>, builtinTools: List<KoogBuiltinTool> = emptyList(), subAgents: List<KoogSubAgent> = emptyList()): String {
```

Add import: `import com.agentwork.modeler.export.model.KoogSubAgent`

Inside the `buildString`, add import for sub-agents after existing imports:

```kotlin
if (subAgents.isNotEmpty()) {
    appendLine("import generated.subagents.*")
}
```

After the filesystem built-in block (before the closing `appendLine("    }")`), add:

```kotlin
// Sub-agents as tools
for (sa in subAgents) {
    val funcName = "create${sa.name.replaceFirstChar { it.uppercase() }}"
    appendLine("""        tool(""")
    appendLine("""            $funcName().asTool(""")
    appendLine("""                agentName = "${sa.name}",""")
    appendLine("""                agentDescription = "${sa.description.replace("\"", "\\\"")}",""")
    appendLine("""                inputDescription = "${sa.inputDescription.replace("\"", "\\\"")}" """)
    appendLine("""            )""")
    appendLine("""        )""")
}
```

**Note:** The exact `asTool(...)` API must match the findings from Task 1. Adjust parameter names if needed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest"`

Expected: ALL tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt
git commit -m "feat(registry): register sub-agents as tools in ToolRegistry"
```

---

## Task 7: ExportService — Wire SubAgentGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Write failing integration test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `exports graph with sub-agent and sub-agent tools`() {
    val graph = ProjectGraph(1, "TestProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MainAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("sa1", "sub-agent", Position(200.0, 0.0), mapOf(
                "name" to "ResearchAgent", "description" to "Researches",
                "systemPrompt" to "Research.", "inputDescription" to "A topic",
                "modelProvider" to "anthropic", "delegationMode" to "automatic"
            )),
            GraphNode("t1", "tool", Position(100.0, 200.0), mapOf(
                "name" to "fetchTool", "description" to "Fetches",
                "parameters" to emptyList<Any>()
            ))
        ),
        listOf(
            GraphEdge("e1", "sa1", "a1"),
            GraphEdge("e2", "t1", "sa1")
        )
    )
    val zip = service.export(graph)
    val entries = readZipEntries(zip)

    assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/subagents/ResearchAgentSubAgent.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/subagents/ResearchAgentToolRegistry.kt"))
    assertTrue(entries.contains("src/main/kotlin/generated/tools/FetchToolToolSet.kt"))

    val registryContent = readZipFileContent(zip, "src/main/kotlin/generated/ToolRegistry.kt")
    assertContains(registryContent, "createResearchAgent()")
    assertContains(registryContent, "asTool")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest.exports graph with sub-agent and sub-agent tools"`

Expected: FAIL — sub-agent files not in ZIP.

- [ ] **Step 3: Wire SubAgentGenerator into ExportService**

In `ExportService.kt`, add constructor parameter:

```kotlin
private val subAgentGenerator: SubAgentGenerator,
```

In the `export` method, after the built-in tools block and before the registry generation, add:

```kotlin
// Sub-agents
for (subAgent in agent.subAgents) {
    val saFileName = "${subAgent.name.replaceFirstChar { it.uppercase() }}SubAgent"
    files["$pkg/subagents/$saFileName.kt"] = subAgentGenerator.generate(subAgent)

    val registryCode = subAgentGenerator.generateRegistry(subAgent)
    if (registryCode != null) {
        val regFileName = "${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"
        files["$pkg/subagents/$regFileName.kt"] = registryCode
    }

    // Generate ToolSet files for sub-agent's custom tools
    for (tool in subAgent.tools) {
        val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
        if (!files.containsKey("$pkg/tools/$className.kt")) {
            files["$pkg/tools/$className.kt"] = toolGenerator.generate(tool)
        }
    }

    // Generate ToolSet files for sub-agent's built-in tools
    for (builtinTool in subAgent.builtinTools) {
        val code = builtinToolGenerator.generate(builtinTool)
        val fileName = builtinTool.type.toFileName()
        if (code != null && fileName != null && !files.containsKey("$pkg/tools/$fileName")) {
            files["$pkg/tools/$fileName"] = code
        }
    }
}
```

Update the registry generation line to pass sub-agents:

```kotlin
if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty()) {
    files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents)
}
```

- [ ] **Step 4: Update ExportServiceTest constructor**

In `ExportServiceTest.kt`, add `subAgentGenerator = SubAgentGenerator()` to the `ExportService` constructor:

```kotlin
private val service = ExportService(
    validator = GraphValidator(),
    adapter = GraphToKoogAdapter(),
    agentGenerator = AgentGenerator(),
    toolGenerator = ToolGenerator(),
    registryGenerator = RegistryGenerator(),
    applicationGenerator = ApplicationGenerator(),
    buildGradleGenerator = BuildGradleGenerator(),
    settingsGradleGenerator = SettingsGradleGenerator(),
    gradlePropertiesGenerator = GradlePropertiesGenerator(),
    applicationPropertiesGenerator = ApplicationPropertiesGenerator(),
    dockerfileGenerator = DockerfileGenerator(),
    controllerGenerator = ControllerGenerator(),
    builtinToolGenerator = BuiltinToolGenerator(),
    subAgentGenerator = SubAgentGenerator(),
    zipBuilder = ZipBuilder()
)
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`

Expected: ALL tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt \
        src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat(export): wire SubAgentGenerator into export pipeline"
```

---

## Task 8: Frontend Types — SubAgentConfig and NodeType

**Files:**
- Create: `frontend/types/subagent.ts`
- Modify: `frontend/types/graph.ts`
- Modify: `frontend/types/koog.ts`

- [ ] **Step 1: Create SubAgentConfig interface**

Create `frontend/types/subagent.ts`:

```typescript
import type { ModelProvider } from "./koog";

export type DelegationMode = "automatic" | "manual" | "fallback" | "specialized";

export interface SubAgentConfig {
  name: string;
  description: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  delegationMode: DelegationMode;
  inputDescription: string;
}
```

- [ ] **Step 2: Extend NodeType and GraphNode**

In `frontend/types/graph.ts`, change:

```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent";
```

Update the `GraphNode` data union:

```typescript
import { AgentConfig, ToolConfig } from "./koog";
import { SubAgentConfig } from "./subagent";

// ... existing code ...

export interface GraphNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig;
}
```

- [ ] **Step 3: Re-export from koog.ts**

Add at the bottom of `frontend/types/koog.ts`:

```typescript
export type { SubAgentConfig, DelegationMode } from "./subagent";
```

- [ ] **Step 4: Commit**

```bash
git add frontend/types/subagent.ts frontend/types/graph.ts frontend/types/koog.ts
git commit -m "feat(types): add SubAgentConfig and extend NodeType with sub-agent"
```

---

## Task 9: Zustand Store — Sub-Agent Defaults

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Add sub-agent defaults and update addNode**

Add import at top:

```typescript
import { SubAgentConfig } from "@/types/subagent";
```

Update the `EditorState` interface `updateNodeData` signature:

```typescript
updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig>) => void;
```

Add default data after `builtinToolDefaults`:

```typescript
const defaultSubAgentData: SubAgentConfig = {
  name: "",
  description: "",
  systemPrompt: "",
  modelProvider: "openai",
  delegationMode: "automatic",
  inputDescription: "",
};
```

Update the `addNode` function — add an `else if` before the final `else`:

```typescript
addNode: (type, position, toolType?) =>
  set((state) => {
    let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig;
    if (type === "agent") {
      data = { ...defaultAgentData };
    } else if (type === "sub-agent") {
      data = { ...defaultSubAgentData };
    } else if (type === "builtin-tool" && toolType) {
      data = { ...builtinToolDefaults[toolType] };
    } else {
      data = { ...defaultToolData };
    }
    return {
      nodes: [
        ...state.nodes,
        { id: `node-${nextId++}`, type, position, data },
      ],
    };
  }),
```

- [ ] **Step 2: Verify frontend compiles**

Run: `cd frontend && npx next build` (or `npx tsc --noEmit`)

Expected: No type errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat(store): add sub-agent defaults to editorStore"
```

---

## Task 10: Rete Node Class — SubAgentReteNode

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`

- [ ] **Step 1: Add SubAgentReteNode class**

Add after `BuiltinToolReteNode`:

```typescript
export class SubAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Sub-Agent");
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}
```

Update the `AppNode` type:

```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/rete-nodes.ts
git commit -m "feat(rete): add SubAgentReteNode with input and output ports"
```

---

## Task 11: Connection Validation — Rete Setup

**Files:**
- Modify: `frontend/lib/rete-setup.ts`

- [ ] **Step 1: Update connection validation**

Add `SubAgentReteNode` to imports:

```typescript
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
  SubAgentReteNode,
} from "./rete-nodes";
```

Replace the connection validation pipe:

```typescript
// Connection validation:
// - Tool/BuiltinTool output -> Agent or SubAgent input
// - SubAgent output -> Agent input (NOT SubAgent)
editor.addPipe((context) => {
  if (context.type === "connectioncreate") {
    const { source, target } = context.data;
    const sourceNode = editor.getNode(source);
    const targetNode = editor.getNode(target);

    const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
    const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
    const isAgentTarget = targetNode instanceof AgentReteNode;
    const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

    // Tool/Builtin -> Agent or SubAgent: OK
    if (isToolSource && (isAgentTarget || isSubAgentTarget)) {
      return context;
    }
    // SubAgent -> Agent: OK
    if (isSubAgentSource && isAgentTarget) {
      return context;
    }
    // Everything else: blocked
    return undefined;
  }
  return context;
});
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/rete-setup.ts
git commit -m "feat(rete): update connection rules for sub-agent nodes"
```

---

## Task 12: Canvas Sync — Sub-Agent Nodes and Connections

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`

- [ ] **Step 1: Update imports**

Add `SubAgentReteNode` to rete-nodes import:

```typescript
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode } from "@/lib/rete-nodes";
```

- [ ] **Step 2: Update Zustand → Rete node creation**

In the `useEditorStore.subscribe` callback, update the node creation block. Replace the `if/else` chain inside `for (const node of state.nodes)`:

```typescript
if (node.type === "agent") {
  reteNode = new AgentReteNode(node.id, (node.data as any).name || "Agent");
} else if (node.type === "sub-agent") {
  reteNode = new SubAgentReteNode(node.id, (node.data as any).name || "Sub-Agent");
} else if (node.type === "builtin-tool") {
  const toolType = (node.data as any).toolType || "tool";
  const label = BUILTIN_TOOL_LABELS[toolType as BuiltinToolType] || toolType;
  reteNode = new BuiltinToolReteNode(node.id, label);
} else {
  reteNode = new ToolReteNode(node.id, (node.data as any).name || "Tool");
}
```

- [ ] **Step 3: Update Rete → Zustand connection created**

Replace the `connectioncreated` pipe to handle sub-agent connections:

```typescript
editor.addPipe((context) => {
  if (context.type === "connectioncreated") {
    const conn = context.data;
    const sourceNode = editor.getNode(conn.source);
    const targetNode = editor.getNode(conn.target);
    const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
    const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
    const isAgentTarget = targetNode instanceof AgentReteNode;
    const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

    if ((isToolSource || isSubAgentSource) && (isAgentTarget || isSubAgentTarget)) {
      syncing = true;
      const sourceStoreId = (sourceNode as any).storeNodeId;
      const targetStoreId = (targetNode as any).storeNodeId;
      useEditorStore.getState().addEdge(sourceStoreId, targetStoreId);
      syncing = false;
    }
  }
  return context;
});
```

- [ ] **Step 4: Update Rete → Zustand connection removed**

Replace the `connectionremoved` pipe similarly:

```typescript
editor.addPipe((context) => {
  if (context.type === "connectionremoved") {
    const conn = context.data;
    const sourceNode = editor.getNode(conn.source);
    const targetNode = editor.getNode(conn.target);
    const isToolSource = sourceNode instanceof ToolReteNode || sourceNode instanceof BuiltinToolReteNode;
    const isSubAgentSource = sourceNode instanceof SubAgentReteNode;
    const isAgentTarget = targetNode instanceof AgentReteNode;
    const isSubAgentTarget = targetNode instanceof SubAgentReteNode;

    if ((isToolSource || isSubAgentSource) && (isAgentTarget || isSubAgentTarget)) {
      syncing = true;
      const sourceStoreId = (sourceNode as any).storeNodeId;
      const targetStoreId = (targetNode as any).storeNodeId;
      const edges = useEditorStore.getState().edges;
      const edge = edges.find(
        (e) => e.source === sourceStoreId && e.target === targetStoreId
      );
      if (edge) useEditorStore.getState().removeEdge(edge.id);
      syncing = false;
    }
  }
  return context;
});
```

- [ ] **Step 5: Update label sync**

In the label update loop, add sub-agent handling. The existing code uses `node.type === "builtin-tool"` for special label logic. Sub-agents use `name` like agents/tools, so the existing `else` branch handles it. No change needed here — the default branch `(node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1)` will produce "Sub-agent" as fallback, which is fine.

- [ ] **Step 6: Add dashed connection styling for sub-agent links**

Sub-Agent → Agent connections should render with dashed lines to visually distinguish delegation from tool-bindings (per spec). In Rete.js, connections can be styled via the React render plugin. Add a custom connection component or use the connection plugin's path customizer to apply `stroke-dasharray: "6,3"` when the source node is a `SubAgentReteNode`. The exact approach depends on Rete.js plugin capabilities — check `rete-react-plugin` for connection rendering customization.

**Fallback approach:** If Rete.js does not support per-connection styling easily, add a CSS rule targeting connections where the source is a sub-agent node (using a data attribute or class on the SVG path).

- [ ] **Step 7: Commit**

```bash
git add frontend/components/editor/Canvas.tsx
git commit -m "feat(canvas): sync sub-agent nodes and connections with Zustand"
```

---

## Task 13: Frontend Validation — Sub-Agent Rules

**Files:**
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Add sub-agent validation**

Add import:

```typescript
import { SubAgentConfig } from "@/types/subagent";
```

Add after the `builtin-tool` block in `validateGraph`:

```typescript
if (node.type === "sub-agent") {
  const data = node.data as SubAgentConfig;
  if (!data.name.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a name" });
  }
  if (!data.description.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a description" });
  }
  if (!data.systemPrompt.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires a system prompt" });
  }
  if (!data.inputDescription.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent requires an input description" });
  }

  const isConnected = graph.edges.some((e) => e.source === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent must be connected to the main Agent" });
  }

  // Check not connected to another sub-agent
  const targets = graph.edges
    .filter((e) => e.source === node.id)
    .map((e) => graph.nodes.find((n) => n.id === e.target));
  for (const target of targets) {
    if (target?.type === "sub-agent") {
      errors.push({ nodeId: node.id, severity: "error", message: "Sub-Agent cannot delegate to another Sub-Agent" });
    }
  }

  // Warning: sub-agent without tools
  const hasTools = graph.edges.some(
    (e) => e.target === node.id && graph.nodes.some((n) => n.id === e.source && (n.type === "tool" || n.type === "builtin-tool"))
  );
  if (!hasTools) {
    errors.push({ nodeId: node.id, severity: "warning", message: "Sub-Agent has no tools — consider adding tools for specialization" });
  }
}

// Check: tool connected to multiple agents/sub-agents
if (node.type === "tool" || node.type === "builtin-tool") {
  const connectedTargets = graph.edges
    .filter((e) => e.source === node.id)
    .map((e) => graph.nodes.find((n) => n.id === e.target))
    .filter((n) => n?.type === "agent" || n?.type === "sub-agent");
  if (connectedTargets.length > 1) {
    errors.push({ nodeId: node.id, severity: "error", message: "Tool can only be connected to one Agent or Sub-Agent" });
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/validation.ts
git commit -m "feat(validation): add frontend sub-agent validation rules"
```

---

## Task 14: SubAgentProperties Panel

**Files:**
- Create: `frontend/components/panels/SubAgentProperties.tsx`

- [ ] **Step 1: Create SubAgentProperties component**

Create `frontend/components/panels/SubAgentProperties.tsx`:

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { SubAgentConfig, DelegationMode } from "@/types/subagent";
import type { ModelProvider } from "@/types/koog";

export function SubAgentProperties({ nodeId, data }: { nodeId: string; data: SubAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<SubAgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="sa-name">Name</Label>
        <Input id="sa-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="ResearchAgent" />
      </div>
      <div>
        <Label htmlFor="sa-description">Description</Label>
        <Textarea id="sa-description" value={data.description} onChange={(e) => update({ description: e.target.value })} placeholder="Describes what this Sub-Agent can do" rows={2} />
      </div>
      <div>
        <Label htmlFor="sa-prompt">System Prompt</Label>
        <Textarea id="sa-prompt" value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} placeholder="You are a specialist in..." rows={4} />
      </div>
      <div>
        <Label htmlFor="sa-input-desc">Input Description</Label>
        <Input id="sa-input-desc" value={data.inputDescription} onChange={(e) => update({ inputDescription: e.target.value })} placeholder="What does this Sub-Agent expect as input?" />
      </div>
      <div>
        <Label>Model Provider</Label>
        <Select value={data.modelProvider} onValueChange={(v) => update({ modelProvider: v as ModelProvider })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="openai">OpenAI</SelectItem>
            <SelectItem value="anthropic">Anthropic</SelectItem>
            <SelectItem value="google">Google</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label htmlFor="sa-model-id">Model ID (optional)</Label>
        <Input id="sa-model-id" value={data.modelId || ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="gpt-4o" />
      </div>
      <div>
        <Label htmlFor="sa-temperature">Temperature (optional)</Label>
        <Input id="sa-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
      <div>
        <Label>Delegation Mode</Label>
        <Select value={data.delegationMode} onValueChange={(v) => update({ delegationMode: v as DelegationMode })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="automatic">Automatic</SelectItem>
            <SelectItem value="manual" disabled>Manual (coming soon)</SelectItem>
            <SelectItem value="fallback" disabled>Fallback (coming soon)</SelectItem>
            <SelectItem value="specialized" disabled>Specialized (coming soon)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/components/panels/SubAgentProperties.tsx
git commit -m "feat(ui): add SubAgentProperties panel"
```

---

## Task 15: PropertiesPanel Routing + Toolbar Button

**Files:**
- Modify: `frontend/components/panels/PropertiesPanel.tsx`
- Modify: `frontend/components/panels/Toolbar.tsx`

- [ ] **Step 1: Update PropertiesPanel**

Add imports:

```typescript
import { SubAgentProperties } from "./SubAgentProperties";
import { SubAgentConfig } from "@/types/subagent";
```

Update the title rendering to handle sub-agent:

```tsx
<h3 className="font-semibold mb-4 capitalize">
  {selectedNode.type === "builtin-tool" ? "Built-in Tool" :
   selectedNode.type === "sub-agent" ? "Sub-Agent" :
   selectedNode.type} Properties
</h3>
```

Add rendering branch after the `builtin-tool` block:

```tsx
{selectedNode.type === "sub-agent" && (
  <SubAgentProperties nodeId={selectedNode.id} data={selectedNode.data as SubAgentConfig} />
)}
```

- [ ] **Step 2: Update Toolbar**

In `Toolbar.tsx`, add a "+Sub-Agent" button after the Tool dropdown div:

```tsx
<Button variant="outline" size="sm" onClick={() => addNode("sub-agent", { x: 200, y: 200 })}>
  + Sub-Agent
</Button>
```

- [ ] **Step 3: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/components/panels/PropertiesPanel.tsx \
        frontend/components/panels/Toolbar.tsx
git commit -m "feat(ui): wire SubAgentProperties panel and add toolbar button"
```

---

## Task 16: Manual Testing

- [ ] **Step 1: Start frontend and backend**

Run backend: `./gradlew bootRun`
Run frontend: `cd frontend && npm run dev`

- [ ] **Step 2: Test scenario: Create sub-agent**

1. Click "+ Sub-Agent" in toolbar
2. Verify purple-styled node appears on canvas
3. Click it — verify SubAgentProperties panel shows with all 8 fields
4. Fill in name, description, system prompt, input description
5. Select model provider and delegation mode (verify only "automatic" is selectable)

- [ ] **Step 3: Test scenario: Connect sub-agent to agent**

1. Create Agent node
2. Drag connection from Sub-Agent output to Agent input — should work
3. Try connecting Sub-Agent to Sub-Agent — should be blocked
4. Try connecting Agent to Sub-Agent — should be blocked

- [ ] **Step 4: Test scenario: Attach tools to sub-agent**

1. Create a Tool node
2. Connect it to the Sub-Agent input — should work
3. Validate — should pass (no errors)

- [ ] **Step 5: Test scenario: Validation**

1. Create Sub-Agent with empty name — validate — expect error
2. Create Sub-Agent without connection — validate — expect error
3. Create Sub-Agent without tools — validate — expect warning

- [ ] **Step 6: Test scenario: Export**

1. Create: Agent + Sub-Agent (with tool) connected
2. Fill in all required fields
3. Export — download ZIP
4. Verify ZIP contains `subagents/` folder with SubAgent.kt and ToolRegistry.kt
5. Verify main ToolRegistry.kt contains `asTool` registration

---

## Task 17: Final Cleanup and All Tests

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`

Expected: ALL tests PASS.

- [ ] **Step 2: Run frontend type check**

Run: `cd frontend && npx tsc --noEmit`

Expected: No errors.

- [ ] **Step 3: Final commit**

If any fixes were needed during manual testing:

```bash
git add -A
git commit -m "fix: address issues found during manual testing of sub-agent feature"
```
