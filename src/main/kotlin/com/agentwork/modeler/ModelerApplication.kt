package com.agentwork.modeler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ModelerApplication

fun main(args: Array<String>) {
    runApplication<ModelerApplication>(*args)
}
