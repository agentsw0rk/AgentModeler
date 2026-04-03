package com.agentwork.modeler.export.generator.lib

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class LibBuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.2.21"
        const val KOOG_VERSION = "0.7.2"
    }

    fun generate(agent: KoogAgent, targets: List<String>): String = buildString {
        appendLine("""plugins {""")
        appendLine("""    kotlin("multiplatform") version "$KOTLIN_VERSION"""")
        appendLine("""    kotlin("plugin.serialization") version "$KOTLIN_VERSION"""")
        appendLine("""    id("maven-publish")""")
        appendLine("""}""")
        appendLine()
        appendLine("""group = "com.example"""")
        appendLine("""version = "0.1.0"""")
        appendLine()

        val hasWasm = "wasmJs" in targets
        if (hasWasm) {
            appendLine("""@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)""")
        }
        appendLine("""kotlin {""")

        for (target in targets) {
            when (target) {
                "jvm" -> appendLine("""    jvm()""")
                "wasmJs" -> {
                    appendLine("""    wasmJs {""")
                    appendLine("""        nodejs()""")
                    appendLine("""    }""")
                }
                "js" -> {
                    appendLine("""    js(IR) {""")
                    appendLine("""        nodejs()""")
                    appendLine("""    }""")
                }
                "ios" -> {
                    appendLine("""    iosArm64()""")
                    appendLine("""    iosSimulatorArm64()""")
                }
            }
        }

        appendLine()
        appendLine("""    sourceSets {""")
        appendLine("""        commonMain.dependencies {""")
        appendLine("""            implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")""")
        if (agent.mcpServers.isNotEmpty()) {
            appendLine("""            implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
        }
        if (agent.modelProvider == ModelProvider.BEDROCK) {
            appendLine("""            implementation("ai.koog.prompt:prompt-executor-bedrock-client:$KOOG_VERSION")""")
        }
        if (agent.ragNodes.any { it.storageType.uppercase() == "POSTGRESQL" }) {
            appendLine("""            implementation("org.postgresql:postgresql:42.7.4")""")
            appendLine("""            implementation("com.pgvector:pgvector:0.1.6")""")
        }
        appendLine("""        }""")
        appendLine("""        commonTest.dependencies {""")
        appendLine("""            implementation(kotlin("test"))""")
        appendLine("""        }""")
        appendLine("""    }""")
        appendLine("""}""")
        appendLine()
        appendLine("""repositories {""")
        appendLine("""    mavenCentral()""")
        appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")""")
        appendLine("""}""")
    }
}
