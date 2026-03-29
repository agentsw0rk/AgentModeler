package com.agentwork.modeler.build

import org.springframework.stereotype.Component

@Component
class GradleErrorParser {

    private val errorPattern = Regex("""^(e|w): file:///project/(\S+):(\d+):(\d+) (.+)$""", RegexOption.MULTILINE)

    fun parse(output: String, fileToNodeMap: Map<String, String>): List<BuildError> {
        return errorPattern.findAll(output).map { match ->
            val severity = if (match.groupValues[1] == "e") "error" else "warning"
            val file = match.groupValues[2]
            val line = match.groupValues[3].toIntOrNull() ?: 0
            val column = match.groupValues[4].toIntOrNull() ?: 0
            val message = match.groupValues[5]
            val nodeId = fileToNodeMap[file]

            BuildError(
                file = file,
                line = line,
                column = column,
                message = message,
                severity = severity,
                nodeId = nodeId
            )
        }.toList()
    }
}
