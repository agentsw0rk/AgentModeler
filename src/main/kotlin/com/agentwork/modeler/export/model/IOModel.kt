package com.agentwork.modeler.export.model

data class KoogInput(
    val name: String,
    val description: String,
    val dataType: String,
    val required: Boolean,
    val defaultValue: String?
)

data class KoogOutput(
    val name: String,
    val description: String,
    val dataType: String
)
