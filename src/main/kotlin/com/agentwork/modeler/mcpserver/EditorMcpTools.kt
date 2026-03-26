package com.agentwork.modeler.mcpserver

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.ExportValidationException
import com.agentwork.modeler.export.validation.GraphValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

@Service
class EditorMcpTools(
    private val editorStateService: EditorStateService,
    private val graphValidator: GraphValidator,
    private val exportService: ExportService
) {
    private val mapper = jacksonObjectMapper()

    @Tool(description = "List all available node types with their required and optional fields")
    fun listNodeTypes(): String {
        val types = listOf(
            mapOf(
                "type" to "agent",
                "required" to listOf("name", "systemPrompt"),
                "optional" to listOf("modelProvider", "modelId", "temperature", "maxTokens")
            ),
            mapOf(
                "type" to "tool",
                "required" to listOf("name"),
                "optional" to listOf("description", "parameters")
            ),
            mapOf(
                "type" to "builtin-tool",
                "required" to listOf("toolType"),
                "optional" to emptyList<String>()
            ),
            mapOf(
                "type" to "sub-agent",
                "required" to listOf("name", "description", "systemPrompt", "inputDescription"),
                "optional" to listOf("modelProvider", "modelId")
            ),
            mapOf(
                "type" to "mcp-server",
                "required" to listOf("name", "transportType"),
                "optional" to listOf("url", "command", "args", "env")
            ),
            mapOf(
                "type" to "planner-agent",
                "required" to listOf("name", "goals", "actions"),
                "optional" to listOf("plannerType", "modelProvider", "modelId")
            )
        )
        return mapper.writeValueAsString(mapOf("types" to types))
    }

    @Tool(description = "Get the full workspace graph including name, all nodes, all edges, and counts")
    fun getGraph(): String {
        val graph = editorStateService.getGraph()
        return mapper.writeValueAsString(
            mapOf(
                "name" to graph.name,
                "nodes" to graph.nodes,
                "edges" to graph.edges,
                "nodeCount" to graph.nodes.size,
                "edgeCount" to graph.edges.size
            )
        )
    }

    @Tool(description = "Get a single node by its ID")
    fun getNode(
        @ToolParam(description = "The ID of the node to retrieve") nodeId: String
    ): String {
        val node = editorStateService.getNode(nodeId)
            ?: return mapper.writeValueAsString(mapOf("error" to "Node not found: $nodeId"))
        return mapper.writeValueAsString(node)
    }

    @Tool(description = "Create a new node in the graph with a given type, position, and data properties")
    fun createNode(
        @ToolParam(description = "Node type: agent, tool, builtin-tool, sub-agent, mcp-server, or planner-agent") type: String,
        @ToolParam(description = "X position on canvas") x: Double,
        @ToolParam(description = "Y position on canvas") y: Double,
        @ToolParam(description = "Node data properties as key-value pairs") data: Map<String, Any?>
    ): String {
        return try {
            val nodeId = editorStateService.createNode(type, x, y, data)
            mapper.writeValueAsString(mapOf("nodeId" to nodeId))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Create an edge connecting a source node to a target node")
    fun createEdge(
        @ToolParam(description = "ID of the source node (tool, builtin-tool, sub-agent, or mcp-server)") sourceId: String,
        @ToolParam(description = "ID of the target node (agent, sub-agent, or planner-agent)") targetId: String
    ): String {
        return try {
            val edgeId = editorStateService.createEdge(sourceId, targetId)
            mapper.writeValueAsString(mapOf("edgeId" to edgeId))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Update properties of an existing node by merging new data with existing data")
    fun updateNode(
        @ToolParam(description = "The ID of the node to update") nodeId: String,
        @ToolParam(description = "New data properties to merge into the node") data: Map<String, Any?>
    ): String {
        return try {
            editorStateService.updateNode(nodeId, data)
            mapper.writeValueAsString(mapOf("status" to "updated", "nodeId" to nodeId))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Delete a node and all its connected edges from the graph")
    fun deleteNode(
        @ToolParam(description = "The ID of the node to delete") nodeId: String
    ): String {
        return try {
            editorStateService.deleteNode(nodeId)
            mapper.writeValueAsString(mapOf("status" to "deleted", "nodeId" to nodeId))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Delete an edge from the graph")
    fun deleteEdge(
        @ToolParam(description = "The ID of the edge to delete") edgeId: String
    ): String {
        return try {
            editorStateService.deleteEdge(edgeId)
            mapper.writeValueAsString(mapOf("status" to "deleted", "edgeId" to edgeId))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Set the workspace name")
    fun setWorkspaceName(
        @ToolParam(description = "The new workspace name") name: String
    ): String {
        editorStateService.setWorkspaceName(name)
        return mapper.writeValueAsString(mapOf("status" to "renamed", "name" to name))
    }

    @Tool(description = "Reset the graph by clearing all nodes, edges, and resetting the workspace name")
    fun resetGraph(): String {
        editorStateService.resetGraph()
        return mapper.writeValueAsString(mapOf("status" to "reset"))
    }

    @Tool(description = "Apply a predefined template: basic-agent, agent-with-tools, agent-with-mcp, or planner-agent")
    fun applyTemplate(
        @ToolParam(description = "Template name: basic-agent, agent-with-tools, agent-with-mcp, or planner-agent") template: String
    ): String {
        editorStateService.resetGraph()
        return try {
            when (template) {
                "basic-agent" -> {
                    val agentId = editorStateService.createNode(
                        "agent", 300.0, 200.0,
                        mapOf("name" to "MyAgent", "systemPrompt" to "You are a helpful assistant.")
                    )
                    mapper.writeValueAsString(mapOf("template" to template, "nodesCreated" to 1, "agentId" to agentId))
                }

                "agent-with-tools" -> {
                    val agentId = editorStateService.createNode(
                        "agent", 300.0, 200.0,
                        mapOf("name" to "MyAgent", "systemPrompt" to "You are a helpful assistant.")
                    )
                    val tool1Id = editorStateService.createNode(
                        "tool", 100.0, 100.0,
                        mapOf("name" to "myTool", "description" to "A custom tool")
                    )
                    val tool2Id = editorStateService.createNode(
                        "builtin-tool", 100.0, 300.0,
                        mapOf("toolType" to "think")
                    )
                    editorStateService.createEdge(tool1Id, agentId)
                    editorStateService.createEdge(tool2Id, agentId)
                    mapper.writeValueAsString(mapOf("template" to template, "nodesCreated" to 3, "edgesCreated" to 2))
                }

                "agent-with-mcp" -> {
                    val agentId = editorStateService.createNode(
                        "agent", 300.0, 200.0,
                        mapOf("name" to "MyAgent", "systemPrompt" to "You are a helpful assistant.")
                    )
                    val mcpId = editorStateService.createNode(
                        "mcp-server", 100.0, 200.0,
                        mapOf("name" to "myMcpServer", "transportType" to "stdio", "command" to "npx", "args" to listOf("-y", "my-mcp-server"))
                    )
                    editorStateService.createEdge(mcpId, agentId)
                    mapper.writeValueAsString(mapOf("template" to template, "nodesCreated" to 2, "edgesCreated" to 1))
                }

                "planner-agent" -> {
                    val plannerId = editorStateService.createNode(
                        "planner-agent", 300.0, 200.0,
                        mapOf(
                            "name" to "MyPlanner",
                            "goals" to listOf("Complete task"),
                            "actions" to listOf("Execute step"),
                            "plannerType" to "llm"
                        )
                    )
                    mapper.writeValueAsString(mapOf("template" to template, "nodesCreated" to 1, "plannerId" to plannerId))
                }

                else -> mapper.writeValueAsString(mapOf("error" to "Unknown template: $template. Available: basic-agent, agent-with-tools, agent-with-mcp, planner-agent"))
            }
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }

    @Tool(description = "Validate the current graph and return any validation errors")
    fun validateGraph(): String {
        val graph = editorStateService.getGraph()
        val errors = graphValidator.validate(graph)
        return mapper.writeValueAsString(
            mapOf(
                "valid" to errors.isEmpty(),
                "errors" to errors
            )
        )
    }

    @Tool(description = "Export the current graph as generated Kotlin/Spring Boot code files")
    fun exportCode(): String {
        return try {
            val graph = editorStateService.getGraph()
            val (_, files) = exportService.exportWithFiles(graph)
            mapper.writeValueAsString(
                mapOf(
                    "status" to "exported",
                    "fileCount" to files.size,
                    "files" to files.keys.toList()
                )
            )
        } catch (e: ExportValidationException) {
            mapper.writeValueAsString(mapOf("error" to "Validation failed", "errors" to e.errors))
        } catch (e: IllegalArgumentException) {
            mapper.writeValueAsString(mapOf("error" to e.message))
        }
    }
}
