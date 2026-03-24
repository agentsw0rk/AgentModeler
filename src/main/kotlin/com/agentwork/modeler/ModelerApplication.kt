package com.agentwork.modeler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ModelerApplication

fun main(args: Array<String>) {
    runApplication<ModelerApplication>(*args)
}
