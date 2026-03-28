package com.agentwork.modeler.export.model

data class KoogField(
    val name: String,
    val type: String,
    val description: String,
    val optional: Boolean
)

data class KoogInput(
    val className: String?,
    val fields: List<KoogField>
)

data class KoogOutput(
    val className: String?,
    val fields: List<KoogField>
)
