package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent

interface GeneratorBackend {
    val id: String
    val displayName: String
    val supportedPlatforms: List<String>

    fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String>

    fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?
    ): Map<String, String>

    fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>
    ): Map<String, String>
}
