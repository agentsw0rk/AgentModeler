package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogStrategyEdge
import com.agentwork.modeler.export.model.KoogStrategyNode
import org.springframework.stereotype.Component

@Component
class GraphAgentGenerator {

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

        // Collect tool names referenced in subgraph toolSelectionStrategy
        val referencedToolNames = agent.strategyNodes
            .filter { it.type == "node-subgraph" && it.subgraphToolSelection == "SPECIFIC" }
            .flatMap { it.subgraphTools ?: emptyList() }
            .toSet()

        // Check if any subgraph uses ToolSelectionStrategy (NONE or SPECIFIC)
        val usesToolSelectionStrategy = agent.strategyNodes.any {
            it.type == "node-subgraph" && it.subgraphToolSelection in listOf("NONE", "SPECIFIC")
        }

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.dsl.builder.strategy")
            appendLine("import ai.koog.agents.core.dsl.builder.forwardTo")
            if ("node-llm-request" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMRequest")
            if ("node-execute-tool" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeExecuteTool")
            if ("node-llm-send-tool-result" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult")
            if ("node-execute-multiple-tools" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools")
            if ("node-send-multiple-results" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults")
            if ("node-compress-history" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory")
            if ("node-llm-streaming" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming")
            if ("node-llm-structured" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured")
            if ("node-do-nothing" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.extension.nodeDoNothing")
            if ("node-custom" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.builder.node")
            if ("node-subgraph" in nodeTypes) appendLine("import ai.koog.agents.core.dsl.builder.subgraph")
            if ("onAssistantMessage" in edgeTypes) appendLine("import ai.koog.agents.core.dsl.extension.onAssistantMessage")
            if ("onToolCall" in edgeTypes) appendLine("import ai.koog.agents.core.dsl.extension.onToolCall")
            if ("onMultipleToolCalls" in edgeTypes) appendLine("import ai.koog.agents.core.dsl.extension.onMultipleToolCalls")
            if (usesToolSelectionStrategy) {
                appendLine("import ai.koog.agents.core.agent.entity.ToolSelectionStrategy")
                appendLine("import ai.koog.agents.core.tools.ToolDescriptor")
            }
            // onCondition is a member function on AIAgentEdgeBuilderIntermediateBase, no import needed
            appendLine()
            appendLine("fun $funcName() = strategy<String, String>(\"${agent.name}\") {")

            for (sn in agent.strategyNodes) {
                val varName = sanitizeIdentifier(sn.label ?: sn.id)
                when (sn.type) {
                    "node-start", "node-finish" -> {}
                    "node-llm-request" -> appendLine("    val $varName by nodeLLMRequest()")
                    "node-execute-tool" -> appendLine("    val $varName by nodeExecuteTool()")
                    "node-llm-send-tool-result" -> appendLine("    val $varName by nodeLLMSendToolResult()")
                    "node-condition" -> appendLine("    val $varName by nodeCondition(\"${sn.condition}\")")
                    "node-execute-multiple-tools" -> appendLine("    val $varName by nodeExecuteMultipleTools()")
                    "node-send-multiple-results" -> appendLine("    val $varName by nodeLLMSendMultipleToolResults()")
                    "node-compress-history" -> {
                        val strategy = sn.compressionStrategy
                        if (strategy != null && strategy.isNotBlank()) {
                            appendLine("    val $varName by nodeLLMCompressHistory(\"$strategy\")")
                        } else {
                            appendLine("    val $varName by nodeLLMCompressHistory()")
                        }
                    }
                    "node-llm-streaming" -> appendLine("    val $varName by nodeLLMRequestStreaming()")
                    "node-llm-structured" -> {
                        val schema = sn.outputSchema
                        if (schema != null && schema.isNotBlank()) {
                            appendLine("    val $varName by nodeLLMRequestStructured<$schema>()")
                        } else {
                            appendLine("    val $varName by nodeLLMRequestStructured<String>()")
                        }
                    }
                    "node-do-nothing" -> appendLine("    val $varName by nodeDoNothing()")
                    "node-custom" -> {
                        val inputT = sn.inputType?.ifBlank { null } ?: "String"
                        val outputT = sn.outputType?.ifBlank { null } ?: "String"
                        val body = sn.body ?: ""
                        appendLine("    val $varName by node<$inputT, $outputT> { input ->")
                        appendLine("        $body")
                        appendLine("    }")
                    }
                    "node-subgraph" -> {
                        val sgName = sn.subgraphName?.ifBlank { null } ?: "subgraph"
                        val inputT = sn.subgraphInputType?.ifBlank { null } ?: "String"
                        val outputT = sn.subgraphOutputType?.ifBlank { null } ?: "String"
                        val toolStrategy = when (sn.subgraphToolSelection) {
                            "NONE" -> ",\n        toolSelectionStrategy = ToolSelectionStrategy.NONE"
                            "SPECIFIC" -> {
                                val descriptors = (sn.subgraphTools ?: emptyList()).flatMap { toolName ->
                                    val tool = toolByName[toolName]
                                    tool?.functions?.map { fn ->
                                        val escaped = fn.description.replace("\"", "\\\"")
                                        "ToolDescriptor(\"${fn.name}\", \"$escaped\")"
                                    } ?: emptyList()
                                }
                                if (descriptors.isNotEmpty()) {
                                    ",\n        toolSelectionStrategy = ToolSelectionStrategy.Tools(listOf(\n            ${descriptors.joinToString(",\n            ")}\n        ))"
                                } else ""
                            }
                            else -> ""
                        }
                        appendLine("    val $varName by subgraph<$inputT, $outputT>(")
                        appendLine("        name = \"$sgName\"$toolStrategy")
                        appendLine("    ) {")
                        val innerNodeMap = (sn.subgraphNodes ?: emptyList()).associateBy { it.id }
                        for (innerSn in sn.subgraphNodes ?: emptyList()) {
                            val innerVarName = sanitizeIdentifier(innerSn.label ?: innerSn.id)
                            when (innerSn.type) {
                                "node-start", "node-finish" -> {}
                                "node-llm-request" -> appendLine("        val $innerVarName by nodeLLMRequest()")
                                "node-execute-tool" -> appendLine("        val $innerVarName by nodeExecuteTool()")
                                "node-llm-send-tool-result" -> appendLine("        val $innerVarName by nodeLLMSendToolResult()")
                                "node-condition" -> appendLine("        val $innerVarName by nodeCondition(\"${innerSn.condition}\")")
                                "node-execute-multiple-tools" -> appendLine("        val $innerVarName by nodeExecuteMultipleTools()")
                                "node-send-multiple-results" -> appendLine("        val $innerVarName by nodeLLMSendMultipleToolResults()")
                                "node-compress-history" -> appendLine("        val $innerVarName by nodeLLMCompressHistory()")
                                "node-llm-streaming" -> appendLine("        val $innerVarName by nodeLLMRequestStreaming()")
                                "node-llm-structured" -> appendLine("        val $innerVarName by nodeLLMRequestStructured<String>()")
                                "node-do-nothing" -> appendLine("        val $innerVarName by nodeDoNothing()")
                                "node-custom" -> {
                                    val iT = innerSn.inputType?.ifBlank { null } ?: "String"
                                    val oT = innerSn.outputType?.ifBlank { null } ?: "String"
                                    val b = innerSn.body ?: ""
                                    appendLine("        val $innerVarName by node<$iT, $oT> { input ->")
                                    appendLine("            $b")
                                    appendLine("        }")
                                }
                            }
                        }
                        appendLine()
                        val innerEdges = sn.subgraphEdges ?: emptyList()
                        for (innerSe in innerEdges) {
                            val srcNode = innerNodeMap[innerSe.source]
                            val tgtNode = innerNodeMap[innerSe.target]
                            // Fix type-incompatible edge: nodeLLMSendToolResult → nodeLLMRequest via onCondition
                            // send outputs Message.Response, llm expects String — replace with
                            // onToolCall → exec and onAssistantMessage → nodeFinish
                            if (srcNode?.type == "node-llm-send-tool-result" &&
                                tgtNode?.type == "node-llm-request" &&
                                innerSe.edgeType == "onCondition"
                            ) {
                                // Find the edges from the target llm node to replicate routing
                                val llmEdges = innerEdges.filter { it.source == innerSe.target }
                                for (llmEdge in llmEdges) {
                                    val srcRef = resolveNodeRef(innerSe.source, innerNodeMap)
                                    val reTgtRef = resolveNodeRef(llmEdge.target, innerNodeMap)
                                    val reSuffix = formatEdgeSuffix(llmEdge)
                                    appendLine("        edge($srcRef forwardTo $reTgtRef$reSuffix)")
                                }
                                continue
                            }
                            val srcRef = resolveNodeRef(innerSe.source, innerNodeMap)
                            val tgtRef = resolveNodeRef(innerSe.target, innerNodeMap)
                            val suffix = formatEdgeSuffix(innerSe)
                            appendLine("        edge($srcRef forwardTo $tgtRef$suffix)")
                        }
                        appendLine("    }")
                    }
                }
            }

            appendLine()

            for (se in agent.strategyEdges) {
                val sourceRef = resolveNodeRef(se.source, nodeMap)
                val targetRef = resolveNodeRef(se.target, nodeMap)
                val edgeSuffix = formatEdgeSuffix(se)
                appendLine("    edge($sourceRef forwardTo $targetRef$edgeSuffix)")
            }

            appendLine("}")
        }
    }

    private fun sanitizeIdentifier(value: String): String {
        // Replace hyphens and other invalid chars with underscores, ensure valid Kotlin identifier
        return value.replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("^[0-9]"), "_$0")
            .ifEmpty { "node" }
    }

    private fun resolveNodeRef(nodeId: String, nodeMap: Map<String, KoogStrategyNode>): String {
        val node = nodeMap[nodeId] ?: return sanitizeIdentifier(nodeId)
        return when (node.type) {
            "node-start" -> "nodeStart"
            "node-finish" -> "nodeFinish"
            else -> sanitizeIdentifier(node.label ?: node.id)
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
