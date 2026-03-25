package com.agentwork.modeler.vfs.model

data class FileContent(
    val path: String,
    val name: String,
    val content: String,
    val language: String,
    val size: Long
)
