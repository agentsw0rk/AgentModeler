# AWS Bedrock Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add AWS Bedrock as fourth `ModelProvider` with 8 models, dual env-var auth, `BedrockClientSettings` for region, JVM-only.

**Architecture:** Add `BEDROCK` to `ModelProvider` enum, extend `mapModelId()` in 3 generators, add Bedrock-specific executor generation in `AgentGenerator` (2 keys + settings), add Bedrock dependency to build.gradle generators, add "AWS Bedrock" option to 4 frontend provider dropdowns.

**Tech Stack:** Kotlin, KotlinPoet, Spring Boot 4, Koog 0.7.2 (`prompt-executor-bedrock-client`), Next.js 16, React 19

---

## File Structure

### Modified files (Backend)
- `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt` — add `BEDROCK` to `ModelProvider`
- `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt` — Bedrock executor generation (2 keys + settings), `mapModelId()`, imports, constructor
- `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorMainGenerator.kt` — `mapModelId()` Bedrock case
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt` — `mapModelId()` Bedrock case
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt` — Bedrock dependency
- `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt` — Bedrock dependency
- `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt` — Bedrock properties (2 keys)

### Modified files (Frontend)
- `frontend/types/koog.ts` — add `"bedrock"` to `ModelProvider` type
- `frontend/components/panels/AgentProperties.tsx` — Bedrock SelectItem
- `frontend/components/panels/SubAgentProperties.tsx` — Bedrock SelectItem
- `frontend/components/panels/GraphAgentProperties.tsx` — Bedrock SelectItem
- `frontend/components/planner/SettingsTab.tsx` — Bedrock SelectItem

### Test files
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt` — Bedrock agent test

---

## Task 1: ModelProvider Enum + mapModelId in All Generators

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorMainGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt`

- [ ] **Step 1: Add BEDROCK to ModelProvider enum**

In `KoogModel.kt`, add the fourth enum value after GOOGLE:

```kotlin
enum class ModelProvider(val importPackage: String, val executorFactory: String, val defaultModel: String) {
    OPENAI("ai.koog.prompt.executor.clients.openai.OpenAIModels", "simpleOpenAIExecutor", "OpenAIModels.Chat.GPT4o"),
    ANTHROPIC("ai.koog.prompt.executor.clients.anthropic.AnthropicModels", "simpleAnthropicExecutor", "AnthropicModels.Sonnet_4"),
    GOOGLE("ai.koog.prompt.executor.clients.google.GoogleModels", "simpleGoogleExecutor", "GoogleModels.Gemini2Flash"),
    BEDROCK("ai.koog.prompt.executor.clients.bedrock.BedrockModels", "simpleBedrockExecutor", "BedrockModels.AnthropicClaude4Sonnet")
}
```

- [ ] **Step 2: Add Bedrock case to mapModelId in KtorMainGenerator**

In `KtorMainGenerator.kt`, add after the `ModelProvider.GOOGLE` when-branch in `mapModelId()`:

```kotlin
ModelProvider.BEDROCK -> when (id.lowercase()) {
    "claude-4.5-sonnet" -> "BedrockModels.AnthropicClaude4_5Sonnet"
    "claude-4-sonnet" -> "BedrockModels.AnthropicClaude4Sonnet"
    "claude-4-opus" -> "BedrockModels.AnthropicClaude4Opus"
    "nova-pro" -> "BedrockModels.AmazonNovaPro"
    "nova-lite" -> "BedrockModels.AmazonNovaLite"
    "nova-micro" -> "BedrockModels.AmazonNovaMicro"
    "llama-3.3-70b" -> "BedrockModels.MetaLlama3_3_70BInstruct"
    "llama-3.1-405b" -> "BedrockModels.MetaLlama3_1_405BInstruct"
    else -> provider.defaultModel
}
```

- [ ] **Step 3: Add same Bedrock case to mapModelId in AgentFactoryGenerator**

Add the identical `ModelProvider.BEDROCK -> when` block to the `mapModelId()` function in `AgentFactoryGenerator.kt`.

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/model/KoogModel.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/ktor/KtorMainGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/lib/AgentFactoryGenerator.kt
git commit -m "feat: add BEDROCK to ModelProvider enum and mapModelId in generators"
```

---

## Task 2: AgentGenerator — Bedrock Executor Generation

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`

This is the most complex task. The `AgentGenerator` uses KotlinPoet and needs changes in 4 places:

1. `buildConstructor` — add `accessKey` + `secretKey` params instead of single `apiKey` for Bedrock
2. Properties — add both properties instead of single `apiKey`
3. `buildAgentBody` — generate `simpleBedrockExecutor(accessKey, secretKey, settings = ...)` instead of `simpleOpenAIExecutor(apiKey)`
4. `mapModelId` — add Bedrock case
5. Imports — add `BedrockClientSettings` import for Bedrock

- [ ] **Step 1: Add mapModelId Bedrock case**

In `AgentGenerator.kt`, add after the `ModelProvider.GOOGLE` when-branch in `mapModelId()`:

```kotlin
ModelProvider.BEDROCK -> when (id.lowercase()) {
    "claude-4.5-sonnet" -> "BedrockModels.AnthropicClaude4_5Sonnet"
    "claude-4-sonnet" -> "BedrockModels.AnthropicClaude4Sonnet"
    "claude-4-opus" -> "BedrockModels.AnthropicClaude4Opus"
    "nova-pro" -> "BedrockModels.AmazonNovaPro"
    "nova-lite" -> "BedrockModels.AmazonNovaLite"
    "nova-micro" -> "BedrockModels.AmazonNovaMicro"
    "llama-3.3-70b" -> "BedrockModels.MetaLlama3_3_70BInstruct"
    "llama-3.1-405b" -> "BedrockModels.MetaLlama3_1_405BInstruct"
    else -> provider.defaultModel
}
```

- [ ] **Step 2: Modify buildConstructor for Bedrock dual keys**

In `buildConstructor()`, replace the single `apiKey` parameter block with a conditional:

```kotlin
val isBedrock = agent.modelProvider == ModelProvider.BEDROCK
if (isBedrock) {
    ctorBuilder.addParameter(
        com.squareup.kotlinpoet.ParameterSpec.builder("accessKey", String::class)
            .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${bedrock.access-key}").build())
            .build()
    )
    ctorBuilder.addParameter(
        com.squareup.kotlinpoet.ParameterSpec.builder("secretKey", String::class)
            .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${bedrock.secret-key}").build())
            .build()
    )
} else {
    ctorBuilder.addParameter(
        com.squareup.kotlinpoet.ParameterSpec.builder("apiKey", String::class)
            .addAnnotation(AnnotationSpec.builder(valueClass).addMember("%S", "\${$providerKey.api-key}").build())
            .build()
    )
}
```

- [ ] **Step 3: Modify properties for Bedrock dual keys**

In the `generate()` method, replace the single `apiKey` property with a conditional:

```kotlin
val isBedrock = agent.modelProvider == ModelProvider.BEDROCK
if (isBedrock) {
    classBuilder.addProperty(
        PropertySpec.builder("accessKey", String::class)
            .initializer("accessKey")
            .addModifiers(KModifier.PRIVATE)
            .build()
    )
    classBuilder.addProperty(
        PropertySpec.builder("secretKey", String::class)
            .initializer("secretKey")
            .addModifiers(KModifier.PRIVATE)
            .build()
    )
} else {
    classBuilder.addProperty(
        PropertySpec.builder("apiKey", String::class)
            .initializer("apiKey")
            .addModifiers(KModifier.PRIVATE)
            .build()
    )
}
```

- [ ] **Step 4: Modify @PostConstruct validateApiKey for Bedrock**

Replace the existing `validateApiKey` `@PostConstruct` with a conditional:

```kotlin
if (isBedrock) {
    classBuilder.addFunction(
        FunSpec.builder("validateCredentials")
            .addAnnotation(postConstructAnnotation)
            .addStatement("require(accessKey.isNotBlank()) { %S }", "AWS_BEDROCK_ACCESS_KEY environment variable must be set")
            .addStatement("require(secretKey.isNotBlank()) { %S }", "AWS_BEDROCK_SECRET_ACCESS_KEY environment variable must be set")
            .build()
    )
} else {
    classBuilder.addFunction(
        FunSpec.builder("validateApiKey")
            .addAnnotation(postConstructAnnotation)
            .addStatement("require(apiKey.isNotBlank()) { %S }", "${agent.modelProvider.name}_API_KEY environment variable must be set")
            .build()
    )
}
```

- [ ] **Step 5: Modify buildAgentBody for Bedrock executor**

In `buildAgentBody()`, replace the `promptExecutor` line with a conditional:

```kotlin
if (agent.modelProvider == ModelProvider.BEDROCK) {
    builder.add("    promptExecutor = %L(\n", agent.modelProvider.executorFactory)
    builder.add("        awsAccessKeyId = accessKey,\n")
    builder.add("        awsSecretAccessKey = secretKey,\n")
    builder.add("        settings = BedrockClientSettings(\n")
    builder.add("            region = System.getenv(\"AWS_BEDROCK_REGION\") ?: \"us-west-2\"\n")
    builder.add("        )\n")
    builder.add("    ),\n")
} else {
    builder.add("    promptExecutor = %L(apiKey),\n", agent.modelProvider.executorFactory)
}
```

- [ ] **Step 6: Add Bedrock imports**

After the existing import additions in the `generate()` method (after `fileBuilder.addImport` calls), add:

```kotlin
if (agent.modelProvider == ModelProvider.BEDROCK) {
    fileBuilder.addImport("ai.koog.prompt.executor.clients.bedrock", "BedrockClientSettings")
}
```

- [ ] **Step 7: Compile and run tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt
git commit -m "feat: add Bedrock executor generation with dual keys and BedrockClientSettings"
```

---

## Task 3: BuildGradle + ApplicationProperties + LibBuildGradle — Bedrock Dependencies

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt`
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt`

- [ ] **Step 1: Add Bedrock dependency to BuildGradleGenerator**

In `BuildGradleGenerator.kt`, after the MCP dependency block (after `if (agent.mcpServers.isNotEmpty()) { ... }`), add:

```kotlin
if (agent.modelProvider == ModelProvider.BEDROCK) {
    appendLine("""    implementation("ai.koog.prompt:prompt-executor-bedrock-client:$KOOG_VERSION")""")
}
```

Add import at top:

```kotlin
import com.agentwork.modeler.export.model.ModelProvider
```

- [ ] **Step 2: Add Bedrock dependency to LibBuildGradleGenerator**

In `LibBuildGradleGenerator.kt`, after the MCP dependency block, add:

```kotlin
if (agent.modelProvider == ModelProvider.BEDROCK) {
    appendLine("""            implementation("ai.koog.prompt:prompt-executor-bedrock-client:$KOOG_VERSION")""")
}
```

Add import at top:

```kotlin
import com.agentwork.modeler.export.model.ModelProvider
```

- [ ] **Step 3: Modify ApplicationPropertiesGenerator for Bedrock**

In `ApplicationPropertiesGenerator.kt`, replace the `api-key` line with a conditional:

```kotlin
fun generate(agent: KoogAgent): String {
    return buildString {
        appendLine("server.port=\${SERVER_PORT:8080}")
        if (agent.modelProvider == ModelProvider.BEDROCK) {
            appendLine("bedrock.access-key=\${AWS_BEDROCK_ACCESS_KEY:}")
            appendLine("bedrock.secret-key=\${AWS_BEDROCK_SECRET_ACCESS_KEY:}")
        } else {
            val providerKey = agent.modelProvider.name.lowercase()
            appendLine("${providerKey}.api-key=\${${agent.modelProvider.name}_API_KEY:}")
        }
        if (agent.otelConfig?.enabled == true) {
            // ... existing OTel code unchanged
        }
    }
}
```

Add import at top:

```kotlin
import com.agentwork.modeler.export.model.ModelProvider
```

- [ ] **Step 4: Compile and run tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/lib/LibBuildGradleGenerator.kt \
  src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt
git commit -m "feat: add Bedrock dependency and properties to build/config generators"
```

---

## Task 4: ExportService Integration Test

**Files:**
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Add Bedrock agent integration test**

Add to `ExportServiceTest.kt`:

```kotlin
@Test
fun `exports Bedrock agent with correct dependencies and properties`() {
    val graph = WorkspaceGraph(1, "BedrockProject",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf(
                "name" to "MyAgent",
                "systemPrompt" to "Help",
                "modelProvider" to "bedrock",
                "modelId" to "claude-4.5-sonnet"
            ))
        ),
        emptyList()
    )
    val (_, files) = service.exportWithFiles(graph)

    // build.gradle has Bedrock dependency
    assertContains(files["build.gradle.kts"]!!, "prompt-executor-bedrock-client")

    // AgentConfig uses Bedrock executor
    val agentCode = files["src/main/kotlin/generated/AgentConfig.kt"]!!
    assertContains(agentCode, "simpleBedrockExecutor")
    assertContains(agentCode, "BedrockClientSettings")
    assertContains(agentCode, "accessKey")
    assertContains(agentCode, "secretKey")
    assertContains(agentCode, "BedrockModels.AnthropicClaude4_5Sonnet")

    // application.properties has Bedrock keys
    val props = files["src/main/resources/application.properties"]!!
    assertContains(props, "bedrock.access-key")
    assertContains(props, "bedrock.secret-key")
    assertTrue(!props.contains("bedrock.api-key"))
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: All tests pass including new Bedrock test

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "test: add Bedrock agent integration test"
```

---

## Task 5: Frontend — ModelProvider Type and Provider Dropdowns

**Files:**
- Modify: `frontend/types/koog.ts`
- Modify: `frontend/components/panels/AgentProperties.tsx`
- Modify: `frontend/components/panels/SubAgentProperties.tsx`
- Modify: `frontend/components/panels/GraphAgentProperties.tsx`
- Modify: `frontend/components/planner/SettingsTab.tsx`

- [ ] **Step 1: Add "bedrock" to ModelProvider type**

In `frontend/types/koog.ts`, change:

```typescript
export type ModelProvider = "openai" | "anthropic" | "google" | "bedrock";
```

- [ ] **Step 2: Add Bedrock to AgentProperties.tsx**

Find the SelectContent with the 3 provider options and add after `<SelectItem value="google">Google</SelectItem>`:

```tsx
<SelectItem value="bedrock">AWS Bedrock</SelectItem>
```

- [ ] **Step 3: Add Bedrock to SubAgentProperties.tsx**

Same change — add after the Google SelectItem:

```tsx
<SelectItem value="bedrock">AWS Bedrock</SelectItem>
```

- [ ] **Step 4: Add Bedrock to GraphAgentProperties.tsx**

Same change:

```tsx
<SelectItem value="bedrock">AWS Bedrock</SelectItem>
```

- [ ] **Step 5: Add Bedrock to SettingsTab.tsx (Planner)**

Same change:

```tsx
<SelectItem value="bedrock">AWS Bedrock</SelectItem>
```

- [ ] **Step 6: Run frontend lint and build**

Run: `cd frontend && npm run lint && npm run build`
Expected: No errors

- [ ] **Step 7: Commit**

```bash
git add frontend/types/koog.ts \
  frontend/components/panels/AgentProperties.tsx \
  frontend/components/panels/SubAgentProperties.tsx \
  frontend/components/panels/GraphAgentProperties.tsx \
  frontend/components/planner/SettingsTab.tsx
git commit -m "feat: add AWS Bedrock to frontend model provider dropdowns"
```

---

## Task 6: Full Stack Verification

- [ ] **Step 1: Run all backend tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification**

Start backend and frontend, verify:
1. Agent node shows "AWS Bedrock" in provider dropdown
2. Selecting Bedrock shows model options
3. Code explorer generates correct Bedrock code
4. Export ZIP contains `prompt-executor-bedrock-client` in build.gradle
