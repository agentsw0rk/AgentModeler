# Typed AIAgent I/O with @Serializable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire generated Input/Output data classes into AIAgent generics with @Serializable, so the generated code actually uses typed I/O instead of `String, String`.

**Architecture:** Add `@Serializable` annotation to generated I/O classes. Pass I/O class names through from ExportService to AgentGenerator, ControllerGenerator, SubAgentGenerator, and RegistryGenerator. Fallback to `String` when no I/O nodes exist.

**Tech Stack:** Kotlin, kotlinx.serialization, Koog AIAgent framework, Spring Boot

---

### Task 1: Add @Serializable to InputOutputGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `InputOutputGeneratorTest.kt`:

```kotlin
@Test
fun `generates input class with Serializable annotation`() {
    val inputs = listOf(KoogInput("query", "Search query", "string", true, null))
    val code = generator.generateInputClass("MyAgent", inputs)!!
    assertContains(code, "@Serializable")
    assertContains(code, "import kotlinx.serialization.Serializable")
}

@Test
fun `generates output class with Serializable annotation`() {
    val outputs = listOf(KoogOutput("result", "The result", "string"))
    val code = generator.generateOutputClass("MyAgent", outputs)!!
    assertContains(code, "@Serializable")
    assertContains(code, "import kotlinx.serialization.Serializable")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.InputOutputGeneratorTest" --info`
Expected: FAIL — generated code does not contain `@Serializable`

- [ ] **Step 3: Add @Serializable to generated code**

In `InputOutputGenerator.kt`, modify `generateInputClass` — after `appendLine("package generated")`:

```kotlin
appendLine()
appendLine("import kotlinx.serialization.Serializable")
appendLine()
appendLine("@Serializable")
appendLine("data class $className(")
```

Same change in `generateOutputClass`.

- [ ] **Step 4: Run tests to verify all pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.InputOutputGeneratorTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/InputOutputGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/InputOutputGeneratorTest.kt
git commit -m "feat(export): add @Serializable annotation to generated I/O data classes"
```

---

### Task 2: Add I/O type parameters to AgentGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `AgentGeneratorTest.kt`:

```kotlin
@Test
fun `generates typed agent when inputs and outputs provided`() {
    val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent, inputClassName = "MyAgentInput", outputClassName = "MyAgentOutput")
    assertContains(code, "fun agent(): AIAgent<MyAgentInput, MyAgentOutput>")
    assertFalse(code.contains("AIAgent<String, String>"))
}

@Test
fun `generates typed agent with only input`() {
    val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent, inputClassName = "MyAgentInput")
    assertContains(code, "fun agent(): AIAgent<MyAgentInput, String>")
}

@Test
fun `generates typed agent with only output`() {
    val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent, outputClassName = "MyAgentOutput")
    assertContains(code, "fun agent(): AIAgent<String, MyAgentOutput>")
}

@Test
fun `generates String String agent when no IO classes`() {
    val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
    val code = generator.generate(agent)
    assertContains(code, "fun agent(): AIAgent<String, String>")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest" --info`
Expected: FAIL — `generate` does not accept `inputClassName`/`outputClassName` parameters

- [ ] **Step 3: Modify AgentGenerator.generate signature and implementation**

Change the `generate` method signature in `AgentGenerator.kt`:

```kotlin
fun generate(agent: KoogAgent, strategyCall: String? = null, inputClassName: String? = null, outputClassName: String? = null): String {
```

Add a local variable after the existing locals:

```kotlin
val inputType = inputClassName ?: "String"
val outputType = outputClassName ?: "String"
val agentType = "AIAgent<$inputType, $outputType>"
```

Replace both occurrences of `fun agent(): AIAgent<String, String>` with `fun agent(): $agentType`.

Replace both occurrences of `AIAgent(` (the constructor calls on lines 101 and 110) — these stay as `AIAgent(` since the constructor infers types.

- [ ] **Step 4: Run ALL AgentGenerator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest" --info`
Expected: ALL PASS (existing tests still pass because default is String, String)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat(export): parameterize AgentGenerator with I/O type names"
```

---

### Task 3: Add I/O type parameters to ControllerGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `ControllerGeneratorTest.kt`:

```kotlin
@Test
fun `generates typed controller with custom IO`() {
    val code = generator.generate(inputClassName = "MyAgentInput", outputClassName = "MyAgentOutput")
    assertContains(code, "AIAgent<MyAgentInput, MyAgentOutput>")
    assertContains(code, "fun chat(@RequestBody request: MyAgentInput): MyAgentOutput")
    assertContains(code, "agent.run(request)")
    assertFalse(code.contains("ChatRequest"))
    assertFalse(code.contains("ChatResponse"))
}

@Test
fun `generates typed controller with only input`() {
    val code = generator.generate(inputClassName = "MyInput")
    assertContains(code, "AIAgent<MyInput, String>")
    assertContains(code, "fun chat(@RequestBody request: MyInput): ChatResponse")
    assertContains(code, "agent.run(request)")
}

@Test
fun `generates typed controller with only output`() {
    val code = generator.generate(outputClassName = "MyOutput")
    assertContains(code, "AIAgent<String, MyOutput>")
    assertContains(code, "fun chat(@RequestBody request: ChatRequest): MyOutput")
    assertContains(code, "agent.run(request.message)")
}

@Test
fun `generates default String String controller when no IO`() {
    val code = generator.generate()
    assertContains(code, "AIAgent<String, String>")
    assertContains(code, "ChatRequest")
    assertContains(code, "ChatResponse")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ControllerGeneratorTest" --info`
Expected: FAIL

- [ ] **Step 3: Rewrite ControllerGenerator.generate**

```kotlin
@Component
class ControllerGenerator {
    fun generate(inputClassName: String? = null, outputClassName: String? = null): String {
        val inputType = inputClassName ?: "String"
        val outputType = outputClassName ?: "String"
        val hasCustomInput = inputClassName != null
        val hasCustomOutput = outputClassName != null

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            appendLine("import kotlinx.coroutines.runBlocking")
            appendLine("import org.springframework.web.bind.annotation.PostMapping")
            appendLine("import org.springframework.web.bind.annotation.RequestBody")
            appendLine("import org.springframework.web.bind.annotation.RestController")
            appendLine()
            appendLine("@RestController")
            appendLine("class ChatController(private val agent: AIAgent<$inputType, $outputType>) {")
            appendLine()
            appendLine("""    @PostMapping("/api/chat")""")

            val requestType = if (hasCustomInput) inputType else "ChatRequest"
            val responseType = if (hasCustomOutput) outputType else "ChatResponse"
            val runArg = if (hasCustomInput) "request" else "request.message"
            val resultWrap = if (hasCustomOutput) "result" else "ChatResponse(response = result)"

            appendLine("    fun chat(@RequestBody request: $requestType): $responseType {")
            appendLine("        val result = runBlocking { agent.run($runArg) }")
            appendLine("        return $resultWrap")
            appendLine("    }")
            appendLine("}")

            if (!hasCustomInput) {
                appendLine()
                appendLine("data class ChatRequest(val message: String)")
            }
            if (!hasCustomOutput) {
                appendLine()
                appendLine("data class ChatResponse(val response: String)")
            }
        }
    }
}
```

- [ ] **Step 4: Run ALL ControllerGenerator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ControllerGeneratorTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt
git commit -m "feat(export): parameterize ControllerGenerator with I/O type names"
```

---

### Task 4: Add I/O type parameters to SubAgentGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

Add to `SubAgentGeneratorTest.kt`:

```kotlin
@Test
fun `generates typed sub-agent when inputs and outputs provided`() {
    val subAgent = KoogSubAgent(
        name = "Research", description = "Researches",
        systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
        modelId = null, temperature = null, delegationMode = "automatic",
        inputDescription = "Input",
        tools = emptyList(), builtinTools = emptyList(),
        inputs = listOf(KoogInput("query", "Query", "string", true, null)),
        outputs = listOf(KoogOutput("result", "Result", "string"))
    )
    val code = generator.generate(subAgent)
    assertContains(code, "fun createResearch(): AIAgent<ResearchInput, ResearchOutput>")
    assertContains(code, "import generated.subagents.ResearchInput")
    assertContains(code, "import generated.subagents.ResearchOutput")
}

@Test
fun `generates String String sub-agent when no IO`() {
    val subAgent = KoogSubAgent(
        name = "Simple", description = "Desc",
        systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
        modelId = null, temperature = null, delegationMode = "automatic",
        inputDescription = "Input",
        tools = emptyList(), builtinTools = emptyList()
    )
    val code = generator.generate(subAgent)
    assertContains(code, "fun createSimple() = AIAgent(")
    assertFalse(code.contains("AIAgent<"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SubAgentGeneratorTest" --info`
Expected: FAIL

- [ ] **Step 3: Modify SubAgentGenerator.generate**

In `SubAgentGenerator.kt`, after computing `funcName`, add:

```kotlin
val hasInputs = subAgent.inputs.isNotEmpty()
val hasOutputs = subAgent.outputs.isNotEmpty()
val inputType = if (hasInputs) "${subAgent.name.replaceFirstChar { it.uppercase() }}Input" else null
val outputType = if (hasOutputs) "${subAgent.name.replaceFirstChar { it.uppercase() }}Output" else null
```

In the imports section (after `import $modelImport`), add:

```kotlin
if (inputType != null) {
    appendLine("import generated.subagents.$inputType")
}
if (outputType != null) {
    appendLine("import generated.subagents.$outputType")
}
```

Change the function signature line from:

```kotlin
appendLine("fun $funcName() = AIAgent(")
```

To:

```kotlin
if (inputType != null || outputType != null) {
    val i = inputType ?: "String"
    val o = outputType ?: "String"
    appendLine("fun $funcName(): AIAgent<$i, $o> = AIAgent(")
} else {
    appendLine("fun $funcName() = AIAgent(")
}
```

- [ ] **Step 4: Run ALL SubAgentGenerator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SubAgentGeneratorTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/SubAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/SubAgentGeneratorTest.kt
git commit -m "feat(export): parameterize SubAgentGenerator with I/O type names"
```

---

### Task 5: Update RegistryGenerator sub-agent generics

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt`

- [ ] **Step 1: Write failing test**

Add to `RegistryGeneratorTest.kt`:

```kotlin
@Test
fun `generates typed sub-agent tool when IO present`() {
    val subAgent = KoogSubAgent(
        name = "Research", description = "Researches topics",
        systemPrompt = "Prompt", modelProvider = ModelProvider.OPENAI,
        modelId = null, temperature = null, delegationMode = "automatic",
        inputDescription = "A topic",
        tools = emptyList(), builtinTools = emptyList(),
        inputs = listOf(KoogInput("query", "Q", "string", true, null)),
        outputs = listOf(KoogOutput("result", "R", "string"))
    )
    val code = generator.generate(emptyList(), emptyList(), listOf(subAgent))
    assertContains(code, "GraphAIAgent<ResearchInput, ResearchOutput>")
    assertContains(code, "createAgentTool<ResearchInput, ResearchOutput>")
    assertContains(code, "import generated.subagents.ResearchInput")
    assertContains(code, "import generated.subagents.ResearchOutput")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest" --info`
Expected: FAIL

- [ ] **Step 3: Modify RegistryGenerator sub-agent section**

In `RegistryGenerator.kt`, in the sub-agents loop (around line 113), replace the hardcoded `<String, String>` generics:

```kotlin
for (sa in subAgents) {
    val funcName = "create${sa.name.replaceFirstChar { it.uppercase() }}"
    val desc = sa.description.replace("\"", "\\\"")
    val inputDesc = sa.inputDescription.replace("\"", "\\\"")
    val hasInputs = sa.inputs.isNotEmpty()
    val hasOutputs = sa.outputs.isNotEmpty()
    val i = if (hasInputs) "${sa.name.replaceFirstChar { it.uppercase() }}Input" else "String"
    val o = if (hasOutputs) "${sa.name.replaceFirstChar { it.uppercase() }}Output" else "String"
    appendLine("        tool(")
    appendLine("            AIAgentService.fromAgent($funcName() as GraphAIAgent<$i, $o>).createAgentTool<$i, $o>(")
    appendLine("                agentName = \"${sa.name}\",")
    appendLine("                agentDescription = \"$desc\",")
    appendLine("                inputDescription = \"$inputDesc\"")
    appendLine("            )")
    appendLine("        )")
}
```

Also add imports for typed sub-agent I/O classes. In the `if (subAgents.isNotEmpty())` import block, add:

```kotlin
for (sa in subAgents) {
    if (sa.inputs.isNotEmpty()) {
        appendLine("import generated.subagents.${sa.name.replaceFirstChar { it.uppercase() }}Input")
    }
    if (sa.outputs.isNotEmpty()) {
        appendLine("import generated.subagents.${sa.name.replaceFirstChar { it.uppercase() }}Output")
    }
}
```

- [ ] **Step 4: Run ALL RegistryGenerator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.RegistryGeneratorTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/RegistryGeneratorTest.kt
git commit -m "feat(export): use typed generics for sub-agent tools in RegistryGenerator"
```

---

### Task 6: Wire ExportService to pass I/O class names to generators

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Write failing test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `generates typed agent and controller when IO nodes present`() {
    // Build a graph with agent + input + output nodes and edges
    val graph = WorkspaceGraph(
        version = 1,
        name = "TypedAgent",
        nodes = listOf(
            GraphNode("n1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "MyAgent", "systemPrompt" to "Help",
                "modelProvider" to "openai", "tools" to emptyList<Any>()
            )),
            GraphNode("n2", "input", Position(-200.0, 0.0), mapOf(
                "name" to "query", "description" to "Search query",
                "dataType" to "string", "required" to true
            )),
            GraphNode("n3", "output", Position(200.0, 0.0), mapOf(
                "name" to "result", "description" to "The result",
                "dataType" to "string"
            ))
        ),
        edges = listOf(
            GraphEdge("e1", "n2", "n1"),  // input -> agent
            GraphEdge("e2", "n1", "n3")   // agent -> output
        )
    )
    val files = exportService.generateFiles(graph)

    val agentCode = files["src/main/kotlin/generated/AgentConfig.kt"]!!
    assertContains(agentCode, "AIAgent<MyAgentInput, MyAgentOutput>")

    val controllerCode = files["src/main/kotlin/generated/ChatController.kt"]!!
    assertContains(controllerCode, "AIAgent<MyAgentInput, MyAgentOutput>")
    assertContains(controllerCode, "@RequestBody request: MyAgentInput")
    assertFalse(controllerCode.contains("ChatRequest"))
}
```

Note: Adapt this test to match the existing test patterns in `ExportServiceTest.kt` — use the same `GraphNode`, `GraphEdge`, `Position`, and `WorkspaceGraph` constructors that the existing tests use.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest" --info`
Expected: FAIL

- [ ] **Step 3: Modify ExportService to compute and pass I/O class names**

Add a helper method to `ExportService`:

```kotlin
private fun ioClassNames(name: String, inputs: List<KoogInput>, outputs: List<KoogOutput>): Pair<String?, String?> {
    val inputClassName = if (inputs.isNotEmpty()) "${name.replaceFirstChar { it.uppercase() }}Input" else null
    val outputClassName = if (outputs.isNotEmpty()) "${name.replaceFirstChar { it.uppercase() }}Output" else null
    return Pair(inputClassName, outputClassName)
}
```

**For regular agents** (around line 200), change:

```kotlin
val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
files["$pkg/AgentConfig.kt"] = agentGenerator.generate(agent, inputClassName = inputClassName, outputClassName = outputClassName)
files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)
```

**For graph agents** (around line 158), change:

```kotlin
val (gaInputClassName, gaOutputClassName) = ioClassNames(ga.name, ga.inputs, ga.outputs)
files["$pkg/AgentConfig.kt"] = agentGenerator.generate(infraAgent, "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()", inputClassName = gaInputClassName, outputClassName = gaOutputClassName)
files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = gaInputClassName, outputClassName = gaOutputClassName)
```

- [ ] **Step 4: Run ALL ExportService tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest" --info`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat(export): wire I/O class names from ExportService to generators"
```

---

### Task 7: Run full test suite and fix any regressions

**Files:** All modified files from Tasks 1-6

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test --info`
Expected: ALL PASS

- [ ] **Step 2: Fix any failing tests**

If existing tests fail because they assert `AIAgent<String, String>` and the agent now has I/O nodes in the test fixture, update those assertions to match the new typed output. If they fail because the `generate()` signature changed, update the call sites.

- [ ] **Step 3: Commit fixes if any**

```bash
git add -u
git commit -m "fix(export): fix test regressions from typed I/O integration"
```
