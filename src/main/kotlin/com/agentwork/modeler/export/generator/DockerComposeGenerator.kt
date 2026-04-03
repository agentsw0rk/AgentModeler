package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class DockerComposeGenerator {

    fun generate(agent: KoogAgent): String = buildString {
        val apiKeyEnvVar = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "OPENAI_API_KEY"
            ModelProvider.ANTHROPIC -> "ANTHROPIC_API_KEY"
            ModelProvider.GOOGLE -> "GOOGLE_API_KEY"
            ModelProvider.BEDROCK -> "AWS_BEDROCK_ACCESS_KEY"
        }

        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: pgvector/pgvector:pg17")
        appendLine("    environment:")
        appendLine("      POSTGRES_DB: ragdb")
        appendLine("      POSTGRES_USER: postgres")
        appendLine("      POSTGRES_PASSWORD: postgres")
        appendLine("    ports:")
        appendLine("      - \"5432:5432\"")
        appendLine("    volumes:")
        appendLine("      - pgdata:/var/lib/postgresql/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD-SHELL\", \"pg_isready -U postgres\"]")
        appendLine("      interval: 5s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        appendLine()
        appendLine("  app:")
        appendLine("    build: .")
        appendLine("    ports:")
        appendLine("      - \"8080:8080\"")
        appendLine("    environment:")
        appendLine("      PGVECTOR_HOST: db")
        appendLine("      PGVECTOR_PORT: \"5432\"")
        appendLine("      PGVECTOR_DB: ragdb")
        appendLine("      PGVECTOR_USER: postgres")
        appendLine("      PGVECTOR_PASSWORD: postgres")
        appendLine("      $apiKeyEnvVar: \${$apiKeyEnvVar}")
        if (agent.modelProvider == ModelProvider.BEDROCK) {
            appendLine("      AWS_BEDROCK_SECRET_ACCESS_KEY: \${AWS_BEDROCK_SECRET_ACCESS_KEY}")
            appendLine("      AWS_BEDROCK_REGION: \${AWS_BEDROCK_REGION:-us-west-2}")
        }
        appendLine("    depends_on:")
        appendLine("      db:")
        appendLine("        condition: service_healthy")
        appendLine()
        appendLine("volumes:")
        appendLine("  pgdata:")
    }
}
