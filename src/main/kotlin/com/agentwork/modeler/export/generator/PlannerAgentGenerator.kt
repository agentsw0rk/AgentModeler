package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.springframework.stereotype.Component

@Component
class PlannerAgentGenerator {

    fun generate(planner: KoogPlannerAgent): Map<String, String> {
        val files = mutableMapOf<String, String>()
        files["PlannerGoals.kt"] = generateGoals(planner)
        files["PlannerActions.kt"] = generateActions(planner)
        files["PlannerAgent.kt"] = generateConfig(planner)
        return files
    }

    private fun generateGoals(planner: KoogPlannerAgent): String = buildString {
        appendLine("package generated.planner")
        appendLine()
        appendLine("enum class ConditionOp {")
        appendLine("    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN")
        appendLine("}")
        appendLine()
        appendLine("data class StateConditionDef(")
        appendLine("    val key: String,")
        appendLine("    val operator: ConditionOp,")
        appendLine("    val value: Any")
        appendLine(")")
        appendLine()
        appendLine("data class PlannerGoalDef(")
        appendLine("    val name: String,")
        appendLine("    val conditions: List<StateConditionDef>,")
        appendLine("    val priority: Int = 0")
        appendLine(")")
        appendLine()
        appendLine("val plannerGoals = listOf(")
        planner.goals.forEachIndexed { i, goal ->
            val conditionsStr = goal.conditions.joinToString(", ") { cond ->
                val valueStr = formatValue(cond.value)
                "StateConditionDef(\"${cond.key}\", ConditionOp.${cond.operator.name}, $valueStr)"
            }
            val comma = if (i < planner.goals.size - 1) "," else ""
            appendLine("    PlannerGoalDef(\"${goal.name}\", listOf($conditionsStr), ${goal.priority})$comma")
        }
        appendLine(")")
    }

    private fun generateActions(planner: KoogPlannerAgent): String = buildString {
        appendLine("package generated.planner")
        appendLine()
        appendLine("data class StateEffectDef(")
        appendLine("    val key: String,")
        appendLine("    val value: Any")
        appendLine(")")
        appendLine()
        appendLine("data class PlannerActionDef(")
        appendLine("    val name: String,")
        appendLine("    val description: String,")
        appendLine("    val preconditions: List<StateConditionDef>,")
        appendLine("    val effects: List<StateEffectDef>,")
        appendLine("    val cost: Int = 1")
        appendLine(")")
        appendLine()
        appendLine("val plannerActions = listOf(")
        planner.actions.forEachIndexed { i, action ->
            val precondsStr = action.preconditions.joinToString(", ") { cond ->
                val valueStr = formatValue(cond.value)
                "StateConditionDef(\"${cond.key}\", ConditionOp.${cond.operator.name}, $valueStr)"
            }
            val effectsStr = action.effects.joinToString(", ") { eff ->
                val valueStr = formatValue(eff.value)
                "StateEffectDef(\"${eff.key}\", $valueStr)"
            }
            val comma = if (i < planner.actions.size - 1) "," else ""
            appendLine("    PlannerActionDef(\"${action.name}\", \"${action.description}\", listOf($precondsStr), listOf($effectsStr), cost = ${action.cost})$comma")
        }
        appendLine(")")
    }

    private fun generateConfig(planner: KoogPlannerAgent): String = buildString {
        val hasTools = planner.tools.isNotEmpty() || planner.mcpServers.isNotEmpty()
        val isLlm = planner.plannerType == PlannerType.LLM

        appendLine("package generated")
        appendLine()
        if (isLlm && planner.modelProvider != null) {
            appendLine("import ai.koog.prompt.executor.llms.all.${planner.modelProvider.executorFactory}")
            appendLine("import ${planner.modelProvider.importPackage}")
        }
        if (hasTools) {
            appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        }
        appendLine("import org.springframework.beans.factory.annotation.Value")
        appendLine("import org.springframework.context.annotation.Bean")
        appendLine("import org.springframework.context.annotation.Configuration")
        appendLine()
        appendLine("@Configuration")
        if (isLlm && planner.modelProvider != null) {
            appendLine("class PlannerAgentConfig(")
            appendLine("    @Value(\"\${'$'}{${planner.modelProvider.name.lowercase()}.api-key}\") private val apiKey: String")
            appendLine(") {")
        } else {
            appendLine("class PlannerAgentConfig {")
            appendLine()
            appendLine("    // GOAP planner uses A* solver for planning")
        }
        appendLine()
        appendLine("    @Bean")
        appendLine("    fun plannerAgent() {")

        if (isLlm && planner.modelProvider != null) {
            val modelRef = planner.modelId?.let { mapModelId(planner.modelProvider, it) }
                ?: planner.modelProvider.defaultModel
            appendLine("        val executor = ${planner.modelProvider.executorFactory}(apiKey)")
            appendLine("        val model = $modelRef")
            appendLine()
        }

        // Initial state
        appendLine("        val initialState = mapOf(")
        planner.initialState.entries.forEachIndexed { i, (key, value) ->
            val comma = if (i < planner.initialState.size - 1) "," else ""
            appendLine("            \"$key\" to ${formatValue(value)}$comma")
        }
        appendLine("        )")
        appendLine()

        // Goals
        appendLine("        // Goals")
        planner.goals.forEach { goal ->
            appendLine("        // - ${goal.name} (priority=${goal.priority})")
        }
        appendLine()

        // Actions
        appendLine("        // Actions")
        planner.actions.forEach { action ->
            if (planner.plannerType == PlannerType.GOAP) {
                appendLine("        // - ${action.name}: ${action.description} (cost = ${action.cost})")
            } else {
                appendLine("        // - ${action.name}: ${action.description}")
            }
        }
        appendLine()

        appendLine("        val maxReplanAttempts = ${planner.maxReplanAttempts}")

        if (hasTools) {
            appendLine()
            appendLine("        val toolRegistry = createToolRegistry()")
        }

        appendLine("    }")
        appendLine("}")
    }

    private fun formatValue(value: Any): String = when (value) {
        is String -> "\"$value\""
        else -> value.toString()
    }

    private fun mapModelId(provider: ModelProvider, id: String): String = when (provider) {
        ModelProvider.OPENAI -> when (id.lowercase()) {
            "gpt-4o" -> "OpenAIModels.Chat.GPT4o"
            "gpt-4o-mini" -> "OpenAIModels.Chat.GPT4oMini"
            "gpt-4-1" -> "OpenAIModels.Chat.GPT4_1"
            else -> provider.defaultModel
        }
        ModelProvider.ANTHROPIC -> when (id.lowercase()) {
            "claude-sonnet-4" -> "AnthropicModels.Sonnet_4"
            "claude-opus-4" -> "AnthropicModels.Opus_4"
            "claude-haiku-3.5" -> "AnthropicModels.Haiku_35"
            else -> provider.defaultModel
        }
        ModelProvider.GOOGLE -> when (id.lowercase()) {
            "gemini-2-flash" -> "GoogleModels.Gemini2Flash"
            "gemini-2-pro" -> "GoogleModels.Gemini2Pro"
            else -> provider.defaultModel
        }
        ModelProvider.BEDROCK -> when (id.lowercase()) {
            "claude-4.5-sonnet" -> "BedrockModels.AnthropicClaude4_5Sonnet"
            "claude-4-sonnet" -> "BedrockModels.AnthropicClaude4Sonnet"
            "claude-4-opus" -> "BedrockModels.AnthropicClaude4Opus"
            "nova-pro" -> "BedrockModels.AmazonNovaPro"
            "nova-lite" -> "BedrockModels.AmazonNovaLite"
            "nova-micro" -> "BedrockModels.AmazonNovaMicro"
            "llama-3.3-70b" -> "BedrockModels.MetaLlama3_3_70BInstruct"
            "llama-3.1-405b" -> "BedrockModels.MetaLlama3_1_405BInstruct"
            else -> provider.defaultModel
        }
        ModelProvider.OLLAMA -> provider.defaultModel
    }
}
