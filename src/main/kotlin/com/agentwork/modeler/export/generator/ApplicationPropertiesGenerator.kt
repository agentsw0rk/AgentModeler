package com.agentwork.modeler.export.generator

import org.springframework.stereotype.Component

@Component
class ApplicationPropertiesGenerator {
    fun generate(): String {
        return "server.port=\${SERVER_PORT:8080}\n"
    }
}
