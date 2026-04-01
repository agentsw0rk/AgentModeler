# KotlinPoet Migration Phase 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the 6 most complex Kotlin code generators from `buildString` to KotlinPoet, establishing a shared utility layer and the migration pattern for subsequent phases.

**Architecture:** Add KotlinPoet 2.2.0 dependency, create `KotlinPoetUtils.kt` with shared helpers (KoogAnnotations, KoogModels, sanitization), then migrate generators one-by-one. Each generator keeps its `fun generate(...): String` signature — `FileSpec.toString()` replaces `buildString`. Existing tests validate output equivalence.

**Tech Stack:** KotlinPoet 2.2.0, Kotlin 2.2.21, Spring Boot 4.0.4

---

## File Structure

### New Files
- `src/main/kotlin/com/agentwork/modeler/export/generator/KotlinPoetUtils.kt` — Shared KoogAnnotations, KoogModels, sanitization extensions

### Modified Files
- `build.gradle.kts` — Add KotlinPoet dependency
- `src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt` — Migrate to KotlinPoet
- `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt` — Migrate to KotlinPoet
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt` — Migrate to KotlinPoet
- `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt` — Migrate to KotlinPoet
- `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt` — Migrate to KotlinPoet
- `src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt` — Migrate to KotlinPoet

### Test Files (assertions may need minor adjustments for formatting)
- `src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

---

### Task 1: Add KotlinPoet Dependency

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add KotlinPoet to dependencies**

In `build.gradle.kts`, add inside the `dependencies {}` block after the existing implementations:

```kotlin
implementation("com.squareup:kotlinpoet:2.2.0")
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add KotlinPoet 2.2.0 dependency"
```

---

### Task 2: Create KotlinPoetUtils.kt

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/KotlinPoetUtils.kt`

- [ ] **Step 1: Create the utility file**

```kotlin
package com.agentwork.modeler.export.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

/**
 * Shared KotlinPoet utilities for Koog code generation.
 */

// --- Koog Annotation Factories ---

object KoogAnnotations {
    private val TOOL = ClassName("ai.koog.agents.core.tools.annotations", "Tool")
    private val LLM_DESCRIPTION = ClassName("ai.koog.agents.core.tools.annotations", "LLMDescription")
    private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
    private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

    fun tool(): AnnotationSpec = AnnotationSpec.builder(TOOL).build()

    fun llmDescription(desc: String): AnnotationSpec =
        AnnotationSpec.builder(LLM_DESCRIPTION).addMember("%S", desc).build()

    fun serializable(): AnnotationSpec = AnnotationSpec.builder(SERIALIZABLE).build()

    fun serialName(name: String): AnnotationSpec =
        AnnotationSpec.builder(SERIAL_NAME).addMember("%S", name).build()
}

// --- Koog Model/Provider ClassNames ---

object KoogModels {
    fun clientClassName(provider: String): ClassName = when (provider.uppercase()) {
        "OPENAI" -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAILLMClient")
        "OLLAMA" -> ClassName("ai.koog.prompt.executor.clients.ollama", "OllamaClient")
        "ANTHROPIC" -> ClassName("ai.koog.prompt.executor.clients.anthropic", "AnthropicLLMClient")
        "GOOGLE" -> ClassName("ai.koog.prompt.executor.clients.google", "GoogleLLMClient")
        else -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAILLMClient")
    }

    fun modelsClassName(provider: String): ClassName = when (provider.uppercase()) {
        "OPENAI" -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAIModels")
        "OLLAMA" -> ClassName("ai.koog.prompt.executor.clients.ollama", "OllamaModels")
        "ANTHROPIC" -> ClassName("ai.koog.prompt.executor.clients.anthropic", "AnthropicModels")
        "GOOGLE" -> ClassName("ai.koog.prompt.executor.clients.google", "GoogleModels")
        else -> ClassName("ai.koog.prompt.executor.clients.openai", "OpenAIModels")
    }

    fun executorMemberName(provider: String): String = when (provider.uppercase()) {
        "OPENAI" -> "simpleOpenAIExecutor"
        "OLLAMA" -> "simpleOllamaExecutor"
        "ANTHROPIC" -> "simpleAnthropicExecutor"
        "GOOGLE" -> "simpleGoogleExecutor"
        else -> "simpleOpenAIExecutor"
    }
}

// --- Common Koog ClassNames ---

object KoogTypes {
    val TOOL_SET = ClassName("ai.koog.agents.core.tools.reflect", "ToolSet")
    val TOOL_REGISTRY = ClassName("ai.koog.agents.core.tools", "ToolRegistry")
    val TOOLS_REFLECT = ClassName("ai.koog.agents.core.tools.reflect", "tools")
    val SIMPLE_TOOL = ClassName("ai.koog.agents.core.tools", "SimpleTool")
    val AI_AGENT = ClassName("ai.koog.agents.core.agent", "AIAgent")
    val LLM_EMBEDDER = ClassName("ai.koog.embeddings.local", "LLMEmbedder")
}

// --- Identifier Sanitization ---

fun String.sanitizeIdentifier(): String =
    replace(Regex("[^a-zA-Z0-9_]"), "_")
        .replace(Regex("^[0-9]"), "_$0")
        .ifEmpty { "node" }

fun String.sanitizeClassName(): String =
    replace(Regex("[^a-zA-Z0-9]"), "")
        .replaceFirstChar { it.uppercase() }
        .ifEmpty { "Generated" }

fun String.sanitizeVarPrefix(): String {
    val cleaned = replace(Regex("[^a-zA-Z0-9]"), "")
    if (cleaned.isEmpty()) return "generated"
    return cleaned.replaceFirstChar { it.lowercase() }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/KotlinPoetUtils.kt
git commit -m "feat: add KotlinPoetUtils with KoogAnnotations, KoogModels, and sanitization"
```

---

### Task 3: Migrate ToolGenerator

This is the **reference migration** — the simplest of the 6 generators, establishing the pattern for all others.

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt` (existing tests)

- [ ] **Step 1: Rewrite ToolGenerator with KotlinPoet**

Replace the entire file content with:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import com.squareup.kotlinpoet.*
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String {
        val typeSpec = TypeSpec.classBuilder(tool.name)
            .addSuperinterface(KoogTypes.TOOL_SET)

        if (tool.description.isNotBlank()) {
            typeSpec.addAnnotation(KoogAnnotations.llmDescription(tool.description))
        }

        for (fn in tool.functions) {
            typeSpec.addFunction(buildToolFunction(fn))
        }

        return FileSpec.builder("generated.tools", tool.name)
            .addImport("generated", "*")
            .addType(typeSpec.build())
            .build()
            .toString()
    }

    private fun buildToolFunction(fn: KoogToolFunction): FunSpec {
        val returnType = if (fn.returnType == "Unit") UNIT else String::class.asTypeName()
        val funSpec = FunSpec.builder(fn.name)
            .addAnnotation(KoogAnnotations.tool())
            .returns(returnType)

        if (fn.description.isNotBlank()) {
            funSpec.addAnnotation(KoogAnnotations.llmDescription(fn.description))
        }

        // Parameters: required first, then optional
        val required = fn.parameters.filter { it.required }
        val optional = fn.parameters.filter { !it.required }
        for (param in required + optional) {
            funSpec.addParameter(buildParameter(param))
        }

        // Body
        if (fn.body.isBlank()) {
            funSpec.addComment("TODO: Implement tool logic")
            if (fn.returnType != "Unit") {
                funSpec.addStatement("return %S", "Not implemented")
            }
        } else {
            for (line in fn.body.lines()) {
                if (line.isBlank()) continue
                funSpec.addStatement("%L", line.trim())
            }
        }

        return funSpec.build()
    }

    private fun buildParameter(param: KoogToolParameter): ParameterSpec {
        val type = param.type.kotlinType.let { ClassName.bestGuess(it) }
        val paramSpec = ParameterSpec.builder(param.name, type)

        if (param.description.isNotBlank()) {
            paramSpec.addAnnotation(KoogAnnotations.llmDescription(param.description))
        }

        if (!param.required) {
            paramSpec.defaultValue("%L", defaultLiteral(param.type, param.defaultValue))
        }

        return paramSpec.build()
    }

    private fun defaultLiteral(type: KoogParamType, value: String?): String {
        if (value == null || value.isBlank()) {
            return when (type) {
                KoogParamType.STRING -> "\"\""
                KoogParamType.INT -> "0"
                KoogParamType.DOUBLE -> "0.0"
                KoogParamType.FLOAT -> "0.0f"
                KoogParamType.BOOLEAN -> "false"
                KoogParamType.LIST_STRING -> "emptyList()"
                KoogParamType.MAP_STRING_STRING -> "emptyMap()"
            }
        }
        return when (type) {
            KoogParamType.STRING -> "\"$value\""
            KoogParamType.INT -> value.toIntOrNull()?.toString() ?: "0"
            KoogParamType.DOUBLE -> value.toDoubleOrNull()?.toString() ?: "0.0"
            KoogParamType.FLOAT -> value.toFloatOrNull()?.let { "${it}f" } ?: "0.0f"
            KoogParamType.BOOLEAN -> if (value.lowercase() == "true") "true" else "false"
            KoogParamType.LIST_STRING -> "emptyList()"
            KoogParamType.MAP_STRING_STRING -> "emptyMap()"
        }
    }
}
```

- [ ] **Step 2: Run existing tests**

Run: `cd /Users/czarnik/IdeaProjects/modeler && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. If tests fail due to formatting differences (import order, whitespace), adjust test assertions to match KotlinPoet's output style.

- [ ] **Step 3: Build-validate with example JSON**

Export the RAG example and verify the generated `CustomerTools.kt` is valid:
```bash
curl -s -X POST http://localhost:8080/api/export -H "Content-Type: application/json" \
  -d @docs/examples/rag-agent-example.json -o /tmp/test-export.zip && \
unzip -p /tmp/test-export.zip src/main/kotlin/generated/tools/CustomerTools.kt
```

Verify the output contains `@Tool`, `@LLMDescription`, class structure, and proper imports.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt
git commit -m "refactor: migrate ToolGenerator to KotlinPoet"
```

---

### Task 4: Migrate RegistryGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt`

The RegistryGenerator creates a `createToolRegistry()` function with conditional tool registrations. Key KotlinPoet patterns:
- `FileSpec` with a single top-level function (no class)
- Conditional imports via `ClassName` (auto-managed by `%T`)
- `beginControlFlow`/`endControlFlow` for the `ToolRegistry { }` builder block

- [ ] **Step 1: Rewrite RegistryGenerator**

The generator must produce a file with:
- Package `generated`
- A `fun createToolRegistry(): ToolRegistry` (or `suspend fun` if MCP servers present)
- Inside: `ToolRegistry { }` builder block with conditional tool registrations
- Tool instances: `val ${name}Tools = ${Name}()`
- Builtin tool registrations (Shell, Fetch, Memory, Todo, UserPrompt, Filesystem)
- SubAgent-as-tool registrations with `AIAgentService.fromAgent(...).createAgentTool<I, O>(...)`
- MCP registries merged with `+` operator

Use `CodeBlock.builder()` with `beginControlFlow`/`endControlFlow` for the `ToolRegistry { }` block. Use `%T` for all type references to get auto-imports. Use `%S` for all string literals (descriptions, names) to eliminate manual escaping.

Key ClassNames to use from `KotlinPoetUtils`:
- `KoogTypes.TOOL_REGISTRY` for the return type
- `ClassName("ai.koog.agents.core.tools.reflect", "tools")` for the `tools()` function
- `ClassName("ai.koog.agents.ext.tool", "AskUser")` etc. for builtins

Keep the same method signature: `fun generate(tools, builtinTools, subAgents, mcpServers): String`

- [ ] **Step 2: Run tests and fix assertions if needed**

Run: `./gradlew test`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt
git commit -m "refactor: migrate RegistryGenerator to KotlinPoet"
```

---

### Task 5: Migrate BuiltinToolGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt`

This generator has 4 private methods (`generateShell`, `generateFetch`, `generateMemory`, `generateTodo`) each producing a full ToolSet class file. Key KotlinPoet patterns:
- Each method returns `FileSpec.builder("generated.tools", className).addType(...).build().toString()`
- `TypeSpec.classBuilder` with `ToolSet` superinterface
- `FunSpec.builder` with `@Tool`/`@LLMDescription` annotations
- Constructor with `PropertySpec` for config values (workingDir, baseUrl, path)
- Function bodies with `addStatement` — use `%L` for raw code lines (ProcessBuilder, HttpClient, JDBC)
- `%S` for all string literals in annotations and return values

The function bodies contain JVM API calls (ProcessBuilder, HttpClient, DriverManager). These should be added as raw `%L` statements since they're implementation code, not generated structure.

Keep the same method signature: `fun generate(tool: KoogBuiltinTool): String?`

- [ ] **Step 1: Rewrite BuiltinToolGenerator**
- [ ] **Step 2: Run tests**

Run: `./gradlew test`

- [ ] **Step 3: Build-validate**

Export with a builtin-tool graph and verify the generated Shell/Fetch/Memory/Todo files compile.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt
git commit -m "refactor: migrate BuiltinToolGenerator to KotlinPoet"
```

---

### Task 6: Migrate AgentGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`

The most complex single-file generator. Key KotlinPoet patterns:

- `TypeSpec.classBuilder("AgentConfig")` with Spring annotations (`@Configuration`)
- `PropertySpec` with `@Value` annotations for API key, OTel config — use `AnnotationSpec.builder(ClassName("org.springframework.beans.factory.annotation", "Value")).addMember("%S", ...)` 
- **System prompt escaping** — the main win: `%S` handles `"`, `\`, `\n` automatically. No more `replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")`
- `@Bean` function returning `AIAgent` with conditional parameters (toolRegistry, mcpScope)
- `@PostConstruct` function for API key validation
- OTel setup block via `appendOtelInstallBlock` — use `beginControlFlow`/`endControlFlow`
- `mapModelId` helper stays as-is (returns String for model ID mapping)
- Conditional `suspend` modifier if MCP servers present

Keep the same method signature: `fun generate(agent, strategyCall?, inputClassName?, outputClassName?): String`

- [ ] **Step 1: Rewrite AgentGenerator**
- [ ] **Step 2: Run tests**

Run: `./gradlew test`

- [ ] **Step 3: Build-validate with JVM and Wasm**

```bash
curl -s -X POST http://localhost:8080/api/build -H "Content-Type: application/json" \
  -d "{\"graph\": $(cat docs/examples/rag-agent-example.json), \"platform\": \"jvm\", \"backend\": \"spring-boot\"}" \
  | python3 -c "import sys,json; r=json.load(sys.stdin); print(f'success={r[\"success\"]}')"
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt
git commit -m "refactor: migrate AgentGenerator to KotlinPoet"
```

---

### Task 7: Migrate GraphAgentGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt`

The deeply nested generator. Key KotlinPoet patterns:

- `FileSpec` with a single top-level function `createXxxStrategy()`
- The strategy DSL body (`strategy<String, String>("name") { ... }`) must be a raw `CodeBlock` since it's Koog-specific DSL, not standard Kotlin structure
- Use `CodeBlock.builder()` for the entire strategy body
- `beginControlFlow("strategy<String, String>(%S)", agent.name)` for the outer block
- Node declarations: `addStatement("val %L by %L()", varName, nodeFunction)` 
- Edge declarations: `addStatement("edge(%L forwardTo %L%L)", sourceRef, targetRef, edgeSuffix)`
- Subgraph blocks: nested `beginControlFlow`/`endControlFlow`
- Conditional imports still via `%T` with `ClassName` where possible, but the Koog DSL extension functions (`nodeLLMRequest`, `onToolCall`) need explicit `addImport` calls since they're used as raw names in CodeBlock
- `sanitizeIdentifier()` from `KotlinPoetUtils` replaces the local version

The key insight: this generator produces **DSL code**, not class/function structure. KotlinPoet's main benefit here is **automatic indentation** via control flow and **import management**, not type-safe construction.

Keep the same method signature: `fun generate(agent: KoogGraphAgent): String`

- [ ] **Step 1: Rewrite GraphAgentGenerator**
- [ ] **Step 2: Run tests and fix assertions**

Run: `./gradlew test --tests "com.agentwork.modeler.export.GraphAgentGeneratorTest"`
Fix any assertion failures from import ordering or whitespace changes.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/GraphAgentGeneratorTest.kt
git commit -m "refactor: migrate GraphAgentGenerator to KotlinPoet"
```

---

### Task 8: Migrate RagCodeGenerator

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt`
- Test: `src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt`

Two generation methods to migrate: `generateRagFile` (JVM) and `generateWasmRagFile` (WasmJS).

**JVM variant (`generateRagFile`):**
- `FileSpec.builder("generated.rag", className)`
- `TypeSpec.objectBuilder(className)` for the RAG setup object
- `PropertySpec` for embedder, documentEmbedder, storage — use `%T` for all Koog types
- `FunSpec.builder("initialize").addModifiers(KModifier.SUSPEND)` for document loading
- Optional `getRelevantContext` function
- Optional `TypeSpec.classBuilder("${name}SearchTools")` with `ToolSet` superinterface for search tool
- Use `KoogModels.clientClassName(provider)` and `KoogModels.modelsClassName(provider)` from Utils
- `sanitizeClassName()` and `sanitizeVarPrefix()` from Utils replace local methods

**WasmJS variant (`generateWasmRagFile`):**
- Same `FileSpec`/`TypeSpec` pattern but different types:
  - `StringDocumentEmbedder` as inner class
  - `InMemoryDocumentEmbeddingStorage<String>` instead of JVM storage
  - `SimpleTool` with `@Serializable` Args data class instead of ToolSet
  - `getEnv()` helper function instead of `System.getenv()`
- Use `%T` for `SimpleTool`, `DocumentEmbedder`, etc. to auto-manage imports

Keep the same method signatures: `fun generateRagFile(rag): String` and `fun generateWasmRagFile(rag): String`

- [ ] **Step 1: Rewrite RagCodeGenerator**
- [ ] **Step 2: Run tests and fix assertions**

Run: `./gradlew test --tests "com.agentwork.modeler.export.RagCodeGeneratorTest"`

- [ ] **Step 3: Build-validate both platforms**

```bash
# JVM
curl -s -X POST http://localhost:8080/api/build -H "Content-Type: application/json" \
  -d "{\"graph\": $(cat docs/examples/rag-agent-example.json), \"platform\": \"jvm\", \"backend\": \"spring-boot\"}" \
  | python3 -c "import sys,json; r=json.load(sys.stdin); print(f'JVM: success={r[\"success\"]}')"
# Wasm
curl -s -X POST http://localhost:8080/api/build -H "Content-Type: application/json" \
  -d "{\"graph\": $(cat docs/examples/rag-agent-example.json), \"platform\": \"wasm\", \"backend\": \"ktor-wasm\"}" \
  | python3 -c "import sys,json; r=json.load(sys.stdin); print(f'Wasm: success={r[\"success\"]}')"
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt src/test/kotlin/com/agentwork/modeler/export/RagCodeGeneratorTest.kt
git commit -m "refactor: migrate RagCodeGenerator to KotlinPoet"
```

---

### Task 9: Final Validation & Cleanup

**Files:**
- Modify: Tests if needed
- Remove: Unused local sanitize methods from migrated generators

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 2: Build-validate both platforms with RAG example**

Both JVM and Wasm builds must succeed with `rag-agent-example.json`.

- [ ] **Step 3: Verify no manual escaping remains in migrated generators**

```bash
grep -rn 'replace("\\\\' src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt
```
Expected: No matches (all manual escaping eliminated).

- [ ] **Step 4: Verify no manual indentation remains**

```bash
grep -rn '"    ' src/main/kotlin/com/agentwork/modeler/export/generator/ToolGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/RegistryGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/BuiltinToolGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/GraphAgentGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/RagCodeGenerator.kt
```
Expected: No matches (KotlinPoet handles all indentation).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: complete KotlinPoet Phase 1 — 6 generators migrated"
```
