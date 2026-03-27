package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogStrategyEdge
import org.springframework.stereotype.Component

@Component
class GraphAgentGenerator {

    fun generate(agent: KoogGraphAgent): String {
        val funcName = "create${agent.name.replaceFirstChar { it.uppercase() }}Strategy"
        val nodeMap = agent.strategyNodes.associateBy { it.id }

        return buildString {
            appendLine("package generated")
            appendLine()
            appendLine("import ai.koog.agents.core.dsl.builder.strategy")
            appendLine("import ai.koog.agents.core.dsl.builder.forwardTo")
            appendLine()
            appendLine("fun $funcName() = strategy<String, String>(\"${agent.name}\") {")

            for (sn in agent.strategyNodes) {
                when (sn.type) {
                    "node-start", "node-finish" -> {}
                    "node-llm-request" -> appendLine("    val ${sn.label} by nodeLLMRequest()")
                    "node-execute-tool" -> appendLine("    val ${sn.label} by nodeExecuteTool()")
                    "node-llm-send-tool-result" -> appendLine("    val ${sn.label} by nodeLLMSendToolResult()")
                    "node-condition" -> appendLine("    val ${sn.label} by nodeCondition(\"${sn.condition}\")")
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

    private fun resolveNodeRef(nodeId: String, nodeMap: Map<String, com.agentwork.modeler.export.model.KoogStrategyNode>): String {
        val node = nodeMap[nodeId] ?: return nodeId
        return when (node.type) {
            "node-start" -> "nodeStart"
            "node-finish" -> "nodeFinish"
            else -> node.label ?: nodeId
        }
    }

    private fun formatEdgeSuffix(edge: KoogStrategyEdge): String {
        return when (edge.edgeType) {
            "onToolCall" -> " onToolCall { true }"
            "onAssistantMessage" -> " onAssistantMessage { true }"
            "onCondition" -> {
                val cond = edge.condition
                val trans = edge.transformation
                if (cond != null && trans != null) {
                    " onCondition { $cond } transformed { $trans }"
                } else if (cond != null) {
                    " onCondition { $cond }"
                } else {
                    ""
                }
            }
            else -> ""
        }
    }
}
