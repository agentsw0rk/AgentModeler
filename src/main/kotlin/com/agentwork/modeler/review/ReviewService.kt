package com.agentwork.modeler.review

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ReviewService(
    private val reviewProvider: ReviewProvider,
    @Value("\${review.max-chars:100000}") private val maxChars: Int
) {
    private val coreFiles = listOf(
        "AgentConfig.kt", "ToolRegistry.kt", "build.gradle.kts",
        "settings.gradle.kts", "Application.kt", "ChatController.kt"
    )

    suspend fun review(files: Map<String, String>, graph: WorkspaceGraph): ReviewResult {
        val totalChars = files.values.sumOf { it.length }
        val reviewFiles: Map<String, String>
        val skipped: Int

        if (totalChars > maxChars) {
            reviewFiles = files.filter { (name, _) ->
                coreFiles.any { core -> name.endsWith(core) } ||
                name.contains("/subagents/") ||
                name.contains("/mcp/")
            }
            skipped = files.size - reviewFiles.size
        } else {
            reviewFiles = files
            skipped = 0
        }

        val summary = buildGraphSummary(graph)
        val request = ReviewRequest(files = reviewFiles, projectName = graph.name, graphSummary = summary)

        return try {
            val start = System.currentTimeMillis()
            val result = reviewProvider.review(request)
            val duration = System.currentTimeMillis() - start
            result.copy(metadata = result.metadata.copy(skippedFiles = skipped, durationMs = duration))
        } catch (e: Exception) {
            ReviewResult(
                status = ReviewStatus.UNAVAILABLE,
                summary = "Review unavailable: ${e.message}",
                findings = emptyList(),
                metadata = ReviewMetadata(model = "unknown", reviewedFiles = reviewFiles.size, skippedFiles = skipped, durationMs = 0)
            )
        }
    }

    private fun buildGraphSummary(graph: WorkspaceGraph): String {
        val agents = graph.nodes.count { it.type == "agent" }
        val tools = graph.nodes.count { it.type == "tool" }
        val builtins = graph.nodes.count { it.type == "builtin-tool" }
        val subAgents = graph.nodes.count { it.type == "sub-agent" }
        val mcps = graph.nodes.count { it.type == "mcp-server" }
        val parts = mutableListOf<String>()
        if (agents > 0) parts.add("$agents agent(s)")
        if (tools > 0) parts.add("$tools custom tool(s)")
        if (builtins > 0) parts.add("$builtins builtin tool(s)")
        if (subAgents > 0) parts.add("$subAgents sub-agent(s)")
        if (mcps > 0) parts.add("$mcps MCP server(s)")
        return parts.joinToString(", ")
    }
}
