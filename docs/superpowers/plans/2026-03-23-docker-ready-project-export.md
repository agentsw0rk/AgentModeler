# Docker-Ready Project Export — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the code-export pipeline produce a complete, Docker-buildable Spring Boot project instead of bare Kotlin files.

**Architecture:** Add six new generators for infrastructure files (build.gradle.kts, settings.gradle.kts, gradle.properties, Dockerfile, application.properties, ChatController.kt), modify AgentGenerator to produce a Spring @Configuration bean, rename MainGenerator to ApplicationGenerator with @SpringBootApplication output, add single-agent validation, and rewire ExportService to emit the full Gradle project layout.

**Tech Stack:** Kotlin, Spring Boot 4, Koog 0.7.2, Gradle, Docker multi-stage build

**Spec:** `docs/superpowers/specs/2026-03-23-docker-ready-project-export-design.md`

---

## File Map

### New Files
- `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/DockerfileGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationGenerator.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/DockerfileGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationGeneratorTest.kt`

### Modified Files
- `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`
- `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

### Deleted Files
- `src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt`
- `src/test/kotlin/com/agentwork/modeler/export/generator/MainGeneratorTest.kt`

---

## Task 1: GraphValidator — single-agent-only rule

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `GraphValidatorTest.kt`:

```kotlin
@Test
fun `reports error for multiple agent nodes`() {
    val graph = ProjectGraph(1, "Test",
        listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
            GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "anthropic"))
        ),
        emptyList())
    val errors = validator.validate(graph)
    assertTrue(errors.any { it.contains("single-agent") || it.contains("Only single") })
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest.reports error for multiple agent nodes"`
Expected: FAIL — no such validation rule yet.

- [ ] **Step 3: Implement the validation rule**

In `GraphValidator.kt`, add at the top of the `validate` method (before the `for` loop):

```kotlin
val agentNodes = graph.nodes.filter { it.type == "agent" }
if (agentNodes.size > 1) {
    errors.add("Only single-agent graphs are supported for export")
}
```

- [ ] **Step 4: Run all validator tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.validation.GraphValidatorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/validation/GraphValidator.kt src/test/kotlin/com/agentwork/modeler/export/validation/GraphValidatorTest.kt
git commit -m "feat: add single-agent-only validation rule for export"
```

---

## Task 2: ApplicationGenerator (new, MainGenerator deleted later in Task 7)

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationGeneratorTest.kt`

Note: `MainGenerator` and `MainGeneratorTest` are NOT deleted here — they are still referenced by `ExportService`. They will be deleted in Task 7 when ExportService is rewritten.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ApplicationGeneratorTest {
    private val generator = ApplicationGenerator()

    @Test
    fun `generates SpringBootApplication entry point`() {
        val code = generator.generate()
        assertContains(code, "package generated")
        assertContains(code, "@SpringBootApplication")
        assertContains(code, "class Application")
        assertContains(code, "fun main(args: Array<String>)")
        assertContains(code, "runApplication<Application>(*args)")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ApplicationGeneratorTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement ApplicationGenerator**

Create `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ApplicationGenerator {
    fun generate(): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import org.springframework.boot.autoconfigure.SpringBootApplication")
            appendLine("import org.springframework.boot.runApplication")
            appendLine()
            appendLine("@SpringBootApplication")
            appendLine("class Application")
            appendLine()
            appendLine("fun main(args: Array<String>) {")
            appendLine("    runApplication<Application>(*args)")
            appendLine("}")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ApplicationGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationGeneratorTest.kt
git commit -m "feat: add ApplicationGenerator for Spring Boot entry point"
```

---

## Task 3: Modify AgentGenerator for Spring @Configuration output

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt`

- [ ] **Step 1: Update the tests**

Replace `AgentGeneratorTest.kt` content:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AgentGeneratorTest {

    private val generator = AgentGenerator()

    @Test
    fun `generates Spring Configuration with Bean`() {
        val agent = KoogAgent("MyAgent", "You are a helpful assistant.", ModelProvider.OPENAI, "gpt-4o", 0.7, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "@Configuration")
        assertContains(code, "@Bean")
        assertContains(code, "fun agent(): AIAgent<String, String>")
        assertContains(code, "simpleOpenAIExecutor")
        assertContains(code, "You are a helpful assistant.")
        assertContains(code, "GPT4o")
        assertContains(code, "temperature = 0.7")
    }

    @Test
    fun `generates agent with tool registry reference`() {
        val agent = KoogAgent("AgentWithTools", "Help", ModelProvider.ANTHROPIC, null, null,
            listOf(KoogTool("searchTool", "Searches", emptyList(), null)))
        val code = generator.generate(agent)
        assertContains(code, "toolRegistry = createToolRegistry()")
        assertContains(code, "AnthropicModels")
        assertContains(code, "ANTHROPIC_API_KEY")
    }

    @Test
    fun `omits toolRegistry when no tools`() {
        val agent = KoogAgent("SimpleAgent", "Help", ModelProvider.GOOGLE, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("toolRegistry"))
        assertContains(code, "GoogleModels")
    }

    @Test
    fun `does not produce old standalone class pattern`() {
        val agent = KoogAgent("MyAgent", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertFalse(code.contains("class MyAgent"))
        assertFalse(code.contains("suspend fun run("))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest"`
Expected: FAIL — current output has `class MyAgent` not `@Configuration`.

- [ ] **Step 3: Rewrite AgentGenerator**

Replace `AgentGenerator.kt` content:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
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

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.agent.AIAgent")
            if (agent.tools.isNotEmpty()) {
                appendLine("import ai.koog.agents.core.tools.ToolRegistry")
            }
            appendLine("import ai.koog.prompt.executor.llms.all.${agent.modelProvider.executorFactory}")
            appendLine("import $modelImport")
            appendLine("import org.springframework.context.annotation.Bean")
            appendLine("import org.springframework.context.annotation.Configuration")
            appendLine()
            appendLine("@Configuration")
            appendLine("class AgentConfig {")
            appendLine("    @Bean")
            appendLine("    fun agent(): AIAgent<String, String> = AIAgent(")
            appendLine("            $paramBlock")
            appendLine("    )")
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

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.AgentGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/AgentGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/AgentGeneratorTest.kt
git commit -m "feat: AgentGenerator produces Spring @Configuration/@Bean output"
```

---

## Task 4: BuildGradleGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class BuildGradleGeneratorTest {
    private val generator = BuildGradleGenerator()

    @Test
    fun `generates build file with Spring Boot and Koog plugins`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, """kotlin("jvm")""")
        assertContains(code, """kotlin("plugin.spring")""")
        assertContains(code, "org.springframework.boot")
        assertContains(code, "io.spring.dependency-management")
    }

    @Test
    fun `includes Koog repository`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "mavenCentral()")
        assertContains(code, "packages.jetbrains.team/maven/p/grazi/koog-public")
    }

    @Test
    fun `includes core dependencies`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "spring-boot-starter-webflux")
        assertContains(code, "kotlin-reflect")
        assertContains(code, "kotlinx-coroutines-reactor")
        assertContains(code, "koog-agents")
    }

    @Test
    fun `includes OpenAI executor dependency for OpenAI provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-openai")
    }

    @Test
    fun `includes Anthropic executor dependency for Anthropic provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.ANTHROPIC, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-anthropic")
    }

    @Test
    fun `includes Google executor dependency for Google provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.GOOGLE, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-google")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement BuildGradleGenerator**

Create `src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class BuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.2.21"
        const val SPRING_BOOT_VERSION = "4.0.4"
        const val DEPENDENCY_MANAGEMENT_VERSION = "1.1.7"
        const val KOOG_VERSION = "0.7.2"
    }

    fun generate(agent: KoogAgent): String {
        val providerDep = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "koog-prompt-executor-openai"
            ModelProvider.ANTHROPIC -> "koog-prompt-executor-anthropic"
            ModelProvider.GOOGLE -> "koog-prompt-executor-google"
        }

        return buildString {
            appendLine("""plugins {""")
            appendLine("""    kotlin("jvm") version "$KOTLIN_VERSION"""")
            appendLine("""    kotlin("plugin.spring") version "$KOTLIN_VERSION"""")
            appendLine("""    id("org.springframework.boot") version "$SPRING_BOOT_VERSION"""")
            appendLine("""    id("io.spring.dependency-management") version "$DEPENDENCY_MANAGEMENT_VERSION"""")
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
            appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/koog-public")""")
            appendLine("""}""")
            appendLine()
            appendLine("""dependencies {""")
            appendLine("""    implementation("org.springframework.boot:spring-boot-starter-webflux")""")
            appendLine("""    implementation("org.jetbrains.kotlin:kotlin-reflect")""")
            appendLine("""    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")""")
            appendLine("""    implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
            appendLine("""    implementation("ai.koog:$providerDep:$KOOG_VERSION")""")
            appendLine("""}""")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.BuildGradleGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/BuildGradleGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/BuildGradleGeneratorTest.kt
git commit -m "feat: add BuildGradleGenerator with provider-specific dependencies"
```

---

## Task 5: Static generators (Settings, GradleProperties, ApplicationProperties, Dockerfile)

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/DockerfileGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGeneratorTest.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGeneratorTest.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGeneratorTest.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/DockerfileGeneratorTest.kt`

- [ ] **Step 1: Write all four failing tests**

Create `SettingsGradleGeneratorTest.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class SettingsGradleGeneratorTest {
    private val generator = SettingsGradleGenerator()

    @Test
    fun `generates settings with project name`() {
        val code = generator.generate("my-cool-agent")
        assertContains(code, """rootProject.name = "my-cool-agent"""")
    }
}
```

Create `GradlePropertiesGeneratorTest.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class GradlePropertiesGeneratorTest {
    private val generator = GradlePropertiesGenerator()

    @Test
    fun `generates gradle properties`() {
        val code = generator.generate()
        assertContains(code, "kotlin.code.style=official")
        assertContains(code, "org.gradle.jvmargs=-Xmx512m")
    }
}
```

Create `ApplicationPropertiesGeneratorTest.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ApplicationPropertiesGeneratorTest {
    private val generator = ApplicationPropertiesGenerator()

    @Test
    fun `generates application properties with dynamic port`() {
        val code = generator.generate()
        assertContains(code, "server.port=\${SERVER_PORT:8080}")
    }
}
```

Create `DockerfileGeneratorTest.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class DockerfileGeneratorTest {
    private val generator = DockerfileGenerator()

    @Test
    fun `generates multi-stage Dockerfile`() {
        val code = generator.generate()
        assertContains(code, "FROM gradle:8-jdk21 AS build")
        assertContains(code, "gradle bootJar")
        assertContains(code, "FROM eclipse-temurin:21-jre")
        assertContains(code, "COPY --from=build")
        assertContains(code, "EXPOSE 8080")
        assertContains(code, """ENTRYPOINT ["java", "-jar", "app.jar"]""")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SettingsGradleGeneratorTest" --tests "com.agentwork.modeler.export.generator.GradlePropertiesGeneratorTest" --tests "com.agentwork.modeler.export.generator.ApplicationPropertiesGeneratorTest" --tests "com.agentwork.modeler.export.generator.DockerfileGeneratorTest"`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement all four generators**

Create `SettingsGradleGenerator.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class SettingsGradleGenerator {
    fun generate(projectName: String): String {
        return """rootProject.name = "$projectName"
"""
    }
}
```

Create `GradlePropertiesGenerator.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class GradlePropertiesGenerator {
    fun generate(): String {
        return buildString {
            appendLine("kotlin.code.style=official")
            appendLine("org.gradle.jvmargs=-Xmx512m")
        }
    }
}
```

Create `ApplicationPropertiesGenerator.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ApplicationPropertiesGenerator {
    fun generate(): String {
        return "server.port=\${SERVER_PORT:8080}\n"
    }
}
```

Create `DockerfileGenerator.kt`:
```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class DockerfileGenerator {
    fun generate(): String {
        return buildString {
            appendLine("# Stage 1: Build")
            appendLine("FROM gradle:8-jdk21 AS build")
            appendLine("WORKDIR /app")
            appendLine("COPY build.gradle.kts settings.gradle.kts gradle.properties ./")
            appendLine("RUN gradle dependencies --no-daemon")
            appendLine("COPY src/ src/")
            appendLine("RUN gradle bootJar --no-daemon")
            appendLine()
            appendLine("# Stage 2: Run")
            appendLine("FROM eclipse-temurin:21-jre")
            appendLine("WORKDIR /app")
            appendLine("COPY --from=build /app/build/libs/*.jar app.jar")
            appendLine("EXPOSE 8080")
            appendLine("""ENTRYPOINT ["java", "-jar", "app.jar"]""")
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.SettingsGradleGeneratorTest" --tests "com.agentwork.modeler.export.generator.GradlePropertiesGeneratorTest" --tests "com.agentwork.modeler.export.generator.ApplicationPropertiesGeneratorTest" --tests "com.agentwork.modeler.export.generator.DockerfileGeneratorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGenerator.kt \
        src/main/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGenerator.kt \
        src/main/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGenerator.kt \
        src/main/kotlin/com/agentwork/modeler/export/generator/DockerfileGenerator.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/SettingsGradleGeneratorTest.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/GradlePropertiesGeneratorTest.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/ApplicationPropertiesGeneratorTest.kt \
        src/test/kotlin/com/agentwork/modeler/export/generator/DockerfileGeneratorTest.kt
git commit -m "feat: add Settings, GradleProperties, ApplicationProperties, Dockerfile generators"
```

---

## Task 6: ControllerGenerator

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ControllerGeneratorTest {
    private val generator = ControllerGenerator()

    @Test
    fun `generates REST controller with chat endpoint`() {
        val code = generator.generate()
        assertContains(code, "package generated")
        assertContains(code, "@RestController")
        assertContains(code, """@PostMapping("/api/chat")""")
        assertContains(code, "ChatController")
        assertContains(code, "AIAgent<String, String>")
        assertContains(code, "agent.run(request.message)")
        assertContains(code, "runBlocking")
        assertContains(code, "data class ChatRequest")
        assertContains(code, "data class ChatResponse")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ControllerGeneratorTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement ControllerGenerator**

Create `src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt`:

```kotlin
package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ControllerGenerator {
    fun generate(): String {
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
            appendLine("class ChatController(private val agent: AIAgent<String, String>) {")
            appendLine()
            appendLine("""    @PostMapping("/api/chat")""")
            appendLine("    fun chat(@RequestBody request: ChatRequest): ChatResponse {")
            appendLine("        val result = runBlocking { agent.run(request.message) }")
            appendLine("        return ChatResponse(response = result)")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("data class ChatRequest(val message: String)")
            appendLine("data class ChatResponse(val response: String)")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.agentwork.modeler.export.generator.ControllerGeneratorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/export/generator/ControllerGenerator.kt src/test/kotlin/com/agentwork/modeler/export/generator/ControllerGeneratorTest.kt
git commit -m "feat: add ControllerGenerator for REST chat endpoint"
```

---

## Task 7: Rewire ExportService and update ExportServiceTest

**Files:**
- Modify: `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`
- Modify: `src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt`

- [ ] **Step 1: Update ExportServiceTest**

Replace `ExportServiceTest.kt` content:

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.export.zip.ZipBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ExportServiceTest {

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
        zipBuilder = ZipBuilder()
    )

    @Test
    fun `exports a valid graph as ZIP with full project structure`() {
        val graph = ProjectGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "MyAgent", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("t1", "tool", Position(200.0, 0.0), mapOf("name" to "searchTool", "description" to "Searches",
                    "parameters" to listOf(mapOf("id" to "p1", "name" to "query", "type" to "String", "description" to "query", "required" to true))))
            ),
            listOf(GraphEdge("e1", "t1", "a1"))
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)

        // Infrastructure files
        assertTrue(entries.contains("build.gradle.kts"))
        assertTrue(entries.contains("settings.gradle.kts"))
        assertTrue(entries.contains("gradle.properties"))
        assertTrue(entries.contains("Dockerfile"))
        assertTrue(entries.contains("src/main/resources/application.properties"))

        // Kotlin sources
        assertTrue(entries.contains("src/main/kotlin/generated/Application.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ChatController.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
        assertTrue(entries.contains("src/main/kotlin/generated/tools/SearchToolToolSet.kt"))

        // Verify content wiring (spot check)
        val settingsContent = readZipFileContent(zip, "settings.gradle.kts")
        assertContains(settingsContent, "TestProject")
        val buildContent = readZipFileContent(zip, "build.gradle.kts")
        assertContains(buildContent, "koog-prompt-executor-openai")
    }

    @Test
    fun `exports agent without tools omits ToolRegistry`() {
        val graph = ProjectGraph(1, "TestProject",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "SimpleAgent", "systemPrompt" to "Help", "modelProvider" to "openai"))
            ),
            emptyList()
        )
        val zip = service.export(graph)
        val entries = readZipEntries(zip)
        assertTrue(entries.contains("src/main/kotlin/generated/AgentConfig.kt"))
        assertTrue(!entries.contains("src/main/kotlin/generated/ToolRegistry.kt"))
    }

    @Test
    fun `throws ExportValidationException for invalid graph`() {
        val graph = ProjectGraph(1, "Test",
            listOf(GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "", "systemPrompt" to "", "modelProvider" to "openai"))),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.isNotEmpty())
    }

    @Test
    fun `throws ExportValidationException for multi-agent graph`() {
        val graph = ProjectGraph(1, "Test",
            listOf(
                GraphNode("a1", "agent", Position(0.0, 0.0), mapOf("name" to "Agent1", "systemPrompt" to "Help", "modelProvider" to "openai")),
                GraphNode("a2", "agent", Position(300.0, 0.0), mapOf("name" to "Agent2", "systemPrompt" to "Help2", "modelProvider" to "openai"))
            ),
            emptyList())
        val ex = assertThrows<ExportValidationException> { service.export(graph) }
        assertTrue(ex.errors.any { it.contains("single-agent") || it.contains("Only single") })
    }

    private fun readZipEntries(data: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) { entries.add(entry.name); entry = zis.nextEntry }
        }
        return entries
    }

    private fun readZipFileContent(data: ByteArray, fileName: String): String {
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == fileName) return zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        error("File $fileName not found in ZIP")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: FAIL — ExportService constructor signature mismatch.

- [ ] **Step 3: Rewrite ExportService**

Replace `ExportService.kt` content:

```kotlin
package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.*
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
    private val applicationGenerator: ApplicationGenerator,
    private val buildGradleGenerator: BuildGradleGenerator,
    private val settingsGradleGenerator: SettingsGradleGenerator,
    private val gradlePropertiesGenerator: GradlePropertiesGenerator,
    private val applicationPropertiesGenerator: ApplicationPropertiesGenerator,
    private val dockerfileGenerator: DockerfileGenerator,
    private val controllerGenerator: ControllerGenerator,
    private val zipBuilder: ZipBuilder
) {
    fun export(graph: ProjectGraph): ByteArray {
        val validationErrors = validator.validate(graph)
        if (validationErrors.isNotEmpty()) {
            throw ExportValidationException(validationErrors)
        }

        val agents = adapter.convert(graph)
        val agent = agents.first()
        val pkg = "src/main/kotlin/generated"
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate()

        // Kotlin sources
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(agent)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate()

        // Tools
        for (tool in agent.tools) {
            val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
            files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
        }
        if (agent.tools.isNotEmpty()) {
            files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools)
        }

        return zipBuilder.buildZip(files)
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --tests "com.agentwork.modeler.export.ExportServiceTest"`
Expected: ALL PASS

- [ ] **Step 5: Delete MainGenerator and its test (now safe — no longer referenced)**

```bash
rm src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt
rm src/test/kotlin/com/agentwork/modeler/export/generator/MainGeneratorTest.kt
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: ALL PASS (no regressions)

- [ ] **Step 7: Commit**

```bash
git rm src/main/kotlin/com/agentwork/modeler/export/generator/MainGenerator.kt \
      src/test/kotlin/com/agentwork/modeler/export/generator/MainGeneratorTest.kt
git add src/main/kotlin/com/agentwork/modeler/export/ExportService.kt src/test/kotlin/com/agentwork/modeler/export/ExportServiceTest.kt
git commit -m "feat: rewire ExportService for full Docker-ready project export

Replaces MainGenerator with ApplicationGenerator, adds all infrastructure
generators, produces complete Gradle/Docker project in ZIP."
```
