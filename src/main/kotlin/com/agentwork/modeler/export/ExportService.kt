package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.AgentGenerator
import com.agentwork.modeler.export.generator.MainGenerator
import com.agentwork.modeler.export.generator.RegistryGenerator
import com.agentwork.modeler.export.generator.ToolGenerator
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
    private val mainGenerator: MainGenerator,
    private val zipBuilder: ZipBuilder
) {
    fun export(graph: ProjectGraph): ByteArray {
        val validationErrors = validator.validate(graph)
        if (validationErrors.isNotEmpty()) {
            throw ExportValidationException(validationErrors)
        }

        val agents = adapter.convert(graph)
        val files = mutableMapOf<String, String>()

        for (agent in agents) {
            files["src/${agent.name}.kt"] = agentGenerator.generate(agent)
            for (tool in agent.tools) {
                val className = tool.name.replaceFirstChar { it.uppercase() } + "ToolSet"
                files["src/tools/${className}.kt"] = toolGenerator.generate(tool)
            }
            if (agent.tools.isNotEmpty()) {
                files["src/ToolRegistry.kt"] = registryGenerator.generate(agent.tools)
            }
            files["src/Main.kt"] = mainGenerator.generate(agent)
        }

        return zipBuilder.buildZip(files)
    }
}
