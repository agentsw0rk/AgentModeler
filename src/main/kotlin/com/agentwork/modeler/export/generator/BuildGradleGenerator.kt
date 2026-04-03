package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import com.agentwork.modeler.export.model.OtelConfig
import org.springframework.stereotype.Component

@Component
class BuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.3.0"
        const val SPRING_BOOT_VERSION = "4.0.4"
        const val DEPENDENCY_MANAGEMENT_VERSION = "1.1.7"
        const val KOOG_VERSION = "0.7.2"
        const val SQLITE_VERSION = "3.47.2.0"
        const val OTEL_BOM_VERSION = "1.48.0"
    }

    fun generate(agent: KoogAgent): String {
        return buildString {
            appendLine("""plugins {""")
            appendLine("""    kotlin("jvm") version "$KOTLIN_VERSION"""")
            appendLine("""    kotlin("plugin.spring") version "$KOTLIN_VERSION"""")
            appendLine("""    id("org.springframework.boot") version "$SPRING_BOOT_VERSION"""")
            appendLine("""    id("io.spring.dependency-management") version "$DEPENDENCY_MANAGEMENT_VERSION"""")
            appendLine("""}""")
            appendLine()
            appendLine("""java {""")
            appendLine("""    toolchain {""")
            appendLine("""        languageVersion = JavaLanguageVersion.of(21)""")
            appendLine("""    }""")
            appendLine("""}""")
            appendLine()
            appendLine("""repositories {""")
            appendLine("""    mavenCentral()""")
            appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")""")
            appendLine("""}""")
            appendLine()
            appendLine("""dependencies {""")
            appendLine("""    implementation("org.springframework.boot:spring-boot-starter-webflux")""")
            appendLine("""    implementation("org.jetbrains.kotlin:kotlin-reflect")""")
            appendLine("""    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")""")
            appendLine("""    implementation("ai.koog:koog-spring-boot-starter:$KOOG_VERSION")""")
            appendLine("""    implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
            if (agent.builtinTools.any { it.type == BuiltinToolType.MEMORY }) {
                appendLine("""    implementation("org.xerial:sqlite-jdbc:$SQLITE_VERSION")""")
            }
            if (agent.mcpServers.isNotEmpty()) {
                appendLine("""    implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
            }
            if (agent.modelProvider == ModelProvider.BEDROCK) {
                appendLine("""    implementation("ai.koog.prompt:prompt-executor-bedrock-client:$KOOG_VERSION")""")
            }
            if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
                appendLine("""    implementation("org.postgresql:postgresql:42.7.4")""")
                appendLine("""    implementation("com.pgvector:pgvector:0.1.6")""")
            }
            if (agent.otelConfig?.enabled == true) {
                appendLine("""    implementation(platform("io.opentelemetry:opentelemetry-bom:$OTEL_BOM_VERSION"))""")
                appendLine("""    implementation("ai.koog:koog-opentelemetry:$KOOG_VERSION")""")
                appendLine("""    implementation("io.opentelemetry:opentelemetry-sdk")""")
                when (agent.otelConfig?.exporterType) {
                    "logging" -> appendLine("""    implementation("io.opentelemetry:opentelemetry-exporter-logging")""")
                    else -> appendLine("""    implementation("io.opentelemetry:opentelemetry-exporter-otlp")""")
                }
            }

            appendLine("""}""")
        }
    }
}
