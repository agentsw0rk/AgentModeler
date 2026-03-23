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
    private val builtinToolGenerator: BuiltinToolGenerator,
    private val subAgentGenerator: SubAgentGenerator,
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
        }

        if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty()) {
            files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents)
        }

        return zipBuilder.buildZip(files)
    }
}
