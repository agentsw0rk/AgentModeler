package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class ApplicationPropertiesGenerator {
    fun generate(agent: KoogAgent): String {
        return buildString {
            appendLine("server.port=\${SERVER_PORT:8080}")
            if (agent.modelProvider == ModelProvider.BEDROCK) {
                appendLine("bedrock.access-key=\${AWS_BEDROCK_ACCESS_KEY:}")
                appendLine("bedrock.secret-key=\${AWS_BEDROCK_SECRET_ACCESS_KEY:}")
            } else {
                val providerKey = agent.modelProvider.name.lowercase()
                appendLine("${providerKey}.api-key=\${${agent.modelProvider.name}_API_KEY:}")
            }
            if (agent.otelConfig?.enabled == true) {
                val otel = agent.otelConfig!!
                appendLine()
                appendLine("# OpenTelemetry")
                if (otel.exporterType != "logging") {
                    val defaultEndpoint = if (otel.exporterType == "grpc") "http://localhost:4317" else "http://localhost:4318/v1/traces"
                    appendLine("otel.endpoint=${otel.endpoint ?: defaultEndpoint}")
                }
                appendLine("otel.service-name=${otel.serviceName ?: "ai.koog"}")
                appendLine("otel.service-version=${otel.serviceVersion ?: "1.0.0"}")
                otel.authorizationHeader?.let { appendLine("otel.authorization-header=$it") }
                appendLine("otel.verbose=${otel.verbose}")
            }
        }
    }
}
