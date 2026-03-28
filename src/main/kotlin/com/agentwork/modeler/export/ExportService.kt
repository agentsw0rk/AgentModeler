package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.generator.A2AServerGenerator
import com.agentwork.modeler.export.generator.A2AClientGenerator
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import com.agentwork.modeler.export.model.KoogPlannerAgent
import com.agentwork.modeler.export.model.WorkspaceGraph
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
    private val builtinToolGenerator: BuiltinToolGenerator,
    private val subAgentGenerator: SubAgentGenerator,
    private val mcpServerGenerator: McpServerGenerator,
    private val plannerAgentGenerator: PlannerAgentGenerator,
    private val a2aServerGenerator: A2AServerGenerator,
    private val a2aClientGenerator: A2AClientGenerator,
    private val inputOutputGenerator: InputOutputGenerator,
    private val graphAgentGenerator: GraphAgentGenerator,
    private val zipBuilder: ZipBuilder
) {
    fun export(graph: WorkspaceGraph): ByteArray {
        val files = generateFiles(graph)
        return zipBuilder.buildZip(files)
    }

    fun exportWithFiles(graph: WorkspaceGraph): Pair<ByteArray, Map<String, String>> {
        val files = generateFiles(graph)
        val zip = zipBuilder.buildZip(files)
        return Pair(zip, files)
    }

    fun generateFiles(graph: WorkspaceGraph): Map<String, String> {
        val validationErrors = validator.validate(graph)
        if (validationErrors.isNotEmpty()) {
            throw ExportValidationException(validationErrors)
        }

        val agents = adapter.convert(graph)
        val plannerAgents = adapter.convertPlannerAgents(graph)
        val pkg = "src/main/kotlin/generated"
        val files = mutableMapOf<String, String>()

        if (plannerAgents.isNotEmpty()) {
            val planner = plannerAgents.first()
            val agentForInfra = agents.firstOrNull()

            // Infrastructure (still generated for planner agents)
            files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
            files["gradle.properties"] = gradlePropertiesGenerator.generate()
            files["Dockerfile"] = dockerfileGenerator.generate()
            if (agentForInfra != null) {
                files["build.gradle.kts"] = buildGradleGenerator.generate(agentForInfra)
                files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(agentForInfra)
            } else {
                files["build.gradle.kts"] = generatePlannerBuildGradle()
                files["src/main/resources/application.properties"] = generatePlannerApplicationProperties(planner)
            }

            // Kotlin sources
            files["$pkg/Application.kt"] = applicationGenerator.generate()

            // Planner-specific files
            val plannerFiles = plannerAgentGenerator.generate(planner)
            files["$pkg/planner/PlannerGoals.kt"] = plannerFiles["PlannerGoals.kt"] ?: ""
            files["$pkg/planner/PlannerActions.kt"] = plannerFiles["PlannerActions.kt"] ?: ""
            files["$pkg/PlannerAgent.kt"] = plannerFiles["PlannerAgent.kt"] ?: ""

            // PlanController instead of ChatController
            files["$pkg/PlanController.kt"] = generatePlanController()

            // Tools connected to planner
            for (tool in planner.tools) {
                val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
            }

            // MCP servers connected to planner
            for (mcpServer in planner.mcpServers) {
                val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
                files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
            }

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

            // Input/Output classes
            val mergedIn = mergedInput(planner.inputs)
            val inputCode = inputOutputGenerator.generateInputClass(planner.name, mergedIn)
            if (inputCode != null) {
                val inName = mergedIn.className?.ifBlank { null } ?: "${planner.name.replaceFirstChar { it.uppercase() }}Input"
                files["$pkg/$inName.kt"] = inputCode
            }
            val mergedOut = mergedOutput(planner.outputs)
            val outputCode = inputOutputGenerator.generateOutputClass(planner.name, mergedOut)
            if (outputCode != null) {
                val outName = mergedOut.className?.ifBlank { null } ?: "${planner.name.replaceFirstChar { it.uppercase() }}Output"
                files["$pkg/$outName.kt"] = outputCode
            }

            if (planner.tools.isNotEmpty() || planner.builtinTools.isNotEmpty() || planner.mcpServers.isNotEmpty()) {
                files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(planner.tools, planner.builtinTools, emptyList(), planner.mcpServers)
            }

            return files
        }

        val graphAgents = adapter.convertGraphAgents(graph)
        if (graphAgents.isNotEmpty()) {
            val ga = graphAgents.first()

            files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
            files["gradle.properties"] = gradlePropertiesGenerator.generate()
            files["Dockerfile"] = dockerfileGenerator.generate()

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

            files["$pkg/Application.kt"] = applicationGenerator.generate()
            val (gaInputClassName, gaOutputClassName) = ioClassNames(ga.name, ga.inputs, ga.outputs)
            files["$pkg/AgentConfig.kt"] = agentGenerator.generate(infraAgent, "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()", inputClassName = gaInputClassName, outputClassName = gaOutputClassName)
            files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = gaInputClassName, outputClassName = gaOutputClassName)

            files["$pkg/${ga.name.replaceFirstChar { it.uppercase() }}Strategy.kt"] = graphAgentGenerator.generate(ga)

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

            val gaIn = mergedInput(ga.inputs)
            val gaInputCode = inputOutputGenerator.generateInputClass(ga.name, gaIn)
            if (gaInputCode != null) {
                val inName = gaIn.className?.ifBlank { null } ?: "${ga.name.replaceFirstChar { it.uppercase() }}Input"
                files["$pkg/$inName.kt"] = gaInputCode
            }
            val gaOut = mergedOutput(ga.outputs)
            val gaOutputCode = inputOutputGenerator.generateOutputClass(ga.name, gaOut)
            if (gaOutputCode != null) {
                val outName = gaOut.className?.ifBlank { null } ?: "${ga.name.replaceFirstChar { it.uppercase() }}Output"
                files["$pkg/$outName.kt"] = gaOutputCode
            }

            if (ga.tools.isNotEmpty() || ga.builtinTools.isNotEmpty() || ga.subAgents.isNotEmpty() || ga.mcpServers.isNotEmpty()) {
                files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(ga.tools, ga.builtinTools, ga.subAgents, ga.mcpServers)
            }

            return files
        }

        val agent = agents.first()

        // Infrastructure
        files["build.gradle.kts"] = buildGradleGenerator.generate(agent)
        files["settings.gradle.kts"] = settingsGradleGenerator.generate(graph.name)
        files["gradle.properties"] = gradlePropertiesGenerator.generate()
        files["Dockerfile"] = dockerfileGenerator.generate()
        files["src/main/resources/application.properties"] = applicationPropertiesGenerator.generate(agent)

        // Kotlin sources
        files["$pkg/Application.kt"] = applicationGenerator.generate()
        val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
        files["$pkg/AgentConfig.kt"] = agentGenerator.generate(agent, inputClassName = inputClassName, outputClassName = outputClassName)
        files["$pkg/ChatController.kt"] = controllerGenerator.generate(inputClassName = inputClassName, outputClassName = outputClassName)

        // Tools
        for (tool in agent.tools) {
            val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
            files["$pkg/tools/${className}.kt"] = toolGenerator.generate(tool)
        }

        // Built-in tools
        for (builtinTool in agent.builtinTools) {
            val code = builtinToolGenerator.generate(builtinTool)
            val fileName = builtinTool.type.toFileName()
            if (code != null && fileName != null) {
                files["$pkg/tools/$fileName"] = code
            }
        }

        // Sub-agents
        for (subAgent in agent.subAgents) {
            val saFileName = "${subAgent.name.replaceFirstChar { it.uppercase() }}SubAgent"
            files["$pkg/subagents/$saFileName.kt"] = subAgentGenerator.generate(subAgent)

            val registryCode = subAgentGenerator.generateRegistry(subAgent)
            if (registryCode != null) {
                val regFileName = "${subAgent.name.replaceFirstChar { it.uppercase() }}ToolRegistry"
                files["$pkg/subagents/$regFileName.kt"] = registryCode
            }

            // Generate ToolSet files for sub-agent's custom tools
            for (tool in subAgent.tools) {
                val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                if (!files.containsKey("$pkg/tools/$className.kt")) {
                    files["$pkg/tools/$className.kt"] = toolGenerator.generate(tool)
                }
            }

            // Generate ToolSet files for sub-agent's built-in tools
            for (builtinTool in subAgent.builtinTools) {
                val code = builtinToolGenerator.generate(builtinTool)
                val fileName = builtinTool.type.toFileName()
                if (code != null && fileName != null && !files.containsKey("$pkg/tools/$fileName")) {
                    files["$pkg/tools/$fileName"] = code
                }
            }

            // Input/Output classes for sub-agent
            val saIn = mergedInput(subAgent.inputs)
            val saInputCode = inputOutputGenerator.generateInputClass(subAgent.name, saIn)
            if (saInputCode != null) {
                val saInName = saIn.className?.ifBlank { null } ?: "${subAgent.name.replaceFirstChar { it.uppercase() }}Input"
                files["$pkg/subagents/$saInName.kt"] = saInputCode
            }
            val saOut = mergedOutput(subAgent.outputs)
            val saOutputCode = inputOutputGenerator.generateOutputClass(subAgent.name, saOut)
            if (saOutputCode != null) {
                val saOutName = saOut.className?.ifBlank { null } ?: "${subAgent.name.replaceFirstChar { it.uppercase() }}Output"
                files["$pkg/subagents/$saOutName.kt"] = saOutputCode
            }
        }

        // MCP servers
        for (mcpServer in agent.mcpServers) {
            val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
            files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
        }

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

        // Input/Output classes
        val mergedIn = mergedInput(agent.inputs)
        val inputCode = inputOutputGenerator.generateInputClass(agent.name, mergedIn)
        if (inputCode != null) {
            val inName = mergedIn.className?.ifBlank { null } ?: "${agent.name.replaceFirstChar { it.uppercase() }}Input"
            files["$pkg/$inName.kt"] = inputCode
        }
        val mergedOut = mergedOutput(agent.outputs)
        val outputCode = inputOutputGenerator.generateOutputClass(agent.name, mergedOut)
        if (outputCode != null) {
            val outName = mergedOut.className?.ifBlank { null } ?: "${agent.name.replaceFirstChar { it.uppercase() }}Output"
            files["$pkg/$outName.kt"] = outputCode
        }

        if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
            files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents, agent.mcpServers)
        }

        return files
    }

    private fun mergedInput(inputs: List<KoogInput>): KoogInput {
        val className = inputs.firstNotNullOfOrNull { it.className?.ifBlank { null } }
        val fields = inputs.flatMap { it.fields }
        return KoogInput(className = className, fields = fields)
    }

    private fun mergedOutput(outputs: List<KoogOutput>): KoogOutput {
        val className = outputs.firstNotNullOfOrNull { it.className?.ifBlank { null } }
        val fields = outputs.flatMap { it.fields }
        return KoogOutput(className = className, fields = fields)
    }

    private fun ioClassNames(name: String, inputs: List<KoogInput>, outputs: List<KoogOutput>): Pair<String?, String?> {
        val mergedIn = mergedInput(inputs)
        val inputClassName = if (mergedIn.fields.isNotEmpty()) {
            mergedIn.className?.ifBlank { null } ?: "${name.replaceFirstChar { c -> c.uppercase() }}Input"
        } else null
        val mergedOut = mergedOutput(outputs)
        val outputClassName = if (mergedOut.fields.isNotEmpty()) {
            mergedOut.className?.ifBlank { null } ?: "${name.replaceFirstChar { c -> c.uppercase() }}Output"
        } else null
        return Pair(inputClassName, outputClassName)
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
