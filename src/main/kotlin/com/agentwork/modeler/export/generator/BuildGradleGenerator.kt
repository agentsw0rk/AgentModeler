package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.ModelProvider
import org.springframework.stereotype.Component

@Component
class BuildGradleGenerator {

    companion object {
        const val KOTLIN_VERSION = "2.2.21"
        const val SPRING_BOOT_VERSION = "4.0.4"
        const val DEPENDENCY_MANAGEMENT_VERSION = "1.1.7"
        const val KOOG_VERSION = "0.7.2"
    }

    fun generate(agent: KoogAgent): String {
        val providerDep = when (agent.modelProvider) {
            ModelProvider.OPENAI -> "koog-prompt-executor-openai"
            ModelProvider.ANTHROPIC -> "koog-prompt-executor-anthropic"
            ModelProvider.GOOGLE -> "koog-prompt-executor-google"
        }

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
            appendLine("""    maven("https://packages.jetbrains.team/maven/p/grazi/koog-public")""")
            appendLine("""}""")
            appendLine()
            appendLine("""dependencies {""")
            appendLine("""    implementation("org.springframework.boot:spring-boot-starter-webflux")""")
            appendLine("""    implementation("org.jetbrains.kotlin:kotlin-reflect")""")
            appendLine("""    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")""")
            appendLine("""    implementation("ai.koog:koog-agents:$KOOG_VERSION")""")
            appendLine("""    implementation("ai.koog:$providerDep:$KOOG_VERSION")""")
            appendLine("""}""")
        }
    }
}
