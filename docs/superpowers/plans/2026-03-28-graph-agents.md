# Graph Agents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Graph Agents as a first-class feature — visual state-machine modeling of Koog strategies with nodes, conditional edges, validation, persistence, and Kotlin code generation.

**Architecture:** Extend existing infrastructure (NodeType union, editorStore, Rete canvas, export pipeline) with 7 new node types and edge config data. Domain model (`KoogGraphAgent`) is derived at export/validation boundaries via the adapter pattern already used for other agent types.

**Tech Stack:** Next.js + React + Rete.js + Zustand (frontend), Spring Boot + Kotlin (backend), JUnit 5 (tests)

---

## File Map

### Frontend — New Files
- `frontend/types/graph-agent.ts` — Config types for all 7 new node types + edge config
- `frontend/components/panels/GraphAgentProperties.tsx` — Properties panel for Graph Agent container
- `frontend/components/panels/StrategyNodeProperties.tsx` — Properties panels for all 6 strategy node types
- `frontend/components/panels/EdgePropertiesPanel.tsx` — Properties panel for strategy edges

### Frontend — Modified Files
- `frontend/types/graph.ts` — Add new NodeType values, extend GraphEdge with optional `data` field
- `frontend/lib/rete-nodes.ts` — Add 7 new Rete node classes + StrategySocket + update AppNode union
- `frontend/lib/rete-setup.ts` — Add connection validation rules for strategy nodes
- `frontend/lib/validation.ts` — Add graph-agent-specific validation rules
- `frontend/store/editorStore.ts` — Add defaults for new node types, `selectedEdgeId`, `createGraphAgentFromTemplate()`
- `frontend/components/editor/Canvas.tsx` — Add Rete↔Store sync for new node types + strategy edge connections
- `frontend/components/editor/CustomNode.tsx` — Add icons, colors, labels, subtitles for new node types
- `frontend/components/panels/Toolbar.tsx` — Add Graph Agent button + Strategy Nodes section
- `frontend/components/panels/PropertiesPanel.tsx` — Add cases for new node types + edge selection

### Backend — New Files
- `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt` ��� Domain model
- `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt` — Kotlin code generator

### Backend — Modified Files
- `src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt` — Add optional `data` field to GraphEdge
- `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt` — Add `convertGraphAgents()` method
- `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt` — Add graph-agent validation rules
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt` — Integrate graph agent export

### Test Files — New
- `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterGraphAgentTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/GraphAgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorGraphAgentTest.kt`

---

## Task 1: Frontend Types & Edge Model

**Files:**
- Create: `frontend/types/graph-agent.ts`
- Modify: `frontend/types/graph.ts:30-43`

- [ ] **Step 1: Create `frontend/types/graph-agent.ts`**

```typescript
import { ModelProvider } from "./koog";

export interface GraphAgentConfig {
  name: string;
  description: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
}

export interface StrategyStartConfig {
  label: string;
  parentGraphAgentId: string;
}

export interface StrategyFinishConfig {
  label: string;
  parentGraphAgentId: string;
}

export interface StrategyLLMRequestConfig {
  label: string;
  parentGraphAgentId: string;
  systemPrompt?: string;
  maxTokens?: number;
}

export interface StrategyExecuteToolConfig {
  label: string;
  parentGraphAgentId: string;
}

export interface StrategySendToolResultConfig {
  label: string;
  parentGraphAgentId: string;
}

export interface StrategyConditionConfig {
  label: string;
  parentGraphAgentId: string;
  expression: string;
}

export type StrategyNodeConfig =
  | StrategyStartConfig
  | StrategyFinishConfig
  | StrategyLLMRequestConfig
  | StrategyExecuteToolConfig
  | StrategySendToolResultConfig
  | StrategyConditionConfig;

export type GraphEdgeType = "standard" | "onToolCall" | "onAssistantMessage" | "onCondition";

export interface GraphEdgeConfig {
  edgeType: GraphEdgeType;
  conditionExpression?: string;
  transformationExpression?: string;
  label?: string;
}
```

- [ ] **Step 2: Update `frontend/types/graph.ts`**

Add imports at the top:

```typescript
import { GraphAgentConfig, StrategyStartConfig, StrategyFinishConfig, StrategyLLMRequestConfig, StrategyExecuteToolConfig, StrategySendToolResultConfig, StrategyConditionConfig, GraphEdgeConfig } from "./graph-agent";
```

Change the `NodeType` line (line 30) from:

```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output";
```

to:

```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output" | "graph-agent" | "strategy-start" | "strategy-finish" | "strategy-llm-request" | "strategy-execute-tool" | "strategy-send-tool-result" | "strategy-condition";
```

Update the `GraphNode.data` union (line 36) to include the new config types:

```typescript
data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig | StrategyStartConfig | StrategyFinishConfig | StrategyLLMRequestConfig | StrategyExecuteToolConfig | StrategySendToolResultConfig | StrategyConditionConfig;
```

Update `GraphEdge` (lines 39-43) to add optional data:

```typescript
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  data?: GraphEdgeConfig;
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No new errors from type definitions (existing errors may exist)

- [ ] **Step 4: Commit**

```bash
git add frontend/types/graph-agent.ts frontend/types/graph.ts
git commit -m "feat(types): add Graph Agent and strategy node type definitions"
```

---

## Task 2: Backend Domain Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt:22-24`

- [ ] **Step 1: Create `GraphAgentModel.kt`**

```kotlin
package com.agentwork.modeler.export.model

data class KoogGraphAgent(
    val name: String,
    val modelProvider: ModelProvider,
    val modelId: String?,
    val temperature: Double?,
    val strategyNodes: List<KoogStrategyNode>,
    val strategyEdges: List<KoogStrategyEdge>,
    val tools: List<KoogTool>,
    val builtinTools: List<KoogBuiltinTool>,
    val mcpServers: List<KoogMcpServer>,
    val subAgents: List<KoogSubAgent>
)

data class KoogStrategyNode(
    val id: String,
    val type: StrategyNodeType,
    val label: String,
    val config: Map<String, String> = emptyMap()
)

data class KoogStrategyEdge(
    val sourceNodeId: String,
    val targetNodeId: String,
    val edgeType: StrategyEdgeType,
    val conditionExpression: String? = null,
    val transformationExpression: String? = null,
    val label: String? = null
)

enum class StrategyNodeType {
    START, FINISH, LLM_REQUEST, EXECUTE_TOOL, SEND_TOOL_RESULT, CONDITION
}

enum class StrategyEdgeType {
    STANDARD, ON_TOOL_CALL, ON_ASSISTANT_MESSAGE, ON_CONDITION
}
```

- [ ] **Step 2: Add optional `data` field to `GraphEdge` in `GraphModel.kt`**

Change line 22-24 from:

```kotlin
data class GraphEdge(
    val id: String,
    val source: String,
    val target: String
)
```

to:

```kotlin
data class GraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val data: Map<String, Any?>? = null
)
```

- [ ] **Step 3: Verify backend compiles**

Run: `./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/GraphAgentModel.kt src/main/kotlin/com/agentwork/modeler/export/model/GraphModel.kt
git commit -m "feat(model): add Graph Agent domain model and extend GraphEdge with data field"
```

---

## Task 3: Backend Adapter — `convertGraphAgents()`

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterGraphAgentTest.kt`

- [ ] **Step 1: Write test file `GraphToKoogAdapterGraphAgentTest.kt`**

```kotlin
package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphToKoogAdapterGraphAgentTest {

    private val adapter = GraphToKoogAdapter()

    @Test
    fun `converts linear graph agent with start, llm, finish`() {
        val graph = WorkspaceGraph(
            version = 1, name = "TestProject",
            nodes = listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "MyGraphAgent",
                    "modelProvider" to "openai",
                    "modelId" to "gpt-4o",
                    "temperature" to 0.7
                )),
                GraphNode("s1", "strategy-start", Position(0.0, 100.0), mapOf(
                    "label" to "Start",
                    "parentGraphAgentId" to "ga1"
                )),
                GraphNode("llm1", "strategy-llm-request", Position(0.0, 200.0), mapOf(
                    "label" to "CallLLM",
                    "parentGraphAgentId" to "ga1",
                    "systemPrompt" to "Be helpful"
                )),
                GraphNode("f1", "strategy-finish", Position(0.0, 300.0), mapOf(
                    "label" to "Finish",
                    "parentGraphAgentId" to "ga1"
                ))
            ),
            edges = listOf(
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "llm1", "f1", mapOf("edgeType" to "onAssistantMessage"))
            )
        )

        val agents = adapter.convertGraphAgents(graph)
        assertEquals(1, agents.size)
        assertEquals("MyGraphAgent", agents[0].name)
        assertEquals(ModelProvider.OPENAI, agents[0].modelProvider)
        assertEquals(3, agents[0].strategyNodes.size)
        assertEquals(2, agents[0].strategyEdges.size)
        assertEquals(StrategyNodeType.START, agents[0].strategyNodes[0].type)
        assertEquals(StrategyEdgeType.ON_ASSISTANT_MESSAGE, agents[0].strategyEdges[1].edgeType)
    }

    @Test
    fun `converts tool-calling loop with tools connected to graph agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "TestProject",
            nodes = listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf(
                    "name" to "ToolAgent",
                    "modelProvider" to "anthropic"
                )),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf(
                    "name" to "searchTool",
                    "description" to "Searches",
                    "parameters" to listOf(
                        mapOf("name" to "query", "type" to "String", "description" to "Query", "required" to true)
                    )
                )),
                GraphNode("s1", "strategy-start", Position(0.0, 100.0), mapOf(
                    "label" to "Start", "parentGraphAgentId" to "ga1"
                )),
                GraphNode("llm1", "strategy-llm-request", Position(0.0, 200.0), mapOf(
                    "label" to "CallLLM", "parentGraphAgentId" to "ga1"
                )),
                GraphNode("et1", "strategy-execute-tool", Position(0.0, 300.0), mapOf(
                    "label" to "ExecTool", "parentGraphAgentId" to "ga1"
                )),
                GraphNode("str1", "strategy-send-tool-result", Position(0.0, 400.0), mapOf(
                    "label" to "SendResult", "parentGraphAgentId" to "ga1"
                )),
                GraphNode("f1", "strategy-finish", Position(0.0, 500.0), mapOf(
                    "label" to "Finish", "parentGraphAgentId" to "ga1"
                ))
            ),
            edges = listOf(
                GraphEdge("e0", "t1", "ga1"),       // tool -> graph agent
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "llm1", "et1", mapOf("edgeType" to "onToolCall")),
                GraphEdge("e3", "llm1", "f1", mapOf("edgeType" to "onAssistantMessage")),
                GraphEdge("e4", "et1", "str1", mapOf("edgeType" to "standard")),
                GraphEdge("e5", "str1", "et1", mapOf("edgeType" to "onToolCall")),
                GraphEdge("e6", "str1", "f1", mapOf("edgeType" to "onAssistantMessage"))
            )
        )

        val agents = adapter.convertGraphAgents(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].tools.size)
        assertEquals("searchTool", agents[0].tools[0].name)
        assertEquals(5, agents[0].strategyNodes.size)
        assertEquals(6, agents[0].strategyEdges.size)
    }

    @Test
    fun `ignores strategy nodes without parentGraphAgentId`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "modelProvider" to "openai")),
                GraphNode("s1", "strategy-start", Position(0.0, 100.0), mapOf("label" to "Start", "parentGraphAgentId" to "ga1")),
                GraphNode("s2", "strategy-start", Position(0.0, 200.0), mapOf("label" to "Orphan"))  // no parentGraphAgentId
            ),
            edges = emptyList()
        )

        val agents = adapter.convertGraphAgents(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].strategyNodes.size)
    }

    @Test
    fun `converts multiple graph agents in same workspace`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "modelProvider" to "openai")),
                GraphNode("ga2", "graph-agent", Position(400.0, 0.0), mapOf("name" to "Agent2", "modelProvider" to "anthropic")),
                GraphNode("s1", "strategy-start", Position(0.0, 100.0), mapOf("label" to "Start", "parentGraphAgentId" to "ga1")),
                GraphNode("s2", "strategy-start", Position(400.0, 100.0), mapOf("label" to "Start", "parentGraphAgentId" to "ga2"))
            ),
            edges = emptyList()
        )

        val agents = adapter.convertGraphAgents(graph)
        assertEquals(2, agents.size)
        assertEquals("Agent1", agents[0].name)
        assertEquals("Agent2", agents[1].name)
    }

    @Test
    fun `converts edge with onCondition and transformation`() {
        val graph = WorkspaceGraph(
            version = 1, name = "Test",
            nodes = listOf(
                GraphNode("ga1", "graph-agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "modelProvider" to "openai")),
                GraphNode("s1", "strategy-start", Position(0.0, 100.0), mapOf("label" to "Start", "parentGraphAgentId" to "ga1")),
                GraphNode("f1", "strategy-finish", Position(0.0, 200.0), mapOf("label" to "Finish", "parentGraphAgentId" to "ga1"))
            ),
            edges = listOf(
                GraphEdge("e1", "s1", "f1", mapOf(
                    "edgeType" to "onCondition",
                    "conditionExpression" to "it.isValid()",
                    "transformationExpression" to "it.value",
                    "label" to "valid path"
                ))
            )
        )

        val agents = adapter.convertGraphAgents(graph)
        val edge = agents[0].strategyEdges[0]
        assertEquals(StrategyEdgeType.ON_CONDITION, edge.edgeType)
        assertEquals("it.isValid()", edge.conditionExpression)
        assertEquals("it.value", edge.transformationExpression)
        assertEquals("valid path", edge.label)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterGraphAgentTest" 2>&1 | tail -10`
Expected: FAIL — `convertGraphAgents` method does not exist yet

- [ ] **Step 3: Implement `convertGraphAgents()` in `GraphToKoogAdapter.kt`**

Add this method at the end of the class (before the closing `}`), after the `extractA2AConfig` method:

```kotlin
    fun convertGraphAgents(graph: WorkspaceGraph): List<KoogGraphAgent> {
        val graphAgentNodes = graph.nodes.filter { it.type == "graph-agent" }
        val strategyTypes = setOf(
            "strategy-start", "strategy-finish", "strategy-llm-request",
            "strategy-execute-tool", "strategy-send-tool-result", "strategy-condition"
        )
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
        val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
        val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }

        return graphAgentNodes.map { gaNode ->
            val name = gaNode.data["name"] as? String ?: ""
            require(name.isNotBlank()) { "Graph Agent requires a name" }

            // Collect strategy nodes belonging to this graph agent
            val strategyNodes = graph.nodes
                .filter { it.type in strategyTypes && it.data["parentGraphAgentId"] == gaNode.id }
                .map { convertStrategyNode(it) }

            val strategyNodeIds = graph.nodes
                .filter { it.type in strategyTypes && it.data["parentGraphAgentId"] == gaNode.id }
                .map { it.id }
                .toSet()

            // Collect strategy edges (both source and target are strategy nodes of this agent)
            val strategyEdges = graph.edges
                .filter { it.source in strategyNodeIds && it.target in strategyNodeIds }
                .map { convertStrategyEdge(it) }

            // Collect tools/MCP/sub-agents connected to graph agent container
            val connectedIds = graph.edges
                .filter { it.target == gaNode.id }
                .map { it.source }

            val tools = toolNodes
                .filter { it.id in connectedIds }
                .map { convertTool(it) }

            val builtinTools = builtinToolNodes
                .filter { it.id in connectedIds }
                .map { convertBuiltinTool(it) }

            val mcpServers = mcpServerNodes
                .filter { it.id in connectedIds }
                .map { convertMcpServer(it) }

            val subAgents = subAgentNodes
                .filter { it.id in connectedIds }
                .map { saNode ->
                    val inputNodes = graph.nodes.filter { it.type == "input" }
                    val outputNodes = graph.nodes.filter { it.type == "output" }
                    convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes)
                }

            KoogGraphAgent(
                name = name,
                modelProvider = parseProvider(gaNode.data["modelProvider"] as? String ?: "openai"),
                modelId = gaNode.data["modelId"] as? String,
                temperature = (gaNode.data["temperature"] as? Number)?.toDouble(),
                strategyNodes = strategyNodes,
                strategyEdges = strategyEdges,
                tools = tools,
                builtinTools = builtinTools,
                mcpServers = mcpServers,
                subAgents = subAgents
            )
        }
    }

    private fun convertStrategyNode(node: GraphNode): KoogStrategyNode {
        val type = when (node.type) {
            "strategy-start" -> StrategyNodeType.START
            "strategy-finish" -> StrategyNodeType.FINISH
            "strategy-llm-request" -> StrategyNodeType.LLM_REQUEST
            "strategy-execute-tool" -> StrategyNodeType.EXECUTE_TOOL
            "strategy-send-tool-result" -> StrategyNodeType.SEND_TOOL_RESULT
            "strategy-condition" -> StrategyNodeType.CONDITION
            else -> throw IllegalArgumentException("Unknown strategy node type: ${node.type}")
        }
        val config = mutableMapOf<String, String>()
        (node.data["systemPrompt"] as? String)?.let { config["systemPrompt"] = it }
        (node.data["expression"] as? String)?.let { config["expression"] = it }
        (node.data["maxTokens"] as? Number)?.let { config["maxTokens"] = it.toString() }

        return KoogStrategyNode(
            id = node.id,
            type = type,
            label = node.data["label"] as? String ?: type.name,
            config = config
        )
    }

    private fun convertStrategyEdge(edge: GraphEdge): KoogStrategyEdge {
        val data = edge.data ?: emptyMap()
        val edgeType = when (data["edgeType"] as? String) {
            "onToolCall" -> StrategyEdgeType.ON_TOOL_CALL
            "onAssistantMessage" -> StrategyEdgeType.ON_ASSISTANT_MESSAGE
            "onCondition" -> StrategyEdgeType.ON_CONDITION
            else -> StrategyEdgeType.STANDARD
        }
        return KoogStrategyEdge(
            sourceNodeId = edge.source,
            targetNodeId = edge.target,
            edgeType = edgeType,
            conditionExpression = data["conditionExpression"] as? String,
            transformationExpression = data["transformationExpression"] as? String,
            label = data["label"] as? String
        )
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.adapter.GraphToKoogAdapterGraphAgentTest" 2>&1 | tail -10`
Expected: All 5 tests PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: No regressions

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterGraphAgentTest.kt
git commit -m "feat(adapter): add convertGraphAgents() for mapping graph agents to domain model"
```

---

## Task 4: Backend Validation

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorGraphAgentTest.kt`

- [ ] **Step 1: Write test file `GraphValidatorGraphAgentTest.kt`**

```kotlin
package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphValidatorGraphAgentTest {

    private val validator = GraphValidator()

    private fun graphAgent(name: String = "Agent1") = GraphNode(
        "ga1", "graph-agent", Position(0.0, 0.0),
        mapOf("name" to name, "modelProvider" to "openai")
    )

    private fun start(parent: String = "ga1") = GraphNode(
        "s1", "strategy-start", Position(0.0, 100.0),
        mapOf("label" to "Start", "parentGraphAgentId" to parent)
    )

    private fun finish(parent: String = "ga1") = GraphNode(
        "f1", "strategy-finish", Position(0.0, 300.0),
        mapOf("label" to "Finish", "parentGraphAgentId" to parent)
    )

    private fun llm(id: String = "llm1", parent: String = "ga1") = GraphNode(
        id, "strategy-llm-request", Position(0.0, 200.0),
        mapOf("label" to "LLM", "parentGraphAgentId" to parent)
    )

    @Test
    fun `valid linear graph agent produces no errors`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), llm(), finish()),
            edges = listOf(
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "llm1", "f1", mapOf("edgeType" to "standard"))
            )
        )
        val errors = validator.validate(graph)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `graph agent without name produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(name = ""), start(), finish()),
            edges = listOf(GraphEdge("e1", "s1", "f1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Graph Agent requires a name") })
    }

    @Test
    fun `graph agent without start node produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), finish()),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("must have a Start node") })
    }

    @Test
    fun `graph agent without finish node produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start()),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("must have a Finish node") })
    }

    @Test
    fun `start without outgoing edge produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), finish()),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Start node must have at least one outgoing edge") })
    }

    @Test
    fun `finish not reachable from start produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), llm(), finish()),
            edges = listOf(
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard"))
                // llm1 -> f1 edge missing: finish not reachable
            )
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Finish node is not reachable from Start") })
    }

    @Test
    fun `orphan strategy node produces error`() {
        val orphan = GraphNode("orphan1", "strategy-llm-request", Position(0.0, 500.0),
            mapOf("label" to "Orphan", "parentGraphAgentId" to "ga1"))
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), llm(), finish(), orphan),
            edges = listOf(
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "llm1", "f1", mapOf("edgeType" to "standard"))
            )
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("not reachable") && it.contains("orphan1") })
    }

    @Test
    fun `condition node without expression produces error`() {
        val cond = GraphNode("c1", "strategy-condition", Position(0.0, 200.0),
            mapOf("label" to "Check", "parentGraphAgentId" to "ga1", "expression" to ""))
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), cond, finish()),
            edges = listOf(
                GraphEdge("e1", "s1", "c1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "c1", "f1", mapOf("edgeType" to "standard"))
            )
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Condition node") && it.contains("expression") })
    }

    @Test
    fun `onCondition edge without condition expression produces error`() {
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), llm(), finish()),
            edges = listOf(
                GraphEdge("e1", "s1", "llm1", mapOf("edgeType" to "standard")),
                GraphEdge("e2", "llm1", "f1", mapOf("edgeType" to "onCondition"))
                // missing conditionExpression
            )
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("onCondition") && it.contains("expression") })
    }

    @Test
    fun `strategy node with invalid parentGraphAgentId produces error`() {
        val badNode = GraphNode("s2", "strategy-start", Position(0.0, 100.0),
            mapOf("label" to "Start", "parentGraphAgentId" to "nonexistent"))
        val graph = WorkspaceGraph(1, "Test",
            nodes = listOf(graphAgent(), start(), finish(), badNode),
            edges = listOf(GraphEdge("e1", "s1", "f1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("invalid parentGraphAgentId") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorGraphAgentTest" 2>&1 | tail -10`
Expected: FAIL — validation rules not implemented yet

- [ ] **Step 3: Implement graph agent validation in `GraphValidator.kt`**

First, update the top-level agent count check (lines 10-18). Replace:

```kotlin
        val agentCount = graph.nodes.count { it.type == "agent" }
        val plannerCount = graph.nodes.count { it.type == "planner-agent" }
        if (agentCount == 0 && plannerCount == 0) {
            errors.add("Workspace must have at least one agent or planner-agent")
        }
        if (agentCount > 0 && plannerCount > 0) {
            errors.add("Workspace cannot have both a regular agent and a planner-agent")
        }
```

with:

```kotlin
        val agentCount = graph.nodes.count { it.type == "agent" }
        val plannerCount = graph.nodes.count { it.type == "planner-agent" }
        val graphAgentCount = graph.nodes.count { it.type == "graph-agent" }
        if (agentCount == 0 && plannerCount == 0 && graphAgentCount == 0) {
            errors.add("Workspace must have at least one agent, planner-agent, or graph-agent")
        }
        if (agentCount > 0 && plannerCount > 0) {
            errors.add("Workspace cannot have both a regular agent and a planner-agent")
        }
```

Then add a new `when` branch inside the node loop for `"graph-agent"`:

```kotlin
                "graph-agent" -> {
                    val name = node.data["name"] as? String ?: ""
                    if (name.isBlank()) errors.add("[${node.id}] Graph Agent requires a name")
                }
```

After the existing node loop and the duplicate input/output name checks, add the graph agent strategy validation:

```kotlin
        // Graph Agent strategy validation
        val strategyTypes = setOf(
            "strategy-start", "strategy-finish", "strategy-llm-request",
            "strategy-execute-tool", "strategy-send-tool-result", "strategy-condition"
        )
        val graphAgentNodes = graph.nodes.filter { it.type == "graph-agent" }
        val allGraphAgentIds = graphAgentNodes.map { it.id }.toSet()

        // Validate strategy nodes reference valid graph agents
        for (sNode in graph.nodes.filter { it.type in strategyTypes }) {
            val parentId = sNode.data["parentGraphAgentId"] as? String
            if (parentId != null && parentId !in allGraphAgentIds) {
                errors.add("[${sNode.id}] Strategy node has invalid parentGraphAgentId '$parentId'")
            }
        }

        for (gaNode in graphAgentNodes) {
            val stratNodes = graph.nodes.filter {
                it.type in strategyTypes && it.data["parentGraphAgentId"] == gaNode.id
            }
            val stratNodeIds = stratNodes.map { it.id }.toSet()
            val stratEdges = graph.edges.filter { it.source in stratNodeIds && it.target in stratNodeIds }

            val startNodes = stratNodes.filter { it.type == "strategy-start" }
            val finishNodes = stratNodes.filter { it.type == "strategy-finish" }

            if (startNodes.isEmpty()) {
                errors.add("[${gaNode.id}] Graph Agent '${gaNode.data["name"]}' must have a Start node")
            }
            if (finishNodes.isEmpty()) {
                errors.add("[${gaNode.id}] Graph Agent '${gaNode.data["name"]}' must have a Finish node")
            }
            if (startNodes.size > 1) {
                errors.add("[${gaNode.id}] Graph Agent '${gaNode.data["name"]}' has more than one Start node")
            }

            // Start must have outgoing edge
            for (startNode in startNodes) {
                val hasOutgoing = stratEdges.any { it.source == startNode.id }
                if (!hasOutgoing) {
                    errors.add("[${startNode.id}] Start node must have at least one outgoing edge")
                }
            }

            // Finish reachability from start (BFS)
            if (startNodes.isNotEmpty() && finishNodes.isNotEmpty()) {
                val adjacency = mutableMapOf<String, MutableList<String>>()
                for (e in stratEdges) {
                    adjacency.getOrPut(e.source) { mutableListOf() }.add(e.target)
                }
                val reachable = mutableSetOf<String>()
                val queue = ArrayDeque(startNodes.map { it.id })
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (current in reachable) continue
                    reachable.add(current)
                    adjacency[current]?.forEach { queue.add(it) }
                }
                for (finishNode in finishNodes) {
                    if (finishNode.id !in reachable) {
                        errors.add("[${gaNode.id}] Finish node is not reachable from Start in Graph Agent '${gaNode.data["name"]}'")
                    }
                }
                // Orphan detection
                for (sNode in stratNodes) {
                    if (sNode.type != "strategy-start" && sNode.id !in reachable) {
                        errors.add("[${sNode.id}] Strategy node '${sNode.data["label"]}' is not reachable from Start")
                    }
                }
            }

            // Condition nodes must have expression
            for (condNode in stratNodes.filter { it.type == "strategy-condition" }) {
                val expr = condNode.data["expression"] as? String ?: ""
                if (expr.isBlank()) {
                    errors.add("[${condNode.id}] Condition node '${condNode.data["label"]}' requires an expression")
                }
            }

            // onCondition edges must have conditionExpression
            for (edge in stratEdges) {
                val data = edge.data ?: emptyMap()
                if (data["edgeType"] == "onCondition") {
                    val expr = data["conditionExpression"] as? String ?: ""
                    if (expr.isBlank()) {
                        errors.add("[${edge.id}] onCondition edge requires a condition expression")
                    }
                }
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorGraphAgentTest" 2>&1 | tail -15`
Expected: All 9 tests PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: No regressions (existing tests still pass)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorGraphAgentTest.kt
git commit -m "feat(validation): add graph agent strategy validation rules"
```

---

## Task 5: Backend Code Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/generator/GraphAgentGeneratorTest.kt`

- [ ] **Step 1: Write test file `GraphAgentGeneratorTest.kt`**

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GraphAgentGeneratorTest {

    private val generator = GraphAgentGenerator()

    @Test
    fun `generates simple linear strategy`() {
        val agent = KoogGraphAgent(
            name = "SimpleAgent",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("s1", StrategyNodeType.START, "Start"),
                KoogStrategyNode("llm1", StrategyNodeType.LLM_REQUEST, "CallLLM"),
                KoogStrategyNode("f1", StrategyNodeType.FINISH, "Finish")
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("s1", "llm1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("llm1", "f1", StrategyEdgeType.ON_ASSISTANT_MESSAGE)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            mcpServers = emptyList(),
            subAgents = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "strategy(\"simpleAgent\")")
        assertContains(code, "val callLLM by nodeLLMRequest()")
        assertContains(code, "edge(nodeStart forwardTo callLLM)")
        assertContains(code, "edge(callLLM forwardTo nodeFinish onAssistantMessage { true })")
        // nodeStart and nodeFinish should NOT be declared
        assertFalse(code.contains("val nodeStart"))
        assertFalse(code.contains("val nodeFinish"))
    }

    @Test
    fun `generates tool-calling loop`() {
        val agent = KoogGraphAgent(
            name = "ToolLoopAgent",
            modelProvider = ModelProvider.ANTHROPIC,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("s1", StrategyNodeType.START, "Start"),
                KoogStrategyNode("llm1", StrategyNodeType.LLM_REQUEST, "nodeCallLLM"),
                KoogStrategyNode("et1", StrategyNodeType.EXECUTE_TOOL, "nodeExecuteTool"),
                KoogStrategyNode("str1", StrategyNodeType.SEND_TOOL_RESULT, "nodeSendToolResult"),
                KoogStrategyNode("f1", StrategyNodeType.FINISH, "Finish")
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("s1", "llm1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("llm1", "et1", StrategyEdgeType.ON_TOOL_CALL),
                KoogStrategyEdge("llm1", "f1", StrategyEdgeType.ON_ASSISTANT_MESSAGE),
                KoogStrategyEdge("et1", "str1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("str1", "et1", StrategyEdgeType.ON_TOOL_CALL),
                KoogStrategyEdge("str1", "f1", StrategyEdgeType.ON_ASSISTANT_MESSAGE)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            mcpServers = emptyList(),
            subAgents = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "val nodeCallLLM by nodeLLMRequest()")
        assertContains(code, "val nodeExecuteTool by nodeExecuteTool()")
        assertContains(code, "val nodeSendToolResult by nodeLLMSendToolResult()")
        assertContains(code, "edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })")
        assertContains(code, "edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })")
    }

    @Test
    fun `generates onCondition with transformation`() {
        val agent = KoogGraphAgent(
            name = "CondAgent",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("s1", StrategyNodeType.START, "Start"),
                KoogStrategyNode("llm1", StrategyNodeType.LLM_REQUEST, "Review"),
                KoogStrategyNode("f1", StrategyNodeType.FINISH, "Finish")
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("s1", "llm1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("llm1", "f1", StrategyEdgeType.ON_CONDITION,
                    conditionExpression = "review -> review.isGood()",
                    transformationExpression = "review -> review.meal")
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            mcpServers = emptyList(),
            subAgents = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "onCondition { review -> review.isGood() }")
        assertContains(code, "transformed { review -> review.meal }")
    }

    @Test
    fun `sanitizes labels to valid Kotlin identifiers`() {
        val agent = KoogGraphAgent(
            name = "My Agent With Spaces!",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("s1", StrategyNodeType.START, "Start"),
                KoogStrategyNode("llm1", StrategyNodeType.LLM_REQUEST, "Call LLM Node!"),
                KoogStrategyNode("f1", StrategyNodeType.FINISH, "Finish")
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("s1", "llm1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("llm1", "f1", StrategyEdgeType.STANDARD)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            mcpServers = emptyList(),
            subAgents = emptyList()
        )

        val code = generator.generate(agent)
        assertContains(code, "strategy(\"myAgentWithSpaces\")")
        assertContains(code, "val callLLMNode by nodeLLMRequest()")
    }

    @Test
    fun `deterministic output for same input`() {
        val agent = KoogGraphAgent(
            name = "DetAgent",
            modelProvider = ModelProvider.OPENAI,
            modelId = null,
            temperature = null,
            strategyNodes = listOf(
                KoogStrategyNode("s1", StrategyNodeType.START, "Start"),
                KoogStrategyNode("llm1", StrategyNodeType.LLM_REQUEST, "LLM"),
                KoogStrategyNode("f1", StrategyNodeType.FINISH, "Finish")
            ),
            strategyEdges = listOf(
                KoogStrategyEdge("s1", "llm1", StrategyEdgeType.STANDARD),
                KoogStrategyEdge("llm1", "f1", StrategyEdgeType.STANDARD)
            ),
            tools = emptyList(),
            builtinTools = emptyList(),
            mcpServers = emptyList(),
            subAgents = emptyList()
        )

        val code1 = generator.generate(agent)
        val code2 = generator.generate(agent)
        assertEquals(code1, code2)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.GraphAgentGeneratorTest" 2>&1 | tail -10`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create `GraphAgentGenerator.kt`**

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class GraphAgentGenerator {

    fun generate(agent: KoogGraphAgent): String {
        val strategyName = sanitizeIdentifier(agent.name)
        val nodeById = agent.strategyNodes.associateBy { it.id }

        // Collect nodes that need declarations (not START, not FINISH)
        val declarableNodes = agent.strategyNodes
            .filter { it.type != StrategyNodeType.START && it.type != StrategyNodeType.FINISH }
            .sortedBy { node ->
                // Topological hint: order by first appearance as edge target
                val idx = agent.strategyEdges.indexOfFirst { it.targetNodeId == node.id }
                if (idx >= 0) idx else Int.MAX_VALUE
            }

        // Sort edges deterministically: by source node order, then target
        val nodeOrder = agent.strategyNodes.map { it.id }
        val sortedEdges = agent.strategyEdges.sortedWith(
            compareBy<KoogStrategyEdge> { nodeOrder.indexOf(it.sourceNodeId) }
                .thenBy { nodeOrder.indexOf(it.targetNodeId) }
        )

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.dsl.builder.forwardTo")
            appendLine("import ai.koog.agents.core.dsl.builder.strategy")
            appendLine("import ai.koog.agents.core.dsl.extension.*")
            appendLine()
            appendLine("val $strategyName = strategy(\"$strategyName\") {")

            // Node declarations
            for (node in declarableNodes) {
                val varName = sanitizeIdentifier(node.label)
                val factory = nodeTypeToFactory(node.type)
                appendLine("    val $varName by $factory")
            }

            if (declarableNodes.isNotEmpty()) appendLine()

            // Edge declarations
            for (edge in sortedEdges) {
                val sourceName = resolveNodeName(edge.sourceNodeId, nodeById)
                val targetName = resolveNodeName(edge.targetNodeId, nodeById)
                val edgeExpr = buildEdgeExpression(sourceName, targetName, edge)
                appendLine("    edge($edgeExpr)")
            }

            appendLine("}")
        }
    }

    private fun nodeTypeToFactory(type: StrategyNodeType): String = when (type) {
        StrategyNodeType.LLM_REQUEST -> "nodeLLMRequest()"
        StrategyNodeType.EXECUTE_TOOL -> "nodeExecuteTool()"
        StrategyNodeType.SEND_TOOL_RESULT -> "nodeLLMSendToolResult()"
        StrategyNodeType.CONDITION -> "nodeLLMRequest()" // conditions are modeled via edges, not node type
        else -> throw IllegalArgumentException("Cannot declare node of type $type")
    }

    private fun resolveNodeName(nodeId: String, nodeById: Map<String, KoogStrategyNode>): String {
        val node = nodeById[nodeId] ?: throw IllegalArgumentException("Node $nodeId not found")
        return when (node.type) {
            StrategyNodeType.START -> "nodeStart"
            StrategyNodeType.FINISH -> "nodeFinish"
            else -> sanitizeIdentifier(node.label)
        }
    }

    private fun buildEdgeExpression(source: String, target: String, edge: KoogStrategyEdge): String {
        val base = "$source forwardTo $target"
        val withType = when (edge.edgeType) {
            StrategyEdgeType.STANDARD -> base
            StrategyEdgeType.ON_TOOL_CALL -> "$base onToolCall { true }"
            StrategyEdgeType.ON_ASSISTANT_MESSAGE -> "$base onAssistantMessage { true }"
            StrategyEdgeType.ON_CONDITION -> {
                val cond = edge.conditionExpression ?: "true"
                "$base onCondition { $cond }"
            }
        }
        return if (edge.transformationExpression != null) {
            "$withType transformed { ${edge.transformationExpression} }"
        } else {
            withType
        }
    }

    companion object {
        fun sanitizeIdentifier(name: String): String {
            // Remove non-alphanumeric chars, convert to camelCase
            val words = name.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return "unnamed"
            return words.first().replaceFirstChar { it.lowercase() } +
                words.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.GraphAgentGeneratorTest" 2>&1 | tail -10`
Expected: All 5 tests PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: No regressions

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/GraphAgentGeneratorTest.kt
git commit -m "feat(generator): add GraphAgentGenerator for Koog strategy DSL code generation"
```

---

## Task 6: ExportService Integration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add `GraphAgentGenerator` to constructor**

Add to the constructor parameter list (after `inputOutputGenerator`):

```kotlin
    private val graphAgentGenerator: GraphAgentGenerator,
```

- [ ] **Step 2: Add graph agent export logic in `generateFiles()`**

After the existing planner agent block (around line 129, before `val agent = agents.first()`), add:

```kotlin
        val graphAgents = adapter.convertGraphAgents(graph)

        // If workspace has ONLY graph agents (no regular agents or planners)
        if (agents.isEmpty() && plannerAgents.isEmpty() && graphAgents.isNotEmpty()) {
            val firstGA = graphAgents.first()
            files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
            files["gradle.properties"] = gradlePropertiesGenerator.generate()
            files["Dockerfile"] = dockerfileGenerator.generate()
            files["build.gradle.kts"] = generateGraphAgentBuildGradle(firstGA)
            files["src/main/resources/application.properties"] = generateGraphAgentApplicationProperties(firstGA)
            files["$pkg/Application.kt"] = applicationGenerator.generate()

            for (ga in graphAgents) {
                val fileName = GraphAgentGenerator.sanitizeIdentifier(ga.name).replaceFirstChar { it.uppercase() }
                files["$pkg/strategy/${fileName}Strategy.kt"] = graphAgentGenerator.generate(ga)
            }

            return files
        }
```

After the existing agent export (before `return files` at the end), add:

```kotlin
        // Graph agents (when coexisting with regular agents)
        for (ga in graphAgents) {
            val fileName = GraphAgentGenerator.sanitizeIdentifier(ga.name).replaceFirstChar { it.uppercase() }
            files["$pkg/strategy/${fileName}Strategy.kt"] = graphAgentGenerator.generate(ga)
        }
```

- [ ] **Step 3: Add helper methods**

Add at the bottom of the class:

```kotlin
    private fun generateGraphAgentBuildGradle(ga: KoogGraphAgent): String = buildString {
        val koogVersion = BuildGradleGenerator.KOOG_VERSION
        val kotlinVersion = BuildGradleGenerator.KOTLIN_VERSION
        val springBootVersion = BuildGradleGenerator.SPRING_BOOT_VERSION
        val depMgmtVersion = BuildGradleGenerator.DEPENDENCY_MANAGEMENT_VERSION
        appendLine("""plugins {""")
        appendLine("""    kotlin("jvm") version "$kotlinVersion"""")
        appendLine("""    kotlin("plugin.spring") version "$kotlinVersion"""")
        appendLine("""    id("org.springframework.boot") version "$springBootVersion"""")
        appendLine("""    id("io.spring.dependency-management") version "$depMgmtVersion"""")
        appendLine("""}""")
        appendLine()
        appendLine("""java {""")
        appendLine("""    toolchain {""")
        appendLine("""        languageVersion = JavaLanguageVersion.of(21)""")
        appendLine("""    }""")
        appendLine("""}""")
        appendLine()
        appendLine("""repositories {""")
        appendLine("""    mavenCentral()""")
        appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")""")
        appendLine("""}""")
        appendLine()
        appendLine("""dependencies {""")
        appendLine("""    implementation("org.springframework.boot:spring-boot-starter-webflux")""")
        appendLine("""    implementation("org.jetbrains.kotlin:kotlin-reflect")""")
        appendLine("""    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")""")
        appendLine("""    implementation("ai.koog:koog-spring-boot-starter:$koogVersion")""")
        appendLine("""    implementation("ai.koog:koog-agents:$koogVersion")""")
        appendLine("""}""")
    }

    private fun generateGraphAgentApplicationProperties(ga: KoogGraphAgent): String = buildString {
        appendLine("server.port=\${SERVER_PORT:8080}")
        val providerKey = ga.modelProvider.name.lowercase()
        appendLine("${providerKey}.api-key=\${${ga.modelProvider.name}_API_KEY:}")
    }
```

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat(export): integrate GraphAgentGenerator into export pipeline"
```

---

## Task 7: Frontend — Rete Nodes & Socket System

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`

- [ ] **Step 1: Add `StrategySocket` and 7 new Rete node classes**

Add after the `ToolSocket` definition (after line 12):

```typescript
export class StrategySocket extends ClassicPreset.Socket {
  constructor() {
    super("Strategy");
  }
  isCompatibleWith(socket: ClassicPreset.Socket): boolean {
    return socket instanceof StrategySocket;
  }
}

export const strategySocket = new StrategySocket();
```

Add the 7 new classes before the `AppNode` type (before line 112):

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

export class StrategyStartReteNode extends ClassicPreset.Node {
  width = 160;
  height = 60;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Start");
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class StrategyFinishReteNode extends ClassicPreset.Node {
  width = 160;
  height = 60;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Finish");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
  }
}

export class StrategyLLMRequestReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "LLM Request");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class StrategyExecuteToolReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Execute Tool");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class StrategySendToolResultReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Send Tool Result");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}

export class StrategyConditionReteNode extends ClassicPreset.Node {
  width = 200;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Condition");
    this.addInput("in", new ClassicPreset.Input(strategySocket, "In", true));
    this.addOutput("out", new ClassicPreset.Output(strategySocket, "Out"));
  }
}
```

Update the `AppNode` type to include all new classes:

```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode | InputReteNode | OutputReteNode | GraphAgentReteNode | StrategyStartReteNode | StrategyFinishReteNode | StrategyLLMRequestReteNode | StrategyExecuteToolReteNode | StrategySendToolResultReteNode | StrategyConditionReteNode;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/rete-nodes.ts
git commit -m "feat(rete): add StrategySocket and 7 new Rete node classes for Graph Agents"
```

---

## Task 8: Frontend — Connection Rules

**Files:**
- Modify: `frontend/lib/rete-setup.ts`

- [ ] **Step 1: Add imports for new node classes**

Update the import from `./rete-nodes` to include:

```typescript
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
  SubAgentReteNode,
  McpServerReteNode,
  PlannerAgentReteNode,
  A2AAgentReteNode,
  InputReteNode,
  OutputReteNode,
  GraphAgentReteNode,
  StrategyStartReteNode,
  StrategyFinishReteNode,
  StrategyLLMRequestReteNode,
  StrategyExecuteToolReteNode,
  StrategySendToolResultReteNode,
  StrategyConditionReteNode,
} from "./rete-nodes";
```

- [ ] **Step 2: Add connection validation rules**

In the `editor.addPipe` block for `connectioncreate` (lines 62-108), add the following cases **before** the final `return undefined`:

```typescript
      // Graph Agent container: same rules as regular Agent for tool connections
      const isGraphAgentTarget = targetNode instanceof GraphAgentReteNode;
      const isGraphAgentSource = sourceNode instanceof GraphAgentReteNode;
      if (isToolSource && isGraphAgentTarget) return context;
      if (isSubAgentSource && isGraphAgentTarget) return context;
      if (isMcpSource && isGraphAgentTarget) return context;
      if (isA2ASource && isGraphAgentTarget) return context;
      if (isInputSource && isGraphAgentTarget) return context;
      if (isGraphAgentSource && isOutputTarget) return context;

      // Strategy node connections (StrategySocket)
      const isStrategySource = sourceNode instanceof StrategyStartReteNode ||
        sourceNode instanceof StrategyLLMRequestReteNode ||
        sourceNode instanceof StrategyExecuteToolReteNode ||
        sourceNode instanceof StrategySendToolResultReteNode ||
        sourceNode instanceof StrategyConditionReteNode;

      const isStrategyTarget = targetNode instanceof StrategyFinishReteNode ||
        targetNode instanceof StrategyLLMRequestReteNode ||
        targetNode instanceof StrategyExecuteToolReteNode ||
        targetNode instanceof StrategySendToolResultReteNode ||
        targetNode instanceof StrategyConditionReteNode;

      // Strategy -> Strategy (not Start as target, not Finish as source)
      if (isStrategySource && isStrategyTarget) return context;
```

- [ ] **Step 3: Commit**

```bash
git add frontend/lib/rete-setup.ts
git commit -m "feat(rete): add connection validation rules for strategy nodes and Graph Agent"
```

---

## Task 9: Frontend — Editor Store Defaults & Edge Selection

**Files:**
- Modify: `frontend/store/editorStore.ts`

- [ ] **Step 1: Add imports and defaults**

Add to the imports at the top:

```typescript
import { GraphAgentConfig, StrategyStartConfig, StrategyFinishConfig, StrategyLLMRequestConfig, StrategyExecuteToolConfig, StrategySendToolResultConfig, StrategyConditionConfig, GraphEdgeConfig } from "@/types/graph-agent";
```

Add default data objects after `defaultPlannerAgentData` (after line 102):

```typescript
const defaultGraphAgentData: GraphAgentConfig = {
  name: "",
  description: "",
  modelProvider: "openai",
};

const defaultStrategyStartData: StrategyStartConfig = {
  label: "Start",
  parentGraphAgentId: "",
};

const defaultStrategyFinishData: StrategyFinishConfig = {
  label: "Finish",
  parentGraphAgentId: "",
};

const defaultStrategyLLMRequestData: StrategyLLMRequestConfig = {
  label: "LLM Request",
  parentGraphAgentId: "",
};

const defaultStrategyExecuteToolData: StrategyExecuteToolConfig = {
  label: "Execute Tool",
  parentGraphAgentId: "",
};

const defaultStrategySendToolResultData: StrategySendToolResultConfig = {
  label: "Send Tool Result",
  parentGraphAgentId: "",
};

const defaultStrategyConditionData: StrategyConditionConfig = {
  label: "Condition",
  parentGraphAgentId: "",
  expression: "",
};
```

- [ ] **Step 2: Add `selectedEdgeId` to state and `addEdge` with optional data**

Add `selectedEdgeId` to the `EditorState` interface:

```typescript
  selectedEdgeId: string | null;
  setSelectedEdgeId: (id: string | null) => void;
  updateEdgeData: (id: string, data: Partial<GraphEdgeConfig>) => void;
```

Update the `addEdge` signature:

```typescript
  addEdge: (source: string, target: string, data?: GraphEdgeConfig) => void;
```

Update the `updateNodeData` type to include new config types:

```typescript
  updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig | GraphAgentConfig | StrategyStartConfig | StrategyFinishConfig | StrategyLLMRequestConfig | StrategyExecuteToolConfig | StrategySendToolResultConfig | StrategyConditionConfig>) => void;
```

- [ ] **Step 3: Add new node type cases in `addNode` and update `addEdge`**

In the `addNode` method, add cases before the final `else`:

```typescript
      } else if (type === "graph-agent") {
        data = { ...defaultGraphAgentData };
      } else if (type === "strategy-start") {
        data = { ...defaultStrategyStartData };
      } else if (type === "strategy-finish") {
        data = { ...defaultStrategyFinishData };
      } else if (type === "strategy-llm-request") {
        data = { ...defaultStrategyLLMRequestData };
      } else if (type === "strategy-execute-tool") {
        data = { ...defaultStrategyExecuteToolData };
      } else if (type === "strategy-send-tool-result") {
        data = { ...defaultStrategySendToolResultData };
      } else if (type === "strategy-condition") {
        data = { ...defaultStrategyConditionData };
      } else {
```

Update the `addEdge` method to accept optional data:

```typescript
  addEdge(source, target, data?) {
    pushHistory();
    set((state) => ({
      edges: [
        ...state.edges,
        { id: `edge-${nextId++}`, source, target, data },
      ],
    }));
  },
```

- [ ] **Step 4: Add `selectedEdgeId`, `setSelectedEdgeId`, and `updateEdgeData`**

Add to the store implementation:

```typescript
  selectedEdgeId: null,

  setSelectedEdgeId(id) {
    set({ selectedEdgeId: id, selectedNodeIds: [] });
  },

  updateEdgeData(id, data) {
    pushHistory();
    set((state) => ({
      edges: state.edges.map((e) =>
        e.id === id ? { ...e, data: { ...e.data, ...data } as GraphEdgeConfig } : e
      ),
    }));
  },
```

Also update `setSelectedNodeIds` to clear edge selection:

```typescript
  setSelectedNodeIds(ids) {
    set({ selectedNodeIds: ids, selectedEdgeId: null });
  },
```

- [ ] **Step 5: Add `createGraphAgentFromTemplate()` method**

Add to the `EditorState` interface:

```typescript
  createGraphAgentFromTemplate: (template: "empty" | "tool-calling" | "react-loop", position: { x: number; y: number }) => void;
```

Add the implementation:

```typescript
  createGraphAgentFromTemplate(template, position) {
    pushHistory();
    set((state) => {
      const gaId = `node-${nextId++}`;
      const gaNode: GraphNode = {
        id: gaId,
        type: "graph-agent",
        position,
        data: { ...defaultGraphAgentData },
      };

      if (template === "empty") {
        return { nodes: [...state.nodes, gaNode], selectedNodeIds: [gaId] };
      }

      const newNodes: GraphNode[] = [gaNode];
      const newEdges: GraphEdge[] = [];
      const yBase = position.y + 80;
      const xBase = position.x;

      const makeNode = (type: NodeType, label: string, yOff: number): GraphNode => {
        const id = `node-${nextId++}`;
        const defaults: Record<string, any> = {
          "strategy-start": { ...defaultStrategyStartData },
          "strategy-finish": { ...defaultStrategyFinishData },
          "strategy-llm-request": { ...defaultStrategyLLMRequestData },
          "strategy-execute-tool": { ...defaultStrategyExecuteToolData },
          "strategy-send-tool-result": { ...defaultStrategySendToolResultData },
          "strategy-condition": { ...defaultStrategyConditionData },
        };
        return {
          id,
          type,
          position: { x: xBase, y: yBase + yOff },
          data: { ...defaults[type], label, parentGraphAgentId: gaId },
        };
      };

      const makeEdge = (source: string, target: string, edgeType: string = "standard"): GraphEdge => ({
        id: `edge-${nextId++}`, source, target, data: { edgeType } as any,
      });

      if (template === "tool-calling") {
        const start = makeNode("strategy-start", "Start", 0);
        const llm = makeNode("strategy-llm-request", "LLM Request", 100);
        const execTool = makeNode("strategy-execute-tool", "Execute Tool", 200);
        const sendResult = makeNode("strategy-send-tool-result", "Send Tool Result", 300);
        const finish = makeNode("strategy-finish", "Finish", 400);
        newNodes.push(start, llm, execTool, sendResult, finish);
        newEdges.push(
          makeEdge(start.id, llm.id),
          makeEdge(llm.id, execTool.id, "onToolCall"),
          makeEdge(llm.id, finish.id, "onAssistantMessage"),
          makeEdge(execTool.id, sendResult.id),
          makeEdge(sendResult.id, execTool.id, "onToolCall"),
          makeEdge(sendResult.id, finish.id, "onAssistantMessage"),
        );
      }

      if (template === "react-loop") {
        const start = makeNode("strategy-start", "Start", 0);
        const think = makeNode("strategy-llm-request", "Think", 100);
        const execTool = makeNode("strategy-execute-tool", "Execute Tool", 200);
        const observe = makeNode("strategy-send-tool-result", "Observe", 300);
        const finish = makeNode("strategy-finish", "Finish", 400);
        newNodes.push(start, think, execTool, observe, finish);
        newEdges.push(
          makeEdge(start.id, think.id),
          makeEdge(think.id, execTool.id, "onToolCall"),
          makeEdge(think.id, finish.id, "onAssistantMessage"),
          makeEdge(execTool.id, observe.id),
          makeEdge(observe.id, think.id),
        );
      }

      return {
        nodes: [...state.nodes, ...newNodes],
        edges: [...state.edges, ...newEdges],
        selectedNodeIds: [gaId],
      };
    });
  },
```

- [ ] **Step 6: Commit**

```bash
git add frontend/store/editorStore.ts
git commit -m "feat(store): add Graph Agent defaults, edge selection, and template creation"
```

---

## Task 10: Frontend — Canvas Sync for New Node Types

**Files:**
- Modify: `frontend/components/editor/Canvas.tsx`

- [ ] **Step 1: Add imports for new Rete classes**

Update the imports from `@/lib/rete-nodes`:

```typescript
import {
  AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode,
  McpServerReteNode, PlannerAgentReteNode, A2AAgentReteNode,
  InputReteNode, OutputReteNode,
  GraphAgentReteNode, StrategyStartReteNode, StrategyFinishReteNode,
  StrategyLLMRequestReteNode, StrategyExecuteToolReteNode,
  StrategySendToolResultReteNode, StrategyConditionReteNode
} from "@/lib/rete-nodes";
```

- [ ] **Step 2: Add node creation cases in Zustand→Rete sync**

In the `useEditorStore.subscribe` callback, in the block where nodes are created (around lines 146-166), add cases before the final `else`:

```typescript
            } else if (node.type === "graph-agent") {
              reteNode = new GraphAgentReteNode(node.id, (node.data as any).name || "Graph Agent");
            } else if (node.type === "strategy-start") {
              reteNode = new StrategyStartReteNode(node.id, (node.data as any).label || "Start");
            } else if (node.type === "strategy-finish") {
              reteNode = new StrategyFinishReteNode(node.id, (node.data as any).label || "Finish");
            } else if (node.type === "strategy-llm-request") {
              reteNode = new StrategyLLMRequestReteNode(node.id, (node.data as any).label || "LLM Request");
            } else if (node.type === "strategy-execute-tool") {
              reteNode = new StrategyExecuteToolReteNode(node.id, (node.data as any).label || "Execute Tool");
            } else if (node.type === "strategy-send-tool-result") {
              reteNode = new StrategySendToolResultReteNode(node.id, (node.data as any).label || "Send Tool Result");
            } else if (node.type === "strategy-condition") {
              reteNode = new StrategyConditionReteNode(node.id, (node.data as any).label || "Condition");
            } else {
```

- [ ] **Step 3: Add strategy node connection handling in Rete→Zustand pipes**

In the `connectioncreated` pipe (around line 69), add strategy node handling. After the existing connection creation block, add:

```typescript
          // Strategy node connections
          const isStrategySource = sourceNode instanceof StrategyStartReteNode ||
            sourceNode instanceof StrategyLLMRequestReteNode ||
            sourceNode instanceof StrategyExecuteToolReteNode ||
            sourceNode instanceof StrategySendToolResultReteNode ||
            sourceNode instanceof StrategyConditionReteNode;
          const isStrategyTarget = targetNode instanceof StrategyFinishReteNode ||
            targetNode instanceof StrategyLLMRequestReteNode ||
            targetNode instanceof StrategyExecuteToolReteNode ||
            targetNode instanceof StrategySendToolResultReteNode ||
            targetNode instanceof StrategyConditionReteNode;
          const isGraphAgentTarget2 = targetNode instanceof GraphAgentReteNode;
          const isGraphAgentSource2 = sourceNode instanceof GraphAgentReteNode;

          if (isStrategySource && isStrategyTarget) {
            syncing = true;
            const sourceStoreId = (sourceNode as any).storeNodeId;
            const targetStoreId = (targetNode as any).storeNodeId;
            useEditorStore.getState().addEdge(sourceStoreId, targetStoreId);
            syncing = false;
          }
          if (
            ((isToolSource || isSubAgentSource || isMcpSource || isA2ASource || isInputSource) && isGraphAgentTarget2) ||
            (isGraphAgentSource2 && isOutputTarget)
          ) {
            syncing = true;
            const sourceStoreId = (sourceNode as any).storeNodeId;
            const targetStoreId = (targetNode as any).storeNodeId;
            useEditorStore.getState().addEdge(sourceStoreId, targetStoreId);
            syncing = false;
          }
```

Add matching logic in the `connectionremoved` pipe (mirror the above for removal).

- [ ] **Step 4: Update label sync for strategy nodes**

In the label update loop (around line 184), update the label resolution for strategy and graph-agent nodes:

```typescript
            if (node.type === "builtin-tool") {
              const toolType = (node.data as any).toolType || "tool";
              newLabel = BUILTIN_TOOL_LABELS[toolType as BuiltinToolType] || toolType;
            } else if (node.type.startsWith("strategy-")) {
              newLabel = (node.data as any).label || node.type;
            } else {
              newLabel = (node.data as any).name || node.type.charAt(0).toUpperCase() + node.type.slice(1);
            }
```

- [ ] **Step 5: Update edge sync for strategy socket connections**

In the edge sync "add missing connections" block (around line 230), update the input/output key resolution to handle strategy sockets:

```typescript
              const isStrategySourceRete = sourceRete instanceof StrategyStartReteNode ||
                sourceRete instanceof StrategyLLMRequestReteNode ||
                sourceRete instanceof StrategyExecuteToolReteNode ||
                sourceRete instanceof StrategySendToolResultReteNode ||
                sourceRete instanceof StrategyConditionReteNode;
              const isStrategyTargetRete = targetRete instanceof StrategyFinishReteNode ||
                targetRete instanceof StrategyLLMRequestReteNode ||
                targetRete instanceof StrategyExecuteToolReteNode ||
                targetRete instanceof StrategySendToolResultReteNode ||
                targetRete instanceof StrategyConditionReteNode;

              let outputKey: string;
              let inputKey: string;

              if (isStrategySourceRete && isStrategyTargetRete) {
                outputKey = "out";
                inputKey = "in";
              } else {
                outputKey = Object.keys(sourceRete.outputs || {})[0];
                const isInputSource2 = sourceRete instanceof InputReteNode;
                const isOutputTarget2 = targetRete instanceof OutputReteNode;
                inputKey = isOutputTarget2 ? "agent"
                  : isInputSource2 && targetRete.inputs?.["inputs"] ? "inputs"
                  : Object.keys(targetRete.inputs || {})[0];
              }
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/editor/Canvas.tsx
git commit -m "feat(canvas): add Rete sync for Graph Agent and strategy nodes"
```

---

## Task 11: Frontend — CustomNode Visual Updates

**Files:**
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add imports**

Add to the imports from `@/lib/rete-nodes`:

```typescript
import {
  GraphAgentReteNode,
  StrategyStartReteNode,
  StrategyFinishReteNode,
  StrategyLLMRequestReteNode,
  StrategyExecuteToolReteNode,
  StrategySendToolResultReteNode,
  StrategyConditionReteNode,
} from "@/lib/rete-nodes";
```

Add to lucide-react imports:

```typescript
import { NetworkIcon, PlayIcon, SquareIcon, BrainIcon, SendIcon, GitBranchIcon } from "lucide-react";
```

- [ ] **Step 2: Add entries to `nodeIcons`, `nodeColors`, `nodeTypeLabels`**

```typescript
// nodeIcons
"graph-agent": <NetworkIcon size={10} />,
"strategy-start": <PlayIcon size={10} />,
"strategy-finish": <SquareIcon size={10} />,
"strategy-llm-request": <BrainIcon size={10} />,
"strategy-execute-tool": <WrenchIcon size={10} />,
"strategy-send-tool-result": <SendIcon size={10} />,
"strategy-condition": <GitBranchIcon size={10} />,

// nodeColors
"graph-agent": { border: "rgba(6, 182, 212, 0.4)", glow: "rgba(6, 182, 212, 0.12)", badge: "rgba(6, 182, 212, 0.15)", badgeText: "#22d3ee" },
"strategy-start": { border: "rgba(34, 197, 94, 0.4)", glow: "rgba(34, 197, 94, 0.12)", badge: "rgba(34, 197, 94, 0.15)", badgeText: "#4ade80" },
"strategy-finish": { border: "rgba(239, 68, 68, 0.4)", glow: "rgba(239, 68, 68, 0.12)", badge: "rgba(239, 68, 68, 0.15)", badgeText: "#f87171" },
"strategy-llm-request": { border: "rgba(59, 130, 246, 0.4)", glow: "rgba(59, 130, 246, 0.12)", badge: "rgba(59, 130, 246, 0.15)", badgeText: "#60a5fa" },
"strategy-execute-tool": { border: "rgba(249, 115, 22, 0.4)", glow: "rgba(249, 115, 22, 0.12)", badge: "rgba(249, 115, 22, 0.15)", badgeText: "#fb923c" },
"strategy-send-tool-result": { border: "rgba(168, 85, 247, 0.4)", glow: "rgba(168, 85, 247, 0.12)", badge: "rgba(168, 85, 247, 0.15)", badgeText: "#c084fc" },
"strategy-condition": { border: "rgba(234, 179, 8, 0.4)", glow: "rgba(234, 179, 8, 0.12)", badge: "rgba(234, 179, 8, 0.15)", badgeText: "#facc15" },

// nodeTypeLabels
"graph-agent": "GRAPH AGENT",
"strategy-start": "START",
"strategy-finish": "FINISH",
"strategy-llm-request": "LLM REQUEST",
"strategy-execute-tool": "EXECUTE TOOL",
"strategy-send-tool-result": "SEND RESULT",
"strategy-condition": "CONDITION",
```

- [ ] **Step 3: Add new node types to `getNodeType()`**

```typescript
if (data instanceof GraphAgentReteNode) return "graph-agent";
if (data instanceof StrategyStartReteNode) return "strategy-start";
if (data instanceof StrategyFinishReteNode) return "strategy-finish";
if (data instanceof StrategyLLMRequestReteNode) return "strategy-llm-request";
if (data instanceof StrategyExecuteToolReteNode) return "strategy-execute-tool";
if (data instanceof StrategySendToolResultReteNode) return "strategy-send-tool-result";
if (data instanceof StrategyConditionReteNode) return "strategy-condition";
```

- [ ] **Step 4: Add subtitle cases for strategy nodes**

In the subtitle switch statement, add:

```typescript
      case "graph-agent":
        if (d.modelProvider) subtitle = `${d.modelProvider}${d.modelId ? " · " + d.modelId : ""}`;
        break;
      case "strategy-llm-request":
        if (d.systemPrompt) subtitle = d.systemPrompt.slice(0, 30) + (d.systemPrompt.length > 30 ? "..." : "");
        break;
      case "strategy-condition":
        if (d.expression) subtitle = d.expression.slice(0, 30) + (d.expression.length > 30 ? "..." : "");
        break;
```

- [ ] **Step 5: Update `isConfigured` check**

Change the `isConfigured` logic to handle strategy nodes (which use `label` instead of `name`):

```typescript
  const isConfigured = storeNode && (
    (storeNode.data as any).name?.trim() || (storeNode.data as any).label?.trim()
  );
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/editor/CustomNode.tsx
git commit -m "feat(ui): add visual styling for Graph Agent and strategy nodes"
```

---

## Task 12: Frontend — Toolbar & Properties Panel

**Files:**
- Modify: `frontend/components/panels/Toolbar.tsx`
- Create: `frontend/components/panels/GraphAgentProperties.tsx`
- Create: `frontend/components/panels/StrategyNodeProperties.tsx`
- Create: `frontend/components/panels/EdgePropertiesPanel.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Add Graph Agent button + Strategy Nodes section to Toolbar**

Add to the imports:

```typescript
import { NetworkIcon, PlayIcon, SquareIcon, BrainIcon, SendIcon, GitBranchIcon } from "lucide-react";
```

Add after the Output button (before `<div className="flex-1" />`):

```typescript
      <span className="text-border select-none">|</span>

      <NavigationMenuItem>
        <NavigationMenuTrigger className="gap-2"><NetworkIcon className="size-4 text-cyan-400" /> Graph Agent</NavigationMenuTrigger>
        <NavigationMenuContent>
          <div className="w-56">
            <NavigationMenuLink className="cursor-pointer" onClick={() => (useEditorStore.getState() as any).createGraphAgentFromTemplate("empty", { x: 100, y: 100 })}>
              Empty
            </NavigationMenuLink>
            <NavigationMenuLink className="cursor-pointer" onClick={() => (useEditorStore.getState() as any).createGraphAgentFromTemplate("tool-calling", { x: 100, y: 100 })}>
              Tool-Calling Agent
            </NavigationMenuLink>
            <NavigationMenuLink className="cursor-pointer" onClick={() => (useEditorStore.getState() as any).createGraphAgentFromTemplate("react-loop", { x: 100, y: 100 })}>
              ReAct Loop
            </NavigationMenuLink>
          </div>
        </NavigationMenuContent>
      </NavigationMenuItem>
```

Wrap the Graph Agent menu item inside the existing `<NavigationMenuList>`.

- [ ] **Step 2: Create `GraphAgentProperties.tsx`**

```typescript
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { GraphAgentConfig } from "@/types/graph-agent";
import { ModelProvider } from "@/types/koog";

export function GraphAgentProperties({ nodeId, data }: { nodeId: string; data: GraphAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<GraphAgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="ga-name">Name</Label>
        <Input id="ga-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="MyGraphAgent" />
      </div>
      <div>
        <Label htmlFor="ga-desc">Description</Label>
        <Textarea id="ga-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} placeholder="A graph-based strategy agent" rows={2} />
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
        <Label htmlFor="ga-model-id">Model ID (optional)</Label>
        <Input id="ga-model-id" value={data.modelId || ""} onChange={(e) => update({ modelId: e.target.value })} placeholder="gpt-4o" />
      </div>
      <div>
        <Label htmlFor="ga-temperature">Temperature (optional)</Label>
        <Input id="ga-temperature" type="number" step="0.1" min="0" max="2" value={data.temperature ?? ""} onChange={(e) => update({ temperature: e.target.value ? parseFloat(e.target.value) : undefined })} />
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create `StrategyNodeProperties.tsx`**

```typescript
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useEditorStore } from "@/store/editorStore";
import { StrategyLLMRequestConfig, StrategyConditionConfig } from "@/types/graph-agent";

export function StrategyStartProperties({ nodeId, data }: { nodeId: string; data: { label: string } }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
    </div>
  );
}

export function StrategyFinishProperties({ nodeId, data }: { nodeId: string; data: { label: string } }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
    </div>
  );
}

export function StrategyLLMRequestProperties({ nodeId, data }: { nodeId: string; data: StrategyLLMRequestConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
      <div>
        <Label htmlFor="strat-prompt">System Prompt (optional)</Label>
        <Textarea id="strat-prompt" value={data.systemPrompt || ""} onChange={(e) => updateNodeData(nodeId, { systemPrompt: e.target.value })} rows={3} placeholder="Override system prompt for this node" />
      </div>
      <div>
        <Label htmlFor="strat-tokens">Max Tokens (optional)</Label>
        <Input id="strat-tokens" type="number" value={data.maxTokens ?? ""} onChange={(e) => updateNodeData(nodeId, { maxTokens: e.target.value ? parseInt(e.target.value) : undefined })} />
      </div>
    </div>
  );
}

export function StrategyExecuteToolProperties({ nodeId, data }: { nodeId: string; data: { label: string; parentGraphAgentId: string } }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  // Find tools connected to the parent graph agent
  const connectedTools = edges
    .filter((e) => e.target === data.parentGraphAgentId)
    .map((e) => nodes.find((n) => n.id === e.source))
    .filter((n) => n && (n.type === "tool" || n.type === "builtin-tool"));

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
      {connectedTools.length > 0 && (
        <div>
          <Label className="text-muted-foreground text-xs">Available Tools</Label>
          <ul className="text-xs text-muted-foreground mt-1 space-y-1">
            {connectedTools.map((t) => (
              <li key={t!.id}>• {(t!.data as any).name || (t!.data as any).toolType || "Unnamed"}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function StrategySendToolResultProperties({ nodeId, data }: { nodeId: string; data: { label: string } }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
    </div>
  );
}

export function StrategyConditionProperties({ nodeId, data }: { nodeId: string; data: StrategyConditionConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="strat-label">Label</Label>
        <Input id="strat-label" value={data.label} onChange={(e) => updateNodeData(nodeId, { label: e.target.value })} />
      </div>
      <div>
        <Label htmlFor="strat-expr">Expression</Label>
        <Textarea
          id="strat-expr"
          value={data.expression}
          onChange={(e) => updateNodeData(nodeId, { expression: e.target.value })}
          rows={3}
          placeholder="it.isValid()"
          className="font-mono text-xs"
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Create `EdgePropertiesPanel.tsx`**

```typescript
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import { GraphEdgeConfig, GraphEdgeType } from "@/types/graph-agent";

export function EdgePropertiesPanel({ edgeId, data }: { edgeId: string; data?: GraphEdgeConfig }) {
  const updateEdgeData = useEditorStore((s) => s.updateEdgeData);
  const edgeType = data?.edgeType || "standard";

  return (
    <div className="space-y-4">
      <div>
        <Label>Edge Type</Label>
        <Select value={edgeType} onValueChange={(v) => updateEdgeData(edgeId, { edgeType: v as GraphEdgeType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="standard">Standard</SelectItem>
            <SelectItem value="onToolCall">On Tool Call</SelectItem>
            <SelectItem value="onAssistantMessage">On Assistant Message</SelectItem>
            <SelectItem value="onCondition">On Condition</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {edgeType === "onCondition" && (
        <div>
          <Label htmlFor="edge-cond">Condition Expression</Label>
          <Textarea
            id="edge-cond"
            value={data?.conditionExpression || ""}
            onChange={(e) => updateEdgeData(edgeId, { conditionExpression: e.target.value })}
            rows={3}
            placeholder="it.isValid()"
            className="font-mono text-xs"
          />
        </div>
      )}
      <div>
        <Label htmlFor="edge-transform">Transformation (optional)</Label>
        <Textarea
          id="edge-transform"
          value={data?.transformationExpression || ""}
          onChange={(e) => updateEdgeData(edgeId, { transformationExpression: e.target.value })}
          rows={3}
          placeholder="it.value"
          className="font-mono text-xs"
        />
      </div>
      <div>
        <Label htmlFor="edge-label">Label (optional)</Label>
        <Input
          id="edge-label"
          value={data?.label || ""}
          onChange={(e) => updateEdgeData(edgeId, { label: e.target.value })}
          placeholder="Edge label"
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Update `PropertiesPanel.tsx`**

Add imports:

```typescript
import { GraphAgentProperties } from "./GraphAgentProperties";
import { GraphAgentConfig, StrategyLLMRequestConfig, StrategyConditionConfig } from "@/types/graph-agent";
import {
  StrategyStartProperties, StrategyFinishProperties, StrategyLLMRequestProperties,
  StrategyExecuteToolProperties, StrategySendToolResultProperties, StrategyConditionProperties
} from "./StrategyNodeProperties";
import { EdgePropertiesPanel } from "./EdgePropertiesPanel";
```

At the top of the `PropertiesPanel` function, add edge selection handling:

```typescript
  const selectedEdgeId = useEditorStore((s) => s.selectedEdgeId);
  const edges = useEditorStore((s) => s.edges);

  if (selectedEdgeId) {
    const selectedEdge = edges.find((e) => e.id === selectedEdgeId);
    if (selectedEdge) {
      return (
        <ScrollArea className="w-80 border-l bg-background">
          <div className="p-4">
            <h3 className="font-semibold mb-4">Edge Properties</h3>
            <EdgePropertiesPanel edgeId={selectedEdge.id} data={selectedEdge.data} />
          </div>
        </ScrollArea>
      );
    }
  }
```

Add node type label entries to the heading:

```typescript
           selectedNode.type === "graph-agent" ? "Graph Agent" :
           selectedNode.type === "strategy-start" ? "Start" :
           selectedNode.type === "strategy-finish" ? "Finish" :
           selectedNode.type === "strategy-llm-request" ? "LLM Request" :
           selectedNode.type === "strategy-execute-tool" ? "Execute Tool" :
           selectedNode.type === "strategy-send-tool-result" ? "Send Tool Result" :
           selectedNode.type === "strategy-condition" ? "Condition" :
```

Add rendering cases for new node types:

```typescript
        {selectedNode.type === "graph-agent" && (
          <GraphAgentProperties nodeId={selectedNode.id} data={selectedNode.data as GraphAgentConfig} />
        )}
        {selectedNode.type === "strategy-start" && (
          <StrategyStartProperties nodeId={selectedNode.id} data={selectedNode.data as any} />
        )}
        {selectedNode.type === "strategy-finish" && (
          <StrategyFinishProperties nodeId={selectedNode.id} data={selectedNode.data as any} />
        )}
        {selectedNode.type === "strategy-llm-request" && (
          <StrategyLLMRequestProperties nodeId={selectedNode.id} data={selectedNode.data as StrategyLLMRequestConfig} />
        )}
        {selectedNode.type === "strategy-execute-tool" && (
          <StrategyExecuteToolProperties nodeId={selectedNode.id} data={selectedNode.data as any} />
        )}
        {selectedNode.type === "strategy-send-tool-result" && (
          <StrategySendToolResultProperties nodeId={selectedNode.id} data={selectedNode.data as any} />
        )}
        {selectedNode.type === "strategy-condition" && (
          <StrategyConditionProperties nodeId={selectedNode.id} data={selectedNode.data as StrategyConditionConfig} />
        )}
```

- [ ] **Step 6: Commit**

```bash
git add frontend/components/panels/Toolbar.tsx frontend/components/panels/GraphAgentProperties.tsx frontend/components/panels/StrategyNodeProperties.tsx frontend/components/panels/EdgePropertiesPanel.tsx frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat(ui): add properties panels, toolbar entries, and edge config for Graph Agents"
```

---

## Task 13: Frontend — Validation

**Files:**
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Add graph agent validation rules**

Add imports at the top:

```typescript
import { GraphAgentConfig, StrategyConditionConfig, GraphEdgeConfig } from "@/types/graph-agent";
```

At the end of the `validateGraph` function, before `return errors;`, add:

```typescript
  // Graph Agent validation
  const graphAgentNodes = graph.nodes.filter((n) => n.type === "graph-agent");
  const strategyTypes = new Set([
    "strategy-start", "strategy-finish", "strategy-llm-request",
    "strategy-execute-tool", "strategy-send-tool-result", "strategy-condition",
  ]);
  const allGraphAgentIds = new Set(graphAgentNodes.map((n) => n.id));

  // Validate parentGraphAgentId references
  for (const sNode of graph.nodes.filter((n) => strategyTypes.has(n.type))) {
    const parentId = (sNode.data as any).parentGraphAgentId;
    if (!parentId || !allGraphAgentIds.has(parentId)) {
      errors.push({ nodeId: sNode.id, severity: "error", message: "Strategy node has invalid or missing parentGraphAgentId" });
    }
  }

  for (const gaNode of graphAgentNodes) {
    const gaData = gaNode.data as GraphAgentConfig;
    if (!gaData.name?.trim()) {
      errors.push({ nodeId: gaNode.id, severity: "error", message: "Graph Agent requires a name" });
    }

    const stratNodes = graph.nodes.filter(
      (n) => strategyTypes.has(n.type) && (n.data as any).parentGraphAgentId === gaNode.id
    );
    const stratNodeIds = new Set(stratNodes.map((n) => n.id));
    const stratEdges = graph.edges.filter((e) => stratNodeIds.has(e.source) && stratNodeIds.has(e.target));

    const startNodes = stratNodes.filter((n) => n.type === "strategy-start");
    const finishNodes = stratNodes.filter((n) => n.type === "strategy-finish");

    if (startNodes.length === 0) {
      errors.push({ nodeId: gaNode.id, severity: "error", message: "Graph Agent must have a Start node" });
    }
    if (finishNodes.length === 0) {
      errors.push({ nodeId: gaNode.id, severity: "error", message: "Graph Agent must have a Finish node" });
    }
    if (startNodes.length > 1) {
      errors.push({ nodeId: gaNode.id, severity: "error", message: "Graph Agent has more than one Start node" });
    }

    for (const startNode of startNodes) {
      if (!stratEdges.some((e) => e.source === startNode.id)) {
        errors.push({ nodeId: startNode.id, severity: "error", message: "Start node must have at least one outgoing edge" });
      }
    }

    // Reachability check (BFS from start)
    if (startNodes.length > 0 && finishNodes.length > 0) {
      const adj = new Map<string, string[]>();
      for (const e of stratEdges) {
        if (!adj.has(e.source)) adj.set(e.source, []);
        adj.get(e.source)!.push(e.target);
      }
      const reachable = new Set<string>();
      const queue = startNodes.map((n) => n.id);
      while (queue.length > 0) {
        const current = queue.shift()!;
        if (reachable.has(current)) continue;
        reachable.add(current);
        for (const next of adj.get(current) || []) {
          queue.push(next);
        }
      }
      for (const finishNode of finishNodes) {
        if (!reachable.has(finishNode.id)) {
          errors.push({ nodeId: gaNode.id, severity: "error", message: "Finish node is not reachable from Start" });
        }
      }
      for (const sNode of stratNodes) {
        if (sNode.type !== "strategy-start" && !reachable.has(sNode.id)) {
          errors.push({ nodeId: sNode.id, severity: "error", message: `Strategy node '${(sNode.data as any).label}' is not reachable from Start` });
        }
      }
    }

    // Condition nodes must have expression
    for (const condNode of stratNodes.filter((n) => n.type === "strategy-condition")) {
      const expr = (condNode.data as StrategyConditionConfig).expression;
      if (!expr?.trim()) {
        errors.push({ nodeId: condNode.id, severity: "error", message: "Condition node requires an expression" });
      }
    }

    // onCondition edges must have conditionExpression
    for (const edge of stratEdges) {
      const edgeData = edge.data as GraphEdgeConfig | undefined;
      if (edgeData?.edgeType === "onCondition" && !edgeData.conditionExpression?.trim()) {
        errors.push({ nodeId: edge.source, severity: "error", message: "onCondition edge requires a condition expression" });
      }
    }

    // Warning: execute-tool without tools connected to graph agent
    const hasExecuteTool = stratNodes.some((n) => n.type === "strategy-execute-tool");
    const hasConnectedTools = graph.edges.some(
      (e) => e.target === gaNode.id && graph.nodes.some((n) => n.id === e.source && (n.type === "tool" || n.type === "builtin-tool"))
    );
    if (hasExecuteTool && !hasConnectedTools) {
      errors.push({ nodeId: gaNode.id, severity: "warning", message: "Graph Agent has Execute Tool nodes but no tools connected" });
    }
  }
```

- [ ] **Step 2: Commit**

```bash
git add frontend/lib/validation.ts
git commit -m "feat(validation): add frontend graph agent strategy validation"
```

---

## Task 14: Integration Test & Final Verification

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 2: Run frontend TypeScript check**

Run: `cd frontend && npx tsc --noEmit 2>&1 | head -30`
Expected: No new errors

- [ ] **Step 3: Run frontend dev server**

Run: `cd frontend && npm run dev`
Expected: Server starts without errors

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve integration issues in Graph Agent feature"
```
