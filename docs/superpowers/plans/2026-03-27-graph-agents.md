# Graph Agents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Graph Agent support — a state-machine-based agent type with visual sub-canvas modeling, validation, and Kotlin code generation targeting the Koog `strategy { }` DSL.

**Architecture:** Graph Agent is a new node type on the main canvas that opens a sub-canvas (fullscreen overlay) for editing an internal strategy graph. The strategy data (nodes/edges) lives inside `GraphAgentConfig` in the existing `editorStore`. Backend adds a new domain model, adapter method, validator block, and generator. The generated code produces a `strategy<String, String> { }` block with Koog DSL nodes and edges.

**Tech Stack:** Next.js (frontend), Rete.js (graph editor), Zustand (state), Spring Boot + Kotlin (backend), JUnit 5 (tests)

---

### Task 1: Backend Domain Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt`

- [ ] **Step 1: Create GraphAgentModel.kt**

```kotlin
package com.agentwork.modeler.export.model

data class KoogGraphAgent(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val strategyNodes: List<KoogStrategyNode>,
    val strategyEdges: List<KoogStrategyEdge>,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>,
    val subAgents: List<KoogSubAgent>,
    val mcpServers: List<KoogMcpServer>,
    val inputs: List<KoogInput>,
    val outputs: List<KoogOutput>
)

data class KoogStrategyNode(
    val id: String,
    val type: String,
    val label: String?,
    val toolName: String?,
    val condition: String?,
    val temperature: Double?
)

data class KoogStrategyEdge(
    val source: String,
    val target: String,
    val edgeType: String,
    val condition: String?,
    val transformation: String?
)
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt
git commit -m "feat(graph-agent): add backend domain model"
```

---

### Task 2: Backend GraphToKoogAdapter — convertGraphAgents()

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/GraphToKoogAdapterGraphAgentTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphToKoogAdapterGraphAgentTest {

    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts graph-agent node with strategy nodes and edges`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "SearchAgent",
                    "description" to "A search agent",
                    "systemPrompt" to "You are a search agent",
                    "modelProvider" to "anthropic",
                    "modelId" to "claude-sonnet-4",
                    "temperature" to 0.7,
                    "strategyNodes" to listOf(
                        mapOf("id" to "sn1", "type" to "node-start"),
                        mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llmRequest"),
                        mapOf("id" to "sn3", "type" to "node-execute-tool", "label" to "executeTool", "toolName" to "searchTool"),
                        mapOf("id" to "sn4", "type" to "node-llm-send-tool-result", "label" to "sendResult"),
                        mapOf("id" to "sn5", "type" to "node-finish")
                    ),
                    "strategyEdges" to listOf(
                        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
                        mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onToolCall"),
                        mapOf("source" to "sn2", "target" to "sn5", "edgeType" to "onAssistantMessage"),
                        mapOf("source" to "sn3", "target" to "sn4", "edgeType" to "onCondition"),
                        mapOf("source" to "sn4", "target" to "sn2", "edgeType" to "onCondition")
                    )
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool", "description" to "Searches the web",
                    "parameters" to listOf(mapOf("name" to "query", "type" to "String", "description" to "Search query", "required" to true))
                ))
            ),
            listOf(GraphEdge("e1", "t1", "ga1"))
        )

        val result = adapter.convertGraphAgents(graph)
        assertEquals(1, result.size)

        val agent = result.first()
        assertEquals("SearchAgent", agent.name)
        assertEquals("You are a search agent", agent.systemPrompt)
        assertEquals(ModelProvider.ANTHROPIC, agent.modelProvider)
        assertEquals(5, agent.strategyNodes.size)
        assertEquals(5, agent.strategyEdges.size)
        assertEquals(1, agent.tools.size)
        assertEquals("searchTool", agent.tools.first().name)

        val llmNode = agent.strategyNodes.find { it.type == "node-llm-request" }!!
        assertEquals("llmRequest", llmNode.label)

        val toolEdge = agent.strategyEdges.find { it.edgeType == "onToolCall" }!!
        assertEquals("sn2", toolEdge.source)
        assertEquals("sn3", toolEdge.target)
    }

    @Test
    fun `converts graph-agent with inputs and outputs`() {
        val graph = WorkspaceGraph(1, "TestProject",
            listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyAgent",
                    "description" to "",
                    "systemPrompt" to "Help",
                    "modelProvider" to "openai",
                    "strategyNodes" to listOf(
                        mapOf("id" to "sn1", "type" to "node-start"),
                        mapOf("id" to "sn2", "type" to "node-finish")
                    ),
                    "strategyEdges" to listOf(
                        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition")
                    )
                )),
                GraphNode("i1", "input", Position(-100.0, 0.0), mapOf("name" to "query", "description" to "User query", "dataType" to "string", "required" to true)),
                GraphNode("o1", "output", Position(300.0, 0.0), mapOf("name" to "result", "description" to "Result", "dataType" to "string"))
            ),
            listOf(
                GraphEdge("e1", "i1", "ga1"),
                GraphEdge("e2", "ga1", "o1")
            )
        )

        val result = adapter.convertGraphAgents(graph)
        assertEquals(1, result.size)
        assertEquals(1, result.first().inputs.size)
        assertEquals("query", result.first().inputs.first().name)
        assertEquals(1, result.first().outputs.size)
        assertEquals("result", result.first().outputs.first().name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphToKoogAdapterGraphAgentTest" --info`
Expected: Compilation error — `convertGraphAgents` does not exist

- [ ] **Step 3: Implement convertGraphAgents in GraphToKoogAdapter**

Add this method to `GraphToKoogAdapter.kt` after the `convertPlannerAgents` method (after line 306):

```kotlin
fun convertGraphAgents(graph: WorkspaceGraph): List<KoogGraphAgent> {
    val toolNodes = graph.nodes.filter { it.type == "tool" }
    val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
    val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
    val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
    val inputNodes = graph.nodes.filter { it.type == "input" }
    val outputNodes = graph.nodes.filter { it.type == "output" }

    return graph.nodes.filter { it.type == "graph-agent" }.map { gaNode ->
        val connectedIds = graph.edges
            .filter { it.target == gaNode.id }
            .map { it.source }

        val tools = toolNodes.filter { it.id in connectedIds }.map { convertTool(it) }
        val builtinTools = builtinToolNodes.filter { it.id in connectedIds }.map { convertBuiltinTool(it) }
        val subAgents = subAgentNodes.filter { it.id in connectedIds }
            .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes) }
        val mcpServers = mcpServerNodes.filter { it.id in connectedIds }.map { convertMcpServer(it) }
        val inputs = inputNodes.filter { it.id in connectedIds }.map { convertInput(it) }

        val connectedFromGa = graph.edges.filter { it.source == gaNode.id }.map { it.target }
        val outputs = outputNodes.filter { it.id in connectedFromGa }.map { convertOutput(it) }

        @Suppress("UNCHECKED_CAST")
        val strategyNodeMaps = gaNode.data["strategyNodes"] as? List<Map<String, Any?>> ?: emptyList()
        val strategyNodes = strategyNodeMaps.map { snMap ->
            KoogStrategyNode(
                id = snMap["id"] as? String ?: "",
                type = snMap["type"] as? String ?: "",
                label = snMap["label"] as? String,
                toolName = snMap["toolName"] as? String,
                condition = snMap["condition"] as? String,
                temperature = (snMap["temperature"] as? Number)?.toDouble()
            )
        }

        @Suppress("UNCHECKED_CAST")
        val strategyEdgeMaps = gaNode.data["strategyEdges"] as? List<Map<String, Any?>> ?: emptyList()
        val strategyEdges = strategyEdgeMaps.map { seMap ->
            KoogStrategyEdge(
                source = seMap["source"] as? String ?: "",
                target = seMap["target"] as? String ?: "",
                edgeType = seMap["edgeType"] as? String ?: "",
                condition = seMap["condition"] as? String,
                transformation = seMap["transformation"] as? String
            )
        }

        KoogGraphAgent(
            name = gaNode.data["name"] as? String ?: "",
            description = gaNode.data["description"] as? String ?: "",
            systemPrompt = gaNode.data["systemPrompt"] as? String ?: "",
            modelProvider = parseProvider(gaNode.data["modelProvider"] as? String ?: "openai"),
            modelId = gaNode.data["modelId"] as? String,
            temperature = (gaNode.data["temperature"] as? Number)?.toDouble(),
            strategyNodes = strategyNodes,
            strategyEdges = strategyEdges,
            tools = tools,
            builtinTools = builtinTools,
            subAgents = subAgents,
            mcpServers = mcpServers,
            inputs = inputs,
            outputs = outputs
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphToKoogAdapterGraphAgentTest" --info`
Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/test/kotlin/com/agentwork/modeler/export/GraphToKoogAdapterGraphAgentTest.kt
git commit -m "feat(graph-agent): add adapter convertGraphAgents method"
```

---

### Task 3: Backend GraphValidator — graph-agent validation

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/GraphValidatorGraphAgentTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.validation.GraphValidator
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GraphValidatorGraphAgentTest {

    private val validator = GraphValidator()

    private fun validStrategyNodes() = listOf(
        mapOf("id" to "sn1", "type" to "node-start"),
        mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llmRequest"),
        mapOf("id" to "sn3", "type" to "node-finish")
    )

    private fun validStrategyEdges() = listOf(
        mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
        mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onAssistantMessage")
    )

    private fun graphAgentNode(overrides: Map<String, Any?> = emptyMap()): GraphNode {
        val data = mapOf(
            "name" to "TestAgent",
            "description" to "A test agent",
            "systemPrompt" to "Help the user",
            "modelProvider" to "openai",
            "strategyNodes" to validStrategyNodes(),
            "strategyEdges" to validStrategyEdges()
        ) + overrides
        return GraphNode("ga1", "graph-agent", Position(0.0, 0.0), data)
    }

    @Test
    fun `valid graph-agent passes validation`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode()), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `graph-agent without name fails`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("name" to ""))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("requires a name") })
    }

    @Test
    fun `graph-agent without system prompt fails`() {
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("systemPrompt" to ""))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("requires a system prompt") })
    }

    @Test
    fun `graph-agent without node-start fails`() {
        val noStart = listOf(
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onAssistantMessage"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to noStart, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("exactly one node-start") })
    }

    @Test
    fun `graph-agent without node-finish fails`() {
        val noFinish = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm")
        )
        val edges = listOf(mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to noFinish, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("exactly one node-finish") })
    }

    @Test
    fun `graph-agent start without outgoing edge fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-finish")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to emptyList<Any>()))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-start must have at least one outgoing edge") })
    }

    @Test
    fun `graph-agent finish not reachable from start fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-llm-request", "label" to "llm"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        // Edge from start to llm, but no path to finish
        val edges = listOf(mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"))
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-finish must be reachable") })
    }

    @Test
    fun `graph-agent node-execute-tool without toolName fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-execute-tool", "label" to "exec"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-execute-tool requires a toolName") })
    }

    @Test
    fun `graph-agent node-condition without condition fails`() {
        val nodes = listOf(
            mapOf("id" to "sn1", "type" to "node-start"),
            mapOf("id" to "sn2", "type" to "node-condition", "label" to "check"),
            mapOf("id" to "sn3", "type" to "node-finish")
        )
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition", "condition" to "true")
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyNodes" to nodes, "strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("node-condition requires a condition") })
    }

    @Test
    fun `graph-agent onCondition edge without condition fails`() {
        val nodes = validStrategyNodes()
        val edges = listOf(
            mapOf("source" to "sn1", "target" to "sn2", "edgeType" to "onCondition"),
            mapOf("source" to "sn2", "target" to "sn3", "edgeType" to "onCondition") // missing condition
        )
        val graph = WorkspaceGraph(1, "Test", listOf(graphAgentNode(mapOf("strategyEdges" to edges))), emptyList())
        val errors = validator.validate(graph)
        // onCondition from non-start nodes requires a condition expression
        assertTrue(errors.any { it.contains("onCondition edge requires a condition") })
    }

    @Test
    fun `cannot mix graph-agent with regular agent`() {
        val graph = WorkspaceGraph(1, "Test", listOf(
            graphAgentNode(),
            GraphNode("a1", "agent", Position(200.0, 0.0), mapOf("name" to "Regular", "systemPrompt" to "Hi", "modelProvider" to "openai"))
        ), emptyList())
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("cannot have both") || it.contains("cannot mix") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphValidatorGraphAgentTest" --info`
Expected: FAIL — graph-agent not handled in validator

- [ ] **Step 3: Implement graph-agent validation in GraphValidator**

In `GraphValidator.kt`, update the top-level count checks (replace lines 10-23) to include graph-agent:

```kotlin
val agentCount = graph.nodes.count { it.type == "agent" }
val plannerCount = graph.nodes.count { it.type == "planner-agent" }
val graphAgentCount = graph.nodes.count { it.type == "graph-agent" }
val totalAgentTypes = listOf(agentCount, plannerCount, graphAgentCount).count { it > 0 }
if (agentCount == 0 && plannerCount == 0 && graphAgentCount == 0) {
    errors.add("Workspace must have at least one agent, planner-agent, or graph-agent")
}
if (totalAgentTypes > 1) {
    errors.add("Workspace cannot mix different agent types (agent, planner-agent, graph-agent)")
}
if (agentCount > 1) {
    errors.add("Only single-agent workspaces are supported for export")
}
if (plannerCount > 1) {
    errors.add("Workspace can have at most one planner-agent")
}
if (graphAgentCount > 1) {
    errors.add("Workspace can have at most one graph-agent")
}
```

Add a new case in the `when` block (after the `"output"` case, before the closing `}`):

```kotlin
"graph-agent" -> {
    val name = node.data["name"] as? String ?: ""
    val prompt = node.data["systemPrompt"] as? String ?: ""
    if (name.isBlank()) errors.add("[${node.id}] Graph agent requires a name")
    if (prompt.isBlank()) errors.add("[${node.id}] Graph agent requires a system prompt")

    @Suppress("UNCHECKED_CAST")
    val strategyNodes = node.data["strategyNodes"] as? List<Map<String, Any?>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val strategyEdges = node.data["strategyEdges"] as? List<Map<String, Any?>> ?: emptyList()

    val startCount = strategyNodes.count { it["type"] == "node-start" }
    val finishCount = strategyNodes.count { it["type"] == "node-finish" }
    if (startCount != 1) errors.add("[${node.id}] Graph agent must have exactly one node-start")
    if (finishCount != 1) errors.add("[${node.id}] Graph agent must have exactly one node-finish")

    val startId = strategyNodes.find { it["type"] == "node-start" }?.get("id") as? String
    val finishId = strategyNodes.find { it["type"] == "node-finish" }?.get("id") as? String

    if (startId != null) {
        val startOutgoing = strategyEdges.filter { it["source"] == startId }
        if (startOutgoing.isEmpty()) errors.add("[${node.id}] node-start must have at least one outgoing edge")
    }

    // Reachability check: BFS from start to finish
    if (startId != null && finishId != null) {
        val adjacency = mutableMapOf<String, MutableList<String>>()
        for (se in strategyEdges) {
            val src = se["source"] as? String ?: continue
            val tgt = se["target"] as? String ?: continue
            adjacency.getOrPut(src) { mutableListOf() }.add(tgt)
        }
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)
            adjacency[current]?.forEach { queue.add(it) }
        }
        if (finishId !in visited) {
            errors.add("[${node.id}] node-finish must be reachable from node-start")
        }
    }

    // Per-strategy-node validation
    for (sn in strategyNodes) {
        val snType = sn["type"] as? String ?: ""
        when (snType) {
            "node-execute-tool" -> {
                val toolName = sn["toolName"] as? String ?: ""
                if (toolName.isBlank()) errors.add("[${node.id}] node-execute-tool requires a toolName")
            }
            "node-condition" -> {
                val condition = sn["condition"] as? String ?: ""
                if (condition.isBlank()) errors.add("[${node.id}] node-condition requires a condition expression")
            }
        }
    }

    // Edge validation
    for (se in strategyEdges) {
        val edgeType = se["edgeType"] as? String ?: ""
        if (edgeType.isBlank()) errors.add("[${node.id}] Strategy edge requires an edgeType")
        if (edgeType == "onCondition") {
            val source = se["source"] as? String ?: ""
            // onCondition from node-start is OK without explicit condition (always-forward)
            if (source != startId) {
                val condition = se["condition"] as? String ?: ""
                if (condition.isBlank()) errors.add("[${node.id}] onCondition edge requires a condition expression")
            }
        }
    }
}
```

Also add `"graph-agent"` to the `agentTypes` set on the duplicate input/output name check (line ~201):

```kotlin
val agentTypes = setOf("agent", "sub-agent", "planner-agent", "graph-agent")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphValidatorGraphAgentTest" --info`
Expected: All tests PASSED

- [ ] **Step 5: Run all existing tests to check for regressions**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --info`
Expected: All previously passing tests still pass

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/GraphValidatorGraphAgentTest.kt
git commit -m "feat(graph-agent): add backend validation for graph-agent nodes"
```

---

### Task 4: Backend GraphAgentGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.generator.GraphAgentGenerator
import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class GraphAgentGeneratorTest {

    private val generator = GraphAgentGenerator()

    @Test
    fun `generates strategy with tool-calling loop`() {
        val agent = KoogGraphAgent(
            name = "searchAgent",
            description = "A search agent",
            systemPrompt = "Help",
            modelProvider = ModelProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            temperature = 0.7,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-llm-request", "llmRequest", null, null, null),
                KoogStrategyNode("sn3", "node-execute-tool", "executeTool", "searchTool", null, null),
                KoogStrategyNode("sn4", "node-llm-send-tool-result", "sendResult", null, null, null),
                KoogStrategyNode("sn5", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onToolCall", null, null),
                KoogStrategyEdge("sn2", "sn5", "onAssistantMessage", null, null),
                KoogStrategyEdge("sn3", "sn4", "onCondition", null, null),
                KoogStrategyEdge("sn4", "sn2", "onCondition", null, null)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)

        assertContains(code, "fun createSearchAgentStrategy()")
        assertContains(code, "strategy<String, String>(\"searchAgent\")")
        assertContains(code, "val llmRequest by nodeLLMRequest()")
        assertContains(code, "val executeTool by nodeExecuteTool()")
        assertContains(code, "val sendResult by nodeLLMSendToolResult()")
        assertContains(code, "edge(nodeStart forwardTo llmRequest)")
        assertContains(code, "edge(llmRequest forwardTo executeTool onToolCall { true })")
        assertContains(code, "edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })")
        assertContains(code, "edge(executeTool forwardTo sendResult)")
        assertContains(code, "edge(sendResult forwardTo llmRequest)")
        // node-start and node-finish should NOT have val declarations
        assertFalse(code.contains("val nodeStart"))
        assertFalse(code.contains("val nodeFinish"))
    }

    @Test
    fun `generates condition node and onCondition edge with expression`() {
        val agent = KoogGraphAgent(
            name = "condAgent",
            description = "",
            systemPrompt = "Help",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-condition", "checkResult", null, "result.isNotEmpty()", null),
                KoogStrategyNode("sn3", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onCondition", "result.isNotEmpty()", null)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "val checkResult by nodeCondition(\"result.isNotEmpty()\")")
        assertContains(code, "edge(checkResult forwardTo nodeFinish onCondition { result.isNotEmpty() })")
    }

    @Test
    fun `generates onCondition edge with transformation`() {
        val agent = KoogGraphAgent(
            name = "transformAgent",
            description = "",
            systemPrompt = "Help",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("sn1", "node-start", null, null, null, null),
                KoogStrategyNode("sn2", "node-llm-request", "llm", null, null, null),
                KoogStrategyNode("sn3", "node-finish", null, null, null, null)
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("sn1", "sn2", "onCondition", null, null),
                KoogStrategyEdge("sn2", "sn3", "onCondition", "true", "it.uppercase()")
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            subAgents = emptyList(),
            mcpServers = emptyList(),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "onCondition { true } transformed { it.uppercase() }")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphAgentGeneratorTest" --info`
Expected: Compilation error — `GraphAgentGenerator` does not exist

- [ ] **Step 3: Implement GraphAgentGenerator**

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogStrategyEdge
import org.springframework.stereotype.Component

@Component
class GraphAgentGenerator {

    fun generate(agent: KoogGraphAgent): String {
        val funcName = "create${agent.name.replaceFirstChar { it.uppercase() }}Strategy"
        val nodeMap = agent.strategyNodes.associateBy { it.id }

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.dsl.builder.strategy")
            appendLine("import ai.koog.agents.core.dsl.builder.forwardTo")
            appendLine()
            appendLine("fun $funcName() = strategy<String, String>(\"${agent.name}\") {")

            // Node declarations (skip start and finish — they are implicit)
            for (sn in agent.strategyNodes) {
                when (sn.type) {
                    "node-start", "node-finish" -> {} // implicit
                    "node-llm-request" -> appendLine("    val ${sn.label} by nodeLLMRequest()")
                    "node-execute-tool" -> appendLine("    val ${sn.label} by nodeExecuteTool()")
                    "node-llm-send-tool-result" -> appendLine("    val ${sn.label} by nodeLLMSendToolResult()")
                    "node-condition" -> appendLine("    val ${sn.label} by nodeCondition(\"${sn.condition}\")")
                }
            }

            appendLine()

            // Edge declarations
            for (se in agent.strategyEdges) {
                val sourceRef = resolveNodeRef(se.source, nodeMap)
                val targetRef = resolveNodeRef(se.target, nodeMap)
                val edgeSuffix = formatEdgeSuffix(se)
                appendLine("    edge($sourceRef forwardTo $targetRef$edgeSuffix)")
            }

            appendLine("}")
        }
    }

    private fun resolveNodeRef(nodeId: String, nodeMap: Map<String, com.agentwork.modeler.export.model.KoogStrategyNode>): String {
        val node = nodeMap[nodeId] ?: return nodeId
        return when (node.type) {
            "node-start" -> "nodeStart"
            "node-finish" -> "nodeFinish"
            else -> node.label ?: nodeId
        }
    }

    private fun formatEdgeSuffix(edge: KoogStrategyEdge): String {
        return when (edge.edgeType) {
            "onToolCall" -> " onToolCall { true }"
            "onAssistantMessage" -> " onAssistantMessage { true }"
            "onCondition" -> {
                val cond = edge.condition
                val trans = edge.transformation
                if (cond != null && trans != null) {
                    " onCondition { $cond } transformed { $trans }"
                } else if (cond != null) {
                    " onCondition { $cond }"
                } else {
                    "" // no condition = simple forward (e.g. from start)
                }
            }
            else -> ""
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "com.agentwork.modeler.export.GraphAgentGeneratorTest" --info`
Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt
git commit -m "feat(graph-agent): add GraphAgentGenerator for strategy DSL code"
```

---

### Task 5: Backend ExportService Integration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`

- [ ] **Step 1: Add GraphAgentGenerator to ExportService constructor**

In `ExportService.kt`, add after the `inputOutputGenerator` parameter (line 35):

```kotlin
private val graphAgentGenerator: GraphAgentGenerator,
```

- [ ] **Step 2: Add graph-agent generation path in generateFiles()**

In `ExportService.kt`, add a new block after the planner-agent path (after the `return files` on line 129) and before `val agent = agents.first()` (line 132):

```kotlin
val graphAgents = adapter.convertGraphAgents(graph)
if (graphAgents.isNotEmpty()) {
    val ga = graphAgents.first()

    // Infrastructure
    files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
    files["gradle.properties"] = gradlePropertiesGenerator.generate()
    files["Dockerfile"] = dockerfileGenerator.generate()

    // Use a temporary KoogAgent to generate build.gradle and application.properties
    val infraAgent = com.agentwork.modeler.export.model.KoogAgent(
        name = ga.name,
        systemPrompt = ga.systemPrompt,
        modelProvider = ga.modelProvider,
        modelId = ga.modelId,
        temperature = ga.temperature,
        tools = ga.tools,
        builtinTools = ga.builtinTools,
        subAgents = ga.subAgents,
        mcpServers = ga.mcpServers,
        inputs = ga.inputs,
        outputs = ga.outputs
    )
    files["build.gradle.kts"] = buildGradleGenerator.generate(infraAgent)
    files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(infraAgent)

    // Kotlin sources
    files["$pkg/Application.kt"] = applicationGenerator.generate()
    files["$pkg/AgentConfig.kt"] = agentGenerator.generate(infraAgent, "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()")
    files["$pkg/ChatController.kt"] = controllerGenerator.generate()

    // Strategy file
    files["$pkg/${ga.name.replaceFirstChar { it.uppercase() }}Strategy.kt"] = graphAgentGenerator.generate(ga)

    // Tools
    for (tool in ga.tools) {
        val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
        files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
    }
    for (builtinTool in ga.builtinTools) {
        val code = builtinToolGenerator.generate(builtinTool)
        val fileName = builtinTool.type.toFileName()
        if (code != null && fileName != null) files["$pkg/tools/$fileName"] = code
    }
    for (mcpServer in ga.mcpServers) {
        val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
        files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
    }

    // Input/Output
    val inputCode = inputOutputGenerator.generateInputClass(ga.name, ga.inputs)
    if (inputCode != null) files["$pkg/${ga.name.replaceFirstChar { it.uppercase() }}Input.kt"] = inputCode
    val outputCode = inputOutputGenerator.generateOutputClass(ga.name, ga.outputs)
    if (outputCode != null) files["$pkg/${ga.name.replaceFirstChar { it.uppercase() }}Output.kt"] = outputCode

    if (ga.tools.isNotEmpty() || ga.builtinTools.isNotEmpty() || ga.subAgents.isNotEmpty() || ga.mcpServers.isNotEmpty()) {
        files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(ga.tools, ga.builtinTools, ga.subAgents, ga.mcpServers)
    }

    return files
}
```

- [ ] **Step 3: Add strategy parameter to AgentGenerator.generate()**

In `AgentGenerator.kt`, change the method signature from:

```kotlin
fun generate(agent: KoogAgent): String {
```

to:

```kotlin
fun generate(agent: KoogAgent, strategyCall: String? = null): String {
```

Then after `agent.temperature?.let { params.add("temperature = $it") }` (line 26), add:

```kotlin
if (strategyCall != null) {
    params.add("strategy = $strategyCall")
}
```

- [ ] **Step 4: Update ExportServiceTest to include graphAgentGenerator**

In `ExportServiceTest.kt`, add `graphAgentGenerator = GraphAgentGenerator(),` to the `ExportService` constructor call (after the `inputOutputGenerator` line).

- [ ] **Step 5: Run all tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --info`
Expected: All tests PASSED

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat(graph-agent): integrate graph-agent into ExportService and AgentGenerator"
```

---

### Task 6: Frontend Types and Store

**Files:**
- Create: `frontend/types/graph-agent.ts`
- Modify: `frontend/types/graph.ts`
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Create frontend types**

```typescript
// frontend/types/graph-agent.ts

export type GraphStrategyNodeType =
  | "node-start"
  | "node-finish"
  | "node-llm-request"
  | "node-execute-tool"
  | "node-llm-send-tool-result"
  | "node-condition";

export interface GraphStrategyNode {
  id: string;
  type: GraphStrategyNodeType;
  position: { x: number; y: number };
  data: GraphStrategyNodeData;
}

export interface GraphStrategyNodeData {
  label?: string;
  toolName?: string;
  condition?: string;
  temperature?: number;
}

export type GraphEdgeType = "onAssistantMessage" | "onToolCall" | "onCondition";

export interface GraphStrategyEdge {
  id: string;
  source: string;
  target: string;
  edgeType: GraphEdgeType;
  condition?: string;
  transformation?: string;
}

export type GraphAgentTemplate = "empty" | "tool-calling" | "react-loop" | "tool-loop-finish";

export interface GraphAgentConfig {
  name: string;
  description: string;
  systemPrompt: string;
  modelProvider: string;
  modelId?: string;
  temperature?: number;
  strategyNodes: GraphStrategyNode[];
  strategyEdges: GraphStrategyEdge[];
}
```

- [ ] **Step 2: Update types/graph.ts — add graph-agent to NodeType**

In `frontend/types/graph.ts`, add the import:

```typescript
import { GraphAgentConfig } from "./graph-agent";
```

Change the `NodeType` union to:

```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output" | "graph-agent";
```

Add `GraphAgentConfig` to the `GraphNode.data` union type:

```typescript
data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig;
```

- [ ] **Step 3: Update editorStore.ts — add graph-agent defaults and addNode case**

Add import at top of `editorStore.ts`:

```typescript
import { GraphAgentConfig } from "@/types/graph-agent";
```

Add the `GraphAgentConfig` to the `updateNodeData` type union in the `EditorState` interface (line 21):

```typescript
updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig>) => void;
```

Add default data after `defaultPlannerAgentData` (after line 102):

```typescript
const defaultGraphAgentData: GraphAgentConfig = {
  name: "",
  description: "",
  systemPrompt: "",
  modelProvider: "openai",
  strategyNodes: [],
  strategyEdges: [],
};
```

Add the `data` type in the `addNode` method to include `GraphAgentConfig` (line 142):

```typescript
let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig;
```

Add else-if case in `addNode` after the `planner-agent` case (after line 152):

```typescript
} else if (type === "graph-agent") {
  data = { ...defaultGraphAgentData };
```

- [ ] **Step 4: Commit**

```bash
git add frontend/types/graph-agent.ts frontend/types/graph.ts frontend/store/editorStore.ts
git commit -m "feat(graph-agent): add frontend types and store support"
```

---

### Task 7: Frontend Rete Integration (Main Canvas)

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`
- Modify: `frontend/lib/rete-setup.ts`
- Modify: `frontend/components/editor/Canvas.tsx`
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add GraphAgentReteNode to rete-nodes.ts**

Add before the `AppNode` type (before line 109):

```typescript
export class GraphAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 120;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Graph Agent");
    this.addInput("inputs", new ClassicPreset.Input(toolSocket, "Inputs", true));
    this.addInput("tools", new ClassicPreset.Input(toolSocket, "Tools", true));
    this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
  }
}
```

Add `GraphAgentReteNode` to the `AppNode` union type.

- [ ] **Step 2: Add connection rules in rete-setup.ts**

Import `GraphAgentReteNode` from `./rete-nodes`.

In the connection validation pipe, add after the Input check (after line 96):

```typescript
// Tool/Builtin/SubAgent/MCP/Input -> GraphAgent: OK
const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;
if ((isToolSource || isSubAgentSource || isMcpSource || isInputSource) && isGraphAgentTarget) {
  return context;
}
// A2A -> GraphAgent: OK
if (isA2ASource && isGraphAgentTarget) {
  return context;
}
// GraphAgent -> Output: OK
const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;
if (isGraphAgentSource && isOutputTarget) {
  return context;
}
```

- [ ] **Step 3: Add GraphAgentReteNode to Canvas.tsx sync logic**

Import `GraphAgentReteNode` from `@/lib/rete-nodes`.

In the Zustand->Rete node creation block (around line 159), add:

```typescript
} else if (node.type === "graph-agent") {
  reteNode = new GraphAgentReteNode(node.id, (node.data as any).name || "Graph Agent");
```

In the Rete->Zustand `connectioncreated` pipe, add `GraphAgentReteNode` checks:
- Add `const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;` and `const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;`
- Extend the if condition on line 86 to include `isGraphAgentTarget` and `isGraphAgentSource`:

```typescript
if (
  ((isToolSource || isSubAgentSource || isMcpSource || isA2ASource || isInputSource) && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget || isGraphAgentTarget)) ||
  ((isAgentSource || isSubAgentSource || isPlannerAgentSource || isGraphAgentSource) && isOutputTarget)
)
```

Apply the same changes to the `connectionremoved` pipe.

In the edge sync "add missing connections" block, update the `inputKey` logic to handle `GraphAgentReteNode`:

```typescript
const isInputSource = sourceRete instanceof InputReteNode;
const isOutputTarget = targetRete instanceof OutputReteNode;
const inputKey = isOutputTarget ? "agent"
  : isInputSource && targetRete.inputs?.["inputs"] ? "inputs"
  : Object.keys(targetRete.inputs || {})[0];
```

(This already handles GraphAgentReteNode because it has `"inputs"` and `"tools"` sockets just like AgentReteNode.)

- [ ] **Step 4: Add graph-agent styling to CustomNode.tsx**

Add to `nodeIcons`:
```typescript
"graph-agent": <BotIcon size={10} />,
```

(Import `NetworkIcon` from lucide-react if you want a distinct icon, or reuse `BotIcon`.)

Add to `nodeColors`:
```typescript
"graph-agent": {
  border: "rgba(99, 102, 241, 0.4)",
  glow: "rgba(99, 102, 241, 0.12)",
  badge: "rgba(99, 102, 241, 0.15)",
  badgeText: "#818cf8",
},
```

Add to `nodeTypeLabels`:
```typescript
"graph-agent": "GRAPH AGENT",
```

Add to `getNodeType`:
```typescript
if (data instanceof GraphAgentReteNode) return "graph-agent";
```

Import `GraphAgentReteNode` from `@/lib/rete-nodes`.

Add subtitle case in the switch:
```typescript
case "graph-agent":
  if (d.strategyNodes?.length) subtitle = `${d.strategyNodes.length} nodes`;
  break;
```

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/lib/rete-setup.ts frontend/components/editor/Canvas.tsx frontend/components/editor/CustomNode.tsx
git commit -m "feat(graph-agent): add main canvas Rete integration"
```

---

### Task 8: Frontend Templates and Template Dialog

**Files:**
- Create: `frontend/components/graph-agent/templates.ts`
- Create: `frontend/components/dialogs/GraphAgentTemplateDialog.tsx`

- [ ] **Step 1: Create templates.ts**

```typescript
import type { GraphStrategyNode, GraphStrategyEdge, GraphAgentTemplate } from "@/types/graph-agent";

interface TemplateDefinition {
  label: string;
  description: string;
  nodes: GraphStrategyNode[];
  edges: GraphStrategyEdge[];
}

let nextTemplateId = 1;
function sid() { return `sn-${nextTemplateId++}`; }

function createEmpty(): TemplateDefinition {
  const startId = sid();
  const finishId = sid();
  return {
    label: "Empty",
    description: "Start and Finish only",
    nodes: [
      { id: startId, type: "node-start", position: { x: 100, y: 200 }, data: {} },
      { id: finishId, type: "node-finish", position: { x: 500, y: 200 }, data: {} },
    ],
    edges: [],
  };
}

function createToolCalling(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "Tool-Calling Agent",
    description: "LLM calls tools in a loop until it responds with text",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: execId, type: "node-execute-tool", position: { x: 500, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 500, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 750, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: llmId, target: finishId, edgeType: "onAssistantMessage" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: llmId, edgeType: "onCondition" },
    ],
  };
}

function createReactLoop(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const condId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "ReAct Loop",
    description: "LLM reasons, then decides to act or finish",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: condId, type: "node-condition", position: { x: 450, y: 200 }, data: { label: "shouldAct", condition: "hasToolCall" } },
      { id: execId, type: "node-execute-tool", position: { x: 650, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 650, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 850, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: condId, edgeType: "onAssistantMessage" },
      { id: sid(), source: condId, target: execId, edgeType: "onCondition", condition: "hasToolCall" },
      { id: sid(), source: condId, target: finishId, edgeType: "onCondition", condition: "!hasToolCall" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: llmId, edgeType: "onCondition" },
    ],
  };
}

function createToolLoopFinish(): TemplateDefinition {
  const startId = sid();
  const llmId = sid();
  const execId = sid();
  const sendId = sid();
  const finishId = sid();
  return {
    label: "Tool Loop with Finish",
    description: "Tools loop back to LLM, assistant message finishes",
    nodes: [
      { id: startId, type: "node-start", position: { x: 50, y: 200 }, data: {} },
      { id: llmId, type: "node-llm-request", position: { x: 250, y: 200 }, data: { label: "llmRequest" } },
      { id: execId, type: "node-execute-tool", position: { x: 500, y: 100 }, data: { label: "executeTool" } },
      { id: sendId, type: "node-llm-send-tool-result", position: { x: 500, y: 300 }, data: { label: "sendResult" } },
      { id: finishId, type: "node-finish", position: { x: 750, y: 200 }, data: {} },
    ],
    edges: [
      { id: sid(), source: startId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: execId, edgeType: "onToolCall" },
      { id: sid(), source: execId, target: sendId, edgeType: "onCondition" },
      { id: sid(), source: sendId, target: llmId, edgeType: "onCondition" },
      { id: sid(), source: llmId, target: finishId, edgeType: "onAssistantMessage" },
    ],
  };
}

export const TEMPLATES: Record<GraphAgentTemplate, TemplateDefinition> = {
  "empty": createEmpty(),
  "tool-calling": createToolCalling(),
  "react-loop": createReactLoop(),
  "tool-loop-finish": createToolLoopFinish(),
};

export function resetTemplateIds() {
  nextTemplateId = 1;
}
```

- [ ] **Step 2: Create GraphAgentTemplateDialog.tsx**

```tsx
"use client";

import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { TEMPLATES } from "@/components/graph-agent/templates";
import type { GraphAgentTemplate } from "@/types/graph-agent";

const templateKeys: GraphAgentTemplate[] = ["empty", "tool-calling", "react-loop", "tool-loop-finish"];

export function GraphAgentTemplateDialog({
  open,
  onSelect,
  onClose,
}: {
  open: boolean;
  onSelect: (template: GraphAgentTemplate) => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Choose Graph Agent Template</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {templateKeys.map((key) => {
            const t = TEMPLATES[key];
            return (
              <Button
                key={key}
                variant="outline"
                className="w-full justify-start h-auto py-3"
                onClick={() => onSelect(key)}
              >
                <div className="text-left">
                  <div className="font-medium">{t.label}</div>
                  <div className="text-xs text-muted-foreground">{t.description}</div>
                </div>
              </Button>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/graph-agent/templates.ts frontend/components/dialogs/GraphAgentTemplateDialog.tsx
git commit -m "feat(graph-agent): add templates and template selection dialog"
```

---

### Task 9: Frontend Toolbar, Properties Panel, and Validation

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`
- Create: `frontend/components/panels/GraphAgentProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`
- Create: `frontend/lib/graph-agent-validation.ts`
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Add Graph Agent button to Toolbar**

In `Toolbar.tsx`, add import:

```typescript
import { NetworkIcon } from "lucide-react";
import { useState } from "react";
import { GraphAgentTemplateDialog } from "@/components/dialogs/GraphAgentTemplateDialog";
import { TEMPLATES } from "@/components/graph-agent/templates";
import type { GraphAgentTemplate } from "@/types/graph-agent";
```

Inside the `Toolbar` component, add state:

```typescript
const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
```

Add handler:

```typescript
const handleTemplateSelect = (template: GraphAgentTemplate) => {
  const t = TEMPLATES[template];
  addNode("graph-agent", { x: 100, y: 100 });
  // After addNode, update the newly created node with template data
  const state = useEditorStore.getState();
  const newNode = state.nodes[state.nodes.length - 1];
  if (newNode && newNode.type === "graph-agent") {
    useEditorStore.getState().updateNodeData(newNode.id, {
      strategyNodes: structuredClone(t.nodes),
      strategyEdges: structuredClone(t.edges),
    });
  }
  setTemplateDialogOpen(false);
};
```

Add the button after the Output button (before `<div className="flex-1" />`):

```tsx
<span className="text-border select-none">|</span>

<Button
  variant="ghost"
  size="sm"
  onClick={() => setTemplateDialogOpen(true)}
  title="Add Graph Agent"
>
  <NetworkIcon className="h-4 w-4 mr-1 text-indigo-400" />
  Graph Agent
</Button>

<GraphAgentTemplateDialog
  open={templateDialogOpen}
  onSelect={handleTemplateSelect}
  onClose={() => setTemplateDialogOpen(false)}
/>
```

- [ ] **Step 2: Create GraphAgentProperties.tsx**

```tsx
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { GraphAgentConfig } from "@/types/graph-agent";
import { ModelProvider } from "@/types/koog";
import { useState } from "react";
import { GraphStrategyEditor } from "@/components/graph-agent/GraphStrategyEditor";

export function GraphAgentProperties({ nodeId, data }: { nodeId: string; data: GraphAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<GraphAgentConfig>) => updateNodeData(nodeId, field);
  const [editorOpen, setEditorOpen] = useState(false);

  return (
    <>
      <div className="space-y-4">
        <div>
          <Label>Name</Label>
          <Input value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyGraphAgent" />
        </div>
        <div>
          <Label>Description</Label>
          <Textarea value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="Describe what this agent does..." />
        </div>
        <div>
          <Label>System Prompt</Label>
          <Textarea value={data.systemPrompt} onChange={(e) => update({ systemPrompt: e.target.value })} rows={4} placeholder="You are a helpful assistant..." />
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
          <Label>Model ID</Label>
          <Input value={data.modelId ?? ""} onChange={(e) => update({ modelId: e.target.value || undefined })} placeholder="claude-sonnet-4" />
        </div>
        <div>
          <Label>Temperature</Label>
          <Input type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="0.7" />
        </div>
        <div className="text-xs text-muted-foreground">
          {data.strategyNodes.length} strategy nodes, {data.strategyEdges.length} edges
        </div>
        <Button variant="outline" className="w-full" onClick={() => setEditorOpen(true)}>
          Edit Strategy Graph
        </Button>
      </div>
      {editorOpen && (
        <GraphStrategyEditor nodeId={nodeId} data={data} onClose={() => setEditorOpen(false)} />
      )}
    </>
  );
}
```

- [ ] **Step 3: Update PropertiesPanel.tsx**

Add import:

```typescript
import { GraphAgentProperties } from "./GraphAgentProperties";
import { GraphAgentConfig } from "@/types/graph-agent";
```

Add to the title mapping (after the `"output"` case):

```typescript
selectedNode.type === "graph-agent" ? "Graph Agent" :
```

Add rendering case (after the `"output"` rendering):

```tsx
{selectedNode.type === "graph-agent" && (
  <GraphAgentProperties nodeId={selectedNode.id} data={selectedNode.data as GraphAgentConfig} />
)}
```

- [ ] **Step 4: Create graph-agent-validation.ts**

```typescript
import type { GraphNode, GraphEdge, WorkspaceGraph } from "@/types/graph";
import type { ValidationError } from "./validation";

export function validateGraphAgent(node: GraphNode, graph: WorkspaceGraph): ValidationError[] {
  const errors: ValidationError[] = [];
  const d = node.data as any;
  const nodeId = node.id;

  if (!d.name?.trim()) {
    errors.push({ nodeId, severity: "error", message: "Graph Agent requires a name" });
  }
  if (!d.systemPrompt?.trim()) {
    errors.push({ nodeId, severity: "error", message: "Graph Agent requires a system prompt" });
  }

  const strategyNodes: any[] = d.strategyNodes || [];
  const strategyEdges: any[] = d.strategyEdges || [];

  const startCount = strategyNodes.filter((sn: any) => sn.type === "node-start").length;
  const finishCount = strategyNodes.filter((sn: any) => sn.type === "node-finish").length;

  if (startCount !== 1) errors.push({ nodeId, severity: "error", message: "Graph Agent must have exactly one node-start" });
  if (finishCount !== 1) errors.push({ nodeId, severity: "error", message: "Graph Agent must have exactly one node-finish" });

  const startNode = strategyNodes.find((sn: any) => sn.type === "node-start");
  const finishNode = strategyNodes.find((sn: any) => sn.type === "node-finish");

  if (startNode) {
    const startOutgoing = strategyEdges.filter((se: any) => se.source === startNode.id);
    if (startOutgoing.length === 0) {
      errors.push({ nodeId, severity: "error", message: "node-start must have at least one outgoing edge" });
    }
  }

  // Reachability BFS
  if (startNode && finishNode) {
    const adj = new Map<string, string[]>();
    for (const se of strategyEdges) {
      if (!adj.has(se.source)) adj.set(se.source, []);
      adj.get(se.source)!.push(se.target);
    }
    const visited = new Set<string>();
    const queue = [startNode.id];
    while (queue.length > 0) {
      const current = queue.shift()!;
      if (visited.has(current)) continue;
      visited.add(current);
      for (const next of adj.get(current) || []) queue.push(next);
    }
    if (!visited.has(finishNode.id)) {
      errors.push({ nodeId, severity: "error", message: "node-finish must be reachable from node-start" });
    }
  }

  // Per-strategy-node checks
  for (const sn of strategyNodes) {
    if (sn.type === "node-execute-tool" && !sn.data?.toolName?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-execute-tool "${sn.data?.label || sn.id}" requires a toolName` });
    }
    if (sn.type === "node-condition" && !sn.data?.condition?.trim()) {
      errors.push({ nodeId, severity: "error", message: `node-condition "${sn.data?.label || sn.id}" requires a condition` });
    }
  }

  // Edge checks
  for (const se of strategyEdges) {
    if (!se.edgeType) {
      errors.push({ nodeId, severity: "error", message: "Strategy edge requires an edgeType" });
    }
    if (se.edgeType === "onCondition" && se.source !== startNode?.id && !se.condition?.trim()) {
      errors.push({ nodeId, severity: "error", message: "onCondition edge requires a condition expression" });
    }
  }

  // Warnings
  if (strategyNodes.length <= 2 && startCount === 1 && finishCount === 1) {
    errors.push({ nodeId, severity: "warning", message: "Graph Agent has an empty strategy (only Start and Finish)" });
  }

  return errors;
}
```

- [ ] **Step 5: Update lib/validation.ts to call graph-agent validation**

Add import:

```typescript
import { validateGraphAgent } from "./graph-agent-validation";
```

In the `validateGraph` function, add a case for `graph-agent` in the node iteration (alongside the existing node type checks):

```typescript
if (node.type === "graph-agent") {
  errors.push(...validateGraphAgent(node, graph));
}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx frontend/components/panels/GraphAgentProperties.tsx frontend/components/panels/PropertiesPanel.tsx frontend/lib/graph-agent-validation.ts frontend/lib/validation.ts
git commit -m "feat(graph-agent): add toolbar, properties panel, and frontend validation"
```

---

### Task 10: Frontend Sub-Canvas — Strategy Rete Nodes and Setup

**Files:**
- Create: `frontend/lib/strategy-rete-nodes.ts`
- Create: `frontend/lib/strategy-rete-setup.ts`

- [ ] **Step 1: Create strategy-rete-nodes.ts**

```typescript
import { ClassicPreset } from "rete";

export class StrategySocket extends ClassicPreset.Socket {
  constructor() { super("Strategy"); }
  isCompatibleWith(socket: ClassicPreset.Socket): boolean {
    return socket instanceof StrategySocket;
  }
}

export const strategySocket = new StrategySocket();

export class StartReteNode extends ClassicPreset.Node {
  width = 140;
  height = 60;
  constructor(public strategyNodeId: string) {
    super("Start");
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class FinishReteNode extends ClassicPreset.Node {
  width = 140;
  height = 60;
  constructor(public strategyNodeId: string) {
    super("Finish");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
  }
}

export class LLMRequestReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "LLM Request");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class ExecuteToolReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Execute Tool");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class SendToolResultReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Send Tool Result");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class ConditionReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(public strategyNodeId: string, label: string) {
    super(label || "Condition");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export type StrategyNode = StartReteNode | FinishReteNode | LLMRequestReteNode | ExecuteToolReteNode | SendToolResultReteNode | ConditionReteNode;
export type StrategyConnection = ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node>;
```

- [ ] **Step 2: Create strategy-rete-setup.ts**

```typescript
import { NodeEditor, GetSchemes } from "rete";
import { createRoot } from "react-dom/client";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets, ReactArea2D } from "rete-react-plugin";
import {
  StrategyNode,
  StrategyConnection,
  StartReteNode,
  FinishReteNode,
} from "./strategy-rete-nodes";

export type StrategySchemes = GetSchemes<StrategyNode, StrategyConnection>;
type StrategyAreaExtra = ReactArea2D<StrategySchemes>;

export async function createStrategyEditor(container: HTMLElement) {
  const editor = new NodeEditor<StrategySchemes>();
  const area = new AreaPlugin<StrategySchemes, StrategyAreaExtra>(container);
  const connection = new ConnectionPlugin<StrategySchemes, StrategyAreaExtra>();
  const render = new ReactPlugin<StrategySchemes, StrategyAreaExtra>({ createRoot });

  render.addPreset(ReactPresets.classic.setup({
    customize: {
      node() {
        // Will be replaced by StrategyCustomNode in Task 11
        const { StrategyCustomNode } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomNode as any;
      },
      socket() {
        const { StrategyCustomSocket } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomSocket as any;
      },
      connection() {
        const { StrategyCustomConnection } = require("@/components/graph-agent/StrategyCustomNode");
        return StrategyCustomConnection as any;
      },
    },
  }));
  connection.addPreset(ConnectionPresets.classic.setup());

  editor.use(area);
  area.use(connection);
  area.use(render);

  const selector = AreaExtensions.selector();
  const accumulating = AreaExtensions.accumulateOnCtrl();
  AreaExtensions.selectableNodes(area, selector, { accumulating });
  AreaExtensions.simpleNodesOrder(area);

  // Connection validation: Start can only be source, Finish can only be target
  editor.addPipe((context) => {
    if (context.type === "connectioncreate") {
      const { source, target } = context.data;
      const sourceNode = editor.getNode(source);
      const targetNode = editor.getNode(target);
      // Cannot connect TO start or FROM finish
      if (targetNode instanceof StartReteNode) return undefined;
      if (sourceNode instanceof FinishReteNode) return undefined;
      return context;
    }
    return context;
  });

  return { editor, area, connection, render };
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/strategy-rete-nodes.ts frontend/lib/strategy-rete-setup.ts
git commit -m "feat(graph-agent): add strategy sub-canvas Rete nodes and setup"
```

---

### Task 11: Frontend Sub-Canvas — StrategyCustomNode and Edge Auto-Typing

**Files:**
- Create: `frontend/components/graph-agent/StrategyCustomNode.tsx`

- [ ] **Step 1: Create StrategyCustomNode.tsx**

```tsx
"use client";

import * as React from "react";
import { ClassicPreset } from "rete";
import { Presets } from "rete-react-plugin";
import {
  StartReteNode,
  FinishReteNode,
  LLMRequestReteNode,
  ExecuteToolReteNode,
  SendToolResultReteNode,
  ConditionReteNode,
} from "@/lib/strategy-rete-nodes";
import type { StrategySchemes } from "@/lib/strategy-rete-setup";

const strategyNodeColors: Record<string, { border: string; glow: string; badge: string; badgeText: string }> = {
  "node-start":                { border: "rgba(34, 197, 94, 0.5)",  glow: "rgba(34, 197, 94, 0.12)",  badge: "rgba(34, 197, 94, 0.15)",  badgeText: "#4ade80" },
  "node-finish":               { border: "rgba(239, 68, 68, 0.5)",  glow: "rgba(239, 68, 68, 0.12)",  badge: "rgba(239, 68, 68, 0.15)",  badgeText: "#f87171" },
  "node-llm-request":          { border: "rgba(59, 130, 246, 0.5)", glow: "rgba(59, 130, 246, 0.12)", badge: "rgba(59, 130, 246, 0.15)", badgeText: "#60a5fa" },
  "node-execute-tool":         { border: "rgba(16, 185, 129, 0.5)", glow: "rgba(16, 185, 129, 0.12)", badge: "rgba(16, 185, 129, 0.15)", badgeText: "#34d399" },
  "node-llm-send-tool-result": { border: "rgba(251, 191, 36, 0.5)", glow: "rgba(251, 191, 36, 0.12)", badge: "rgba(251, 191, 36, 0.15)", badgeText: "#fbbf24" },
  "node-condition":            { border: "rgba(168, 85, 247, 0.5)", glow: "rgba(168, 85, 247, 0.12)", badge: "rgba(168, 85, 247, 0.15)", badgeText: "#c084fc" },
};

const strategyNodeLabels: Record<string, string> = {
  "node-start": "START",
  "node-finish": "FINISH",
  "node-llm-request": "LLM REQUEST",
  "node-execute-tool": "EXECUTE TOOL",
  "node-llm-send-tool-result": "SEND RESULT",
  "node-condition": "CONDITION",
};

function getStrategyNodeType(data: StrategySchemes["Node"]): string {
  if (data instanceof StartReteNode) return "node-start";
  if (data instanceof FinishReteNode) return "node-finish";
  if (data instanceof LLMRequestReteNode) return "node-llm-request";
  if (data instanceof ExecuteToolReteNode) return "node-execute-tool";
  if (data instanceof SendToolResultReteNode) return "node-llm-send-tool-result";
  if (data instanceof ConditionReteNode) return "node-condition";
  return "node-start";
}

export function StrategyCustomNode(props: { data: StrategySchemes["Node"] & { width?: number }; emit: (props: any) => void }) {
  const { data, emit } = props;
  const nodeType = getStrategyNodeType(data);
  const colors = strategyNodeColors[nodeType] || strategyNodeColors["node-start"];
  const typeLabel = strategyNodeLabels[nodeType] || "NODE";

  const inputs = Object.entries(data.inputs || {});
  const outputs = Object.entries(data.outputs || {});

  return (
    <div
      style={{
        background: "rgba(15, 22, 41, 0.95)",
        border: `1px solid ${colors.border}`,
        borderRadius: 10,
        padding: 0,
        minWidth: data.width || 160,
        boxShadow: `0 0 16px ${colors.glow}`,
        fontFamily: "var(--font-sans), system-ui, sans-serif",
        backdropFilter: "blur(8px)",
      }}
    >
      <div style={{ padding: "6px 10px 4px", borderBottom: `1px solid ${colors.border}` }}>
        <div
          style={{
            fontSize: 8,
            fontWeight: 700,
            letterSpacing: "0.08em",
            color: colors.badgeText,
            background: colors.badge,
            display: "inline-flex",
            padding: "1px 5px",
            borderRadius: 3,
            marginBottom: 3,
          }}
        >
          {typeLabel}
        </div>
        <div style={{ fontSize: 12, fontWeight: 500, color: "#e2e8f0" }}>
          {data.label}
        </div>
      </div>
      <div style={{ padding: "4px 0" }}>
        {inputs.map(([key, input]) =>
          input ? (
            <div key={key} style={{ display: "flex", alignItems: "center", padding: "2px 10px", fontSize: 10, color: "#94a3b8" }}>
              <Presets.classic.RefSocket name="input-socket" emit={emit} side="input" nodeId={data.id} socketKey={key} payload={(input as ClassicPreset.Input<ClassicPreset.Socket>).socket} />
              <span style={{ marginLeft: 6 }}>{(input as ClassicPreset.Input<ClassicPreset.Socket>).label || key}</span>
            </div>
          ) : null
        )}
        {outputs.map(([key, output]) =>
          output ? (
            <div key={key} style={{ display: "flex", alignItems: "center", justifyContent: "flex-end", padding: "2px 10px", fontSize: 10, color: "#94a3b8" }}>
              <span style={{ marginRight: 6 }}>{(output as ClassicPreset.Output<ClassicPreset.Socket>).label || key}</span>
              <Presets.classic.RefSocket name="output-socket" emit={emit} side="output" nodeId={data.id} socketKey={key} payload={(output as ClassicPreset.Output<ClassicPreset.Socket>).socket} />
            </div>
          ) : null
        )}
      </div>
    </div>
  );
}

export function StrategyCustomSocket(props: { data: ClassicPreset.Socket }) {
  return (
    <div
      style={{
        width: 10,
        height: 10,
        borderRadius: "50%",
        background: "rgba(99, 102, 241, 0.3)",
        border: "2px solid rgba(99, 102, 241, 0.6)",
        boxShadow: "0 0 4px rgba(99, 102, 241, 0.3)",
      }}
    />
  );
}

export function StrategyCustomConnection(props: { data: { id: string; source: string; target: string } }) {
  const { path } = Presets.classic.useConnection();
  const [hovered, setHovered] = React.useState(false);

  if (!path) return null;

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    const editorRef = (window as any).__strategyReteEditor;
    if (!editorRef) return;
    // Signal to the parent canvas to remove this edge
    const event = new CustomEvent("strategy-edge-delete", {
      detail: { source: props.data.source, target: props.data.target },
    });
    window.dispatchEvent(event);
  };

  return (
    <svg data-testid="connection" style={{ overflow: "visible", position: "absolute", pointerEvents: "none", width: "100%", height: "100%" }}>
      <path d={path} stroke="transparent" strokeWidth={14} fill="none" style={{ pointerEvents: "stroke", cursor: "pointer" }} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onClick={handleClick} />
      <path d={path} stroke={hovered ? "rgba(239, 68, 68, 0.8)" : "rgba(99, 102, 241, 0.5)"} strokeWidth={hovered ? 3 : 2} fill="none" style={{ pointerEvents: "none", transition: "stroke 0.15s, stroke-width 0.15s" }} />
    </svg>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/components/graph-agent/StrategyCustomNode.tsx
git commit -m "feat(graph-agent): add strategy sub-canvas custom node rendering"
```

---

### Task 12: Frontend Sub-Canvas — GraphStrategyEditor (main component)

**Files:**
- Create: `frontend/components/graph-agent/GraphStrategyToolbar.tsx`
- Create: `frontend/components/graph-agent/GraphStrategyCanvas.tsx`
- Create: `frontend/components/graph-agent/StrategyNodeProperties.tsx`
- Create: `frontend/components/graph-agent/StrategyEdgeProperties.tsx`
- Create: `frontend/components/graph-agent/GraphStrategyEditor.tsx`

- [ ] **Step 1: Create GraphStrategyToolbar.tsx**

```tsx
"use client";

import { Button } from "@/components/ui/button";
import type { GraphStrategyNodeType } from "@/types/graph-agent";

const nodeButtons: { type: GraphStrategyNodeType; label: string; color: string }[] = [
  { type: "node-llm-request", label: "LLM Request", color: "text-blue-400" },
  { type: "node-execute-tool", label: "Execute Tool", color: "text-emerald-400" },
  { type: "node-llm-send-tool-result", label: "Send Tool Result", color: "text-yellow-400" },
  { type: "node-condition", label: "Condition", color: "text-purple-400" },
];

export function GraphStrategyToolbar({ onAddNode }: { onAddNode: (type: GraphStrategyNodeType) => void }) {
  return (
    <div className="flex items-center gap-2 px-4 py-2 border-b border-border">
      {nodeButtons.map((btn) => (
        <Button key={btn.type} variant="outline" size="sm" onClick={() => onAddNode(btn.type)}>
          <span className={`mr-1 ${btn.color}`}>+</span>
          {btn.label}
        </Button>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Create StrategyNodeProperties.tsx**

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyNode } from "@/types/graph-agent";

export function StrategyNodeProperties({
  node,
  connectedTools,
  onUpdate,
}: {
  node: GraphStrategyNode;
  connectedTools: string[];
  onUpdate: (data: Partial<GraphStrategyNode["data"]>) => void;
}) {
  const d = node.data;

  if (node.type === "node-start" || node.type === "node-finish") {
    return <div className="text-sm text-muted-foreground p-4">No configurable properties for {node.type === "node-start" ? "Start" : "Finish"} node.</div>;
  }

  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Label</Label>
        <Input value={d.label ?? ""} onChange={(e) => onUpdate({ label: e.target.value })} placeholder="nodeName" />
      </div>

      {node.type === "node-execute-tool" && (
        <div>
          <Label>Tool</Label>
          <Select value={d.toolName ?? ""} onValueChange={(v) => onUpdate({ toolName: v })}>
            <SelectTrigger><SelectValue placeholder="Select tool..." /></SelectTrigger>
            <SelectContent>
              {connectedTools.map((t) => (
                <SelectItem key={t} value={t}>{t}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      {node.type === "node-condition" && (
        <div>
          <Label>Condition</Label>
          <Input value={d.condition ?? ""} onChange={(e) => onUpdate({ condition: e.target.value })} placeholder="result.isNotEmpty()" />
        </div>
      )}

      {node.type === "node-llm-request" && (
        <div>
          <Label>Temperature Override</Label>
          <Input type="number" step="0.1" min="0" max="2" value={d.temperature ?? ""} onChange={(e) => onUpdate({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} placeholder="Default" />
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Create StrategyEdgeProperties.tsx**

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { GraphStrategyEdge, GraphEdgeType } from "@/types/graph-agent";

export function StrategyEdgeProperties({
  edge,
  onUpdate,
}: {
  edge: GraphStrategyEdge;
  onUpdate: (data: Partial<GraphStrategyEdge>) => void;
}) {
  return (
    <div className="space-y-3 p-4">
      <div>
        <Label>Edge Type</Label>
        <Select value={edge.edgeType} onValueChange={(v) => onUpdate({ edgeType: v as GraphEdgeType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="onAssistantMessage">onAssistantMessage</SelectItem>
            <SelectItem value="onToolCall">onToolCall</SelectItem>
            <SelectItem value="onCondition">onCondition</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {edge.edgeType === "onCondition" && (
        <>
          <div>
            <Label>Condition</Label>
            <Input value={edge.condition ?? ""} onChange={(e) => onUpdate({ condition: e.target.value })} placeholder="true" />
          </div>
          <div>
            <Label>Transformation (optional)</Label>
            <Input value={edge.transformation ?? ""} onChange={(e) => onUpdate({ transformation: e.target.value || undefined })} placeholder="it.uppercase()" />
          </div>
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Create GraphStrategyCanvas.tsx**

```tsx
"use client";

import { useEffect, useRef } from "react";
import { createStrategyEditor } from "@/lib/strategy-rete-setup";
import type { GraphStrategyNode, GraphStrategyEdge } from "@/types/graph-agent";
import {
  StartReteNode, FinishReteNode, LLMRequestReteNode,
  ExecuteToolReteNode, SendToolResultReteNode, ConditionReteNode,
} from "@/lib/strategy-rete-nodes";
import type { StrategySchemes } from "@/lib/strategy-rete-setup";
import type { NodeEditor } from "rete";
import type { AreaPlugin } from "rete-area-plugin";

interface Props {
  strategyNodes: GraphStrategyNode[];
  strategyEdges: GraphStrategyEdge[];
  onNodeSelected: (nodeId: string | null) => void;
  onEdgeSelected: (edgeId: string | null) => void;
  onNodesChanged: (nodes: GraphStrategyNode[]) => void;
  onEdgesChanged: (edges: GraphStrategyEdge[]) => void;
}

let syncing = false;

function autoTypeEdge(sourceType: string, targetType: string): GraphStrategyEdge["edgeType"] {
  if (sourceType === "node-llm-request" && targetType === "node-execute-tool") return "onToolCall";
  if (sourceType === "node-llm-request" && (targetType === "node-finish" || targetType === "node-condition")) return "onAssistantMessage";
  return "onCondition";
}

export function GraphStrategyCanvas(props: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<NodeEditor<StrategySchemes> | null>(null);
  const areaRef = useRef<AreaPlugin<StrategySchemes, any> | null>(null);
  const propsRef = useRef(props);
  propsRef.current = props;

  useEffect(() => {
    if (!containerRef.current) return;

    const init = async () => {
      const { editor, area } = await createStrategyEditor(containerRef.current!);
      editorRef.current = editor;
      areaRef.current = area;
      (window as any).__strategyReteEditor = editor;

      // Node selection
      area.addPipe((context) => {
        if (context.type === "nodepicked") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "strategyNodeId" in reteNode) {
            propsRef.current.onEdgeSelected(null);
            propsRef.current.onNodeSelected((reteNode as any).strategyNodeId);
          }
        }
        return context;
      });

      // Node drag
      area.addPipe((context) => {
        if (context.type === "nodetranslated") {
          const reteNode = editor.getNode(context.data.id);
          if (reteNode && "strategyNodeId" in reteNode) {
            syncing = true;
            const nodes = propsRef.current.strategyNodes.map((sn) =>
              sn.id === (reteNode as any).strategyNodeId
                ? { ...sn, position: context.data.position }
                : sn
            );
            propsRef.current.onNodesChanged(nodes);
            syncing = false;
          }
        }
        return context;
      });

      // Connection created
      editor.addPipe((context) => {
        if (context.type === "connectioncreated") {
          syncing = true;
          const conn = context.data;
          const sourceNode = editor.getNode(conn.source);
          const targetNode = editor.getNode(conn.target);
          if (sourceNode && targetNode && "strategyNodeId" in sourceNode && "strategyNodeId" in targetNode) {
            const sourceSnId = (sourceNode as any).strategyNodeId;
            const targetSnId = (targetNode as any).strategyNodeId;
            const sourceSn = propsRef.current.strategyNodes.find((sn) => sn.id === sourceSnId);
            const targetSn = propsRef.current.strategyNodes.find((sn) => sn.id === targetSnId);
            const edgeType = autoTypeEdge(sourceSn?.type || "", targetSn?.type || "");
            const newEdge: GraphStrategyEdge = {
              id: `se-${Date.now()}`,
              source: sourceSnId,
              target: targetSnId,
              edgeType,
            };
            propsRef.current.onEdgesChanged([...propsRef.current.strategyEdges, newEdge]);
          }
          syncing = false;
        }
        return context;
      });

      // Edge deletion via custom event
      const handleEdgeDelete = (e: Event) => {
        const detail = (e as CustomEvent).detail;
        const sourceRete = editor.getNode(detail.source);
        const targetRete = editor.getNode(detail.target);
        if (sourceRete && targetRete && "strategyNodeId" in sourceRete && "strategyNodeId" in targetRete) {
          const srcId = (sourceRete as any).strategyNodeId;
          const tgtId = (targetRete as any).strategyNodeId;
          const newEdges = propsRef.current.strategyEdges.filter(
            (se) => !(se.source === srcId && se.target === tgtId)
          );
          propsRef.current.onEdgesChanged(newEdges);
        }
      };
      window.addEventListener("strategy-edge-delete", handleEdgeDelete);

      // Initial render
      await syncToRete(editor, area, propsRef.current.strategyNodes, propsRef.current.strategyEdges);
    };

    init();

    return () => {
      areaRef.current?.destroy();
      editorRef.current = null;
      areaRef.current = null;
      delete (window as any).__strategyReteEditor;
    };
  }, []);

  // Sync store changes to Rete
  useEffect(() => {
    if (syncing || !editorRef.current || !areaRef.current) return;
    syncToRete(editorRef.current, areaRef.current, props.strategyNodes, props.strategyEdges);
  }, [props.strategyNodes, props.strategyEdges]);

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full relative"
      style={{
        minHeight: "400px",
        background: "radial-gradient(circle, rgba(99, 102, 241, 0.04) 1px, transparent 1px), var(--background)",
        backgroundSize: "20px 20px",
      }}
    />
  );
}

function createReteNode(sn: GraphStrategyNode) {
  switch (sn.type) {
    case "node-start": return new StartReteNode(sn.id);
    case "node-finish": return new FinishReteNode(sn.id);
    case "node-llm-request": return new LLMRequestReteNode(sn.id, sn.data.label || "LLM Request");
    case "node-execute-tool": return new ExecuteToolReteNode(sn.id, sn.data.label || "Execute Tool");
    case "node-llm-send-tool-result": return new SendToolResultReteNode(sn.id, sn.data.label || "Send Tool Result");
    case "node-condition": return new ConditionReteNode(sn.id, sn.data.label || "Condition");
  }
}

async function syncToRete(
  editor: NodeEditor<StrategySchemes>,
  area: AreaPlugin<StrategySchemes, any>,
  strategyNodes: GraphStrategyNode[],
  strategyEdges: GraphStrategyEdge[]
) {
  const findReteNode = (snId: string) =>
    editor.getNodes().find((n: any) => "strategyNodeId" in n && n.strategyNodeId === snId);

  // Add missing nodes
  for (const sn of strategyNodes) {
    if (!findReteNode(sn.id)) {
      const reteNode = createReteNode(sn);
      await editor.addNode(reteNode);
      await area.translate(reteNode.id, sn.position);
    }
  }

  // Remove stale nodes
  for (const rn of editor.getNodes()) {
    if ("strategyNodeId" in rn) {
      const snId = (rn as any).strategyNodeId;
      if (!strategyNodes.find((sn) => sn.id === snId)) {
        await editor.removeNode(rn.id);
      }
    }
  }

  // Update labels
  for (const sn of strategyNodes) {
    const rn = findReteNode(sn.id);
    if (rn) {
      const newLabel = sn.data.label || sn.type.replace("node-", "").replace(/-/g, " ");
      if (rn.label !== newLabel) {
        rn.label = newLabel;
        area.update("node", rn.id);
      }
    }
  }

  // Sync edges
  const reteConns = editor.getConnections();
  const storeEdgeKeys = new Set(strategyEdges.map((se) => `${se.source}->${se.target}`));

  for (const conn of reteConns) {
    const srcNode = editor.getNode(conn.source);
    const tgtNode = editor.getNode(conn.target);
    if (!srcNode || !tgtNode) {
      syncing = true;
      await editor.removeConnection(conn.id);
      syncing = false;
      continue;
    }
    const srcId = "strategyNodeId" in srcNode ? (srcNode as any).strategyNodeId : null;
    const tgtId = "strategyNodeId" in tgtNode ? (tgtNode as any).strategyNodeId : null;
    if (srcId && tgtId && !storeEdgeKeys.has(`${srcId}->${tgtId}`)) {
      syncing = true;
      await editor.removeConnection(conn.id);
      syncing = false;
    }
  }

  const currentConns = editor.getConnections();
  const reteEdgeKeys = new Set(currentConns.map((c) => {
    const sn = editor.getNode(c.source);
    const tn = editor.getNode(c.target);
    const sid = sn && "strategyNodeId" in sn ? (sn as any).strategyNodeId : null;
    const tid = tn && "strategyNodeId" in tn ? (tn as any).strategyNodeId : null;
    return `${sid}->${tid}`;
  }));

  for (const se of strategyEdges) {
    if (!reteEdgeKeys.has(`${se.source}->${se.target}`)) {
      const srcRete = findReteNode(se.source);
      const tgtRete = findReteNode(se.target);
      if (srcRete && tgtRete) {
        const outKey = Object.keys(srcRete.outputs || {})[0];
        const inKey = Object.keys(tgtRete.inputs || {})[0];
        if (outKey && inKey) {
          syncing = true;
          const { ClassicPreset } = await import("rete");
          const conn = new ClassicPreset.Connection(srcRete, outKey, tgtRete, inKey);
          await editor.addConnection(conn as any);
          syncing = false;
        }
      }
    }
  }
}
```

- [ ] **Step 5: Create GraphStrategyEditor.tsx**

```tsx
"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useEditorStore } from "@/store/editorStore";
import { GraphAgentConfig, GraphStrategyNode, GraphStrategyEdge, GraphStrategyNodeType } from "@/types/graph-agent";
import { GraphStrategyToolbar } from "./GraphStrategyToolbar";
import { GraphStrategyCanvas } from "./GraphStrategyCanvas";
import { StrategyNodeProperties } from "./StrategyNodeProperties";
import { StrategyEdgeProperties } from "./StrategyEdgeProperties";
import { XIcon } from "lucide-react";

let nextStrategyNodeId = 1000;

export function GraphStrategyEditor({
  nodeId,
  data,
  onClose,
}: {
  nodeId: string;
  data: GraphAgentConfig;
  onClose: () => void;
}) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const edges = useEditorStore((s) => s.edges);
  const nodes = useEditorStore((s) => s.nodes);
  const [selectedStrategyNodeId, setSelectedStrategyNodeId] = useState<string | null>(null);
  const [selectedStrategyEdgeId, setSelectedStrategyEdgeId] = useState<string | null>(null);

  // Find tools connected to this graph-agent on the main canvas
  const connectedToolNames: string[] = edges
    .filter((e) => e.target === nodeId)
    .map((e) => nodes.find((n) => n.id === e.source))
    .filter((n) => n && (n.type === "tool" || n.type === "builtin-tool"))
    .map((n) => (n!.data as any).name || (n!.data as any).toolType || "")
    .filter((name) => name);

  const update = (field: Partial<GraphAgentConfig>) => updateNodeData(nodeId, field);

  const handleAddNode = (type: GraphStrategyNodeType) => {
    const id = `sn-${nextStrategyNodeId++}`;
    const defaultLabels: Record<string, string> = {
      "node-llm-request": "llmRequest",
      "node-execute-tool": "executeTool",
      "node-llm-send-tool-result": "sendResult",
      "node-condition": "condition",
    };
    const newNode: GraphStrategyNode = {
      id,
      type,
      position: { x: 300, y: 200 },
      data: { label: defaultLabels[type] || type },
    };
    update({ strategyNodes: [...data.strategyNodes, newNode] });
  };

  const handleNodesChanged = (strategyNodes: GraphStrategyNode[]) => {
    update({ strategyNodes });
  };

  const handleEdgesChanged = (strategyEdges: GraphStrategyEdge[]) => {
    update({ strategyEdges });
  };

  const handleNodeSelected = (snId: string | null) => {
    setSelectedStrategyNodeId(snId);
    setSelectedStrategyEdgeId(null);
  };

  const handleEdgeSelected = (edgeId: string | null) => {
    setSelectedStrategyEdgeId(edgeId);
    setSelectedStrategyNodeId(null);
  };

  const handleUpdateStrategyNodeData = (snId: string, dataUpdate: Partial<GraphStrategyNode["data"]>) => {
    const updatedNodes = data.strategyNodes.map((sn) =>
      sn.id === snId ? { ...sn, data: { ...sn.data, ...dataUpdate } } : sn
    );
    update({ strategyNodes: updatedNodes });
  };

  const handleUpdateEdge = (edgeId: string, edgeUpdate: Partial<GraphStrategyEdge>) => {
    const updatedEdges = data.strategyEdges.map((se) =>
      se.id === edgeId ? { ...se, ...edgeUpdate } : se
    );
    update({ strategyEdges: updatedEdges });
  };

  const handleDeleteStrategyNode = (snId: string) => {
    const sn = data.strategyNodes.find((n) => n.id === snId);
    if (sn?.type === "node-start" || sn?.type === "node-finish") return; // Can't delete start/finish
    const updatedNodes = data.strategyNodes.filter((n) => n.id !== snId);
    const updatedEdges = data.strategyEdges.filter((e) => e.source !== snId && e.target !== snId);
    update({ strategyNodes: updatedNodes, strategyEdges: updatedEdges });
    if (selectedStrategyNodeId === snId) setSelectedStrategyNodeId(null);
  };

  const selectedNode = data.strategyNodes.find((sn) => sn.id === selectedStrategyNodeId);
  const selectedEdge = data.strategyEdges.find((se) => se.id === selectedStrategyEdgeId);

  return (
    <div className="fixed inset-y-0 right-0 w-[70%] bg-background border-l-2 border-indigo-500/40 z-50 flex flex-col shadow-2xl">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Workspace</span>
          <span className="text-muted-foreground">/</span>
          <span className="font-semibold">{data.name || "Graph Agent"}</span>
          <span className="text-muted-foreground">/</span>
          <span className="text-indigo-400">Strategy</span>
        </div>
        <Button variant="ghost" size="sm" onClick={onClose} className="h-7 w-7 p-0">
          <XIcon className="h-4 w-4" />
        </Button>
      </div>

      {/* Strategy Toolbar */}
      <GraphStrategyToolbar onAddNode={handleAddNode} />

      {/* Canvas + Properties */}
      <div className="flex-1 flex overflow-hidden">
        <GraphStrategyCanvas
          strategyNodes={data.strategyNodes}
          strategyEdges={data.strategyEdges}
          onNodeSelected={handleNodeSelected}
          onEdgeSelected={handleEdgeSelected}
          onNodesChanged={handleNodesChanged}
          onEdgesChanged={handleEdgesChanged}
        />

        {/* Right panel */}
        <div className="w-64 border-l bg-background overflow-y-auto">
          {selectedNode && (
            <div>
              <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Node Properties</div>
              <StrategyNodeProperties
                node={selectedNode}
                connectedTools={connectedToolNames}
                onUpdate={(dataUpdate) => handleUpdateStrategyNodeData(selectedNode.id, dataUpdate)}
              />
              {selectedNode.type !== "node-start" && selectedNode.type !== "node-finish" && (
                <div className="px-4 pb-4">
                  <Button variant="destructive" size="sm" className="w-full" onClick={() => handleDeleteStrategyNode(selectedNode.id)}>
                    Delete Node
                  </Button>
                </div>
              )}
            </div>
          )}
          {selectedEdge && (
            <div>
              <div className="px-4 pt-3 pb-1 text-xs font-semibold text-muted-foreground uppercase">Edge Properties</div>
              <StrategyEdgeProperties
                edge={selectedEdge}
                onUpdate={(edgeUpdate) => handleUpdateEdge(selectedEdge.id, edgeUpdate)}
              />
            </div>
          )}
          {!selectedNode && !selectedEdge && (
            <div className="p-4 text-sm text-muted-foreground">Select a node or edge to edit properties.</div>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/graph-agent/
git commit -m "feat(graph-agent): add sub-canvas editor with toolbar, canvas, and properties panels"
```

---

### Task 13: Run All Tests and Final Verification

**Files:** None (verification only)

- [ ] **Step 1: Run backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --info`
Expected: All tests PASSED

- [ ] **Step 2: Run frontend build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 3: Run frontend lint (if available)**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run lint`
Expected: No errors

- [ ] **Step 4: Manual smoke test**

1. Start the app
2. Click "Graph Agent" in toolbar -> template dialog appears
3. Select "Tool-Calling Agent" -> Graph Agent node appears on canvas
4. Connect a Tool node to the Graph Agent
5. Click Graph Agent node -> properties panel shows
6. Click "Edit Strategy Graph" -> sub-canvas opens
7. Verify Start, LLM Request, Execute Tool, Send Result, Finish nodes visible
8. Verify edges are visible and properly connected
9. Click an edge -> properties panel shows edge type
10. Click back -> return to main canvas
11. Generate/Export -> verify Strategy file appears in VFS

- [ ] **Step 5: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix(graph-agent): address smoke test issues"
```
