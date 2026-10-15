package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogTool
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import org.springframework.stereotype.Component

@Component
class RegistryGenerator {
    fun generate(
        tools: List<KoogTool>,
        builtinTools: List<KoogBuiltinTool> = emptyList(),
        subAgents: List<KoogSubAgent> = emptyList(),
        mcpServers: List<KoogMcpServer> = emptyList()
    ): String {
        val hasUserPrompt = builtinTools.any { it.type == BuiltinToolType.USER_PROMPT }
        val hasFilesystem = builtinTools.any { it.type == BuiltinToolType.FILESYSTEM }
        val customBuiltins = builtinTools.filter {
            it.type in setOf(BuiltinToolType.SHELL, BuiltinToolType.FETCH, BuiltinToolType.MEMORY, BuiltinToolType.TODO)
        }
        val hasBaseTools = tools.isNotEmpty() || customBuiltins.isNotEmpty() || hasUserPrompt || hasFilesystem || subAgents.isNotEmpty()
        val hasMcp = mcpServers.isNotEmpty()

        // Koog type references
        val toolRegistryType = KoogTypes.TOOL_REGISTRY
        val askUserType = ClassName("ai.koog.agents.ext.tool", "AskUser")
        val sayToUserType = ClassName("ai.koog.agents.ext.tool", "SayToUser")
        val readFileToolType = ClassName("ai.koog.agents.ext.tool.file", "ReadFileTool")
        val writeFileToolType = ClassName("ai.koog.agents.ext.tool.file", "WriteFileTool")
        val listDirectoryToolType = ClassName("ai.koog.agents.ext.tool.file", "ListDirectoryTool")
        val jvmFileSystemProviderType = ClassName("ai.koog.rag.base.files", "JVMFileSystemProvider")
        val aiAgentServiceType = ClassName("ai.koog.agents.core.agent", "AIAgentService")
        val graphAIAgentType = ClassName("ai.koog.agents.core.agent", "GraphAIAgent")

        // Build the function body as a CodeBlock
        val bodyBuilder = CodeBlock.builder()

        // Custom tool instances
        if (tools.isNotEmpty()) {
            for (tool in tools) {
                val varName = tool.name.replaceFirstChar { it.lowercase() }
                bodyBuilder.addStatement("val %LTools = %L()", varName, tool.name)
            }
            bodyBuilder.add("\n")
        }

        if (hasMcp && hasBaseTools) {
            bodyBuilder.beginControlFlow("val baseRegistry = %T", toolRegistryType)
        } else if (!hasMcp) {
            bodyBuilder.beginControlFlow("return %T", toolRegistryType)
        }
        // if hasMcp && !hasBaseTools: no ToolRegistry block

        if (hasBaseTools) {
            // Custom tools
            for (tool in tools) {
                val varName = tool.name.replaceFirstChar { it.lowercase() }
                bodyBuilder.addStatement("tools(%LTools)", varName)
            }

            // Built-in custom ToolSets
            for (bt in customBuiltins) {
                when (bt.type) {
                    BuiltinToolType.SHELL -> {
                        val wd = bt.config["workingDir"] as? String ?: "."
                        bodyBuilder.addStatement("tools(ShellToolSet(workingDir = %S))", wd)
                    }
                    BuiltinToolType.FETCH -> {
                        val bu = bt.config["baseUrl"] as? String ?: ""
                        bodyBuilder.addStatement("tools(FetchToolSet(baseUrl = %S))", bu)
                    }
                    BuiltinToolType.MEMORY -> {
                        val p = bt.config["path"] as? String ?: "memory.db"
                        bodyBuilder.addStatement("tools(MemoryToolSet(path = %S))", p)
                    }
                    BuiltinToolType.TODO -> bodyBuilder.addStatement("tools(TodoToolSet())")
                    else -> { /* skip */ }
                }
            }

            // Koog framework built-ins
            if (hasUserPrompt) {
                bodyBuilder.addStatement("tool(%T)", askUserType)
                bodyBuilder.addStatement("tool(%T)", sayToUserType)
            }
            if (hasFilesystem) {
                bodyBuilder.addStatement("tool(%T(%T.ReadWrite))", readFileToolType, jvmFileSystemProviderType)
                bodyBuilder.addStatement("tool(%T(%T.ReadWrite))", writeFileToolType, jvmFileSystemProviderType)
                bodyBuilder.addStatement("tool(%T(%T.ReadWrite))", listDirectoryToolType, jvmFileSystemProviderType)
            }

            // Sub-agents as tools
            for (sa in subAgents) {
                val funcName = "create${sa.name.replaceFirstChar { it.uppercase() }}"
                val hasInputs = sa.inputs.isNotEmpty()
                val hasOutputs = sa.outputs.isNotEmpty()
                val iType = if (hasInputs) "${sa.name.replaceFirstChar { it.uppercase() }}Input" else "String"
                val oType = if (hasOutputs) "${sa.name.replaceFirstChar { it.uppercase() }}Output" else "String"
                val iClass = if (hasInputs) ClassName("generated.subagents", iType) else ClassName("kotlin", "String")
                val oClass = if (hasOutputs) ClassName("generated.subagents", oType) else ClassName("kotlin", "String")
                bodyBuilder.add("tool(\n")
                bodyBuilder.add(
                    "    %T.fromAgent(%L() as %T<%T, %T>).createAgentTool<%T, %T>(\n",
                    aiAgentServiceType, funcName, graphAIAgentType, iClass, oClass, iClass, oClass
                )
                bodyBuilder.add("        agentName = %S,\n", sa.name)
                bodyBuilder.add("        agentDescription = %S,\n", sa.description)
                bodyBuilder.add("        inputDescription = %S\n", sa.inputDescription)
                bodyBuilder.add("    )\n")
                bodyBuilder.add(")\n")
            }

            bodyBuilder.endControlFlow()
        }

        if (hasMcp) {
            // Generate MCP registry variables
            val mcpVarNames = mcpServers.map { server ->
                val varName = server.name.replace(" ", "").replaceFirstChar { it.lowercase() } + "Registry"
                val funcName = "create${server.name.replace(" ", "")}Registry"
                bodyBuilder.addStatement("val %L = %L()", varName, funcName)
                varName
            }

            val parts = if (hasBaseTools) listOf("baseRegistry") + mcpVarNames else mcpVarNames
            bodyBuilder.addStatement("return %L", parts.joinToString(" + "))
        }

        // Build the FunSpec
        val funBuilder = FunSpec.builder("createToolRegistry")
            .returns(toolRegistryType)
            .addCode(bodyBuilder.build())

        if (mcpServers.isNotEmpty()) {
            funBuilder.addModifiers(KModifier.SUSPEND)
        }

        // Note: @OptIn annotation is injected as raw text in post-processing to preserve
        // the fully-qualified form @OptIn(kotlin.time.ExperimentalTime::class)

        // Build the FileSpec
        val fileBuilder = FileSpec.builder("generated", "ToolRegistry")
            .addFunction(funBuilder.build())

        // tools(ToolSet) is a ToolRegistryBuilder member since Koog 1.x, so no extra import is needed

        // Add specific subagent IO imports
        for (sa in subAgents) {
            if (sa.inputs.isNotEmpty()) {
                fileBuilder.addImport("generated.subagents", "${sa.name.replaceFirstChar { it.uppercase() }}Input")
            }
            if (sa.outputs.isNotEmpty()) {
                fileBuilder.addImport("generated.subagents", "${sa.name.replaceFirstChar { it.uppercase() }}Output")
            }
        }

        // Add createAgentTool extension import if needed
        if (subAgents.isNotEmpty()) {
            fileBuilder.addImport("ai.koog.agents.core.agent", "createAgentTool")
        }

        // Generate the KotlinPoet output
        val poetOutput = fileBuilder.build().toString()

        // Post-process: inject wildcard imports that KotlinPoet cannot express
        val wildcardImports = buildList {
            if (tools.isNotEmpty() || customBuiltins.isNotEmpty()) {
                add("import generated.tools.*")
            }
            if (subAgents.isNotEmpty()) {
                add("import generated.subagents.*")
            }
            if (mcpServers.isNotEmpty()) {
                add("import generated.mcp.*")
            }
        }

        // Post-process the KotlinPoet output:
        // 1. Inject wildcard imports right after the package line
        // 2. Inject @OptIn annotation before the function (if subagents present)
        // 3. Remove KotlinPoet's auto-import for ExperimentalTime (we use FQN in annotation)
        var result = poetOutput

        // Remove ExperimentalTime import since we use fully-qualified form in annotation
        result = result.lines()
            .filter { it.trim() != "import kotlin.time.ExperimentalTime" }
            .joinToString("\n")

        // Insert wildcard imports after the package declaration line
        if (wildcardImports.isNotEmpty()) {
            val lines = result.lines().toMutableList()
            val packageLineIndex = lines.indexOfFirst { it.startsWith("package ") }
            val insertAt = if (packageLineIndex >= 0) packageLineIndex + 1 else 0
            lines.addAll(insertAt, wildcardImports)
            result = lines.joinToString("\n")
        }

        // Inject @OptIn(kotlin.time.ExperimentalTime::class) before the function declaration
        if (subAgents.isNotEmpty()) {
            val optInLine = "@OptIn(kotlin.time.ExperimentalTime::class)"
            val funPattern = Regex("""((?:suspend )?(?:public )?fun createToolRegistry)""")
            result = funPattern.replaceFirst(result, "$optInLine\n$1")
        }

        return result
    }
}
