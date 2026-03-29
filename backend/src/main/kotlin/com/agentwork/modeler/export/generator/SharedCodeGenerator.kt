package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogSharedCode

class SharedCodeGenerator {
    fun generate(sharedCode: KoogSharedCode): String {
        return buildString {
            appendLine("package generated")
            appendLine()
            append(sharedCode.code)
        }
    }
}
