package com.agentwork.modeler.vfs.model

enum class FileType { FILE, DIRECTORY }

data class FileTreeNode(
    val name: String,
    val path: String,
    val type: FileType,
    val children: List<FileTreeNode>?,
    val size: Long?
)
