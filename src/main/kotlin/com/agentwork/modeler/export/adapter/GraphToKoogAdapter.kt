package com.agentwork.modeler.export.adapter

import com.agentwork.modeler.export.model.*
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogSubAgent
import com.agentwork.modeler.export.model.KoogMcpServer
import com.agentwork.modeler.export.model.McpEnvVarName
import org.springframework.stereotype.Component

@Component
class GraphToKoogAdapter {

    fun convert(graph: WorkspaceGraph): List<KoogAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
        val agentNodes = graph.nodes.filter { it.type == "agent" }
        val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
        val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
        val a2aAgentNodes = graph.nodes.filter { it.type == "a2a-agent" }
        val inputNodes = graph.nodes.filter { it.type == "input" }
        val outputNodes = graph.nodes.filter { it.type == "output" }

        return agentNodes.map { agentNode ->
            val name = agentNode.data["name"] as? String ?: ""
            require(name.isNotBlank()) { "Agent requires a name" }

            val connectedToAgent = graph.edges
                .filter { it.target == agentNode.id }
                .map { it.source }

            // Sub-agent IDs connected to this agent
            val subAgentIds = subAgentNodes
                .filter { it.id in connectedToAgent }
                .map { it.id }
                .toSet()

            // Tool/builtin IDs connected to sub-agents (not main agent)
            val connectedToSubAgents = graph.edges
                .filter { it.target in subAgentIds }
                .map { it.source }
                .toSet()

            // Main agent tools: connected to agent, NOT also connected to a sub-agent
            val tools = toolNodes
                .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
                .map { convertTool(it) }

            val builtinTools = builtinToolNodes
                .filter { it.id in connectedToAgent && it.id !in connectedToSubAgents }
                .map { convertBuiltinTool(it) }

            // Sub-agents with their own tools
            val subAgents = subAgentNodes
                .filter { it.id in connectedToAgent }
                .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes) }

            val mcpServers = mcpServerNodes
                .filter { it.id in connectedToAgent }
                .map { convertMcpServer(it) }

            val a2aAgents = a2aAgentNodes
                .filter { it.id in connectedToAgent }
                .map { convertA2AAgent(it) }

            val inputs = inputNodes
                .filter { it.id in connectedToAgent }
                .map { convertInput(it) }

            val connectedFromAgent = graph.edges
                .filter { it.source == agentNode.id }
                .map { it.target }

            val outputs = outputNodes
                .filter { it.id in connectedFromAgent }
                .map { convertOutput(it) }

            val a2aConfig = extractA2AConfig(agentNode)

            KoogAgent(
                name = name,
                systemPrompt = agentNode.data["systemPrompt"] as? String ?: "",
                modelProvider = parseProvider(agentNode.data["modelProvider"] as? String ?: "openai"),
                modelId = agentNode.data["modelId"] as? String,
                temperature = (agentNode.data["temperature"] as? Number)?.toDouble(),
                tools = tools,
                builtinTools = builtinTools,
                subAgents = subAgents,
                mcpServers = mcpServers,
                otelConfig = graph.otelConfig,
                a2aConfig = a2aConfig,
                a2aAgents = a2aAgents,
                inputs = inputs,
                outputs = outputs
            )
        }
    }

    private fun convertSubAgent(
        saNode: GraphNode,
        graph: WorkspaceGraph,
        toolNodes: List<GraphNode>,
        builtinToolNodes: List<GraphNode>,
        inputNodes: List<GraphNode>,
        outputNodes: List<GraphNode>
    ): KoogSubAgent {
        val connectedIds = graph.edges
            .filter { it.target == saNode.id }
            .map { it.source }

        val tools = toolNodes
            .filter { it.id in connectedIds }
            .map { convertTool(it) }

        val builtinTools = builtinToolNodes
            .filter { it.id in connectedIds }
            .map { convertBuiltinTool(it) }

        val inputs = inputNodes
            .filter { it.id in connectedIds }
            .map { convertInput(it) }

        val connectedFromSa = graph.edges
            .filter { it.source == saNode.id }
            .map { it.target }

        val outputs = outputNodes
            .filter { it.id in connectedFromSa }
            .map { convertOutput(it) }

        return KoogSubAgent(
            name = saNode.data["name"] as? String ?: "",
            description = saNode.data["description"] as? String ?: "",
            systemPrompt = saNode.data["systemPrompt"] as? String ?: "",
            modelProvider = parseProvider(saNode.data["modelProvider"] as? String ?: "openai"),
            modelId = saNode.data["modelId"] as? String,
            temperature = (saNode.data["temperature"] as? Number)?.toDouble(),
            delegationMode = saNode.data["delegationMode"] as? String ?: "automatic",
            inputDescription = saNode.data["inputDescription"] as? String ?: "",
            tools = tools,
            builtinTools = builtinTools,
            inputs = inputs,
            outputs = outputs
        )
    }

    private fun convertBuiltinTool(node: GraphNode): KoogBuiltinTool {
        val typeStr = node.data["toolType"] as? String ?: ""
        val type = BuiltinToolType.valueOf(typeStr.uppercase())
        val config = node.data.filterKeys { it != "toolType" }
        return KoogBuiltinTool(type = type, config = config)
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertTool(node: GraphNode): KoogTool {
        val rawFunctions = node.data["functions"] as? List<Map<String, Any?>> ?: emptyList()
        val functions = rawFunctions.map { fnMap ->
            val rawParams = fnMap["parameters"] as? List<Map<String, Any?>> ?: emptyList()
            val params = rawParams.map { p ->
                KoogToolParameter(
                    name = p["name"] as? String ?: "",
                    type = parseParamType(p["type"] as? String ?: "String"),
                    description = p["description"] as? String ?: "",
                    required = p["required"] as? Boolean ?: true,
                    defaultValue = p["defaultValue"] as? String
                )
            }
            KoogToolFunction(
                name = fnMap["name"] as? String ?: "",
                description = fnMap["description"] as? String ?: "",
                parameters = params,
                returnType = fnMap["returnType"] as? String ?: "String",
                body = fnMap["body"] as? String ?: ""
            )
        }
        return KoogTool(
            name = node.data["name"] as? String ?: "",
            description = node.data["description"] as? String ?: "",
            functions = functions
        )
    }

    private fun convertMcpServer(node: GraphNode): KoogMcpServer {
        val envVars = (node.data["envVars"] as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            McpEnvVarName(name = name)
        } ?: emptyList()

        val args = (node.data["args"] as? List<*>)?.mapNotNull { it as? String }

        return KoogMcpServer(
            name = node.data["name"] as? String ?: "",
            transportType = node.data["transportType"] as? String ?: "sse",
            url = node.data["url"] as? String,
            command = node.data["command"] as? String,
            args = args,
            envVars = envVars
        )
    }

    fun convertPlannerAgents(graph: WorkspaceGraph): List<KoogPlannerAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
        val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
        val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
        val a2aAgentNodes = graph.nodes.filter { it.type == "a2a-agent" }
        val inputNodes = graph.nodes.filter { it.type == "input" }
        val outputNodes = graph.nodes.filter { it.type == "output" }

        return graph.nodes.filter { it.type == "planner-agent" }.map { plannerNode ->
            val connectedIds = graph.edges
                .filter { it.target == plannerNode.id }
                .map { it.source }

            val tools = toolNodes
                .filter { it.id in connectedIds }
                .map { convertTool(it) }

            val builtinTools = builtinToolNodes
                .filter { it.id in connectedIds }
                .map { convertBuiltinTool(it) }

            val subAgents = subAgentNodes
                .filter { it.id in connectedIds }
                .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes) }

            val mcpServers = mcpServerNodes
                .filter { it.id in connectedIds }
                .map { convertMcpServer(it) }

            val a2aAgents = a2aAgentNodes
                .filter { it.id in connectedIds }
                .map { convertA2AAgent(it) }

            val a2aConfig = extractA2AConfig(plannerNode)

            val inputs = inputNodes
                .filter { it.id in connectedIds }
                .map { convertInput(it) }

            val connectedFromPlanner = graph.edges
                .filter { it.source == plannerNode.id }
                .map { it.target }

            val outputs = outputNodes
                .filter { it.id in connectedFromPlanner }
                .map { convertOutput(it) }

            @Suppress("UNCHECKED_CAST")
            val goalMaps = plannerNode.data["goals"] as? List<Map<String, Any?>> ?: emptyList()
            val goals = goalMaps.map { gMap ->
                @Suppress("UNCHECKED_CAST")
                val condMaps = gMap["conditions"] as? List<Map<String, Any?>> ?: emptyList()
                PlannerGoal(
                    name = gMap["name"] as? String ?: "",
                    conditions = condMaps.map { parseCondition(it) },
                    priority = (gMap["priority"] as? Number)?.toInt() ?: 0
                )
            }

            @Suppress("UNCHECKED_CAST")
            val actionMaps = plannerNode.data["actions"] as? List<Map<String, Any?>> ?: emptyList()
            val actions = actionMaps.map { aMap ->
                @Suppress("UNCHECKED_CAST")
                val preMaps = aMap["preconditions"] as? List<Map<String, Any?>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val effMaps = aMap["effects"] as? List<Map<String, Any?>> ?: emptyList()
                PlannerAction(
                    name = aMap["name"] as? String ?: "",
                    description = aMap["description"] as? String ?: "",
                    preconditions = preMaps.map { parseCondition(it) },
                    effects = effMaps.map { parseEffect(it) },
                    cost = (aMap["cost"] as? Number)?.toInt() ?: 1
                )
            }

            @Suppress("UNCHECKED_CAST")
            val stateMaps = plannerNode.data["initialState"] as? List<Map<String, Any?>> ?: emptyList()
            val initialState: Map<String, Any> = stateMaps.mapNotNull { sMap ->
                val key = sMap["key"] as? String ?: return@mapNotNull null
                val value = sMap["value"] ?: return@mapNotNull null
                key to value
            }.toMap()

            val plannerTypeStr = plannerNode.data["plannerType"] as? String ?: "llm"
            val plannerType = when (plannerTypeStr.lowercase()) {
                "goap" -> PlannerType.GOAP
                else -> PlannerType.LLM
            }

            val modelProviderStr = plannerNode.data["modelProvider"] as? String
            val modelProvider = if (modelProviderStr != null) parseProvider(modelProviderStr) else null

            KoogPlannerAgent(
                name = plannerNode.data["name"] as? String ?: "",
                description = plannerNode.data["description"] as? String ?: "",
                plannerType = plannerType,
                modelProvider = modelProvider,
                modelId = plannerNode.data["modelId"] as? String,
                temperature = (plannerNode.data["temperature"] as? Number)?.toDouble(),
                maxReplanAttempts = (plannerNode.data["maxReplanAttempts"] as? Number)?.toInt() ?: 3,
                goals = goals,
                actions = actions,
                initialState = initialState,
                tools = tools,
                builtinTools = builtinTools,
                subAgents = subAgents,
                mcpServers = mcpServers,
                a2aConfig = a2aConfig,
                a2aAgents = a2aAgents,
                inputs = inputs,
                outputs = outputs
            )
        }
    }

    fun convertGraphAgents(graph: WorkspaceGraph): List<KoogGraphAgent> {
        val toolNodes = graph.nodes.filter { it.type == "tool" }
        val builtinToolNodes = graph.nodes.filter { it.type == "builtin-tool" }
        val subAgentNodes = graph.nodes.filter { it.type == "sub-agent" }
        val mcpServerNodes = graph.nodes.filter { it.type == "mcp-server" }
        val inputNodes = graph.nodes.filter { it.type == "input" }
        val outputNodes = graph.nodes.filter { it.type == "output" }
        val ragNodes = graph.nodes.filter { it.type == "rag" }

        return graph.nodes.filter { it.type == "graph-agent" }.map { gaNode ->
            val connectedIds = graph.edges
                .filter { it.target == gaNode.id }
                .map { it.source }

            val rags = ragNodes.filter { it.id in connectedIds }.map { convertRagNode(it) }
            val tools = toolNodes.filter { it.id in connectedIds }.map { convertTool(it) }
            val builtinTools = builtinToolNodes.filter { it.id in connectedIds }.map { convertBuiltinTool(it) }
            val subAgents = subAgentNodes.filter { it.id in connectedIds }
                .map { saNode -> convertSubAgent(saNode, graph, toolNodes, builtinToolNodes, inputNodes, outputNodes) }
            val mcpServers = mcpServerNodes.filter { it.id in connectedIds }.map { convertMcpServer(it) }
            val inputs = inputNodes.filter { it.id in connectedIds }.map { convertInput(it) }

            val connectedFromGa = graph.edges.filter { it.source == gaNode.id }.map { it.target }
            val outputs = outputNodes.filter { it.id in connectedFromGa }.map { convertOutput(it) }

            @Suppress("UNCHECKED_CAST")
            val strategyNodeMaps = gaNode.data["strategyNodes"] as? List<Map<String, Any?>> ?: emptyList()
            val strategyNodes = strategyNodeMaps.map { snMap ->
                @Suppress("UNCHECKED_CAST")
                val snData = snMap["data"] as? Map<String, Any?> ?: emptyMap()
                KoogStrategyNode(
                    id = snMap["id"] as? String ?: "",
                    type = snMap["type"] as? String ?: "",
                    label = snData["label"] as? String,
                    toolName = snData["toolName"] as? String,
                    condition = snData["condition"] as? String,
                    temperature = (snData["temperature"] as? Number)?.toDouble(),
                    compressionStrategy = snData["compressionStrategy"] as? String,
                    outputSchema = snData["outputSchema"] as? String,
                    inputType = snData["inputType"] as? String,
                    outputType = snData["outputType"] as? String,
                    body = snData["body"] as? String,
                    subgraphName = snData["subgraphName"] as? String,
                    subgraphInputType = snData["subgraphInputType"] as? String,
                    subgraphOutputType = snData["subgraphOutputType"] as? String,
                    subgraphToolSelection = snData["subgraphToolSelection"] as? String,
                    subgraphTools = (snData["subgraphTools"] as? List<*>)?.filterIsInstance<String>(),
                    subgraphNodes = (snData["subgraphNodes"] as? List<Map<String, Any?>>)?.map { innerSnMap ->
                        @Suppress("UNCHECKED_CAST")
                        val innerSnData = innerSnMap["data"] as? Map<String, Any?> ?: emptyMap()
                        KoogStrategyNode(
                            id = innerSnMap["id"] as? String ?: "",
                            type = innerSnMap["type"] as? String ?: "",
                            label = innerSnData["label"] as? String,
                            toolName = innerSnData["toolName"] as? String,
                            condition = innerSnData["condition"] as? String,
                            temperature = (innerSnData["temperature"] as? Number)?.toDouble(),
                            compressionStrategy = innerSnData["compressionStrategy"] as? String,
                            outputSchema = innerSnData["outputSchema"] as? String,
                            inputType = innerSnData["inputType"] as? String,
                            outputType = innerSnData["outputType"] as? String,
                            body = innerSnData["body"] as? String,
                            subgraphName = null, subgraphInputType = null, subgraphOutputType = null,
                            subgraphToolSelection = null, subgraphTools = null,
                            subgraphNodes = null, subgraphEdges = null
                        )
                    },
                    subgraphEdges = (snData["subgraphEdges"] as? List<Map<String, Any?>>)?.map { innerSeMap ->
                        KoogStrategyEdge(
                            source = innerSeMap["source"] as? String ?: "",
                            target = innerSeMap["target"] as? String ?: "",
                            edgeType = innerSeMap["edgeType"] as? String ?: "",
                            condition = innerSeMap["condition"] as? String,
                            transformation = innerSeMap["transformation"] as? String
                        )
                    }
                )
            }

            @Suppress("UNCHECKED_CAST")
            val strategyEdgeMaps = gaNode.data["strategyEdges"] as? List<Map<String, Any?>> ?: emptyList()
            val strategyEdges = strategyEdgeMaps.map { seMap ->
                KoogStrategyEdge(
                    source = seMap["source"] as? String ?: "",
                    target = seMap["target"] as? String ?: "",
                    edgeType = seMap["edgeType"] as? String ?: "",
                    condition = seMap["condition"] as? String,
                    transformation = seMap["transformation"] as? String
                )
            }

            KoogGraphAgent(
                name = gaNode.data["name"] as? String ?: "",
                description = gaNode.data["description"] as? String ?: "",
                systemPrompt = gaNode.data["systemPrompt"] as? String ?: "",
                modelProvider = parseProvider(gaNode.data["modelProvider"] as? String ?: "openai"),
                modelId = gaNode.data["modelId"] as? String,
                temperature = (gaNode.data["temperature"] as? Number)?.toDouble(),
                strategyNodes = strategyNodes,
                strategyEdges = strategyEdges,
                tools = tools,
                builtinTools = builtinTools,
                subAgents = subAgents,
                mcpServers = mcpServers,
                ragNodes = rags,
                inputs = inputs,
                outputs = outputs
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertRagNode(node: GraphNode): KoogRagNode {
        val d = node.data
        return KoogRagNode(
            id = node.id,
            name = d["name"] as? String ?: "",
            embedderProvider = d["embedderProvider"] as? String ?: "OpenAI",
            embedderModel = d["embedderModel"] as? String ?: "TextEmbeddingAda3Large",
            apiKeyVariable = d["apiKeyVariable"] as? String ?: "OPENAI_API_KEY",
            storageType = d["storageType"] as? String ?: "InMemory",
            storagePath = (d["storagePath"] as? String)?.takeIf { it.isNotBlank() },
            customStorageClass = (d["customStorageClass"] as? String)?.takeIf { it.isNotBlank() },
            similarityThreshold = (d["similarityThreshold"] as? Number)?.toDouble() ?: 0.7,
            filePaths = (d["filePaths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            urlSources = (d["urlSources"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            maxDocuments = (d["maxDocuments"] as? Number)?.toInt() ?: 5,
            injectAsContext = d["injectAsContext"] as? Boolean ?: true,
            provideAsTool = d["provideAsTool"] as? Boolean ?: false,
            toolDescription = d["toolDescription"] as? String
        )
    }

    private fun parseCondition(map: Map<String, Any?>): StateCondition {
        val operator = when ((map["operator"] as? String)?.lowercase()) {
            "notequals", "not_equals" -> ConditionOperator.NOT_EQUALS
            "greaterthan", "greater_than" -> ConditionOperator.GREATER_THAN
            "lessthan", "less_than" -> ConditionOperator.LESS_THAN
            else -> ConditionOperator.EQUALS
        }
        return StateCondition(
            key = map["key"] as? String ?: "",
            operator = operator,
            value = map["value"] ?: ""
        )
    }

    private fun parseEffect(map: Map<String, Any?>): StateEffect {
        return StateEffect(
            key = map["key"] as? String ?: "",
            value = map["value"] ?: ""
        )
    }

    private fun parseProvider(value: String): ModelProvider = when (value.lowercase()) {
        "openai" -> ModelProvider.OPENAI
        "anthropic" -> ModelProvider.ANTHROPIC
        "google" -> ModelProvider.GOOGLE
        "bedrock" -> ModelProvider.BEDROCK
        else -> ModelProvider.OPENAI
    }

    private fun parseParamType(value: String): KoogParamType = when (value) {
        "Int" -> KoogParamType.INT
        "Double" -> KoogParamType.DOUBLE
        "Float" -> KoogParamType.FLOAT
        "Boolean" -> KoogParamType.BOOLEAN
        "List<String>" -> KoogParamType.LIST_STRING
        "Map<String, String>" -> KoogParamType.MAP_STRING_STRING
        else -> KoogParamType.STRING
    }

    private fun convertA2AAgent(node: GraphNode): KoogA2AAgent {
        return KoogA2AAgent(
            name = node.data["name"] as? String ?: "",
            description = node.data["description"] as? String ?: "",
            url = node.data["url"] as? String ?: "",
            authType = node.data["authType"] as? String ?: "none",
            authToken = node.data["authToken"] as? String,
            timeout = (node.data["timeout"] as? Number)?.toInt() ?: 30000,
            communicationMode = node.data["communicationMode"] as? String ?: "message"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertInput(node: GraphNode): KoogInput {
        val className = node.data["className"] as? String
        val rawFields = node.data["fields"] as? List<Map<String, Any?>> ?: emptyList()
        val fields = rawFields.map { f ->
            KoogField(
                name = f["name"] as? String ?: "",
                type = f["type"] as? String ?: "String",
                description = f["description"] as? String ?: "",
                optional = f["optional"] as? Boolean ?: false
            )
        }
        return KoogInput(className = className, fields = fields)
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertOutput(node: GraphNode): KoogOutput {
        val className = node.data["className"] as? String
        val rawFields = node.data["fields"] as? List<Map<String, Any?>> ?: emptyList()
        val fields = rawFields.map { f ->
            KoogField(
                name = f["name"] as? String ?: "",
                type = f["type"] as? String ?: "String",
                description = f["description"] as? String ?: "",
                optional = f["optional"] as? Boolean ?: false
            )
        }
        return KoogOutput(className = className, fields = fields)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractA2AConfig(node: GraphNode): KoogA2AConfig? {
        val configMap = node.data["a2aConfig"] as? Map<String, Any?> ?: return null
        val enabled = configMap["enabled"] as? Boolean ?: false
        if (!enabled) return null

        val cardMap = configMap["agentCard"] as? Map<String, Any?> ?: return null
        val capsMap = cardMap["capabilities"] as? Map<String, Any?> ?: emptyMap()
        val skillsList = (cardMap["skills"] as? List<Map<String, Any?>>)?.map { sMap ->
            KoogA2ASkill(
                id = sMap["id"] as? String ?: "",
                name = sMap["name"] as? String ?: "",
                description = sMap["description"] as? String ?: "",
                tags = (sMap["tags"] as? List<String>) ?: emptyList()
            )
        } ?: emptyList()

        val serverMap = configMap["server"] as? Map<String, Any?> ?: emptyMap()

        return KoogA2AConfig(
            enabled = true,
            agentCard = KoogAgentCard(
                name = cardMap["name"] as? String ?: "",
                description = cardMap["description"] as? String ?: "",
                url = cardMap["url"] as? String ?: "",
                version = cardMap["version"] as? String ?: "1.0.0",
                capabilities = KoogA2ACapabilities(
                    streaming = capsMap["streaming"] as? Boolean ?: false,
                    pushNotifications = capsMap["pushNotifications"] as? Boolean ?: false,
                    stateTransitionHistory = capsMap["stateTransitionHistory"] as? Boolean ?: false
                ),
                skills = skillsList,
                defaultInputModes = (cardMap["defaultInputModes"] as? List<String>) ?: listOf("text"),
                defaultOutputModes = (cardMap["defaultOutputModes"] as? List<String>) ?: listOf("text")
            ),
            server = KoogA2AServerConfig(
                transportType = serverMap["transportType"] as? String ?: "http-jsonrpc",
                port = (serverMap["port"] as? Number)?.toInt() ?: 9000,
                basePath = serverMap["basePath"] as? String ?: "/a2a"
            )
        )
    }

    fun convertSharedCode(graph: WorkspaceGraph): List<KoogSharedCode> {
        return graph.nodes
            .filter { it.type == "code" }
            .map { node ->
                val data = node.data as Map<*, *>
                KoogSharedCode(
                    name = data["fileName"] as? String ?: "",
                    code = data["code"] as? String ?: ""
                )
            }
    }
}
