plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}
val springAiVersion by extra("2.0.0-M3")

group = "com.agentwork"
version = "0.0.1-SNAPSHOT"
description = "modeler"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("ai.koog:koog-spring-boot-starter:1.3.0-beta")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.google.jimfs:jimfs:1.3.0")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("com.squareup:kotlinpoet:2.2.0")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.mockito:mockito-core:5.+")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// End-to-end tests against a real local Ollama are slow and need `ollama serve`, so they
// only run via `./gradlew ollamaE2eTest` (see OllamaShellToolE2ETest).
tasks.test {
    useJUnitPlatform { excludeTags("ollama-e2e") }
}

val ollamaE2eTest by tasks.registering(Test::class) {
    description = "Runs end-to-end tests against a local Ollama instance."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ollama-e2e") }
    // The modeler's Spring context needs an OpenAI key bean; the e2e test itself never calls OpenAI
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "not-used-by-e2e")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
