# Input/Output Nodes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit Input and Output nodes to the graph editor so agents, sub-agents, and planners can visually model their data flow — what data they expect and what they produce.

**Architecture:** Two new node types (`input`, `output`) as standalone graph nodes. Input nodes connect TO agents (like tools), output nodes receive FROM agents (agents need a new output socket). The adapter collects connected input/output nodes per agent and adds them to the Koog model. A new generator produces typed input/output data classes.

**Tech Stack:** Kotlin/Spring Boot (backend models, adapter, validator, generator), TypeScript/React/Zustand (frontend types, store, UI), Rete.js (graph nodes), JUnit 5 (tests)

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `src/main/kotlin/.../export/model/IOModel.kt` | Backend IO data classes |
| `src/main/kotlin/.../export/generator/InputOutputGenerator.kt` | IO code generator |
| `src/test/kotlin/.../export/adapter/GraphToKoogAdapterIOTest.kt` | Adapter IO tests |
| `src/test/kotlin/.../export/validation/GraphValidatorIOTest.kt` | Validator IO tests |
| `src/test/kotlin/.../export/generator/InputOutputGeneratorTest.kt` | Generator tests |
| `frontend/types/io.ts` | Frontend IO TypeScript types |
| `frontend/components/panels/InputProperties.tsx` | Input node properties panel |
| `frontend/components/panels/OutputProperties.tsx` | Output node properties panel |

### Modified Files
| File | Change |
|------|--------|
| `src/.../export/model/KoogModel.kt` | Add inputs/outputs to KoogAgent, KoogSubAgent |
| `src/.../export/model/PlannerModel.kt` | Add inputs/outputs to KoogPlannerAgent |
| `src/.../export/adapter/GraphToKoogAdapter.kt` | Parse input/output nodes |
| `src/.../export/validation/GraphValidator.kt` | Add IO validation rules |
| `src/.../export/ExportService.kt` | Register InputOutputGenerator |
| `frontend/types/graph.ts` | Add `"input"` / `"output"` to NodeType |
| `frontend/store/editorStore.ts` | Handle input/output defaults |
| `frontend/lib/rete-nodes.ts` | Add InputReteNode, OutputReteNode, add output socket to agent nodes |
| `frontend/lib/rete-setup.ts` | Add connection rules |
| `frontend/lib/validation.ts` | Add IO validation rules |
| `frontend/components/editor/Canvas.tsx` | Handle input/output node types |
| `frontend/components/editor/CustomNode.tsx` | Add input/output colors/icon/subtitle |
| `frontend/components/panels/PropertiesPanel.tsx` | Route to InputProperties/OutputProperties |

---

### Task 1: Backend IO Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/IOModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt`

- [ ] **Step 1: Create IOModel.kt**

```kotlin
package com.agentwork.modeler.export.model

data class KoogInput(
    val name: String,
    val description: String,
    val dataType: String,
    val required: Boolean,
    val defaultValue: String?
)

data class KoogOutput(
    val name: String,
    val description: String,
    val dataType: String
)
```

- [ ] **Step 2: Add inputs/outputs to KoogAgent**

In `KoogModel.kt`, add as last parameters of `KoogAgent`:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

Add same to `KoogSubAgent`:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

- [ ] **Step 3: Add inputs/outputs to KoogPlannerAgent**

In `PlannerModel.kt`, add as last parameters of `KoogPlannerAgent`:
```kotlin
val inputs: List<KoogInput> = emptyList(),
val outputs: List<KoogOutput> = emptyList()
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/IOModel.kt \
        src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt \
        src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt
git commit -m "feat(io): add backend Input/Output data model classes"
```

---

### Task 2: Backend Adapter — IO Support

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterIOTest.kt`

- [ ] **Step 1: Write the failing test**

Create `GraphToKoogAdapterIOTest.kt`:

```kotlin
package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphToKoogAdapterIOTest {

    private val adapter = GraphToKoogAdapter()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    @Test
    fun `converts input nodes connected to agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("i1", "input", mapOf("name" to "query", "description" to "Search query", "dataType" to "string", "required" to true, "defaultValue" to null)),
                node("i2", "input", mapOf("name" to "limit", "description" to "Max results", "dataType" to "number", "required" to false, "defaultValue" to "10"))
            ),
            edges = listOf(edge("i1", "a1"), edge("i2", "a1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(2, agents[0].inputs.size)
        val q = agents[0].inputs.find { it.name == "query" }!!
        assertEquals("string", q.dataType)
        assertTrue(q.required)
        val l = agents[0].inputs.find { it.name == "limit" }!!
        assertEquals("number", l.dataType)
        assertEquals("10", l.defaultValue)
    }

    @Test
    fun `converts output nodes connected from agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("o1", "output", mapOf("name" to "result", "description" to "The result", "dataType" to "string"))
            ),
            edges = listOf(edge("a1", "o1"))
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].outputs.size)
        assertEquals("result", agents[0].outputs[0].name)
        assertEquals("string", agents[0].outputs[0].dataType)
    }

    @Test
    fun `agent without io has empty lists`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))),
            edges = emptyList()
        )
        val agents = adapter.convert(graph)
        assertTrue(agents[0].inputs.isEmpty())
        assertTrue(agents[0].outputs.isEmpty())
    }

    @Test
    fun `converts input and output nodes connected to sub-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("sa1", "sub-agent", mapOf("name" to "Research", "description" to "d", "systemPrompt" to "p", "inputDescription" to "i", "delegationMode" to "automatic")),
                node("i1", "input", mapOf("name" to "topic", "description" to "Research topic", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "findings", "description" to "Research findings", "dataType" to "string"))
            ),
            edges = listOf(edge("sa1", "a1"), edge("i1", "sa1"), edge("sa1", "o1"))
        )
        val agents = adapter.convert(graph)
        val sa = agents[0].subAgents[0]
        assertEquals(1, sa.inputs.size)
        assertEquals("topic", sa.inputs[0].name)
        assertEquals(1, sa.outputs.size)
        assertEquals("findings", sa.outputs[0].name)
    }

    @Test
    fun `converts io nodes connected to planner-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("p1", "planner-agent", mapOf(
                    "name" to "Planner", "plannerType" to "llm", "modelProvider" to "openai",
                    "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)), "priority" to 0)),
                    "actions" to listOf(mapOf("name" to "a1", "description" to "act", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true)), "cost" to 1))
                )),
                node("i1", "input", mapOf("name" to "goal", "description" to "Goal description", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "plan", "description" to "Generated plan", "dataType" to "object"))
            ),
            edges = listOf(edge("i1", "p1"), edge("p1", "o1"))
        )
        val planners = adapter.convertPlannerAgents(graph)
        assertEquals(1, planners[0].inputs.size)
        assertEquals("goal", planners[0].inputs[0].name)
        assertEquals(1, planners[0].outputs.size)
        assertEquals("plan", planners[0].outputs[0].name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapterIOTest*" 2>&1 | tail -20`
Expected: FAIL

- [ ] **Step 3: Implement adapter changes**

In `GraphToKoogAdapter.kt`:

**Add node filters** in `convert()` after a2aAgentNodes (line 20):
```kotlin
val inputNodes = graph.nodes.filter { it.type == "input" }
val outputNodes = graph.nodes.filter { it.type == "output" }
```

**Add input/output conversion** inside the `agentNodes.map` block, after a2aConfig (before KoogAgent constructor):
```kotlin
val inputs = inputNodes
    .filter { it.id in connectedToAgent }
    .map { convertInput(it) }

val connectedFromAgent = graph.edges
    .filter { it.source == agentNode.id }
    .map { it.target }

val outputs = outputNodes
    .filter { it.id in connectedFromAgent }
    .map { convertOutput(it) }
```

**Add to KoogAgent constructor** (after a2aAgents):
```kotlin
inputs = inputs,
outputs = outputs
```

**Update convertSubAgent** to include inputs/outputs. Add parameters to the method signature:
```kotlin
private fun convertSubAgent(
    saNode: GraphNode,
    graph: WorkspaceGraph,
    toolNodes: List<GraphNode>,
    builtinToolNodes: List<GraphNode>,
    inputNodes: List<GraphNode>,
    outputNodes: List<GraphNode>
): KoogSubAgent {
```

Inside, after builtinTools conversion, add:
```kotlin
val inputs = inputNodes
    .filter { it.id in connectedIds }
    .map { convertInput(it) }

val connectedFromSa = graph.edges
    .filter { it.source == saNode.id }
    .map { it.target }

val outputs = outputNodes
    .filter { it.id in connectedFromSa }
    .map { convertOutput(it) }
```

And add to KoogSubAgent constructor:
```kotlin
inputs = inputs,
outputs = outputs
```

**Update the call sites** for `convertSubAgent` in both `convert()` and `convertPlannerAgents()` to pass the new parameters:
```kotlin
.map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes) }
```

**In `convertPlannerAgents()`**, add input/output node filters and conversion (same pattern as convert()):
```kotlin
val inputNodes = graph.nodes.filter { it.type == "input" }
val outputNodes = graph.nodes.filter { it.type == "output" }
```

Inside the planner map, after a2aConfig:
```kotlin
val inputs = inputNodes
    .filter { it.id in connectedIds }
    .map { convertInput(it) }

val connectedFromPlanner = graph.edges
    .filter { it.source == plannerNode.id }
    .map { it.target }

val outputs = outputNodes
    .filter { it.id in connectedFromPlanner }
    .map { convertOutput(it) }
```

Add to KoogPlannerAgent constructor:
```kotlin
inputs = inputs,
outputs = outputs
```

**Add converter methods** at the bottom of the class:
```kotlin
private fun convertInput(node: GraphNode): KoogInput {
    return KoogInput(
        name = node.data["name"] as? String ?: "",
        description = node.data["description"] as? String ?: "",
        dataType = node.data["dataType"] as? String ?: "string",
        required = node.data["required"] as? Boolean ?: true,
        defaultValue = node.data["defaultValue"] as? String
    )
}

private fun convertOutput(node: GraphNode): KoogOutput {
    return KoogOutput(
        name = node.data["name"] as? String ?: "",
        description = node.data["description"] as? String ?: "",
        dataType = node.data["dataType"] as? String ?: "string"
    )
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapterIOTest*" 2>&1 | tail -20`
Expected: All 5 tests PASS

- [ ] **Step 5: Run all adapter tests for regression**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapter*" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt \
        src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterIOTest.kt
git commit -m "feat(io): add Input/Output support to GraphToKoogAdapter"
```

---

### Task 3: Backend Validator — IO Rules

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorIOTest.kt`

- [ ] **Step 1: Write the failing test**

Create `GraphValidatorIOTest.kt`:

```kotlin
package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class GraphValidatorIOTest {

    private val validator = GraphValidator()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    private fun agentNode(id: String = "a1") =
        node(id, "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))

    @Test
    fun `input without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", mapOf("name" to "", "dataType" to "string", "required" to true))),
            edges = listOf(edge("i1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("i1") })
    }

    @Test
    fun `output without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", mapOf("name" to "", "dataType" to "string"))),
            edges = listOf(edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("o1") })
    }

    @Test
    fun `input not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("i1") })
    }

    @Test
    fun `output not connected is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(agentNode(), node("o1", "output", mapOf("name" to "result", "dataType" to "string"))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("o1") })
    }

    @Test
    fun `input connected to multiple agents is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                node("a1", "agent", mapOf("name" to "Bot1", "systemPrompt" to "Hi")),
                node("a2", "agent", mapOf("name" to "Bot2", "systemPrompt" to "Hi")),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true))
            ),
            edges = listOf(edge("i1", "a1"), edge("i1", "a2"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("one") && it.contains("i1") })
    }

    @Test
    fun `duplicate input name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true)),
                node("i2", "input", mapOf("name" to "query", "dataType" to "number", "required" to false))
            ),
            edges = listOf(edge("i1", "a1"), edge("i2", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Duplicate") && it.contains("query") })
    }

    @Test
    fun `duplicate output name on same agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("o1", "output", mapOf("name" to "result", "dataType" to "string")),
                node("o2", "output", mapOf("name" to "result", "dataType" to "number"))
            ),
            edges = listOf(edge("a1", "o1"), edge("a1", "o2"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("Duplicate") && it.contains("result") })
    }

    @Test
    fun `valid input and output produce no errors`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("i1", "input", mapOf("name" to "query", "dataType" to "string", "required" to true)),
                node("o1", "output", mapOf("name" to "result", "dataType" to "string"))
            ),
            edges = listOf(edge("i1", "a1"), edge("a1", "o1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.none { it.contains("i1") || it.contains("o1") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidatorIOTest*" 2>&1 | tail -20`
Expected: FAIL

- [ ] **Step 3: Add IO validation to GraphValidator**

In `GraphValidator.kt`, add new `when` branches inside the `for (node in graph.nodes)` block:

```kotlin
"input" -> {
    val name = node.data["name"] as? String ?: ""
    if (name.isBlank()) errors.add("[${node.id}] Input requires a name")
    val isConnected = graph.edges.any { it.source == node.id }
    if (!isConnected) errors.add("[${node.id}] Input must be connected to an Agent")
    val connectedAgentCount = graph.edges
        .filter { it.source == node.id }
        .map { edge -> graph.nodes.find { it.id == edge.target } }
        .count { it?.type == "agent" || it?.type == "sub-agent" || it?.type == "planner-agent" }
    if (connectedAgentCount > 1) {
        errors.add("[${node.id}] Input can only be connected to one Agent")
    }
}
"output" -> {
    val name = node.data["name"] as? String ?: ""
    if (name.isBlank()) errors.add("[${node.id}] Output requires a name")
    val isConnected = graph.edges.any { it.target == node.id }
    if (!isConnected) errors.add("[${node.id}] Output must be connected to an Agent")
    val connectedAgentCount = graph.edges
        .filter { it.target == node.id }
        .map { edge -> graph.nodes.find { it.id == edge.source } }
        .count { it?.type == "agent" || it?.type == "sub-agent" || it?.type == "planner-agent" }
    if (connectedAgentCount > 1) {
        errors.add("[${node.id}] Output can only be connected to one Agent")
    }
}
```

**Add duplicate name checks** after the main `for` loop, before `return errors`:

```kotlin
// Duplicate input/output name checks per agent
val agentTypes = setOf("agent", "sub-agent", "planner-agent")
for (agentNode in graph.nodes.filter { it.type in agentTypes }) {
    val inputNames = mutableSetOf<String>()
    val connectedInputs = graph.edges
        .filter { it.target == agentNode.id }
        .mapNotNull { edge -> graph.nodes.find { it.id == edge.source && it.type == "input" } }
    for (inputNode in connectedInputs) {
        val name = inputNode.data["name"] as? String ?: ""
        if (name.isNotBlank() && !inputNames.add(name)) {
            errors.add("[${inputNode.id}] Duplicate input name '${name}' on agent '${agentNode.data["name"]}'")
        }
    }
    val outputNames = mutableSetOf<String>()
    val connectedOutputs = graph.edges
        .filter { it.source == agentNode.id }
        .mapNotNull { edge -> graph.nodes.find { it.id == edge.target && it.type == "output" } }
    for (outputNode in connectedOutputs) {
        val name = outputNode.data["name"] as? String ?: ""
        if (name.isNotBlank() && !outputNames.add(name)) {
            errors.add("[${outputNode.id}] Duplicate output name '${name}' on agent '${agentNode.data["name"]}'")
        }
    }
}
```

- [ ] **Step 4: Run IO validator tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidatorIOTest*" 2>&1 | tail -20`
Expected: All 8 tests PASS

- [ ] **Step 5: Run all validator tests for regression**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidator*" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt \
        src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorIOTest.kt
git commit -m "feat(io): add Input/Output validation rules to GraphValidator"
```

---

### Task 4: Backend InputOutput Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `InputOutputGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class InputOutputGeneratorTest {

    private val generator = InputOutputGenerator()

    @Test
    fun `generates input data class with required field`() {
        val inputs = listOf(KoogInput("query", "Search query", "string", true, null))
        val code = generator.generateInputClass("MyAgent", inputs)
        assertContains(code, "package generated")
        assertContains(code, "data class MyAgentInput")
        assertContains(code, "val query: String")
        assertFalse(code.contains("="))
    }

    @Test
    fun `generates input data class with optional field and default`() {
        val inputs = listOf(
            KoogInput("query", "Search query", "string", true, null),
            KoogInput("limit", "Max results", "number", false, "10")
        )
        val code = generator.generateInputClass("MyAgent", inputs)
        assertContains(code, "val query: String")
        assertContains(code, "val limit: Double = 10.0")
    }

    @Test
    fun `generates output data class`() {
        val outputs = listOf(
            KoogOutput("result", "The result", "string"),
            KoogOutput("count", "Number of results", "number")
        )
        val code = generator.generateOutputClass("MyAgent", outputs)
        assertContains(code, "data class MyAgentOutput")
        assertContains(code, "val result: String")
        assertContains(code, "val count: Double")
    }

    @Test
    fun `maps all data types correctly`() {
        val inputs = listOf(
            KoogInput("a", "", "string", true, null),
            KoogInput("b", "", "number", true, null),
            KoogInput("c", "", "boolean", true, null),
            KoogInput("d", "", "object", true, null),
            KoogInput("e", "", "array", true, null)
        )
        val code = generator.generateInputClass("Test", inputs)
        assertContains(code, "val a: String")
        assertContains(code, "val b: Double")
        assertContains(code, "val c: Boolean")
        assertContains(code, "val d: Map<String, Any>")
        assertContains(code, "val e: List<Any>")
    }

    @Test
    fun `returns null for empty inputs`() {
        val code = generator.generateInputClass("MyAgent", emptyList())
        kotlin.test.assertNull(code)
    }

    @Test
    fun `returns null for empty outputs`() {
        val code = generator.generateOutputClass("MyAgent", emptyList())
        kotlin.test.assertNull(code)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*InputOutputGeneratorTest*" 2>&1 | tail -20`
Expected: FAIL

- [ ] **Step 3: Implement InputOutputGenerator**

Create `InputOutputGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import org.springframework.stereotype.Component

@Component
class InputOutputGenerator {

    private fun kotlinType(dataType: String): String = when (dataType) {
        "number" -> "Double"
        "boolean" -> "Boolean"
        "object" -> "Map<String, Any>"
        "array" -> "List<Any>"
        else -> "String"
    }

    private fun defaultLiteral(dataType: String, defaultValue: String): String = when (dataType) {
        "number" -> defaultValue.toDoubleOrNull()?.let { "$it" } ?: "0.0"
        "boolean" -> if (defaultValue.lowercase() == "true") "true" else "false"
        else -> "\"$defaultValue\""
    }

    fun generateInputClass(agentName: String, inputs: List<KoogInput>): String? {
        if (inputs.isEmpty()) return null
        val className = "${agentName.replaceFirstChar { it.uppercase() }}Input"
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("data class $className(")
            val required = inputs.filter { it.required }
            val optional = inputs.filter { !it.required }
            val all = required + optional
            for ((i, input) in all.withIndex()) {
                val kt = kotlinType(input.dataType)
                val comma = if (i < all.size - 1) "," else ""
                if (input.required) {
                    appendLine("    val ${input.name}: $kt$comma")
                } else {
                    val default = if (input.defaultValue != null) {
                        " = ${defaultLiteral(input.dataType, input.defaultValue)}"
                    } else {
                        " = null"
                    }
                    val type = if (input.defaultValue == null) "$kt?" else kt
                    appendLine("    val ${input.name}: $type$default$comma")
                }
            }
            appendLine(")")
        }
    }

    fun generateOutputClass(agentName: String, outputs: List<KoogOutput>): String? {
        if (outputs.isEmpty()) return null
        val className = "${agentName.replaceFirstChar { it.uppercase() }}Output"
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("data class $className(")
            for ((i, output) in outputs.withIndex()) {
                val kt = kotlinType(output.dataType)
                val comma = if (i < outputs.size - 1) "," else ""
                appendLine("    val ${output.name}: $kt$comma")
            }
            appendLine(")")
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*InputOutputGeneratorTest*" 2>&1 | tail -20`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt
git commit -m "feat(io): add InputOutputGenerator for typed input/output classes"
```

---

### Task 5: Backend ExportService Integration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add generator dependency**

Add after `private val a2aClientGenerator: A2AClientGenerator,`:
```kotlin
private val inputOutputGenerator: InputOutputGenerator,
```

- [ ] **Step 2: Add IO generation in regular agent path**

In `generateFiles()`, in the regular agent path, after A2A clients section, before ToolRegistry:

```kotlin
// Input/Output classes
val inputCode = inputOutputGenerator.generateInputClass(agent.name, agent.inputs)
if (inputCode != null) {
    files["$pkg/${agent.name.replaceFirstChar { it.uppercase() }}Input.kt"] = inputCode
}
val outputCode = inputOutputGenerator.generateOutputClass(agent.name, agent.outputs)
if (outputCode != null) {
    files["$pkg/${agent.name.replaceFirstChar { it.uppercase() }}Output.kt"] = outputCode
}
```

- [ ] **Step 3: Add IO generation in planner path**

Same pattern in the planner path:

```kotlin
// Input/Output classes
val inputCode = inputOutputGenerator.generateInputClass(planner.name, planner.inputs)
if (inputCode != null) {
    files["$pkg/${planner.name.replaceFirstChar { it.uppercase() }}Input.kt"] = inputCode
}
val outputCode = inputOutputGenerator.generateOutputClass(planner.name, planner.outputs)
if (outputCode != null) {
    files["$pkg/${planner.name.replaceFirstChar { it.uppercase() }}Output.kt"] = outputCode
}
```

- [ ] **Step 4: Verify build and run all tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat(io): integrate InputOutputGenerator into ExportService"
```

---

### Task 6: Frontend Types

**Files:**
- Create: `frontend/types/io.ts`
- Modify: `frontend/types/graph.ts`

- [ ] **Step 1: Create io.ts**

```typescript
export type IODataType = "string" | "number" | "boolean" | "object" | "array";

export const IO_DATA_TYPES: IODataType[] = ["string", "number", "boolean", "object", "array"];

export const IO_DATA_TYPE_LABELS: Record<IODataType, string> = {
  string: "String",
  number: "Number",
  boolean: "Boolean",
  object: "Object",
  array: "Array",
};

export interface InputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
  required: boolean;
  defaultValue?: string;
}

export interface OutputNodeConfig {
  name: string;
  description: string;
  dataType: IODataType;
}
```

- [ ] **Step 2: Update graph.ts**

Add import:
```typescript
import { InputNodeConfig, OutputNodeConfig } from "./io";
```

Update NodeType:
```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent" | "input" | "output";
```

Update GraphNode.data:
```typescript
data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig;
```

- [ ] **Step 3: Commit**

```bash
git add frontend/types/io.ts frontend/types/graph.ts
git commit -m "feat(io): add frontend Input/Output TypeScript types"
```

---

### Task 7: Frontend Store & Validation

**Files:**
- Modify: `frontend/store/editorStore.ts`
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Update editorStore**

Add import:
```typescript
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
```

Add defaults after `defaultA2AAgentData`:
```typescript
const defaultInputData: InputNodeConfig = {
  name: "",
  description: "",
  dataType: "string",
  required: true,
};

const defaultOutputData: OutputNodeConfig = {
  name: "",
  description: "",
  dataType: "string",
};
```

Update the `data` variable type in `addNode` to include the new types:
```typescript
let data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig | InputNodeConfig | OutputNodeConfig;
```

Add branches in `addNode` before the final `else`:
```typescript
} else if (type === "input") {
  data = { ...defaultInputData };
} else if (type === "output") {
  data = { ...defaultOutputData };
} else {
```

Update `updateNodeData` type to include `InputNodeConfig | OutputNodeConfig`.

- [ ] **Step 2: Update validation.ts**

Add import:
```typescript
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
```

Add validation blocks inside the loop:

```typescript
if (node.type === "input") {
  const data = node.data as InputNodeConfig;
  if (!data.name?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Input requires a name" });
  }
  const isConnected = graph.edges.some((e) => e.source === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "Input must be connected to an Agent" });
  }
  const connectedAgentCount = graph.edges
    .filter((e) => e.source === node.id)
    .map((e) => graph.nodes.find((n) => n.id === e.target))
    .filter((n) => n?.type === "agent" || n?.type === "sub-agent" || n?.type === "planner-agent")
    .length;
  if (connectedAgentCount > 1) {
    errors.push({ nodeId: node.id, severity: "error", message: "Input can only be connected to one Agent" });
  }
  if (!data.required && !data.defaultValue?.trim()) {
    errors.push({ nodeId: node.id, severity: "warning", message: "Optional input has no default value" });
  }
}

if (node.type === "output") {
  const data = node.data as OutputNodeConfig;
  if (!data.name?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "Output requires a name" });
  }
  const isConnected = graph.edges.some((e) => e.target === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "Output must be connected to an Agent" });
  }
}
```

Add duplicate name checks after the main loop, before `return errors`:

```typescript
// Duplicate input/output name checks
const agentTypeNodes = graph.nodes.filter((n) => n.type === "agent" || n.type === "sub-agent" || n.type === "planner-agent");
for (const agentNode of agentTypeNodes) {
  const inputNames = new Set<string>();
  const connectedInputs = graph.edges
    .filter((e) => e.target === agentNode.id)
    .map((e) => graph.nodes.find((n) => n.id === e.source && n.type === "input"))
    .filter(Boolean);
  for (const inputNode of connectedInputs) {
    const name = (inputNode!.data as InputNodeConfig).name?.trim();
    if (name && inputNames.has(name)) {
      errors.push({ nodeId: inputNode!.id, severity: "error", message: `Duplicate input name '${name}'` });
    }
    if (name) inputNames.add(name);
  }
  const outputNames = new Set<string>();
  const connectedOutputs = graph.edges
    .filter((e) => e.source === agentNode.id)
    .map((e) => graph.nodes.find((n) => n.id === e.target && n.type === "output"))
    .filter(Boolean);
  for (const outputNode of connectedOutputs) {
    const name = (outputNode!.data as OutputNodeConfig).name?.trim();
    if (name && outputNames.has(name)) {
      errors.push({ nodeId: outputNode!.id, severity: "error", message: `Duplicate output name '${name}'` });
    }
    if (name) outputNames.add(name);
  }
}
```

- [ ] **Step 3: Verify TypeScript**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`

- [ ] **Step 4: Commit**

```bash
git add frontend/store/editorStore.ts frontend/lib/validation.ts
git commit -m "feat(io): update store and validation for input/output nodes"
```

---

### Task 8: Frontend Rete Integration

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`
- Modify: `frontend/lib/rete-setup.ts`
- Modify: `frontend/components/editor/Canvas.tsx`
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add nodes and output sockets to rete-nodes.ts**

Add `InputReteNode` and `OutputReteNode` before the `AppNode` type:

```typescript
export class InputReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Input");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}

export class OutputReteNode extends ClassicPreset.Node {
  width = 180;
  height = 80;

  constructor(public storeNodeId: string, label: string) {
    super(label || "Output");
    this.addInput("agent", new ClassicPreset.Input(toolSocket, "Agent", true));
  }
}
```

Add output socket to `AgentReteNode` (after the input socket):
```typescript
this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
```

Add output socket to `SubAgentReteNode` (after the existing output socket — rename existing to "parentAgent"):
```typescript
this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
```

Add output socket to `PlannerAgentReteNode` (after the input socket):
```typescript
this.addOutput("outputs", new ClassicPreset.Output(toolSocket, "Outputs"));
```

Update `AppNode` type:
```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode | InputReteNode | OutputReteNode;
```

- [ ] **Step 2: Update rete-setup.ts — add connection rules**

Add `InputReteNode` and `OutputReteNode` to the import.

Add connection rules in the pipe handler, after the A2A rule:

```typescript
// Input -> Agent or SubAgent or PlannerAgent: OK
const isInputSource = sourceNode instanceof InputReteNode;
if (isInputSource && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget)) {
  return context;
}
// Agent or SubAgent or PlannerAgent -> Output: OK
const isOutputTarget = targetNode instanceof OutputReteNode;
const isAgentSource = sourceNode instanceof AgentReteNode;
const isSubAgentSource2 = sourceNode instanceof SubAgentReteNode;
const isPlannerAgentSource = sourceNode instanceof PlannerAgentReteNode;
if ((isAgentSource || isSubAgentSource2 || isPlannerAgentSource) && isOutputTarget) {
  return context;
}
```

- [ ] **Step 3: Update Canvas.tsx**

Add imports for `InputReteNode`, `OutputReteNode`.

In the node creation section, add branches:
```typescript
} else if (node.type === "input") {
  reteNode = new InputReteNode(node.id, (node.data as any).name || "Input");
} else if (node.type === "output") {
  reteNode = new OutputReteNode(node.id, (node.data as any).name || "Output");
}
```

In `connectioncreated` pipe, add:
```typescript
const isInputSource = sourceNode instanceof InputReteNode;
const isOutputTarget = targetNode instanceof OutputReteNode;
const isAgentSource = sourceNode instanceof AgentReteNode;
const isPlannerAgentSource = sourceNode instanceof PlannerAgentReteNode;
```
Update condition to include these.

Same for `connectionremoved` pipe.

- [ ] **Step 4: Update CustomNode.tsx**

Add imports for `InputReteNode`, `OutputReteNode`, and `ArrowDownToLineIcon`, `ArrowUpFromLineIcon` from lucide-react.

Add to `nodeIcons`:
```typescript
"input": <ArrowDownToLineIcon size={10} />,
"output": <ArrowUpFromLineIcon size={10} />,
```

Add to `nodeColors`:
```typescript
"input": {
  border: "rgba(6, 182, 212, 0.4)",
  glow: "rgba(6, 182, 212, 0.12)",
  badge: "rgba(6, 182, 212, 0.15)",
  badgeText: "#22d3ee",
},
"output": {
  border: "rgba(249, 115, 22, 0.4)",
  glow: "rgba(249, 115, 22, 0.12)",
  badge: "rgba(249, 115, 22, 0.15)",
  badgeText: "#fb923c",
},
```

Add to `nodeTypeLabels`:
```typescript
"input": "INPUT",
"output": "OUTPUT",
```

Add to `getNodeType`:
```typescript
if (data instanceof InputReteNode) return "input";
if (data instanceof OutputReteNode) return "output";
```

Add subtitle cases:
```typescript
case "input":
  if (d.dataType) subtitle = `${d.dataType}${d.required ? " · required" : " · optional"}`;
  break;
case "output":
  if (d.dataType) subtitle = d.dataType;
  break;
```

- [ ] **Step 5: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/lib/rete-setup.ts \
        frontend/components/editor/Canvas.tsx frontend/components/editor/CustomNode.tsx
git commit -m "feat(io): add Input/Output nodes to graph editor"
```

---

### Task 9: Frontend Properties Panels

**Files:**
- Create: `frontend/components/panels/InputProperties.tsx`
- Create: `frontend/components/panels/OutputProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Create InputProperties.tsx**

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useEditorStore } from "@/store/editorStore";
import type { InputNodeConfig, IODataType } from "@/types/io";
import { IO_DATA_TYPE_LABELS } from "@/types/io";

export function InputProperties({ nodeId, data }: { nodeId: string; data: InputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<InputNodeConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="input-name">Name</Label>
        <Input id="input-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="query" />
      </div>
      <div>
        <Label htmlFor="input-desc">Description</Label>
        <Textarea id="input-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What this input represents" />
      </div>
      <div>
        <Label>Data Type</Label>
        <Select value={data.dataType} onValueChange={(v) => update({ dataType: v as IODataType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {Object.entries(IO_DATA_TYPE_LABELS).map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center justify-between">
        <Label>Required</Label>
        <Switch checked={data.required} onCheckedChange={(v) => update({ required: v })} />
      </div>
      {!data.required && (
        <div>
          <Label htmlFor="input-default">Default Value</Label>
          <Input id="input-default" value={data.defaultValue || ""} onChange={(e) => update({ defaultValue: e.target.value || undefined })} placeholder="Default value" />
        </div>
      )}
    </div>
  );
}
```

Note: If `Switch` from `@/components/ui/switch` does not exist (it was noted in the A2A implementation that it may not be available), use the same toggle pattern that was used in `A2AConfigSection.tsx`.

- [ ] **Step 2: Create OutputProperties.tsx**

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { OutputNodeConfig, IODataType } from "@/types/io";
import { IO_DATA_TYPE_LABELS } from "@/types/io";

export function OutputProperties({ nodeId, data }: { nodeId: string; data: OutputNodeConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<OutputNodeConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="output-name">Name</Label>
        <Input id="output-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="result" />
      </div>
      <div>
        <Label htmlFor="output-desc">Description</Label>
        <Textarea id="output-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What this output represents" />
      </div>
      <div>
        <Label>Data Type</Label>
        <Select value={data.dataType} onValueChange={(v) => update({ dataType: v as IODataType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {Object.entries(IO_DATA_TYPE_LABELS).map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Update PropertiesPanel.tsx**

Add imports:
```typescript
import { InputProperties } from "./InputProperties";
import { OutputProperties } from "./OutputProperties";
import { InputNodeConfig, OutputNodeConfig } from "@/types/io";
```

Add heading mapping:
```typescript
selectedNode.type === "input" ? "Input" :
selectedNode.type === "output" ? "Output" :
```

Add rendering blocks:
```tsx
{selectedNode.type === "input" && (
  <InputProperties nodeId={selectedNode.id} data={selectedNode.data as InputNodeConfig} />
)}
{selectedNode.type === "output" && (
  <OutputProperties nodeId={selectedNode.id} data={selectedNode.data as OutputNodeConfig} />
)}
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`

- [ ] **Step 5: Commit**

```bash
git add frontend/components/panels/InputProperties.tsx \
        frontend/components/panels/OutputProperties.tsx \
        frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat(io): add Input and Output properties panels"
```

---

### Task 10: Final Verification

**Files:** None (test-only task)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 2: Verify frontend compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Run frontend build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build 2>&1 | tail -20`
Expected: Build succeeds
