package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogStrategyEdge
import com.agentwork.modeler.export.model.KoogStrategyNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import org.springframework.stereotype.Component

@Component
class GraphAgentGenerator {

    private val toolSelectionStrategyClass = ClassName("ai.koog.agents.core.agent.entity", "ToolSelectionStrategy")
    private val toolDescriptorClass = ClassName("ai.koog.agents.core.tools", "ToolDescriptor")

    fun generate(agent: KoogGraphAgent): String {
        val funcName = "create${agent.name.replaceFirstChar { it.uppercase() }}Strategy"
        val nodeMap = agent.strategyNodes.associateBy { it.id }
        val toolByName = agent.tools.associateBy { it.name }

        // Collect which edge types and node types are used for imports
        val innerEdgeTypes = agent.strategyNodes
            .filter { it.type == "node-subgraph" }
            .flatMap { it.subgraphEdges?.map { se -> se.edgeType } ?: emptyList() }
        val edgeTypes = (agent.strategyEdges.map { it.edgeType } + innerEdgeTypes).toSet()
        val innerNodeTypes = agent.strategyNodes
            .filter { it.type == "node-subgraph" }
            .flatMap { it.subgraphNodes?.map { sn -> sn.type } ?: emptyList() }
        val nodeTypes = (agent.strategyNodes.map { it.type } + innerNodeTypes).toSet()

        val usesToolSelectionStrategy = agent.strategyNodes.any {
            it.type == "node-subgraph" && it.subgraphToolSelection in listOf("NONE", "SPECIFIC")
        }

        // Build the strategy body CodeBlock
        val strategyBody = CodeBlock.builder()
        strategyBody.beginControlFlow("return strategy<String, String>(%S)", agent.name)

        for (sn in agent.strategyNodes) {
            emitNodeDeclaration(strategyBody, sn, toolByName, nodeMap, agent)
        }

        strategyBody.add("\n")

        for (se in agent.strategyEdges) {
            val sourceRef = resolveNodeRef(se.source, nodeMap)
            val targetRef = resolveNodeRef(se.target, nodeMap)
            val edgeSuffix = formatEdgeSuffix(se)
            strategyBody.addStatement("edge($sourceRef forwardTo $targetRef$edgeSuffix)")
        }

        strategyBody.endControlFlow()

        val strategyReturnType = ClassName("ai.koog.agents.core.agent.entity", "AIAgentGraphStrategy")
        val strategyFun = FunSpec.builder(funcName)
            .returns(strategyReturnType)
            .addCode(strategyBody.build())
            .build()

        val fileSpec = FileSpec.builder("generated", "${agent.name}Strategy")
            .addImport("ai.koog.agents.core.dsl.builder", "strategy")
            .addImport("ai.koog.agents.core.dsl.builder", "forwardTo")

        // Conditional node-type imports
        if ("node-llm-request" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMRequest")
        if ("node-execute-tool" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeExecuteTool")
        if ("node-llm-send-tool-result" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMSendToolResult")
        if ("node-execute-multiple-tools" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeExecuteMultipleTools")
        if ("node-send-multiple-results" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMSendMultipleToolResults")
        if ("node-compress-history" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMCompressHistory")
        if ("node-llm-streaming" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMRequestStreaming")
        if ("node-llm-structured" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeLLMRequestStructured")
        if ("node-do-nothing" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "nodeDoNothing")
        if ("node-custom" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.builder", "node")
        if ("node-subgraph" in nodeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.builder", "subgraph")

        // Conditional edge-type imports
        if ("onAssistantMessage" in edgeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "onAssistantMessage")
        if ("onToolCall" in edgeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "onToolCall")
        if ("onMultipleToolCalls" in edgeTypes)
            fileSpec.addImport("ai.koog.agents.core.dsl.extension", "onMultipleToolCalls")
        // onCondition is a member function, no import needed

        if (usesToolSelectionStrategy) {
            fileSpec.addImport("ai.koog.agents.core.agent.entity", "ToolSelectionStrategy")
            fileSpec.addImport("ai.koog.agents.core.tools", "ToolDescriptor")
        }

        fileSpec.addFunction(strategyFun)

        return fileSpec.build().toString()
    }

    private fun emitNodeDeclaration(
        builder: CodeBlock.Builder,
        sn: KoogStrategyNode,
        toolByName: Map<String, com.agentwork.modeler.export.model.KoogTool>,
        nodeMap: Map<String, KoogStrategyNode>,
        agent: KoogGraphAgent
    ) {
        val varName = (sn.label ?: sn.id).sanitizeIdentifier()
        when (sn.type) {
            "node-start", "node-finish" -> {}
            "node-llm-request" -> builder.addStatement("val $varName by nodeLLMRequest()")
            "node-execute-tool" -> builder.addStatement("val $varName by nodeExecuteTool()")
            "node-llm-send-tool-result" -> builder.addStatement("val $varName by nodeLLMSendToolResult()")
            "node-condition" -> builder.addStatement("val $varName by nodeCondition(%S)", sn.condition ?: "")
            "node-execute-multiple-tools" -> builder.addStatement("val $varName by nodeExecuteMultipleTools()")
            "node-send-multiple-results" -> builder.addStatement("val $varName by nodeLLMSendMultipleToolResults()")
            "node-compress-history" -> {
                val strategy = sn.compressionStrategy
                if (strategy != null && strategy.isNotBlank()) {
                    builder.addStatement("val $varName by nodeLLMCompressHistory(%S)", strategy)
                } else {
                    builder.addStatement("val $varName by nodeLLMCompressHistory()")
                }
            }
            "node-llm-streaming" -> builder.addStatement("val $varName by nodeLLMRequestStreaming()")
            "node-llm-structured" -> {
                val schema = sn.outputSchema
                if (schema != null && schema.isNotBlank()) {
                    builder.addStatement("val $varName by nodeLLMRequestStructured<$schema>()")
                } else {
                    builder.addStatement("val $varName by nodeLLMRequestStructured<String>()")
                }
            }
            "node-do-nothing" -> builder.addStatement("val $varName by nodeDoNothing()")
            "node-custom" -> {
                val inputT = sn.inputType?.ifBlank { null } ?: "String"
                val outputT = sn.outputType?.ifBlank { null } ?: "String"
                val body = sn.body ?: ""
                builder.beginControlFlow("val $varName by node<$inputT, $outputT>")
                builder.addStatement("input -> $body")
                builder.endControlFlow()
            }
            "node-subgraph" -> {
                emitSubgraph(builder, sn, toolByName, nodeMap, agent)
            }
        }
    }

    private fun emitSubgraph(
        builder: CodeBlock.Builder,
        sn: KoogStrategyNode,
        toolByName: Map<String, com.agentwork.modeler.export.model.KoogTool>,
        parentNodeMap: Map<String, KoogStrategyNode>,
        agent: KoogGraphAgent
    ) {
        val varName = (sn.label ?: sn.id).sanitizeIdentifier()
        val sgName = sn.subgraphName?.ifBlank { null } ?: "subgraph"
        val inputT = sn.subgraphInputType?.ifBlank { null } ?: "String"
        val outputT = sn.subgraphOutputType?.ifBlank { null } ?: "String"

        val toolStrategy = when (sn.subgraphToolSelection) {
            "NONE" -> ",\n    toolSelectionStrategy = ToolSelectionStrategy.NONE"
            "SPECIFIC" -> {
                val descriptors = (sn.subgraphTools ?: emptyList()).flatMap { toolName ->
                    val tool = toolByName[toolName]
                    tool?.functions?.map { fn ->
                        val escaped = fn.description.replace("\"", "\\\"")
                        "ToolDescriptor(\"${fn.name}\", \"$escaped\")"
                    } ?: emptyList()
                }
                if (descriptors.isNotEmpty()) {
                    ",\n    toolSelectionStrategy = ToolSelectionStrategy.Tools(listOf(\n        ${descriptors.joinToString(",\n        ")}\n    ))"
                } else ""
            }
            else -> ""
        }

        builder.beginControlFlow("val $varName by subgraph<$inputT, $outputT>(\n    name = %S$toolStrategy\n)", sgName)

        val innerNodeMap = (sn.subgraphNodes ?: emptyList()).associateBy { it.id }
        for (innerSn in sn.subgraphNodes ?: emptyList()) {
            val innerVarName = (innerSn.label ?: innerSn.id).sanitizeIdentifier()
            when (innerSn.type) {
                "node-start", "node-finish" -> {}
                "node-llm-request" -> builder.addStatement("val $innerVarName by nodeLLMRequest()")
                "node-execute-tool" -> builder.addStatement("val $innerVarName by nodeExecuteTool()")
                "node-llm-send-tool-result" -> builder.addStatement("val $innerVarName by nodeLLMSendToolResult()")
                "node-condition" -> builder.addStatement("val $innerVarName by nodeCondition(%S)", innerSn.condition ?: "")
                "node-execute-multiple-tools" -> builder.addStatement("val $innerVarName by nodeExecuteMultipleTools()")
                "node-send-multiple-results" -> builder.addStatement("val $innerVarName by nodeLLMSendMultipleToolResults()")
                "node-compress-history" -> builder.addStatement("val $innerVarName by nodeLLMCompressHistory()")
                "node-llm-streaming" -> builder.addStatement("val $innerVarName by nodeLLMRequestStreaming()")
                "node-llm-structured" -> builder.addStatement("val $innerVarName by nodeLLMRequestStructured<String>()")
                "node-do-nothing" -> builder.addStatement("val $innerVarName by nodeDoNothing()")
                "node-custom" -> {
                    val iT = innerSn.inputType?.ifBlank { null } ?: "String"
                    val oT = innerSn.outputType?.ifBlank { null } ?: "String"
                    val b = innerSn.body ?: ""
                    builder.beginControlFlow("val $innerVarName by node<$iT, $oT>")
                    builder.addStatement("input -> $b")
                    builder.endControlFlow()
                }
            }
        }

        builder.add("\n")

        val innerEdges = sn.subgraphEdges ?: emptyList()
        for (innerSe in innerEdges) {
            val srcNode = innerNodeMap[innerSe.source]
            val tgtNode = innerNodeMap[innerSe.target]
            if (srcNode?.type == "node-llm-send-tool-result" &&
                tgtNode?.type == "node-llm-request" &&
                innerSe.edgeType == "onCondition"
            ) {
                val llmEdges = innerEdges.filter { it.source == innerSe.target }
                for (llmEdge in llmEdges) {
                    val srcRef = resolveNodeRef(innerSe.source, innerNodeMap)
                    val reTgtRef = resolveNodeRef(llmEdge.target, innerNodeMap)
                    val reSuffix = formatEdgeSuffix(llmEdge)
                    builder.addStatement("edge($srcRef forwardTo $reTgtRef$reSuffix)")
                }
                continue
            }
            val srcRef = resolveNodeRef(innerSe.source, innerNodeMap)
            val tgtRef = resolveNodeRef(innerSe.target, innerNodeMap)
            val suffix = formatEdgeSuffix(innerSe)
            builder.addStatement("edge($srcRef forwardTo $tgtRef$suffix)")
        }

        builder.endControlFlow()
    }

    private fun resolveNodeRef(nodeId: String, nodeMap: Map<String, KoogStrategyNode>): String {
        val node = nodeMap[nodeId] ?: return nodeId.sanitizeIdentifier()
        return when (node.type) {
            "node-start" -> "nodeStart"
            "node-finish" -> "nodeFinish"
            else -> (node.label ?: node.id).sanitizeIdentifier()
        }
    }

    private fun formatEdgeSuffix(edge: KoogStrategyEdge): String {
        return when (edge.edgeType) {
            "onToolCall" -> " onToolCall { true }"
            "onMultipleToolCalls" -> " onMultipleToolCalls { true }"
            "onAssistantMessage" -> " onAssistantMessage { true }"
            "onCondition" -> {
                val cond = edge.condition
                val trans = edge.transformation
                if (cond != null && trans != null) {
                    " onCondition { $cond } transformed { $trans }"
                } else if (cond != null) {
                    " onCondition { $cond }"
                } else {
                    " onCondition { true }"
                }
            }
            else -> " onCondition { true }"
        }
    }
}
