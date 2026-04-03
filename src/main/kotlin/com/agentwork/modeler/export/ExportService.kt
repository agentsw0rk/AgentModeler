package com.agentwork.modeler.export

import com.agentwork.modeler.export.adapter.GraphToKoogAdapter
import com.agentwork.modeler.export.generator.*
import com.agentwork.modeler.export.generator.A2AClientGenerator
import com.agentwork.modeler.export.generator.SharedCodeGenerator
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogInput
import com.agentwork.modeler.export.model.KoogOutput
import com.agentwork.modeler.export.model.KoogPlannerAgent
import com.agentwork.modeler.export.model.KoogSharedCode
import com.agentwork.modeler.export.model.WorkspaceGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.export.zip.ZipBuilder
import org.springframework.stereotype.Service

class ExportValidationException(val errors: List<String>) : RuntimeException("Validation failed: ${errors.joinToString("; ")}")

@Service
class ExportService(
    private val validator: GraphValidator,
    private val adapter: GraphToKoogAdapter,
    private val toolGenerator: ToolGenerator,
    private val registryGenerator: RegistryGenerator,
    private val builtinToolGenerator: BuiltinToolGenerator,
    private val subAgentGenerator: SubAgentGenerator,
    private val mcpServerGenerator: McpServerGenerator,
    private val a2aClientGenerator: A2AClientGenerator,
    private val inputOutputGenerator: InputOutputGenerator,
    private val graphAgentGenerator: GraphAgentGenerator,
    private val ragCodeGenerator: RagCodeGenerator,
    private val sharedCodeGenerator: SharedCodeGenerator = SharedCodeGenerator(),
    private val zipBuilder: ZipBuilder,
    private val backends: List<GeneratorBackend>
) {
    fun export(graph: WorkspaceGraph, backendId: String = "spring-boot"): ByteArray {
        val files = generateFiles(graph, backendId)
        return zipBuilder.buildZip(files)
    }

    fun exportWithFiles(graph: WorkspaceGraph, backendId: String = "spring-boot"): Pair<ByteArray, Map<String, String>> {
        val files = generateFiles(graph, backendId)
        val zip = zipBuilder.buildZip(files)
        return Pair(zip, files)
    }

    fun generateFiles(graph: WorkspaceGraph, backendId: String = "spring-boot"): Map<String, String> {
        val validationErrors = validator.validate(graph)
        if (validationErrors.isNotEmpty()) {
            throw ExportValidationException(validationErrors)
        }

        val backend = backends.first { it.id == backendId }
        val agents = adapter.convert(graph)
        val plannerAgents = adapter.convertPlannerAgents(graph)
        val graphAgents = adapter.convertGraphAgents(graph)
        val sharedCodes = adapter.convertSharedCode(graph)
        val pkg = "src/main/kotlin/generated"

        if (plannerAgents.isNotEmpty()) {
            val planner = plannerAgents.first()
            val sharedFiles = generatePlannerSharedFiles(planner, sharedCodes, pkg)
            val infraFiles = backend.generatePlannerAgentFiles(planner, graph.name, sharedFiles)
            return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
        }

        if (graphAgents.isNotEmpty()) {
            val ga = graphAgents.first()
            val (inputClassName, outputClassName) = ioClassNames(ga.name, ga.inputs, ga.outputs)
            val strategyCall = "create${ga.name.replaceFirstChar { it.uppercase() }}Strategy()"
            val sharedFiles = generateGraphAgentSharedFiles(ga, sharedCodes, pkg)
            val infraFiles = backend.generateGraphAgentFiles(ga, graph.name, sharedFiles, strategyCall, inputClassName, outputClassName)
            return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
        }

        val agent = agents.first()
        val (inputClassName, outputClassName) = ioClassNames(agent.name, agent.inputs, agent.outputs)
        val sharedFiles = generateAgentSharedFiles(agent, sharedCodes, pkg)
        val infraFiles = backend.generateAgentFiles(agent, graph.name, sharedFiles, inputClassName, outputClassName)
        return if (backend.remapsSharedFiles) infraFiles else sharedFiles + infraFiles
    }

    fun generateFilesWithMapping(graph: WorkspaceGraph, backendId: String = "spring-boot"): Pair<Map<String, String>, Map<String, String>> {
        val files = generateFiles(graph, backendId)
        val fileToNodeId = mutableMapOf<String, String>()

        // Register mappings for both JVM and WasmJS source paths
        val prefixes = listOf("src/main/kotlin/generated", "src/wasmJsMain/kotlin/generated")

        for (node in graph.nodes) {
            when (node.type) {
                "tool" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    for (p in prefixes) {
                        fileToNodeId["$p/tools/${name}Tools.kt"] = node.id
                        fileToNodeId["$p/${name}Tools.kt"] = node.id
                    }
                }
                "code" -> {
                    val fileName = node.data["fileName"] as? String ?: continue
                    for (p in prefixes) fileToNodeId["$p/${fileName}.kt"] = node.id
                }
                "agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    for (p in prefixes) fileToNodeId["$p/${name}Agent.kt"] = node.id
                }
                "graph-agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    for (p in prefixes) {
                        fileToNodeId["$p/${name}Strategy.kt"] = node.id
                        fileToNodeId["$p/${name}Agent.kt"] = node.id
                    }
                }
                "sub-agent" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    for (p in prefixes) fileToNodeId["$p/${name}SubAgent.kt"] = node.id
                }
                "mcp-server" -> {
                    val name = (node.data["name"] as? String)?.replaceFirstChar { it.uppercase() } ?: continue
                    for (p in prefixes) fileToNodeId["$p/${name}McpConfig.kt"] = node.id
                }
            }
        }

        return Pair(files, fileToNodeId)
    }

    private fun generateAgentSharedFiles(agent: KoogAgent, sharedCodes: List<KoogSharedCode>, pkg: String): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Tools
        for (tool in agent.tools) {
            files["$pkg/tools/${tool.name}.kt"] = toolGenerator.generate(tool)
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
                if (!files.containsKey("$pkg/tools/${tool.name}.kt")) {
                    files["$pkg/tools/${tool.name}.kt"] = toolGenerator.generate(tool)
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

        // A2A Clients
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

        // Tool registry
        if (agent.tools.isNotEmpty() || agent.builtinTools.isNotEmpty() || agent.subAgents.isNotEmpty() || agent.mcpServers.isNotEmpty()) {
            files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(agent.tools, agent.builtinTools, agent.subAgents, agent.mcpServers)
        }

        // Shared code
        for (code in sharedCodes) {
            if (code.name.isNotBlank()) {
                files["src/main/kotlin/generated/${code.name}.kt"] = sharedCodeGenerator.generate(code)
            }
        }

        return files
    }

    private fun generateGraphAgentSharedFiles(ga: KoogGraphAgent, sharedCodes: List<KoogSharedCode>, pkg: String): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Graph agent strategy
        files["$pkg/${ga.name.replaceFirstChar { it.uppercase() }}Strategy.kt"] = graphAgentGenerator.generate(ga)

        // Tools
        for (tool in ga.tools) {
            files["$pkg/tools/${tool.name}.kt"] = toolGenerator.generate(tool)
        }

        // Built-in tools
        for (builtinTool in ga.builtinTools) {
            val code = builtinToolGenerator.generate(builtinTool)
            val fileName = builtinTool.type.toFileName()
            if (code != null && fileName != null) files["$pkg/tools/$fileName"] = code
        }

        // MCP servers
        for (mcpServer in ga.mcpServers) {
            val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
            files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
        }

        // Input/Output classes
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

        // RAG files
        for (rag in ga.ragNodes) {
            val className = rag.name.sanitizeClassName() + "Rag"
            files["$pkg/rag/$className.kt"] = ragCodeGenerator.generateRagFile(rag)
        }

        // Tool registry (include RAG search tools)
        val hasRagTools = ga.ragNodes.any { it.provideAsTool }
        if (ga.tools.isNotEmpty() || ga.builtinTools.isNotEmpty() || ga.subAgents.isNotEmpty() || ga.mcpServers.isNotEmpty() || hasRagTools) {
            val ragToolRegistrations = ga.ragNodes.filter { it.provideAsTool }.map { rag ->
                val safeName = rag.name.sanitizeClassName()
                "    tools(${safeName}SearchTools())"
            }
            val baseRegistry = registryGenerator.generate(ga.tools, ga.builtinTools, ga.subAgents, ga.mcpServers)
            if (ragToolRegistrations.isNotEmpty()) {
                // Insert RAG tool registrations into the ToolRegistry block
                val ragImport = "import generated.rag.*"
                val registryWithRag = baseRegistry
                    .replace("import ai.koog.agents.core.tools.ToolRegistry", "import ai.koog.agents.core.tools.ToolRegistry\n$ragImport")
                    .replace("  }\n}", ragToolRegistrations.joinToString("\n") + "\n  }\n}")
                files["$pkg/ToolRegistry.kt"] = registryWithRag
            } else {
                files["$pkg/ToolRegistry.kt"] = baseRegistry
            }
        }

        // Shared code
        for (code in sharedCodes) {
            if (code.name.isNotBlank()) {
                files["src/main/kotlin/generated/${code.name}.kt"] = sharedCodeGenerator.generate(code)
            }
        }

        return files
    }

    private fun generatePlannerSharedFiles(planner: KoogPlannerAgent, sharedCodes: List<KoogSharedCode>, pkg: String): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Tools
        for (tool in planner.tools) {
            files["$pkg/tools/${tool.name}.kt"] = toolGenerator.generate(tool)
        }

        // MCP servers
        for (mcpServer in planner.mcpServers) {
            val fileName = "${mcpServer.name.replace(" ", "")}Mcp"
            files["$pkg/mcp/$fileName.kt"] = mcpServerGenerator.generate(mcpServer)
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

        // Tool registry
        if (planner.tools.isNotEmpty() || planner.builtinTools.isNotEmpty() || planner.mcpServers.isNotEmpty()) {
            files["$pkg/ToolRegistry.kt"] = registryGenerator.generate(planner.tools, planner.builtinTools, emptyList(), planner.mcpServers)
        }

        // Shared code
        for (code in sharedCodes) {
            if (code.name.isNotBlank()) {
                files["src/main/kotlin/generated/${code.name}.kt"] = sharedCodeGenerator.generate(code)
            }
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
}
