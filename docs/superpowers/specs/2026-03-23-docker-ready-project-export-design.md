# Docker-Ready Project Export

**Date:** 2026-03-23
**Status:** Approved

## Summary

Extend the existing code-export pipeline so that the exported ZIP contains a complete, buildable Kotlin/Spring Boot project that can be built and run inside a Docker container. Today the export produces only bare `.kt` source files; after this change it produces a full Gradle project with Dockerfile.

## Scope

This spec covers **single-agent graphs only**. If the `ProjectGraph` contains multiple agent nodes, validation rejects the graph with an error. Multi-agent export is out of scope for this iteration.

## Context

### Current State

The export pipeline (`ExportService`) validates a `ProjectGraph`, converts it to `KoogAgent` models via `GraphToKoogAdapter`, generates Kotlin source files through four generators (`AgentGenerator`, `MainGenerator`, `RegistryGenerator`, `ToolGenerator`), and packs them into a ZIP via `ZipBuilder`.

Current ZIP structure:
```
src/MyAgent.kt
src/tools/SearchToolToolSet.kt
src/ToolRegistry.kt
src/Main.kt
```

This is not buildable — no build system, no dependencies, no entry point configuration.

### Desired State

A ZIP that can be extracted, built with Docker, and started as an HTTP server:

```bash
unzip my-agent.zip -d my-agent && cd my-agent
docker build -t my-agent .
docker run -e OPENAI_API_KEY=sk-... -e SERVER_PORT=8081 -p 8081:8081 my-agent
```

## Generated Project Structure

```
my-agent/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── Dockerfile
└── src/main/
    ├── kotlin/generated/
    │   ├── Application.kt
    │   ├── AgentConfig.kt
    │   ├── ChatController.kt
    │   ├── tools/
    │   │   └── SearchToolToolSet.kt
    │   └── ToolRegistry.kt
    └── resources/
        └── application.properties
```

## REST API Contract

**Endpoint:** `POST /api/chat`

**Request:**
```json
{"message": "Hello, how can you help me?"}
```

**Response:**
```json
{"response": "I can help you with..."}
```

## New Generators

Five new `@Component` classes in `com.agentwork.modeler.export.generator`:

### BuildGradleGenerator

Generates `build.gradle.kts`. Input: `KoogAgent` (to determine provider-specific dependencies).

Method signature: `fun generate(agent: KoogAgent): String`

Plugins:
- `kotlin("jvm")` version `2.2.21`
- `kotlin("plugin.spring")` version `2.2.21`
- `org.springframework.boot` version `4.0.4`
- `io.spring.dependency-management` version `1.1.7`

Repositories:
```kotlin
repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/grazi/koog-public")
}
```

Core dependencies (always):
- `spring-boot-starter-webflux` (needed for Kotlin suspend controller support)
- `kotlin-reflect`
- `kotlinx-coroutines-reactor`
- `ai.koog:koog-agents:0.7.2`

Provider-specific dependencies:
- `OPENAI` → `ai.koog:koog-prompt-executor-openai:0.7.2`
- `ANTHROPIC` → `ai.koog:koog-prompt-executor-anthropic:0.7.2`
- `GOOGLE` → `ai.koog:koog-prompt-executor-google:0.7.2`

Versions are held as constants in the generator for central updates.

### SettingsGradleGenerator

Generates `settings.gradle.kts`.

Method signature: `fun generate(projectName: String): String`

Input: project name string (passed directly from `ProjectGraph.name` by `ExportService`).

Output:
```kotlin
rootProject.name = "my-agent"
```

### ApplicationPropertiesGenerator

Generates `application.properties`. Static content.

Method signature: `fun generate(): String`

Output:
```properties
server.port=${SERVER_PORT:8080}
```

### GradlePropertiesGenerator

Generates `gradle.properties`. Static content.

Method signature: `fun generate(): String`

Output:
```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx512m
```

### DockerfileGenerator

Generates a multi-stage Dockerfile. Static content.

Method signature: `fun generate(): String`

```dockerfile
# Stage 1: Build
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon
COPY src/ src/
RUN gradle bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Note: `EXPOSE 8080` is a fixed default. The actual port is controlled at runtime via `SERVER_PORT` environment variable, which Spring Boot reads from `application.properties`.

### ControllerGenerator

Generates `ChatController.kt`. Static content (no agent-specific input needed — the controller injects the agent bean by type).

Method signature: `fun generate(): String`

```kotlin
package generated

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ChatController(private val agent: AIAgent<String, String>) {

    @PostMapping("/api/chat")
    fun chat(@RequestBody request: ChatRequest): ChatResponse {
        val result = runBlocking { agent.run(request.message) }
        return ChatResponse(response = result)
    }
}

data class ChatRequest(val message: String)
data class ChatResponse(val response: String)
```

Note: Uses `runBlocking` to bridge the suspend `agent.run()` call in a synchronous Spring WebFlux controller, avoiding complexity with reactive types. WebFlux is used for its coroutine-reactor integration; a future iteration could switch to native `suspend fun` controllers.

## Modified Generators

### MainGenerator → ApplicationGenerator (renamed)

Generates `Application.kt` with `@SpringBootApplication` and `main()` instead of `runBlocking`.

Method signature: `fun generate(): String`

```kotlin
package generated

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

### AgentGenerator

Generates `AgentConfig.kt` — a `@Configuration` class with `@Bean fun agent(): AIAgent<String, String>` instead of a standalone class with `suspend fun run()`.

The generator remains **provider-aware**: it uses `agent.modelProvider.executorFactory`, `agent.modelProvider.importPackage`, and `agent.modelProvider.name + "_API_KEY"` to select the correct executor and API key env variable, just as the current implementation does.

Example output for an OpenAI agent:
```kotlin
package generated

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentConfig {
    @Bean
    fun agent(): AIAgent<String, String> = AIAgent(
        promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
        systemPrompt = "You are a helpful assistant.",
        llmModel = OpenAIModels.Chat.GPT4o,
        toolRegistry = createToolRegistry()
    )
}
```

For Anthropic, the generator would produce `simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))` and the corresponding model reference.

### ToolGenerator — no changes

### RegistryGenerator — no changes

## Validation Changes

`GraphValidator` gets one new rule:

- **Single agent only:** If the graph contains more than one agent node, emit error `"Only single-agent graphs are supported for export"`.

## ExportService Changes

`ExportService.export()` generates a full project structure. It takes the first (and only, after validation) agent from the adapter result.

New constructor dependencies:
- `BuildGradleGenerator`
- `SettingsGradleGenerator`
- `GradlePropertiesGenerator`
- `ApplicationPropertiesGenerator`
- `DockerfileGenerator`
- `ControllerGenerator`
- `ApplicationGenerator` (renamed from `MainGenerator`)

File mapping:
```
build.gradle.kts                                    ← buildGradleGenerator.generate(agent)
settings.gradle.kts                                 ← settingsGradleGenerator.generate(graph.name)
gradle.properties                                   ← gradlePropertiesGenerator.generate()
Dockerfile                                          ← dockerfileGenerator.generate()
src/main/resources/application.properties            ← applicationPropertiesGenerator.generate()
src/main/kotlin/generated/Application.kt             ← applicationGenerator.generate()
src/main/kotlin/generated/AgentConfig.kt             ← agentGenerator.generate(agent)
src/main/kotlin/generated/ChatController.kt          ← controllerGenerator.generate()
src/main/kotlin/generated/tools/<Name>ToolSet.kt     ← toolGenerator.generate(tool)
src/main/kotlin/generated/ToolRegistry.kt            ← registryGenerator.generate(agent.tools)
```

**Breaking change:** File paths in the ZIP change from `src/` to full Gradle project layout. Existing tests must be updated.

## Configuration

- **API Key:** via environment variable (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, or `GOOGLE_API_KEY` depending on provider). Docker-idiomatic, no secrets in files.
- **Server Port:** via `SERVER_PORT` environment variable, defaults to `8080`. Allows multiple agent containers on different ports.

## Testing Strategy

- One unit test per new generator, verifying the generated content contains expected elements (dependencies, class names, annotations, repository URLs).
- Updated `ExportServiceTest` verifying the ZIP contains the full project structure with correct paths.
- Updated `AgentGeneratorTest` for Spring `@Configuration`/`@Bean` output.
- Renamed `MainGeneratorTest` → `ApplicationGeneratorTest` for the new `@SpringBootApplication` output.
- Updated `GraphValidatorTest` with a test for the single-agent-only rule.
