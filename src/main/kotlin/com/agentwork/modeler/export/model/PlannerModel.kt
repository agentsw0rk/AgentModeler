package com.agentwork.modeler.export.model

data class KoogPlannerAgent(
    val name: String,
    val description: String,
    val plannerType: PlannerType,
    val modelProvider: ModelProvider?,
    val modelId: String?,
    val temperature: Double?,
    val maxReplanAttempts: Int = 3,
    val goals: List<PlannerGoal>,
    val actions: List<PlannerAction>,
    val initialState: Map<String, Any> = emptyMap(),
    val tools: List<KoogTool> = emptyList(),
    val builtinTools: List<KoogBuiltinTool> = emptyList(),
    val subAgents: List<KoogSubAgent> = emptyList(),
    val mcpServers: List<KoogMcpServer> = emptyList(),
    val a2aConfig: KoogA2AConfig? = null,
    val a2aAgents: List<KoogA2AAgent> = emptyList()
)

enum class PlannerType { LLM, GOAP }

data class PlannerGoal(
    val name: String,
    val conditions: List<StateCondition>,
    val priority: Int = 0
)

data class PlannerAction(
    val name: String,
    val description: String,
    val preconditions: List<StateCondition> = emptyList(),
    val effects: List<StateEffect> = emptyList(),
    val cost: Int = 1
)

data class StateCondition(
    val key: String,
    val operator: ConditionOperator,
    val value: Any
)

data class StateEffect(
    val key: String,
    val value: Any
)

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN
}
