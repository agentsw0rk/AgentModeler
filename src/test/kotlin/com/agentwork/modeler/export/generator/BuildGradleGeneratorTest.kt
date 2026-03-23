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
        assertContains(code, "packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    }

    @Test
    fun `includes core dependencies`() {
        val agent = KoogAgent("Agent1", "Help", ModelProvider.OPENAI, null, null, emptyList())
        val code = generator.generate(agent)
        assertContains(code, "spring-boot-starter-webflux")
        assertContains(code, "kotlin-reflect")
        assertContains(code, "kotlinx-coroutines-reactor")
        assertContains(code, "koog-spring-boot-starter")
        assertContains(code, "koog-agents")
    }
}
