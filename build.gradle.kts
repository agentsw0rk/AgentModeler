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
    implementation("ai.koog:koog-spring-boot-starter:0.7.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.google.jimfs:jimfs:1.3.0")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
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
