package com.agentwork.modeler.export.model

data class KoogRagNode(
    val id: String,
    val name: String,
    val embedderProvider: String,
    val embedderModel: String,
    val apiKeyVariable: String,
    val storageType: String,
    val storagePath: String?,
    val customStorageClass: String?,
    val similarityThreshold: Double,
    val filePaths: List<String>,
    val urlSources: List<String>,
    val maxDocuments: Int,
    val injectAsContext: Boolean,
    val provideAsTool: Boolean,
    val toolDescription: String?
)
