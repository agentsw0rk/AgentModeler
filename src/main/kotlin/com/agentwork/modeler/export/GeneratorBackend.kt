package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.KoogAgent
import com.agentwork.modeler.export.model.KoogGraphAgent
import com.agentwork.modeler.export.model.KoogPlannerAgent

interface GeneratorBackend {
    val id: String
    val displayName: String
    val supportedPlatforms: List<String>

    /** If true, the backend remaps shared files to its own source layout and includes them in its output. */
    val remapsSharedFiles: Boolean get() = false

    fun generateAgentFiles(
        agent: KoogAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>

    fun generateGraphAgentFiles(
        agent: KoogGraphAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        strategyCall: String,
        inputClassName: String?,
        outputClassName: String?,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>

    fun generatePlannerAgentFiles(
        agent: KoogPlannerAgent,
        graphName: String,
        sharedFiles: Map<String, String>,
        config: BackendConfig = BackendConfig()
    ): Map<String, String>
}
