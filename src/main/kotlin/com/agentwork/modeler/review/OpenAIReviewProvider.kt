package com.agentwork.modeler.review

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OpenAIReviewProvider(
    chatClientBuilder: ChatClient.Builder,
    @Value("\${review.model:gpt-4o}") private val model: String
) : ReviewProvider {

    private val chatClient = chatClientBuilder.build()
    private val objectMapper = ObjectMapper()

    override suspend fun review(request: ReviewRequest): ReviewResult {
        val startTime = System.currentTimeMillis()

        val systemPrompt = """
            You are an expert Kotlin code reviewer specializing in the Koog AI agent framework.
            Review the provided generated code and return a JSON response with this exact structure:
            {
              "status": "PASS" | "WARNING" | "CRITICAL",
              "summary": "brief overall assessment",
              "findings": [
                {
                  "severity": "CRITICAL" | "WARNING" | "SUGGESTION",
                  "category": "correctness" | "structure" | "dependencies" | "wiring" | "readability" | "koog-specific",
                  "file": "filename or null for cross-file issues",
                  "message": "description of the finding"
                }
              ]
            }
            Set status to CRITICAL if any finding is CRITICAL, WARNING if any is WARNING, PASS if all are SUGGESTION or no findings.
            Be concise. Focus on real issues, not style preferences.
            Return ONLY valid JSON, no markdown fences.
        """.trimIndent()

        val userMessage = buildString {
            appendLine("Project: ${request.projectName}")
            appendLine("Graph: ${request.graphSummary}")
            appendLine()
            appendLine("Generated files:")
            for ((filename, content) in request.files) {
                appendLine("--- $filename ---")
                appendLine(content)
                appendLine()
            }
        }

        val response = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content() ?: throw RuntimeException("Empty LLM response")

        val durationMs = System.currentTimeMillis() - startTime
        return parseResponse(response, request.files.size, durationMs)
    }

    private fun parseResponse(response: String, fileCount: Int, durationMs: Long): ReviewResult {
        return try {
            val json = objectMapper.readTree(response)
            val status = ReviewStatus.valueOf(json["status"]?.asText() ?: "PASS")
            val summary = json["summary"]?.asText() ?: "No summary provided"
            val findings = json["findings"]?.map { node ->
                ReviewFinding(
                    severity = FindingSeverity.valueOf(node["severity"]?.asText() ?: "SUGGESTION"),
                    category = node["category"]?.asText() ?: "general",
                    file = node["file"]?.asText(),
                    message = node["message"]?.asText() ?: ""
                )
            } ?: emptyList()

            ReviewResult(
                status = status,
                summary = summary,
                findings = findings,
                metadata = ReviewMetadata(
                    model = model,
                    reviewedFiles = fileCount,
                    skippedFiles = 0,
                    durationMs = durationMs
                )
            )
        } catch (e: Exception) {
            ReviewResult(
                status = ReviewStatus.WARNING,
                summary = "Review completed but response parsing was partial: ${e.message}",
                findings = emptyList(),
                metadata = ReviewMetadata(
                    model = model,
                    reviewedFiles = fileCount,
                    skippedFiles = 0,
                    durationMs = durationMs
                )
            )
        }
    }
}
