# A2A Communication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Agent-to-Agent (A2A) communication to the Koog Agent Modeler — incoming (Agent as A2A server) and outgoing (a2a-agent node calling remote agents).

**Architecture:** A2A is a communication capability, not a new agent category. Agents get an optional `A2AConfig` for incoming A2A. A new `a2a-agent` node type represents remote A2A endpoints. Two new generators produce server/client code. The A2A Inspector panel shows message/task status.

**Tech Stack:** Kotlin/Spring Boot (backend models, adapter, validator, generators), TypeScript/React/Zustand (frontend types, store, UI components), Rete.js (graph nodes), JUnit 5 (tests)

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `src/main/kotlin/.../export/model/A2AModel.kt` | Backend A2A data classes |
| `src/main/kotlin/.../export/generator/A2AServerGenerator.kt` | Server code generation |
| `src/main/kotlin/.../export/generator/A2AClientGenerator.kt` | Client code generation |
| `src/test/kotlin/.../export/model/A2AModelTest.kt` | Model tests |
| `src/test/kotlin/.../export/adapter/GraphToKoogAdapterA2ATest.kt` | Adapter A2A tests |
| `src/test/kotlin/.../export/validation/GraphValidatorA2ATest.kt` | Validator A2A tests |
| `src/test/kotlin/.../export/generator/A2AServerGeneratorTest.kt` | Server generator tests |
| `src/test/kotlin/.../export/generator/A2AClientGeneratorTest.kt` | Client generator tests |
| `frontend/types/a2a.ts` | Frontend A2A TypeScript types |
| `frontend/components/a2a/A2AAgentProperties.tsx` | Properties panel for a2a-agent node |
| `frontend/components/a2a/A2AConfigSection.tsx` | A2A config section on agent nodes |
| `frontend/components/a2a/A2AInspectorPanel.tsx` | Message/task inspector |
| `frontend/store/a2aInspectorStore.ts` | Inspector panel state |

### Modified Files
| File | Change |
|------|--------|
| `src/.../export/model/KoogModel.kt` | Add `a2aConfig` to `KoogAgent` |
| `src/.../export/model/PlannerModel.kt` | Add `a2aConfig` to `KoogPlannerAgent` |
| `src/.../export/adapter/GraphToKoogAdapter.kt` | Parse a2a-agent nodes, extract A2AConfig |
| `src/.../export/validation/GraphValidator.kt` | Add A2A validation rules |
| `src/.../export/ExportService.kt` | Register new generators |
| `frontend/types/graph.ts` | Add `"a2a-agent"` to NodeType |
| `frontend/types/koog.ts` | Add `a2aConfig` to AgentConfig |
| `frontend/types/planner.ts` | Add `a2aConfig` to PlannerAgentConfig |
| `frontend/store/editorStore.ts` | Handle a2a-agent defaults |
| `frontend/lib/rete-nodes.ts` | Add A2AAgentReteNode |
| `frontend/lib/rete-setup.ts` | Add connection rules for a2a-agent |
| `frontend/lib/validation.ts` | Add A2A validation rules |
| `frontend/components/editor/Canvas.tsx` | Handle a2a-agent node type |
| `frontend/components/editor/CustomNode.tsx` | Add a2a-agent colors/icon/subtitle |
| `frontend/components/panels/PropertiesPanel.tsx` | Route to A2AAgentProperties |
| `frontend/components/panels/AgentProperties.tsx` | Include A2AConfigSection |
| `frontend/components/panels/PlannerProperties.tsx` | Include A2AConfigSection |
| `frontend/components/code-explorer/ActivityBar.tsx` | Add A2A Inspector button |
| `frontend/app/page.tsx` | Add A2AInspectorPanel |

---

### Task 1: Backend A2A Model

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/model/A2AModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt`

- [ ] **Step 1: Create A2AModel.kt**

```kotlin
package com.agentwork.modeler.export.model

data class KoogA2AConfig(
    val enabled: Boolean,
    val agentCard: KoogAgentCard,
    val server: KoogA2AServerConfig
)

data class KoogAgentCard(
    val name: String,
    val description: String,
    val url: String,
    val version: String,
    val capabilities: KoogA2ACapabilities,
    val skills: List<KoogA2ASkill>,
    val defaultInputModes: List<String>,
    val defaultOutputModes: List<String>
)

data class KoogA2ACapabilities(
    val streaming: Boolean,
    val pushNotifications: Boolean,
    val stateTransitionHistory: Boolean
)

data class KoogA2ASkill(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>
)

data class KoogA2AServerConfig(
    val transportType: String,
    val port: Int,
    val basePath: String
)

data class KoogA2AAgent(
    val name: String,
    val description: String,
    val url: String,
    val authType: String,
    val authToken: String?,
    val timeout: Int,
    val communicationMode: String
)
```

- [ ] **Step 2: Add a2aConfig to KoogAgent**

In `KoogModel.kt`, add `val a2aConfig: KoogA2AConfig? = null` as the last parameter of `KoogAgent`:

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
    val mcpServers: List<KoogMcpServer> = emptyList(),
    val otelConfig: OtelConfig? = null,
    val a2aConfig: KoogA2AConfig? = null,
    val a2aAgents: List<KoogA2AAgent> = emptyList()
)
```

- [ ] **Step 3: Add a2aConfig to KoogPlannerAgent**

In `PlannerModel.kt`, add to `KoogPlannerAgent`:

```kotlin
data class KoogPlannerAgent(
    val name: String,
    val description: String,
    val plannerType: PlannerType,
    val modelProvider: ModelProvider?,
    val modelId: String?,
    val temperature: Double?,
    val maxReplanAttempts: Int = 3,
    val goals: List<PlannerGoal>,
    val actions: List<PlannerAction>,
    val initialState: Map<String, Any> = emptyMap(),
    val tools: List<KoogTool> = emptyList(),
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList(),
    val mcpServers: List<KoogMcpServer> = emptyList(),
    val a2aConfig: KoogA2AConfig? = null,
    val a2aAgents: List<KoogA2AAgent> = emptyList()
)
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/A2AModel.kt \
        src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt \
        src/main/kotlin/com/agentwork/modeler/export/model/PlannerModel.kt
git commit -m "feat(a2a): add backend A2A data model classes"
```

---

### Task 2: Backend Adapter — A2A Support

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterA2ATest.kt`

- [ ] **Step 1: Write the failing test**

Create `GraphToKoogAdapterA2ATest.kt`:

```kotlin
package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphToKoogAdapterA2ATest {

    private val adapter = GraphToKoogAdapter()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    @Test
    fun `converts a2a-agent node connected to agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = listOf(edge("a2a1", "agent1")),
            nodes = listOf(
                node("agent1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")),
                node("a2a1", "a2a-agent", mapOf(
                    "name" to "RemoteBot",
                    "description" to "A remote agent",
                    "url" to "http://remote:8080/a2a",
                    "authType" to "bearer",
                    "authToken" to "tok123",
                    "timeout" to 30000,
                    "communicationMode" to "task"
                ))
            )
        )
        val agents = adapter.convert(graph)
        assertEquals(1, agents.size)
        assertEquals(1, agents[0].a2aAgents.size)
        val a2a = agents[0].a2aAgents[0]
        assertEquals("RemoteBot", a2a.name)
        assertEquals("http://remote:8080/a2a", a2a.url)
        assertEquals("bearer", a2a.authType)
        assertEquals("tok123", a2a.authToken)
        assertEquals(30000, a2a.timeout)
        assertEquals("task", a2a.communicationMode)
    }

    @Test
    fun `extracts a2aConfig from agent node data`() {
        val a2aConfigData = mapOf(
            "enabled" to true,
            "agentCard" to mapOf(
                "name" to "MyBot",
                "description" to "A helpful bot",
                "url" to "http://localhost:9000/a2a",
                "version" to "1.0.0",
                "capabilities" to mapOf(
                    "streaming" to true,
                    "pushNotifications" to false,
                    "stateTransitionHistory" to true
                ),
                "skills" to listOf(
                    mapOf("id" to "s1", "name" to "search", "description" to "Search the web", "tags" to listOf("web"))
                ),
                "defaultInputModes" to listOf("text"),
                "defaultOutputModes" to listOf("text")
            ),
            "server" to mapOf(
                "transportType" to "http-jsonrpc",
                "port" to 9000,
                "basePath" to "/a2a"
            )
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = emptyList(),
            nodes = listOf(
                node("agent1", "agent", mapOf(
                    "name" to "Bot", "systemPrompt" to "Hi",
                    "a2aConfig" to a2aConfigData
                ))
            )
        )
        val agents = adapter.convert(graph)
        val config = agents[0].a2aConfig
        assertNotNull(config)
        assertTrue(config.enabled)
        assertEquals("MyBot", config.agentCard.name)
        assertEquals("http://localhost:9000/a2a", config.agentCard.url)
        assertTrue(config.agentCard.capabilities.streaming)
        assertEquals(1, config.agentCard.skills.size)
        assertEquals("search", config.agentCard.skills[0].name)
        assertEquals(9000, config.server.port)
    }

    @Test
    fun `agent without a2aConfig has null a2aConfig`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = emptyList(),
            nodes = listOf(node("agent1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi")))
        )
        val agents = adapter.convert(graph)
        assertNull(agents[0].a2aConfig)
        assertTrue(agents[0].a2aAgents.isEmpty())
    }

    @Test
    fun `converts a2a-agent connected to planner-agent`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test", edges = listOf(edge("a2a1", "p1")),
            nodes = listOf(
                node("p1", "planner-agent", mapOf(
                    "name" to "Planner", "plannerType" to "llm", "modelProvider" to "openai",
                    "goals" to listOf(mapOf("name" to "g1", "conditions" to listOf(mapOf("key" to "done", "operator" to "equals", "value" to true)), "priority" to 0)),
                    "actions" to listOf(mapOf("name" to "a1", "description" to "act", "preconditions" to emptyList<Any>(), "effects" to listOf(mapOf("key" to "done", "value" to true)), "cost" to 1))
                )),
                node("a2a1", "a2a-agent", mapOf(
                    "name" to "Helper",
                    "description" to "Helps",
                    "url" to "http://helper:8080",
                    "authType" to "none",
                    "timeout" to 5000,
                    "communicationMode" to "message"
                ))
            )
        )
        val planners = adapter.convertPlannerAgents(graph)
        assertEquals(1, planners.size)
        assertEquals(1, planners[0].a2aAgents.size)
        assertEquals("Helper", planners[0].a2aAgents[0].name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapterA2ATest*" 2>&1 | tail -20`
Expected: FAIL — `a2aAgents` property doesn't exist yet in adapter output

- [ ] **Step 3: Implement adapter changes**

In `GraphToKoogAdapter.kt`, add the following changes:

**Add import** at top:
```kotlin
import com.agentwork.modeler.export.model.KoogA2AAgent
import com.agentwork.modeler.export.model.KoogA2AConfig
import com.agentwork.modeler.export.model.KoogAgentCard
import com.agentwork.modeler.export.model.KoogA2ACapabilities
import com.agentwork.modeler.export.model.KoogA2ASkill
import com.agentwork.modeler.export.model.KoogA2AServerConfig
```

**Add `a2aAgentNodes` filter** in `convert()` alongside other node filters (after line 19):
```kotlin
val a2aAgentNodes = graph.nodes.filter { it.type == "a2a-agent" }
```

**Add A2A agent conversion** inside the `agentNodes.map` block, before the `KoogAgent(` constructor call (after mcpServers):
```kotlin
val a2aAgents = a2aAgentNodes
    .filter { it.id in connectedToAgent }
    .map { convertA2AAgent(it) }

val a2aConfig = extractA2AConfig(agentNode)
```

**Add to KoogAgent constructor** (after `otelConfig`):
```kotlin
a2aConfig = a2aConfig,
a2aAgents = a2aAgents
```

**Same for `convertPlannerAgents()`** — add `a2aAgentNodes` filter and conversion inside the planner map block:
```kotlin
val a2aAgentNodes = graph.nodes.filter { it.type == "a2a-agent" }
```
Then inside the `graph.nodes.filter { it.type == "planner-agent" }.map` block, after mcpServers:
```kotlin
val a2aAgents = a2aAgentNodes
    .filter { it.id in connectedIds }
    .map { convertA2AAgent(it) }

val a2aConfig = extractA2AConfig(plannerNode)
```
And add to `KoogPlannerAgent` constructor:
```kotlin
a2aConfig = a2aConfig,
a2aAgents = a2aAgents
```

**Add converter methods** at the bottom of the class:
```kotlin
private fun convertA2AAgent(node: GraphNode): KoogA2AAgent {
    return KoogA2AAgent(
        name = node.data["name"] as? String ?: "",
        description = node.data["description"] as? String ?: "",
        url = node.data["url"] as? String ?: "",
        authType = node.data["authType"] as? String ?: "none",
        authToken = node.data["authToken"] as? String,
        timeout = (node.data["timeout"] as? Number)?.toInt() ?: 30000,
        communicationMode = node.data["communicationMode"] as? String ?: "message"
    )
}

@Suppress("UNCHECKED_CAST")
private fun extractA2AConfig(node: GraphNode): KoogA2AConfig? {
    val configMap = node.data["a2aConfig"] as? Map<String, Any?> ?: return null
    val enabled = configMap["enabled"] as? Boolean ?: false
    if (!enabled) return null

    val cardMap = configMap["agentCard"] as? Map<String, Any?> ?: return null
    val capsMap = cardMap["capabilities"] as? Map<String, Any?> ?: emptyMap()
    val skillsList = (cardMap["skills"] as? List<Map<String, Any?>>)?.map { sMap ->
        KoogA2ASkill(
            id = sMap["id"] as? String ?: "",
            name = sMap["name"] as? String ?: "",
            description = sMap["description"] as? String ?: "",
            tags = (sMap["tags"] as? List<String>) ?: emptyList()
        )
    } ?: emptyList()

    val serverMap = configMap["server"] as? Map<String, Any?> ?: emptyMap()

    return KoogA2AConfig(
        enabled = true,
        agentCard = KoogAgentCard(
            name = cardMap["name"] as? String ?: "",
            description = cardMap["description"] as? String ?: "",
            url = cardMap["url"] as? String ?: "",
            version = cardMap["version"] as? String ?: "1.0.0",
            capabilities = KoogA2ACapabilities(
                streaming = capsMap["streaming"] as? Boolean ?: false,
                pushNotifications = capsMap["pushNotifications"] as? Boolean ?: false,
                stateTransitionHistory = capsMap["stateTransitionHistory"] as? Boolean ?: false
            ),
            skills = skillsList,
            defaultInputModes = (cardMap["defaultInputModes"] as? List<String>) ?: listOf("text"),
            defaultOutputModes = (cardMap["defaultOutputModes"] as? List<String>) ?: listOf("text")
        ),
        server = KoogA2AServerConfig(
            transportType = serverMap["transportType"] as? String ?: "http-jsonrpc",
            port = (serverMap["port"] as? Number)?.toInt() ?: 9000,
            basePath = serverMap["basePath"] as? String ?: "/a2a"
        )
    )
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapterA2ATest*" 2>&1 | tail -20`
Expected: All 4 tests PASS

- [ ] **Step 5: Run all existing adapter tests to ensure no regression**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphToKoogAdapter*" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapter.kt \
        src/test/kotlin/com/agentwork/modeler/export/adapter/GraphToKoogAdapterA2ATest.kt
git commit -m "feat(a2a): add A2A support to GraphToKoogAdapter"
```

---

### Task 3: Backend Validator — A2A Rules

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorA2ATest.kt`

- [ ] **Step 1: Write the failing test**

Create `GraphValidatorA2ATest.kt`:

```kotlin
package com.agentwork.modeler.export.validation

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphValidatorA2ATest {

    private val validator = GraphValidator()

    private fun node(id: String, type: String, data: Map<String, Any?>) =
        GraphNode(id, type, Position(0.0, 0.0), data)

    private fun edge(source: String, target: String) =
        GraphEdge("e-$source-$target", source, target)

    private fun agentNode(id: String = "a1") =
        node(id, "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi"))

    @Test
    fun `a2a-agent without url is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("URL") && it.contains("a2a1") })
    }

    @Test
    fun `a2a-agent not connected to agent is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://x", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("connected") && it.contains("a2a1") })
    }

    @Test
    fun `a2a-agent without name is an error`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "", "url" to "http://x", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("name") && it.contains("a2a1") })
    }

    @Test
    fun `valid a2a-agent produces no errors`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://remote:8080/a2a", "authType" to "none", "timeout" to 30000, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.none { it.contains("a2a1") })
    }

    @Test
    fun `a2a enabled but agentCard name missing is an error`() {
        val a2aConfig = mapOf(
            "enabled" to true,
            "agentCard" to mapOf("name" to "", "url" to "http://x", "description" to "d", "version" to "1"),
            "server" to mapOf("port" to 9000, "basePath" to "/a2a")
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi", "a2aConfig" to a2aConfig))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("AgentCard") && it.contains("name") })
    }

    @Test
    fun `a2a enabled but agentCard url missing is an error`() {
        val a2aConfig = mapOf(
            "enabled" to true,
            "agentCard" to mapOf("name" to "Bot", "url" to "", "description" to "d", "version" to "1"),
            "server" to mapOf("port" to 9000, "basePath" to "/a2a")
        )
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(node("a1", "agent", mapOf("name" to "Bot", "systemPrompt" to "Hi", "a2aConfig" to a2aConfig))),
            edges = emptyList()
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("AgentCard") && it.contains("url") })
    }

    @Test
    fun `a2a-agent with timeout zero is a warning`() {
        val graph = WorkspaceGraph(
            version = 1, name = "test",
            nodes = listOf(
                agentNode(),
                node("a2a1", "a2a-agent", mapOf("name" to "Remote", "url" to "http://x", "authType" to "none", "timeout" to 0, "communicationMode" to "message"))
            ),
            edges = listOf(edge("a2a1", "a1"))
        )
        val errors = validator.validate(graph)
        assertTrue(errors.any { it.contains("timeout") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidatorA2ATest*" 2>&1 | tail -20`
Expected: FAIL

- [ ] **Step 3: Add A2A validation to GraphValidator**

In `GraphValidator.kt`, add a new `when` branch inside the `for (node in graph.nodes)` block, after the `"sub-agent"` case (before the closing `}`):

```kotlin
"a2a-agent" -> {
    val name = node.data["name"] as? String ?: ""
    val url = node.data["url"] as? String ?: ""
    val timeout = (node.data["timeout"] as? Number)?.toInt() ?: 30000
    if (name.isBlank()) errors.add("[${node.id}] A2A Agent requires a name")
    if (url.isBlank()) errors.add("[${node.id}] A2A Agent requires a URL")
    val isConnected = graph.edges.any { it.source == node.id }
    if (!isConnected) errors.add("[${node.id}] A2A Agent must be connected to an Agent")
    if (timeout <= 0) errors.add("[${node.id}] A2A Agent timeout should be greater than 0")
}
```

Also add A2A config validation inside the `"agent"` case, after the system prompt check:

```kotlin
// A2A config validation
@Suppress("UNCHECKED_CAST")
val a2aConfig = node.data["a2aConfig"] as? Map<String, Any?>
if (a2aConfig != null && a2aConfig["enabled"] == true) {
    val cardMap = a2aConfig["agentCard"] as? Map<String, Any?>
    val cardName = (cardMap?.get("name") as? String) ?: ""
    val cardUrl = (cardMap?.get("url") as? String) ?: ""
    if (cardName.isBlank()) errors.add("[${node.id}] A2A AgentCard requires a name")
    if (cardUrl.isBlank()) errors.add("[${node.id}] A2A AgentCard requires a url")
}
```

Add the same A2A config validation inside the `"planner-agent"` case, after existing validation.

Also update the top-level agent count check to **not** count `a2a-agent` as an agent:

The existing check `if (agentCount == 0 && plannerCount == 0)` should remain as-is since `a2a-agent` is already not counted as `"agent"`.

- [ ] **Step 4: Run A2A validator tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidatorA2ATest*" 2>&1 | tail -20`
Expected: All 7 tests PASS

- [ ] **Step 5: Run all validator tests for regression**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*GraphValidator*" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt \
        src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorA2ATest.kt
git commit -m "feat(a2a): add A2A validation rules to GraphValidator"
```

---

### Task 4: Backend A2A Server Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/A2AServerGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/A2AServerGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `A2AServerGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class A2AServerGeneratorTest {

    private val generator = A2AServerGenerator()

    private fun config(
        streaming: Boolean = false,
        skills: List<KoogA2ASkill> = listOf(
            KoogA2ASkill("s1", "search", "Search the web", listOf("web"))
        )
    ) = KoogA2AConfig(
        enabled = true,
        agentCard = KoogAgentCard(
            name = "TestBot",
            description = "A test bot",
            url = "http://localhost:9000/a2a",
            version = "1.0.0",
            capabilities = KoogA2ACapabilities(
                streaming = streaming,
                pushNotifications = false,
                stateTransitionHistory = false
            ),
            skills = skills,
            defaultInputModes = listOf("text"),
            defaultOutputModes = listOf("text")
        ),
        server = KoogA2AServerConfig(
            transportType = "http-jsonrpc",
            port = 9000,
            basePath = "/a2a"
        )
    )

    @Test
    fun `generates AgentCard data object`() {
        val files = generator.generate(config())
        val code = files["A2AAgentCard.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "object A2AAgentCard")
        assertContains(code, "\"TestBot\"")
        assertContains(code, "\"A test bot\"")
        assertContains(code, "\"http://localhost:9000/a2a\"")
        assertContains(code, "\"1.0.0\"")
        assertContains(code, "\"search\"")
    }

    @Test
    fun `generates A2AServer configuration`() {
        val files = generator.generate(config())
        val code = files["A2AServer.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "9000")
        assertContains(code, "/a2a")
        assertContains(code, "A2AAgentCard")
    }

    @Test
    fun `generates streaming handler when streaming enabled`() {
        val files = generator.generate(config(streaming = true))
        val code = files["A2AServer.kt"]!!
        assertContains(code, "streaming")
    }

    @Test
    fun `generates executor skeleton`() {
        val files = generator.generate(config())
        val code = files["A2AExecutor.kt"]!!
        assertContains(code, "package generated.a2a")
        assertContains(code, "class A2AExecutor")
        assertContains(code, "handleMessage")
        assertContains(code, "handleTask")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*A2AServerGeneratorTest*" 2>&1 | tail -20`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement A2AServerGenerator**

Create `A2AServerGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AConfig
import org.springframework.stereotype.Component

@Component
class A2AServerGenerator {

    fun generate(config: KoogA2AConfig): Map<String, String> {
        val files = mutableMapOf<String, String>()
        files["A2AAgentCard.kt"] = generateAgentCard(config)
        files["A2AExecutor.kt"] = generateExecutor(config)
        files["A2AServer.kt"] = generateServer(config)
        return files
    }

    private fun generateAgentCard(config: KoogA2AConfig): String = buildString {
        val card = config.agentCard
        appendLine("package generated.a2a")
        appendLine()
        appendLine("object A2AAgentCard {")
        appendLine("    val name = \"${card.name}\"")
        appendLine("    val description = \"${card.description}\"")
        appendLine("    val url = \"${card.url}\"")
        appendLine("    val version = \"${card.version}\"")
        appendLine()
        appendLine("    val capabilities = mapOf(")
        appendLine("        \"streaming\" to ${card.capabilities.streaming},")
        appendLine("        \"pushNotifications\" to ${card.capabilities.pushNotifications},")
        appendLine("        \"stateTransitionHistory\" to ${card.capabilities.stateTransitionHistory}")
        appendLine("    )")
        appendLine()
        appendLine("    val skills = listOf(")
        for ((i, skill) in card.skills.withIndex()) {
            val comma = if (i < card.skills.size - 1) "," else ""
            appendLine("        mapOf(\"id\" to \"${skill.id}\", \"name\" to \"${skill.name}\", \"description\" to \"${skill.description}\", \"tags\" to listOf(${skill.tags.joinToString(", ") { "\"$it\"" }}))$comma")
        }
        appendLine("    )")
        appendLine()
        appendLine("    val defaultInputModes = listOf(${card.defaultInputModes.joinToString(", ") { "\"$it\"" }})")
        appendLine("    val defaultOutputModes = listOf(${card.defaultOutputModes.joinToString(", ") { "\"$it\"" }})")
        appendLine()
        appendLine("    fun toJson(): String = \"\"\"{")
        appendLine("        |  \"name\": \"${'$'}{name}\",")
        appendLine("        |  \"description\": \"${'$'}{description}\",")
        appendLine("        |  \"url\": \"${'$'}{url}\",")
        appendLine("        |  \"version\": \"${'$'}{version}\"")
        appendLine("        |}\"\"\".trimMargin()")
        appendLine("}")
    }

    private fun generateExecutor(config: KoogA2AConfig): String = buildString {
        appendLine("package generated.a2a")
        appendLine()
        appendLine("/**")
        appendLine(" * A2A Executor — handles incoming A2A requests.")
        appendLine(" * Wire this to your agent's invoke logic.")
        appendLine(" */")
        appendLine("class A2AExecutor {")
        appendLine()
        appendLine("    suspend fun handleMessage(message: String): String {")
        appendLine("        // TODO: Delegate to generated agent")
        appendLine("        return \"Response from \${A2AAgentCard.name}\"")
        appendLine("    }")
        appendLine()
        appendLine("    suspend fun handleTask(taskId: String, message: String): TaskStatus {")
        appendLine("        // TODO: Delegate to generated agent with task tracking")
        appendLine("        return TaskStatus(taskId, \"completed\", \"Task processed by \${A2AAgentCard.name}\")")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("data class TaskStatus(val taskId: String, val status: String, val message: String)")
    }

    private fun generateServer(config: KoogA2AConfig): String = buildString {
        val server = config.server
        appendLine("package generated.a2a")
        appendLine()
        appendLine("import org.springframework.web.bind.annotation.*")
        appendLine("import org.springframework.http.MediaType")
        if (config.agentCard.capabilities.streaming) {
            appendLine("import org.springframework.http.codec.ServerSentEvent")
            appendLine("import reactor.core.publisher.Flux")
        }
        appendLine()
        appendLine("/**")
        appendLine(" * A2A Server — HTTP JSON-RPC transport on port ${server.port}, basePath ${server.basePath}")
        appendLine(" */")
        appendLine("@RestController")
        appendLine("@RequestMapping(\"${server.basePath}\")")
        appendLine("class A2AServer(private val executor: A2AExecutor) {")
        appendLine()
        appendLine("    @GetMapping(\"/.well-known/agent.json\", produces = [MediaType.APPLICATION_JSON_VALUE])")
        appendLine("    fun agentCard(): String = A2AAgentCard.toJson()")
        appendLine()
        appendLine("    @PostMapping(\"/message\")")
        appendLine("    suspend fun sendMessage(@RequestBody request: A2AMessageRequest): A2AMessageResponse {")
        appendLine("        val result = executor.handleMessage(request.message)")
        appendLine("        return A2AMessageResponse(result = result)")
        appendLine("    }")
        appendLine()
        appendLine("    @PostMapping(\"/task\")")
        appendLine("    suspend fun sendTask(@RequestBody request: A2ATaskRequest): TaskStatus {")
        appendLine("        return executor.handleTask(request.taskId, request.message)")
        appendLine("    }")
        if (config.agentCard.capabilities.streaming) {
            appendLine()
            appendLine("    @GetMapping(\"/task/{taskId}/events\", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])")
            appendLine("    fun streamTaskEvents(@PathVariable taskId: String): Flux<ServerSentEvent<String>> {")
            appendLine("        // TODO: Implement streaming task status updates")
            appendLine("        return Flux.just(ServerSentEvent.builder(\"{ \\\"taskId\\\": \\\"${'$'}taskId\\\", \\\"status\\\": \\\"completed\\\" }\").build())")
            appendLine("    }")
        }
        appendLine("}")
        appendLine()
        appendLine("data class A2AMessageRequest(val message: String)")
        appendLine("data class A2AMessageResponse(val result: String)")
        appendLine("data class A2ATaskRequest(val taskId: String, val message: String)")
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*A2AServerGeneratorTest*" 2>&1 | tail -20`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/A2AServerGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/A2AServerGeneratorTest.kt
git commit -m "feat(a2a): add A2AServerGenerator for AgentCard, Executor, Server"
```

---

### Task 5: Backend A2A Client Generator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/A2AClientGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/A2AClientGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `A2AClientGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class A2AClientGeneratorTest {

    private val generator = A2AClientGenerator()

    @Test
    fun `generates client class with sendMessage`() {
        val agent = KoogA2AAgent("RemoteBot", "A remote bot", "http://remote:8080/a2a", "none", null, 30000, "message")
        val code = generator.generate(agent)
        assertContains(code, "package generated.a2a")
        assertContains(code, "class RemoteBotA2AClient")
        assertContains(code, "sendMessage")
        assertContains(code, "http://remote:8080/a2a")
    }

    @Test
    fun `generates sendTask for task communication mode`() {
        val agent = KoogA2AAgent("Helper", "Helps", "http://helper:8080", "bearer", "tok123", 5000, "task")
        val code = generator.generate(agent)
        assertContains(code, "sendTask")
        assertContains(code, "getTaskStatus")
        assertContains(code, "tok123")
        assertContains(code, "Authorization")
    }

    @Test
    fun `generates timeout configuration`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "none", null, 15000, "message")
        val code = generator.generate(agent)
        assertContains(code, "15000")
    }

    @Test
    fun `no auth header when authType is none`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "none", null, 30000, "message")
        val code = generator.generate(agent)
        assertFalse(code.contains("Authorization"))
    }

    @Test
    fun `generates api-key auth header`() {
        val agent = KoogA2AAgent("Bot", "A bot", "http://x", "api-key", "key123", 30000, "message")
        val code = generator.generate(agent)
        assertContains(code, "x-api-key")
        assertContains(code, "key123")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*A2AClientGeneratorTest*" 2>&1 | tail -20`
Expected: FAIL

- [ ] **Step 3: Implement A2AClientGenerator**

Create `A2AClientGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogA2AAgent
import org.springframework.stereotype.Component

@Component
class A2AClientGenerator {

    fun generate(agent: KoogA2AAgent): String {
        val className = "${agent.name.replace(" ", "").replaceFirstChar { it.uppercase() }}A2AClient"
        val baseUrl = agent.url.trimEnd('/')

        return buildString {
            appendLine("package generated.a2a")
            appendLine()
            appendLine("import java.net.URI")
            appendLine("import java.net.http.HttpClient")
            appendLine("import java.net.http.HttpRequest")
            appendLine("import java.net.http.HttpResponse")
            appendLine("import java.time.Duration")
            appendLine()
            appendLine("/**")
            appendLine(" * A2A Client for ${agent.name} — ${agent.description}")
            appendLine(" * Target: $baseUrl")
            appendLine(" */")
            appendLine("class $className {")
            appendLine()
            appendLine("    private val baseUrl = \"$baseUrl\"")
            appendLine("    private val timeout = Duration.ofMillis(${agent.timeout}.toLong())")
            appendLine("    private val client = HttpClient.newBuilder().connectTimeout(timeout).build()")
            appendLine()

            // Auth header helper
            when (agent.authType) {
                "bearer" -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder =")
                    appendLine("        builder.header(\"Authorization\", \"Bearer ${agent.authToken}\")")
                }
                "api-key" -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder =")
                    appendLine("        builder.header(\"x-api-key\", \"${agent.authToken}\")")
                }
                else -> {
                    appendLine("    private fun authHeaders(builder: HttpRequest.Builder): HttpRequest.Builder = builder")
                }
            }
            appendLine()

            // sendMessage
            appendLine("    fun sendMessage(message: String): String {")
            appendLine("        val body = \"\"\"{\"message\": \"${'$'}message\"}\"\"\".trimIndent()")
            appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
            appendLine("            .uri(URI.create(\"${'$'}baseUrl/message\"))")
            appendLine("            .header(\"Content-Type\", \"application/json\")")
            appendLine("            .timeout(timeout)")
            appendLine("            .POST(HttpRequest.BodyPublishers.ofString(body)))")
            appendLine("            .build()")
            appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
            appendLine("        return response.body()")
            appendLine("    }")

            // sendTask (only for task mode)
            if (agent.communicationMode == "task") {
                appendLine()
                appendLine("    fun sendTask(taskId: String, message: String): String {")
                appendLine("        val body = \"\"\"{\"taskId\": \"${'$'}taskId\", \"message\": \"${'$'}message\"}\"\"\".trimIndent()")
                appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
                appendLine("            .uri(URI.create(\"${'$'}baseUrl/task\"))")
                appendLine("            .header(\"Content-Type\", \"application/json\")")
                appendLine("            .timeout(timeout)")
                appendLine("            .POST(HttpRequest.BodyPublishers.ofString(body)))")
                appendLine("            .build()")
                appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
                appendLine("        return response.body()")
                appendLine("    }")
                appendLine()
                appendLine("    fun getTaskStatus(taskId: String): String {")
                appendLine("        val request = authHeaders(HttpRequest.newBuilder()")
                appendLine("            .uri(URI.create(\"${'$'}baseUrl/task/${'$'}taskId\"))")
                appendLine("            .timeout(timeout)")
                appendLine("            .GET())")
                appendLine("            .build()")
                appendLine("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())")
                appendLine("        return response.body()")
                appendLine("    }")
            }

            appendLine("}")
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test --tests "*A2AClientGeneratorTest*" 2>&1 | tail -20`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/A2AClientGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/A2AClientGeneratorTest.kt
git commit -m "feat(a2a): add A2AClientGenerator for remote agent communication"
```

---

### Task 6: Backend ExportService Integration

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`

- [ ] **Step 1: Add generator dependencies to ExportService constructor**

Add after `private val plannerAgentGenerator: PlannerAgentGenerator,`:

```kotlin
private val a2aServerGenerator: A2AServerGenerator,
private val a2aClientGenerator: A2AClientGenerator,
```

- [ ] **Step 2: Add A2A file generation in the regular agent path**

In `generateFiles()`, in the regular agent path (after MCP servers section, before the ToolRegistry section), add:

```kotlin
// A2A Server (if agent has A2A config)
val a2aConfig = agent.a2aConfig
if (a2aConfig != null && a2aConfig.enabled) {
    val a2aFiles = a2aServerGenerator.generate(a2aConfig)
    for ((fileName, content) in a2aFiles) {
        files["$pkg/a2a/$fileName"] = content
    }
}

// A2A Clients (for connected a2a-agent nodes)
for (a2aAgent in agent.a2aAgents) {
    val className = "${a2aAgent.name.replace(" ", "").replaceFirstChar { it.uppercase() }}A2AClient"
    files["$pkg/a2a/${className}.kt"] = a2aClientGenerator.generate(a2aAgent)
}
```

- [ ] **Step 3: Add same generation in planner agent path**

In the planner agent path, add the same A2A generation after MCP servers:

```kotlin
// A2A Server (if planner has A2A config)
val a2aConfig = planner.a2aConfig
if (a2aConfig != null && a2aConfig.enabled) {
    val a2aFiles = a2aServerGenerator.generate(a2aConfig)
    for ((fileName, content) in a2aFiles) {
        files["$pkg/a2a/$fileName"] = content
    }
}

// A2A Clients
for (a2aAgent in planner.a2aAgents) {
    val className = "${a2aAgent.name.replace(" ", "").replaceFirstChar { it.uppercase() }}A2AClient"
    files["$pkg/a2a/${className}.kt"] = a2aClientGenerator.generate(a2aAgent)
}
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test 2>&1 | tail -20`
Expected: All tests PASS (ExportService tests should still pass because a2aConfig defaults to null)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt
git commit -m "feat(a2a): integrate A2A generators into ExportService"
```

---

### Task 7: Frontend Types

**Files:**
- Create: `frontend/types/a2a.ts`
- Modify: `frontend/types/graph.ts`
- Modify: `frontend/types/koog.ts`
- Modify: `frontend/types/planner.ts`

- [ ] **Step 1: Create a2a.ts**

```typescript
export interface A2AConfig {
  enabled: boolean;
  agentCard: AgentCard;
  server: A2AServerConfig;
}

export interface AgentCard {
  name: string;
  description: string;
  url: string;
  version: string;
  capabilities: A2ACapabilities;
  skills: A2ASkill[];
  defaultInputModes: string[];
  defaultOutputModes: string[];
}

export interface A2ACapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

export interface A2ASkill {
  id: string;
  name: string;
  description: string;
  tags: string[];
}

export interface A2AServerConfig {
  transportType: "http-jsonrpc";
  port: number;
  basePath: string;
}

export type A2AAuthType = "none" | "bearer" | "api-key";
export type A2ACommunicationMode = "message" | "task";

export interface A2AAgentConfig {
  name: string;
  description: string;
  url: string;
  authType: A2AAuthType;
  authToken?: string;
  timeout: number;
  communicationMode: A2ACommunicationMode;
}

export type A2ATaskStatus = "submitted" | "working" | "input-required" | "completed" | "failed" | "canceled";

export interface A2AMessage {
  id: string;
  role: "user" | "agent";
  parts: A2AMessagePart[];
  timestamp: string;
}

export interface A2AMessagePart {
  type: "text" | "file" | "data";
  content: string;
  mimeType?: string;
}

export interface A2ATaskEvent {
  id: string;
  taskId: string;
  status: A2ATaskStatus;
  message?: A2AMessage;
  timestamp: string;
}
```

- [ ] **Step 2: Update graph.ts — add a2a-agent to NodeType**

In `frontend/types/graph.ts`, change the NodeType line:

From:
```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent";
```
To:
```typescript
export type NodeType = "agent" | "tool" | "builtin-tool" | "sub-agent" | "mcp-server" | "planner-agent" | "a2a-agent";
```

Add import and update the data union in `GraphNode`:

From:
```typescript
import { AgentConfig, ToolConfig } from "./koog";
import { SubAgentConfig } from "./subagent";
import { McpServerConfig } from "./mcp";
import { PlannerAgentConfig } from "./planner";
```
To:
```typescript
import { AgentConfig, ToolConfig } from "./koog";
import { SubAgentConfig } from "./subagent";
import { McpServerConfig } from "./mcp";
import { PlannerAgentConfig } from "./planner";
import { A2AAgentConfig } from "./a2a";
```

Update GraphNode data:
```typescript
data: AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | PlannerAgentConfig | A2AAgentConfig;
```

- [ ] **Step 3: Update koog.ts — add a2aConfig to AgentConfig**

```typescript
import type { A2AConfig } from "./a2a";

export interface AgentConfig {
  name: string;
  systemPrompt: string;
  modelProvider: ModelProvider;
  modelId?: string;
  temperature?: number;
  a2aConfig?: A2AConfig;
}
```

- [ ] **Step 4: Update planner.ts — add a2aConfig to PlannerAgentConfig**

Add import:
```typescript
import type { A2AConfig } from "./a2a";
```

Add to `PlannerAgentConfig`:
```typescript
a2aConfig?: A2AConfig;
```

- [ ] **Step 5: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors (or pre-existing errors only)

- [ ] **Step 6: Commit**

```bash
git add frontend/types/a2a.ts frontend/types/graph.ts frontend/types/koog.ts frontend/types/planner.ts
git commit -m "feat(a2a): add frontend A2A TypeScript types"
```

---

### Task 8: Frontend Store & Validation

**Files:**
- Modify: `frontend/store/editorStore.ts`
- Modify: `frontend/lib/validation.ts`

- [ ] **Step 1: Update editorStore — add a2a-agent default data and import**

Add import:
```typescript
import { A2AAgentConfig } from "@/types/a2a";
```

Add default data after `defaultPlannerAgentData`:
```typescript
const defaultA2AAgentData: A2AAgentConfig = {
  name: "",
  description: "",
  url: "",
  authType: "none",
  timeout: 30000,
  communicationMode: "message",
};
```

Update the `addNode` method — add a new `else if` before the final `else`:
```typescript
} else if (type === "a2a-agent") {
  data = { ...defaultA2AAgentData };
} else {
```

Update `updateNodeData` type to include `A2AAgentConfig`:
```typescript
updateNodeData: (id: string, data: Partial<AgentConfig | ToolConfig | BuiltinToolConfig | SubAgentConfig | McpServerConfig | A2AAgentConfig>) => void;
```

- [ ] **Step 2: Update validation.ts — add A2A rules**

Add import:
```typescript
import { A2AAgentConfig } from "@/types/a2a";
```

Add a2a-agent validation block inside the `for (const node of graph.nodes)` loop, after the planner-agent block:

```typescript
if (node.type === "a2a-agent") {
  const data = node.data as A2AAgentConfig;
  if (!data.name?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent requires a name" });
  }
  if (!data.url?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent requires a URL" });
  }
  const isConnected = graph.edges.some((e) => e.source === node.id);
  if (!isConnected) {
    errors.push({ nodeId: node.id, severity: "error", message: "A2A Agent must be connected to an Agent" });
  }
  if ((data.timeout ?? 0) <= 0) {
    errors.push({ nodeId: node.id, severity: "warning", message: "A2A Agent timeout should be greater than 0" });
  }
}
```

Add A2A config validation inside the agent block (after the systemPrompt check):

```typescript
// A2A config validation
const agentData = node.data as AgentConfig;
if (agentData.a2aConfig?.enabled) {
  const card = agentData.a2aConfig.agentCard;
  if (!card?.name?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "A2A AgentCard requires a name" });
  }
  if (!card?.url?.trim()) {
    errors.push({ nodeId: node.id, severity: "error", message: "A2A AgentCard requires a URL" });
  }
  if (!card?.skills?.length) {
    errors.push({ nodeId: node.id, severity: "warning", message: "A2A AgentCard has no skills defined" });
  }
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add frontend/store/editorStore.ts frontend/lib/validation.ts
git commit -m "feat(a2a): update store and validation for a2a-agent"
```

---

### Task 9: Frontend Rete Integration (Graph Nodes)

**Files:**
- Modify: `frontend/lib/rete-nodes.ts`
- Modify: `frontend/lib/rete-setup.ts`
- Modify: `frontend/components/editor/Canvas.tsx`
- Modify: `frontend/components/editor/CustomNode.tsx`

- [ ] **Step 1: Add A2AAgentReteNode to rete-nodes.ts**

Add before the `AppNode` type:

```typescript
export class A2AAgentReteNode extends ClassicPreset.Node {
  width = 220;
  height = 100;

  constructor(public storeNodeId: string, label: string) {
    super(label || "A2A Agent");
    this.addOutput("agent", new ClassicPreset.Output(toolSocket, "Agent"));
  }
}
```

Update `AppNode` type:
```typescript
export type AppNode = AgentReteNode | ToolReteNode | BuiltinToolReteNode | SubAgentReteNode | McpServerReteNode | PlannerAgentReteNode | A2AAgentReteNode;
```

- [ ] **Step 2: Update rete-setup.ts — add connection rules for a2a-agent**

Add import:
```typescript
import {
  AppNode,
  AppConnection,
  AgentReteNode,
  ToolReteNode,
  BuiltinToolReteNode,
  SubAgentReteNode,
  McpServerReteNode,
  A2AAgentReteNode,
} from "./rete-nodes";
```

Add connection rule in the pipe handler, after the MCP rule (before `return undefined`):

```typescript
// A2A Agent -> Agent: OK
const isA2ASource = sourceNode instanceof A2AAgentReteNode;
if (isA2ASource && isAgentTarget) {
  return context;
}
```

- [ ] **Step 3: Update Canvas.tsx — handle a2a-agent node type**

Add import:
```typescript
import { AgentReteNode, ToolReteNode, BuiltinToolReteNode, SubAgentReteNode, McpServerReteNode, PlannerAgentReteNode, A2AAgentReteNode } from "@/lib/rete-nodes";
```

In the Zustand → Rete sync subscriber, add a new `else if` block for a2a-agent (after planner-agent, before the final else):

```typescript
} else if (node.type === "a2a-agent") {
  reteNode = new A2AAgentReteNode(node.id, (node.data as any).name || "A2A Agent");
}
```

In the `connectioncreated` pipe, add A2A source check:
```typescript
const isA2ASource = sourceNode instanceof A2AAgentReteNode;
```

Update the condition:
```typescript
if ((isToolSource || isSubAgentSource || isMcpSource || isA2ASource) && (isAgentTarget || isSubAgentTarget || isPlannerAgentTarget)) {
```

Same in the `connectionremoved` pipe.

- [ ] **Step 4: Update CustomNode.tsx — add a2a-agent styling**

Add import:
```typescript
import { A2AAgentReteNode } from "@/lib/rete-nodes";
import { ArrowRightLeftIcon } from "lucide-react";
```

Add to `nodeIcons`:
```typescript
"a2a-agent": <ArrowRightLeftIcon size={10} />,
```

Add to `nodeColors`:
```typescript
"a2a-agent": {
  border: "rgba(244, 63, 94, 0.4)",
  glow: "rgba(244, 63, 94, 0.12)",
  badge: "rgba(244, 63, 94, 0.15)",
  badgeText: "#fb7185",
},
```

Add to `nodeTypeLabels`:
```typescript
"a2a-agent": "A2A AGENT",
```

Add to `getNodeType`:
```typescript
if (data instanceof A2AAgentReteNode) return "a2a-agent";
```

Add subtitle case inside the switch:
```typescript
case "a2a-agent":
  if (d.url) subtitle = d.url.length > 30 ? d.url.slice(0, 30) + "..." : d.url;
  break;
```

- [ ] **Step 5: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add frontend/lib/rete-nodes.ts frontend/lib/rete-setup.ts \
        frontend/components/editor/Canvas.tsx frontend/components/editor/CustomNode.tsx
git commit -m "feat(a2a): add A2A Agent node to graph editor"
```

---

### Task 10: Frontend A2AAgentProperties Component

**Files:**
- Create: `frontend/components/a2a/A2AAgentProperties.tsx`
- Modify: `frontend/components/panels/PropertiesPanel.tsx`

- [ ] **Step 1: Create A2AAgentProperties.tsx**

```tsx
"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useEditorStore } from "@/store/editorStore";
import type { A2AAgentConfig, A2AAuthType, A2ACommunicationMode } from "@/types/a2a";

export function A2AAgentProperties({ nodeId, data }: { nodeId: string; data: A2AAgentConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const update = (field: Partial<A2AAgentConfig>) => updateNodeData(nodeId, field);

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor="a2a-name">Name</Label>
        <Input id="a2a-name" value={data.name} onChange={(e) => update({ name: e.target.value })} placeholder="RemoteAgent" />
      </div>
      <div>
        <Label htmlFor="a2a-desc">Description</Label>
        <Textarea id="a2a-desc" value={data.description} onChange={(e) => update({ description: e.target.value })} rows={2} placeholder="What does this remote agent do?" />
      </div>
      <div>
        <Label htmlFor="a2a-url">URL</Label>
        <Input id="a2a-url" value={data.url} onChange={(e) => update({ url: e.target.value })} placeholder="http://remote-agent:8080/a2a" />
      </div>
      <div>
        <Label>Auth Type</Label>
        <Select value={data.authType} onValueChange={(v) => update({ authType: v as A2AAuthType })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="none">None</SelectItem>
            <SelectItem value="bearer">Bearer Token</SelectItem>
            <SelectItem value="api-key">API Key</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {data.authType !== "none" && (
        <div>
          <Label htmlFor="a2a-token">{data.authType === "bearer" ? "Bearer Token" : "API Key"}</Label>
          <Input id="a2a-token" type="password" value={data.authToken || ""} onChange={(e) => update({ authToken: e.target.value })} placeholder="••••••••" />
        </div>
      )}
      <div>
        <Label htmlFor="a2a-timeout">Timeout (ms)</Label>
        <Input id="a2a-timeout" type="number" min="1000" step="1000" value={data.timeout} onChange={(e) => update({ timeout: parseInt(e.target.value) || 30000 })} />
      </div>
      <div>
        <Label>Communication Mode</Label>
        <Select value={data.communicationMode} onValueChange={(v) => update({ communicationMode: v as A2ACommunicationMode })}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="message">Message (direct response)</SelectItem>
            <SelectItem value="task">Task (status tracking)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Update PropertiesPanel.tsx — route to A2AAgentProperties**

Add import:
```typescript
import { A2AAgentProperties } from "@/components/a2a/A2AAgentProperties";
import { A2AAgentConfig } from "@/types/a2a";
```

Add heading mapping in the h3:
```typescript
selectedNode.type === "a2a-agent" ? "A2A Agent" :
```

Add rendering block after planner-agent:
```tsx
{selectedNode.type === "a2a-agent" && (
  <A2AAgentProperties nodeId={selectedNode.id} data={selectedNode.data as A2AAgentConfig} />
)}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/a2a/A2AAgentProperties.tsx \
        frontend/components/panels/PropertiesPanel.tsx
git commit -m "feat(a2a): add A2AAgentProperties panel"
```

---

### Task 11: Frontend A2AConfigSection on Agent Nodes

**Files:**
- Create: `frontend/components/a2a/A2AConfigSection.tsx`
- Modify: `frontend/components/panels/AgentProperties.tsx`
- Modify: `frontend/components/panels/PlannerProperties.tsx`

- [ ] **Step 1: Create A2AConfigSection.tsx**

```tsx
"use client";

import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { useEditorStore } from "@/store/editorStore";
import type { A2AConfig, A2ASkill } from "@/types/a2a";

const defaultA2AConfig: A2AConfig = {
  enabled: false,
  agentCard: {
    name: "",
    description: "",
    url: "",
    version: "1.0.0",
    capabilities: { streaming: false, pushNotifications: false, stateTransitionHistory: false },
    skills: [],
    defaultInputModes: ["text"],
    defaultOutputModes: ["text"],
  },
  server: {
    transportType: "http-jsonrpc",
    port: 9000,
    basePath: "/a2a",
  },
};

export function A2AConfigSection({ nodeId, a2aConfig }: { nodeId: string; a2aConfig?: A2AConfig }) {
  const updateNodeData = useEditorStore((s) => s.updateNodeData);
  const config = a2aConfig || defaultA2AConfig;
  const [expanded, setExpanded] = useState(config.enabled);

  const updateConfig = (partial: Partial<A2AConfig>) => {
    updateNodeData(nodeId, { a2aConfig: { ...config, ...partial } });
  };

  const updateCard = (partial: Record<string, any>) => {
    updateConfig({ agentCard: { ...config.agentCard, ...partial } });
  };

  const updateCapabilities = (partial: Record<string, boolean>) => {
    updateCard({ capabilities: { ...config.agentCard.capabilities, ...partial } });
  };

  const addSkill = () => {
    const skill: A2ASkill = { id: `skill-${Date.now()}`, name: "", description: "", tags: [] };
    updateCard({ skills: [...config.agentCard.skills, skill] });
  };

  const updateSkill = (id: string, field: Partial<A2ASkill>) => {
    updateCard({ skills: config.agentCard.skills.map((s) => (s.id === id ? { ...s, ...field } : s)) });
  };

  const removeSkill = (id: string) => {
    updateCard({ skills: config.agentCard.skills.filter((s) => s.id !== id) });
  };

  return (
    <div className="border rounded-lg p-3 space-y-3">
      <div className="flex items-center justify-between">
        <Label className="text-xs font-semibold">A2A Communication</Label>
        <Switch
          checked={config.enabled}
          onCheckedChange={(enabled) => {
            updateConfig({ enabled });
            setExpanded(enabled);
          }}
        />
      </div>

      {config.enabled && expanded && (
        <div className="space-y-3 pt-1">
          <div className="text-xs text-muted-foreground font-medium">Agent Card</div>
          <div>
            <Label className="text-xs">Card Name</Label>
            <Input value={config.agentCard.name} onChange={(e) => updateCard({ name: e.target.value })} placeholder="MyBot" className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Description</Label>
            <Textarea value={config.agentCard.description} onChange={(e) => updateCard({ description: e.target.value })} rows={2} className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">URL</Label>
            <Input value={config.agentCard.url} onChange={(e) => updateCard({ url: e.target.value })} placeholder="http://localhost:9000/a2a" className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Version</Label>
            <Input value={config.agentCard.version} onChange={(e) => updateCard({ version: e.target.value })} placeholder="1.0.0" className="text-xs" />
          </div>

          <div className="text-xs text-muted-foreground font-medium pt-2">Capabilities</div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">Streaming</Label>
              <Switch checked={config.agentCard.capabilities.streaming} onCheckedChange={(v) => updateCapabilities({ streaming: v })} />
            </div>
            <div className="flex items-center justify-between">
              <Label className="text-xs">Push Notifications</Label>
              <Switch checked={config.agentCard.capabilities.pushNotifications} onCheckedChange={(v) => updateCapabilities({ pushNotifications: v })} />
            </div>
            <div className="flex items-center justify-between">
              <Label className="text-xs">State History</Label>
              <Switch checked={config.agentCard.capabilities.stateTransitionHistory} onCheckedChange={(v) => updateCapabilities({ stateTransitionHistory: v })} />
            </div>
          </div>

          <div className="text-xs text-muted-foreground font-medium pt-2">Skills</div>
          {config.agentCard.skills.map((skill) => (
            <div key={skill.id} className="border rounded p-2 space-y-1">
              <div className="flex gap-2">
                <Input value={skill.name} onChange={(e) => updateSkill(skill.id, { name: e.target.value })} placeholder="Skill name" className="flex-1 text-xs" />
                <Button variant="ghost" size="sm" onClick={() => removeSkill(skill.id)} className="text-xs px-2">x</Button>
              </div>
              <Input value={skill.description} onChange={(e) => updateSkill(skill.id, { description: e.target.value })} placeholder="Description" className="text-xs" />
              <Input value={skill.tags.join(", ")} onChange={(e) => updateSkill(skill.id, { tags: e.target.value.split(",").map((t) => t.trim()).filter(Boolean) })} placeholder="Tags (comma-separated)" className="text-xs" />
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={addSkill} className="text-xs w-full">+ Add Skill</Button>

          <div className="text-xs text-muted-foreground font-medium pt-2">Server</div>
          <div>
            <Label className="text-xs">Port</Label>
            <Input type="number" value={config.server.port} onChange={(e) => updateConfig({ server: { ...config.server, port: parseInt(e.target.value) || 9000 } })} className="text-xs" />
          </div>
          <div>
            <Label className="text-xs">Base Path</Label>
            <Input value={config.server.basePath} onChange={(e) => updateConfig({ server: { ...config.server, basePath: e.target.value } })} placeholder="/a2a" className="text-xs" />
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Add A2AConfigSection to AgentProperties**

In `AgentProperties.tsx`, add import:
```typescript
import { A2AConfigSection } from "@/components/a2a/A2AConfigSection";
```

Add at the end of the `<div className="space-y-4">` block, after the temperature field:
```tsx
<A2AConfigSection nodeId={nodeId} a2aConfig={data.a2aConfig} />
```

- [ ] **Step 3: Add A2AConfigSection to PlannerProperties**

In `PlannerProperties.tsx`, add import:
```typescript
import { A2AConfigSection } from "@/components/a2a/A2AConfigSection";
```

Add before the "Configure Goals, Actions & State" button:
```tsx
<A2AConfigSection nodeId={nodeId} a2aConfig={data.a2aConfig} />
```

- [ ] **Step 4: Commit**

```bash
git add frontend/components/a2a/A2AConfigSection.tsx \
        frontend/components/panels/AgentProperties.tsx \
        frontend/components/panels/PlannerProperties.tsx
git commit -m "feat(a2a): add A2AConfigSection to agent property panels"
```

---

### Task 12: Frontend A2A Inspector Panel

**Files:**
- Create: `frontend/store/a2aInspectorStore.ts`
- Create: `frontend/components/a2a/A2AInspectorPanel.tsx`
- Modify: `frontend/components/code-explorer/ActivityBar.tsx`
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Create a2aInspectorStore.ts**

```typescript
import { create } from "zustand";
import type { A2AMessage, A2ATaskEvent } from "@/types/a2a";

interface A2AInspectorState {
  isOpen: boolean;
  messages: A2AMessage[];
  taskEvents: A2ATaskEvent[];
  activeTab: "messages" | "tasks";
  togglePanel: () => void;
  setActiveTab: (tab: "messages" | "tasks") => void;
  addMessage: (msg: A2AMessage) => void;
  addTaskEvent: (event: A2ATaskEvent) => void;
  clear: () => void;
}

export const useA2AInspectorStore = create<A2AInspectorState>((set) => ({
  isOpen: false,
  messages: [],
  taskEvents: [],
  activeTab: "messages",
  togglePanel: () => set((s) => ({ isOpen: !s.isOpen })),
  setActiveTab: (tab) => set({ activeTab: tab }),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  addTaskEvent: (event) => set((s) => ({ taskEvents: [...s.taskEvents, event] })),
  clear: () => set({ messages: [], taskEvents: [] }),
}));
```

- [ ] **Step 2: Create A2AInspectorPanel.tsx**

```tsx
"use client";

import { useA2AInspectorStore } from "@/store/a2aInspectorStore";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { A2ATaskStatus } from "@/types/a2a";

const statusColors: Record<A2ATaskStatus, string> = {
  submitted: "text-blue-400",
  working: "text-yellow-400",
  "input-required": "text-purple-400",
  completed: "text-green-400",
  failed: "text-red-400",
  canceled: "text-gray-400",
};

export function A2AInspectorPanel() {
  const isOpen = useA2AInspectorStore((s) => s.isOpen);
  const messages = useA2AInspectorStore((s) => s.messages);
  const taskEvents = useA2AInspectorStore((s) => s.taskEvents);
  const activeTab = useA2AInspectorStore((s) => s.activeTab);
  const setActiveTab = useA2AInspectorStore((s) => s.setActiveTab);
  const clear = useA2AInspectorStore((s) => s.clear);

  if (!isOpen) return null;

  return (
    <div className="w-72 border-l bg-background flex flex-col shrink-0">
      <div className="flex items-center justify-between px-3 py-2 border-b">
        <span className="text-xs font-semibold">A2A Inspector</span>
        <Button variant="ghost" size="sm" onClick={clear} className="text-xs h-6 px-2">Clear</Button>
      </div>
      <div className="flex gap-1 px-3 pt-2">
        <Button
          variant={activeTab === "messages" ? "default" : "ghost"}
          size="sm"
          onClick={() => setActiveTab("messages")}
          className="text-xs h-6"
        >
          Messages
        </Button>
        <Button
          variant={activeTab === "tasks" ? "default" : "ghost"}
          size="sm"
          onClick={() => setActiveTab("tasks")}
          className="text-xs h-6"
        >
          Tasks
        </Button>
      </div>
      <ScrollArea className="flex-1 p-3">
        {activeTab === "messages" && (
          <div className="space-y-2">
            {messages.length === 0 && (
              <p className="text-xs text-muted-foreground">No messages yet. A2A communication events will appear here during preview.</p>
            )}
            {messages.map((msg) => (
              <div key={msg.id} className="border rounded p-2 space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium ${msg.role === "user" ? "text-blue-400" : "text-green-400"}`}>
                    {msg.role === "user" ? "SENT" : "RECEIVED"}
                  </span>
                  <span className="text-xs text-muted-foreground">{new Date(msg.timestamp).toLocaleTimeString()}</span>
                </div>
                {msg.parts.map((part, i) => (
                  <div key={i} className="text-xs text-foreground">{part.content}</div>
                ))}
              </div>
            ))}
          </div>
        )}
        {activeTab === "tasks" && (
          <div className="space-y-2">
            {taskEvents.length === 0 && (
              <p className="text-xs text-muted-foreground">No task events yet. Task status updates will appear here.</p>
            )}
            {taskEvents.map((event) => (
              <div key={event.id} className="border rounded p-2 space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium ${statusColors[event.status]}`}>
                    {event.status.toUpperCase()}
                  </span>
                  <span className="text-xs text-muted-foreground">Task: {event.taskId.slice(0, 8)}</span>
                </div>
                <div className="text-xs text-muted-foreground">{new Date(event.timestamp).toLocaleTimeString()}</div>
                {event.message && event.message.parts.map((part, i) => (
                  <div key={i} className="text-xs text-foreground">{part.content}</div>
                ))}
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}
```

- [ ] **Step 3: Update ActivityBar — add A2A Inspector button**

In `ActivityBar.tsx`, add import:
```typescript
import { useA2AInspectorStore } from "@/store/a2aInspectorStore";
import { ArrowRightLeftIcon } from "lucide-react";
```

Add another button after the Code Explorer button:
```tsx
<button
  onClick={useA2AInspectorStore.getState().togglePanel}
  className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
    useA2AInspectorStore.getState().isOpen
      ? "bg-primary text-primary-foreground"
      : "text-muted-foreground hover:text-foreground hover:bg-muted"
  }`}
  title="A2A Inspector"
>
  <ArrowRightLeftIcon className="size-4" />
</button>
```

Note: Since ActivityBar uses hooks for reactivity, update it to use the hook pattern:
```tsx
export function ActivityBar() {
  const isCodeOpen = useCodeExplorerStore((s) => s.isOpen);
  const toggleCode = useCodeExplorerStore((s) => s.togglePanel);
  const isA2AOpen = useA2AInspectorStore((s) => s.isOpen);
  const toggleA2A = useA2AInspectorStore((s) => s.togglePanel);

  return (
    <div className="flex flex-col items-center w-10 bg-background border-r py-2 gap-3 shrink-0">
      <button
        onClick={toggleCode}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isCodeOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="Code Explorer"
      >
        <CodeIcon className="size-4" />
      </button>
      <button
        onClick={toggleA2A}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isA2AOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="A2A Inspector"
      >
        <ArrowRightLeftIcon className="size-4" />
      </button>
    </div>
  );
}
```

- [ ] **Step 4: Update page.tsx — add A2AInspectorPanel**

Add import:
```typescript
import { A2AInspectorPanel } from "@/components/a2a/A2AInspectorPanel";
```

Add `<A2AInspectorPanel />` in the layout. Place it after `<PropertiesPanel />` inside the flex container:

```tsx
<div className="flex flex-1 overflow-hidden">
  <Canvas />
  <PropertiesPanel />
  <A2AInspectorPanel />
</div>
```

- [ ] **Step 5: Verify build**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add frontend/store/a2aInspectorStore.ts \
        frontend/components/a2a/A2AInspectorPanel.tsx \
        frontend/components/code-explorer/ActivityBar.tsx \
        frontend/app/page.tsx
git commit -m "feat(a2a): add A2A Inspector panel with messages and task status"
```

---

### Task 13: Final Verification

**Files:** None (test-only task)

- [ ] **Step 1: Run all backend tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 2: Verify frontend compiles**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx tsc --noEmit 2>&1 | head -20`
Expected: No errors

- [ ] **Step 3: Run frontend dev server briefly**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npx next build 2>&1 | tail -20`
Expected: Build succeeds
