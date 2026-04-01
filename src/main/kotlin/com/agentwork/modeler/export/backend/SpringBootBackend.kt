package com.agentwork.modeler.export.backend

import com.agentwork.modeler.export.GeneratorBackend
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class SpringBootBackend(
    private val agentGenerator: AgentGenerator,
    private val controllerGenerator: ControllerGenerator,
    private val registryGenerator: RegistryGenerator,
    private val buildGradleGenerator: BuildGradleGenerator,
    private val applicationGenerator: ApplicationGenerator,
    private val applicationPropertiesGenerator: ApplicationPropertiesGenerator,
    private val dockerfileGenerator: DockerfileGenerator,
    private val settingsGradleGenerator: SettingsGradleGenerator,
    private val gradlePropertiesGenerator: GradlePropertiesGenerator,
    private val plannerAgentGenerator: PlannerAgentGenerator,
    private val a2aServerGenerator: A2AServerGenerator
) : GeneratorBackend {

    override val id = "spring-boot"
    override val displayName = "Spring Boot (JVM)"
    override val supportedPlatforms = listOf("jvm", "native")

    private val pkg = "src/main/kotlin/generated"

    override fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(agent)

        // Kotlin sources
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(agent, inputClassName = inputClassName, outputClassName = outputClassName)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)

        // A2A Server
        val a2aConfig = agent.a2aConfig
        if (a2aConfig != null && a2aConfig.enabled) {
            val a2aFiles = a2aServerGenerator.generate(a2aConfig)
            for ((fileName, content) in a2aFiles) {
                files["$pkg/a2a/$fileName"] = content
            }
        }

        // Shared code files
        files.putAll(sharedFiles)

        return files
    }

    override fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Create a KoogAgent facade for infrastructure generators
        val infraAgent = KoogAgent(
            name = agent.name,
            systemPrompt = agent.systemPrompt,
            modelProvider = agent.modelProvider,
            modelId = agent.modelId,
            temperature = agent.temperature,
            tools = agent.tools,
            builtinTools = agent.builtinTools,
            subAgents = agent.subAgents,
            mcpServers = agent.mcpServers,
            inputs = agent.inputs,
            outputs = agent.outputs
        )

        // Infrastructure
        var buildGradle = buildGradleGenerator.generate(infraAgent)
        if (agent.ragNodes.isNotEmpty()) {
            val ragDep = """    implementation("ai.koog:vector-storage:${BuildGradleGenerator.KOOG_VERSION}")""" +
                "\n" + """    implementation("ai.koog:embeddings-llm:${BuildGradleGenerator.KOOG_VERSION}")"""
            buildGradle = buildGradle.replace(
                """    implementation("ai.koog:koog-agents:${BuildGradleGenerator.KOOG_VERSION}")""",
                """    implementation("ai.koog:koog-agents:${BuildGradleGenerator.KOOG_VERSION}")""" + "\n" + ragDep
            )
        }
        files["build.gradle.kts"] = buildGradle
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(infraAgent)

        // Kotlin sources
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(infraAgent, strategyCall, inputClassName = inputClassName, outputClassName = outputClassName)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)

        // Shared code files
        files.putAll(sharedFiles)

        return files
    }

    override fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Infrastructure
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graphName)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["build.gradle.kts"] = generatePlannerBuildGradle()
        files["src/main/resources/application.properties"] = generatePlannerApplicationProperties(agent)

        // Kotlin sources
        files["$pkg/Application.kt"] = applicationGenerator.generate()

        // Planner-specific files
        val plannerFiles = plannerAgentGenerator.generate(agent)
        files["$pkg/planner/PlannerGoals.kt"] = plannerFiles["PlannerGoals.kt"] ?: ""
        files["$pkg/planner/PlannerActions.kt"] = plannerFiles["PlannerActions.kt"] ?: ""
        files["$pkg/PlannerAgent.kt"] = plannerFiles["PlannerAgent.kt"] ?: ""

        // PlanController
        files["$pkg/PlanController.kt"] = generatePlanController()

        // A2A Server
        val a2aConfig = agent.a2aConfig
        if (a2aConfig != null && a2aConfig.enabled) {
            val a2aFiles = a2aServerGenerator.generate(a2aConfig)
            for ((fileName, content) in a2aFiles) {
                files["$pkg/a2a/$fileName"] = content
            }
        }

        // Shared code files
        files.putAll(sharedFiles)

        return files
    }

    private fun generatePlannerBuildGradle(): String = buildString {
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

    private fun generatePlannerApplicationProperties(planner: KoogPlannerAgent): String = buildString {
        appendLine("server.port=\${SERVER_PORT:8080}")
        if (planner.modelProvider != null) {
            val providerKey = planner.modelProvider.name.lowercase()
            appendLine("${providerKey}.api-key=\${${planner.modelProvider.name}_API_KEY:}")
        }
    }

    private fun generatePlanController(): String = buildString {
        appendLine("package generated")
        appendLine()
        appendLine("import org.springframework.web.bind.annotation.*")
        appendLine()
        appendLine("@RestController")
        appendLine("@RequestMapping(\"/api\")")
        appendLine("class PlanController {")
        appendLine()
        appendLine("    @PostMapping(\"/plan\")")
        appendLine("    fun executePlan(@RequestBody request: PlanRequest): PlanResponse {")
        appendLine("        // TODO: Integrate with planner agent bean")
        appendLine("        return PlanResponse(status = \"planned\", steps = emptyList())")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("data class PlanRequest(val goalName: String, val state: Map<String, Any> = emptyMap())")
        appendLine("data class PlanResponse(val status: String, val steps: List<String>)")
    }
}
