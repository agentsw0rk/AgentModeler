package com.agentwork.modeler.export.generator.ktor

import com.agentwork.modeler.export.model.KoogAgent
import org.springframework.stereotype.Component

@Component
class WasmBuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.3.0"
        const val KOOG_VERSION = "1.3.0"
        const val KTOR_VERSION = "3.1.3"
    }

    fun generate(agent: KoogAgent): String = buildString {
        appendLine("""plugins {""")
        appendLine("""    kotlin("multiplatform") version "$KOTLIN_VERSION"""")
        appendLine("""    kotlin("plugin.serialization") version "$KOTLIN_VERSION"""")
        appendLine("""}""")
        appendLine()
        appendLine("""@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)""")
        appendLine("""kotlin {""")
        appendLine("""    wasmJs {""")
        appendLine("""        nodejs()""")
        appendLine("""        binaries.executable()""")
        appendLine("""    }""")
        appendLine()
        appendLine("""    sourceSets {""")
        appendLine("""        wasmJsMain.dependencies {""")
        appendLine("""            implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-server-core:$KTOR_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-server-content-negotiation:$KTOR_VERSION")""")
        appendLine("""            implementation("io.ktor:ktor-serialization-kotlinx-json:$KTOR_VERSION")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")""")
        appendLine("""            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")""")
        if (agent.mcpServers.isNotEmpty()) {
            appendLine("""            implementation("ai.koog:agents-ext-mcp:$KOOG_VERSION")""")
        }
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
