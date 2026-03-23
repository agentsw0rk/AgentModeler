package com.agentwork.modeler.export.generator

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class DockerfileGeneratorTest {
    private val generator = DockerfileGenerator()

    @Test
    fun `generates multi-stage Dockerfile`() {
        val code = generator.generate()
        assertContains(code, "FROM gradle:8-jdk21 AS build")
        assertContains(code, "gradle bootJar")
        assertContains(code, "FROM eclipse-temurin:21-jre")
        assertContains(code, "COPY --from=build")
        assertContains(code, "EXPOSE 8080")
        assertContains(code, """ENTRYPOINT ["java", "-jar", "app.jar"]""")
    }
}
