package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class BuildGradleGeneratorTest {
    private val generator = BuildGradleGenerator()

    @Test
    fun `generates build file with Spring Boot and Koog plugins`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "kotlin(\"jvm\")")
        assertContains(code, "kotlin(\"plugin.spring\")")
        assertContains(code, "org.springframework.boot")
        assertContains(code, "io.spring.dependency-management")
    }

    @Test
    fun `includes Koog repository`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "mavenCentral()")
        assertContains(code, "packages.jetbrains.team/maven/p/grazi/koog-public")
    }

    @Test
    fun `includes core dependencies`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "spring-boot-starter-webflux")
        assertContains(code, "kotlin-reflect")
        assertContains(code, "kotlinx-coroutines-reactor")
        assertContains(code, "koog-agents")
    }

    @Test
    fun `includes OpenAI executor dependency for OpenAI provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-openai")
    }

    @Test
    fun `includes Anthropic executor dependency for Anthropic provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.ANTHROPIC, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-anthropic")
    }

    @Test
    fun `includes Google executor dependency for Google provider`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.GOOGLE, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "koog-prompt-executor-google")
    }
}
