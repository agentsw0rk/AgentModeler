package com.agentwork.modeler.vfs

import com.agentwork.modeler.vfs.model.FileContent
import com.agentwork.modeler.vfs.model.FileTreeNode
import com.agentwork.modeler.vfs.model.FileType
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class ProjectNotFoundException(projectName: String) : RuntimeException("Project not found: $projectName")
class FileNotFoundException(path: String) : RuntimeException("File not found: $path")
class InvalidPathException(path: String) : RuntimeException("Invalid path: $path")

data class ManagedProject(
    val fileSystem: FileSystem,
    val root: Path,
    var generatedAt: Instant = Instant.now(),
    var lastAccess: Instant = Instant.now()
)

@Service
class VirtualFileSystemService {

    companion object {
        private const val TTL_MINUTES = 30L
        private const val MAX_PROJECTS = 10
    }

    private val projects = ConcurrentHashMap<String, ManagedProject>()

    fun writeFiles(projectName: String, files: Map<String, String>) {
        val managed = projects.compute(projectName) { _, existing ->
            if (existing != null) {
                clearDirectory(existing.root)
                existing.generatedAt = Instant.now()
                existing.lastAccess = Instant.now()
                existing
            } else {
                if (projects.size >= MAX_PROJECTS) {
                    throw RuntimeException("Too many active projects (max $MAX_PROJECTS)")
                }
                val fs = Jimfs.newFileSystem(Configuration.unix())
                val root = fs.getPath("/$projectName")
                Files.createDirectories(root)
                ManagedProject(fileSystem = fs, root = root)
            }
        }!!

        files.forEach { (path, content) ->
            val filePath = managed.root.resolve(path)
            Files.createDirectories(filePath.parent)
            Files.writeString(filePath, content)
        }
    }

    fun getTree(projectName: String): FileTreeNode {
        val managed = getProject(projectName)
        return buildTree(managed.root, projectName)
    }

    fun getFileContent(projectName: String, path: String): FileContent {
        validatePath(path)
        val managed = getProject(projectName)
        managed.lastAccess = Instant.now()
        val filePath = managed.root.resolve(path)

        if (!filePath.isRegularFile()) {
            throw FileNotFoundException(path)
        }

        val content = filePath.readText()
        return FileContent(
            path = path,
            name = filePath.name,
            content = content,
            language = detectLanguage(filePath.name),
            size = content.length.toLong()
        )
    }

    fun exists(projectName: String): Boolean = projects.containsKey(projectName)

    fun getGeneratedAt(projectName: String): Instant? = projects[projectName]?.generatedAt

    fun deleteProject(projectName: String) {
        projects.remove(projectName)?.fileSystem?.close()
    }

    fun shutdown() {
        projects.values.forEach { it.fileSystem.close() }
        projects.clear()
    }

    @Scheduled(fixedRate = 300_000)
    fun cleanupExpired() {
        val now = Instant.now()
        projects.entries.removeIf { (_, managed) ->
            val expired = managed.lastAccess.plusSeconds(TTL_MINUTES * 60).isBefore(now)
            if (expired) managed.fileSystem.close()
            expired
        }
    }

    private fun getProject(projectName: String): ManagedProject {
        return projects[projectName] ?: throw ProjectNotFoundException(projectName)
    }

    private fun validatePath(path: String) {
        if (path.startsWith("/") || path.contains("..")) {
            throw InvalidPathException(path)
        }
    }

    private fun buildTree(dir: Path, displayName: String): FileTreeNode {
        val projectRoot = dir.root.resolve(dir.toString().split("/").filter { it.isNotEmpty() }.first())
        val children = Files.list(dir).use { stream ->
            stream.toList()
                .sortedWith(compareBy<Path> { if (it.isDirectory()) 0 else 1 }.thenBy { it.name })
                .map { child ->
                    if (child.isDirectory()) {
                        buildTree(child, child.name)
                    } else {
                        val relativePath = projectRoot.relativize(child).toString()
                        FileTreeNode(
                            name = child.name,
                            path = relativePath,
                            type = FileType.FILE,
                            children = null,
                            size = Files.size(child)
                        )
                    }
                }
        }
        val relativePath = if (dir == projectRoot) "" else projectRoot.relativize(dir).toString()
        return FileTreeNode(
            name = displayName,
            path = relativePath,
            type = FileType.DIRECTORY,
            children = children,
            size = null
        )
    }

    private fun clearDirectory(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .filter { it != dir }
            .forEach { Files.delete(it) }
    }

    private fun detectLanguage(fileName: String): String = when {
        fileName.endsWith(".gradle.kts") -> "kotlin"
        fileName.endsWith(".kt") -> "kotlin"
        fileName.endsWith(".kts") -> "kotlin"
        fileName == "Dockerfile" -> "dockerfile"
        fileName.endsWith(".properties") -> "properties"
        fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> "yaml"
        else -> "text"
    }
}
