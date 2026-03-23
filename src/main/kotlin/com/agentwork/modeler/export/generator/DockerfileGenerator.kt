package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class DockerfileGenerator {
    fun generate(): String {
        return buildString {
            appendLine("# Stage 1: Build")
            appendLine("FROM gradle:8-jdk21 AS build")
            appendLine("WORKDIR /app")
            appendLine("COPY build.gradle.kts settings.gradle.kts gradle.properties ./")
            appendLine("RUN gradle dependencies --no-daemon")
            appendLine("COPY src/ src/")
            appendLine("RUN gradle bootJar --no-daemon")
            appendLine()
            appendLine("# Stage 2: Run")
            appendLine("FROM eclipse-temurin:21-jre")
            appendLine("WORKDIR /app")
            appendLine("COPY --from=build /app/build/libs/*.jar app.jar")
            appendLine("EXPOSE 8080")
            appendLine("""ENTRYPOINT ["java", "-jar", "app.jar"]""")
        }
    }
}
