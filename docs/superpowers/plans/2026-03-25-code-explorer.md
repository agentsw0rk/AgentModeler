# Code Explorer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a virtual file system-backed code explorer that shows generated code in real-time as users build their agent graph.

**Architecture:** Activity Bar + left sidebar panel with file tree, modal code viewer with tabs and Shiki highlighting. Backend uses jimfs per project name, with dedicated REST endpoints for generation/tree/file access. Hybrid update strategy with 800ms debounce when panel is open.

**Tech Stack:** jimfs 1.3.0 (backend VFS), Shiki (frontend syntax highlighting), Zustand (state), Spring Boot REST endpoints.

**Spec:** `docs/superpowers/specs/2026-03-25-code-explorer-design.md`

---

### Task 1: Add jimfs dependency

**Files:**
- Modify: `build.gradle.kts:24-34`

- [ ] **Step 1: Add jimfs dependency to build.gradle.kts**

Add after the `jackson-module-kotlin` line in the dependencies block:

```kotlin
implementation("com.google.jimfs:jimfs:1.3.0")
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add jimfs dependency for virtual file system"
```

---

### Task 2: VFS DTOs

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/model/FileTreeNode.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/model/FileContent.kt`
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/model/GenerationResult.kt`

- [ ] **Step 1: Create FileTreeNode and FileType**

```kotlin
package com.agentwork.modeler.vfs.model

enum class FileType { FILE, DIRECTORY }

data class FileTreeNode(
    val name: String,
    val path: String,
    val type: FileType,
    val children: List<FileTreeNode>?,
    val size: Long?
)
```

- [ ] **Step 2: Create FileContent**

```kotlin
package com.agentwork.modeler.vfs.model

data class FileContent(
    val path: String,
    val name: String,
    val content: String,
    val language: String,
    val size: Long
)
```

- [ ] **Step 3: Create GenerationResult**

```kotlin
package com.agentwork.modeler.vfs.model

enum class GenerationStatus { VALID, INVALID }

data class GenerationResult(
    val status: GenerationStatus,
    val fileCount: Int? = null,
    val generatedAt: String? = null,
    val errors: List<String>? = null
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/vfs/
git commit -m "feat(vfs): add DTOs for file tree, file content, and generation result"
```

---

### Task 3: VirtualFileSystemService — core with tests

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemService.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemServiceTest.kt`

- [ ] **Step 1: Write failing tests for VirtualFileSystemService**

```kotlin
package com.agentwork.modeler.vfs

import com.agentwork.modeler.vfs.model.FileType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VirtualFileSystemServiceTest {

    private val service = VirtualFileSystemService()

    @AfterEach
    fun cleanup() {
        service.shutdown()
    }

    @Test
    fun `writeFiles creates project and files`() {
        val files = mapOf(
            "src/main/kotlin/Main.kt" to "fun main() {}",
            "build.gradle.kts" to "plugins {}"
        )
        service.writeFiles("my-project", files)
        assertTrue(service.exists("my-project"))
    }

    @Test
    fun `getTree returns correct hierarchy`() {
        val files = mapOf(
            "src/main/kotlin/generated/AgentConfig.kt" to "class AgentConfig",
            "src/main/kotlin/generated/Application.kt" to "class Application",
            "build.gradle.kts" to "plugins {}"
        )
        service.writeFiles("my-project", files)
        val tree = service.getTree("my-project")

        assertEquals("my-project", tree.name)
        assertEquals(FileType.DIRECTORY, tree.type)
        assertNotNull(tree.children)
        // root should have "src" directory and "build.gradle.kts" file
        val names = tree.children!!.map { it.name }.toSet()
        assertTrue(names.contains("src"))
        assertTrue(names.contains("build.gradle.kts"))
    }

    @Test
    fun `getTree sorts directories before files`() {
        val files = mapOf(
            "zebra.kt" to "z",
            "alpha/file.kt" to "a"
        )
        service.writeFiles("my-project", files)
        val tree = service.getTree("my-project")
        val children = tree.children!!
        assertEquals(FileType.DIRECTORY, children[0].type)
        assertEquals(FileType.FILE, children[1].type)
    }

    @Test
    fun `getFileContent returns content and metadata`() {
        val code = "fun main() { println(\"hello\") }"
        service.writeFiles("my-project", mapOf("src/Main.kt" to code))
        val content = service.getFileContent("my-project", "src/Main.kt")

        assertEquals("Main.kt", content.name)
        assertEquals("src/Main.kt", content.path)
        assertEquals(code, content.content)
        assertEquals("kotlin", content.language)
        assertEquals(code.length.toLong(), content.size)
    }

    @Test
    fun `getFileContent detects language from extension`() {
        service.writeFiles("my-project", mapOf(
            "build.gradle.kts" to "plugins {}",
            "Dockerfile" to "FROM java",
            "app.properties" to "key=val",
            "settings.gradle.kts" to "rootProject"
        ))
        assertEquals("kotlin", service.getFileContent("my-project", "build.gradle.kts").language)
        assertEquals("dockerfile", service.getFileContent("my-project", "Dockerfile").language)
        assertEquals("properties", service.getFileContent("my-project", "app.properties").language)
        assertEquals("kotlin", service.getFileContent("my-project", "settings.gradle.kts").language)
    }

    @Test
    fun `repeated writeFiles overwrites previous content`() {
        service.writeFiles("my-project", mapOf("Main.kt" to "version1"))
        service.writeFiles("my-project", mapOf("Main.kt" to "version2"))
        assertEquals("version2", service.getFileContent("my-project", "Main.kt").content)
    }

    @Test
    fun `repeated writeFiles removes files not in new map`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a", "B.kt" to "b"))
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        val tree = service.getTree("my-project")
        val names = tree.children!!.map { it.name }
        assertTrue(names.contains("A.kt"))
        assertFalse(names.contains("B.kt"))
    }

    @Test
    fun `getTree throws for non-existent project`() {
        assertThrows<ProjectNotFoundException> {
            service.getTree("nonexistent")
        }
    }

    @Test
    fun `getFileContent throws for non-existent project`() {
        assertThrows<ProjectNotFoundException> {
            service.getFileContent("nonexistent", "file.kt")
        }
    }

    @Test
    fun `getFileContent throws for non-existent file`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        assertThrows<FileNotFoundException> {
            service.getFileContent("my-project", "B.kt")
        }
    }

    @Test
    fun `path traversal is rejected`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        assertThrows<InvalidPathException> {
            service.getFileContent("my-project", "../etc/passwd")
        }
    }

    @Test
    fun `absolute path is rejected`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        assertThrows<InvalidPathException> {
            service.getFileContent("my-project", "/etc/passwd")
        }
    }

    @Test
    fun `deleteProject removes project`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        service.deleteProject("my-project")
        assertFalse(service.exists("my-project"))
    }

    @Test
    fun `exists returns false for unknown project`() {
        assertFalse(service.exists("unknown"))
    }

    @Test
    fun `getGeneratedAt returns timestamp after write`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        val ts = service.getGeneratedAt("my-project")
        assertNotNull(ts)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.VirtualFileSystemServiceTest"`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement VirtualFileSystemService**

```kotlin
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
import kotlin.io.path.name
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.fileSize

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
        val managed = projects.compute(projectName) { name, existing ->
            if (existing != null) {
                // Clear existing files
                clearDirectory(existing.root)
                existing.generatedAt = Instant.now()
                existing.lastAccess = Instant.now()
                existing
            } else {
                if (projects.size >= MAX_PROJECTS) {
                    throw RuntimeException("Too many active projects (max $MAX_PROJECTS)")
                }
                val fs = Jimfs.newFileSystem(name, Configuration.unix())
                val root = fs.getPath("/$name")
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
        val children = Files.list(dir).use { stream ->
            stream.toList()
                .sortedWith(compareBy<Path> { if (it.isDirectory()) 0 else 1 }.thenBy { it.name })
                .map { child ->
                    if (child.isDirectory()) {
                        buildTree(child, child.name)
                    } else {
                        FileTreeNode(
                            name = child.name,
                            path = child.toString().removePrefix("/${dir.toString().split("/")[1]}/"),
                            type = FileType.FILE,
                            children = null,
                            size = child.fileSize()
                        )
                    }
                }
        }
        // Compute relative path for directory (strip project root prefix)
        val rootPrefix = "/${dir.toString().split("/")[1]}/"
        val relativePath = dir.toString().removePrefix(rootPrefix)
        return FileTreeNode(
            name = displayName,
            path = if (dir.toString() == "/${dir.toString().split("/")[1]}") "" else relativePath,
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
        fileName.endsWith(".kt") -> "kotlin"
        fileName.endsWith(".kts") -> "kotlin"
        fileName.endsWith(".gradle.kts") -> "kotlin"
        fileName == "Dockerfile" -> "dockerfile"
        fileName.endsWith(".properties") -> "properties"
        fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> "yaml"
        else -> "text"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.VirtualFileSystemServiceTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/vfs/ src/test/kotlin/com/agentwork/modeler/vfs/
git commit -m "feat(vfs): add VirtualFileSystemService with jimfs backend and tests"
```

---

### Task 4: CodeGenerationService with tests

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/vfs/CodeGenerationServiceTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.vfs.model.GenerationStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeGenerationServiceTest {

    private val exportService = mock(ExportService::class.java)
    private val vfsService = VirtualFileSystemService()
    private val validator = GraphValidator()
    private val service = CodeGenerationService(exportService, vfsService, validator)

    private val validGraph = ProjectGraph(
        version = 1,
        name = "test-project",
        nodes = listOf(
            GraphNode(
                id = "agent-1",
                type = "agent",
                position = Position(0.0, 0.0),
                data = mapOf("name" to "TestAgent", "systemPrompt" to "You are helpful", "modelProvider" to "OPENAI")
            )
        ),
        edges = emptyList()
    )

    private val invalidGraph = ProjectGraph(
        version = 1,
        name = "test-project",
        nodes = emptyList(),
        edges = emptyList()
    )

    @AfterEach
    fun cleanup() {
        vfsService.shutdown()
    }

    @Test
    fun `valid graph generates files and writes to VFS`() {
        val files = mapOf("Main.kt" to "fun main() {}")
        `when`(exportService.generateFiles(validGraph)).thenReturn(files)

        val result = service.generateForProject("test-project", validGraph)

        assertEquals(GenerationStatus.VALID, result.status)
        assertEquals(1, result.fileCount)
        assertNotNull(result.generatedAt)
        assertNull(result.errors)
        assertTrue(vfsService.exists("test-project"))
    }

    @Test
    fun `invalid graph returns errors and does not write to VFS`() {
        val result = service.generateForProject("test-project", invalidGraph)

        assertEquals(GenerationStatus.INVALID, result.status)
        assertNull(result.fileCount)
        assertNotNull(result.errors)
        assertTrue(result.errors!!.isNotEmpty())
    }

    @Test
    fun `repeated generation overwrites previous state`() {
        val files1 = mapOf("Main.kt" to "version1")
        val files2 = mapOf("Main.kt" to "version2")
        `when`(exportService.generateFiles(validGraph)).thenReturn(files1).thenReturn(files2)

        service.generateForProject("test-project", validGraph)
        service.generateForProject("test-project", validGraph)

        val content = vfsService.getFileContent("test-project", "Main.kt")
        assertEquals("version2", content.content)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.CodeGenerationServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement CodeGenerationService**

```kotlin
package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.vfs.model.GenerationResult
import com.agentwork.modeler.vfs.model.GenerationStatus
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CodeGenerationService(
    private val exportService: ExportService,
    private val vfsService: VirtualFileSystemService,
    private val graphValidator: GraphValidator
) {
    fun generateForProject(projectName: String, graph: ProjectGraph): GenerationResult {
        val errors = graphValidator.validate(graph)
        if (errors.isNotEmpty()) {
            return GenerationResult(
                status = GenerationStatus.INVALID,
                errors = errors
            )
        }

        val files = exportService.generateFiles(graph)
        vfsService.writeFiles(projectName, files)

        return GenerationResult(
            status = GenerationStatus.VALID,
            fileCount = files.size,
            generatedAt = Instant.now().toString()
        )
    }
}
```

Note: `ExportService.generateFiles()` is currently `internal`. You need to change its visibility to `public` (or remove the `internal` modifier) in `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt:42`.

- [ ] **Step 4: Change generateFiles visibility**

In `src/main/kotlin/com/agentwork/modeler/export/ExportService.kt`, change line 42 from:
```kotlin
internal fun generateFiles(graph: ProjectGraph): Map<String, String> {
```
to:
```kotlin
fun generateFiles(graph: ProjectGraph): Map<String, String> {
```

- [ ] **Step 5: Add mockito-kotlin test dependency**

In `build.gradle.kts`, add to dependencies:
```kotlin
testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.CodeGenerationServiceTest"`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/vfs/CodeGenerationService.kt \
        src/test/kotlin/com/agentwork/modeler/vfs/CodeGenerationServiceTest.kt \
        src/main/kotlin/com/agentwork/modeler/export/ExportService.kt \
        build.gradle.kts
git commit -m "feat(vfs): add CodeGenerationService with validation and generation orchestration"
```

---

### Task 5: VirtualFileSystemController with tests

**Files:**
- Create: `src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt`
- Create: `src/test/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemControllerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.vfs.model.GenerationResult
import com.agentwork.modeler.vfs.model.GenerationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.bean.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import com.agentwork.modeler.vfs.model.FileTreeNode
import com.agentwork.modeler.vfs.model.FileType
import com.agentwork.modeler.vfs.model.FileContent
import java.time.Instant

@WebMvcTest(VirtualFileSystemController::class)
class VirtualFileSystemControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var codeGenerationService: CodeGenerationService

    @MockBean
    private lateinit var vfsService: VirtualFileSystemService

    private val validGraph = ProjectGraph(
        version = 1,
        name = "test-project",
        nodes = listOf(
            GraphNode("a1", "agent", Position(0.0, 0.0),
                mapOf("name" to "Agent", "systemPrompt" to "Hello", "modelProvider" to "OPENAI"))
        ),
        edges = emptyList()
    )

    @Test
    fun `POST generate with valid graph returns VALID`() {
        val result = GenerationResult(GenerationStatus.VALID, fileCount = 5, generatedAt = Instant.now().toString())
        `when`(codeGenerationService.generateForProject("test-project", validGraph)).thenReturn(result)

        mockMvc.perform(
            post("/api/vfs/test-project/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validGraph))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("VALID"))
            .andExpect(jsonPath("$.fileCount").value(5))
    }

    @Test
    fun `POST generate with invalid graph returns INVALID`() {
        val result = GenerationResult(GenerationStatus.INVALID, errors = listOf("No agent found"))
        `when`(codeGenerationService.generateForProject("test-project", validGraph)).thenReturn(result)

        mockMvc.perform(
            post("/api/vfs/test-project/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validGraph))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INVALID"))
            .andExpect(jsonPath("$.errors[0]").value("No agent found"))
    }

    @Test
    fun `GET tree returns file tree`() {
        val tree = FileTreeNode("test-project", "", FileType.DIRECTORY,
            listOf(FileTreeNode("Main.kt", "Main.kt", FileType.FILE, null, 100L)), null)
        `when`(vfsService.getTree("test-project")).thenReturn(tree)
        `when`(vfsService.getGeneratedAt("test-project")).thenReturn(Instant.parse("2026-03-25T10:00:00Z"))

        mockMvc.perform(get("/api/vfs/test-project/tree"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.root.name").value("test-project"))
            .andExpect(jsonPath("$.generatedAt").value("2026-03-25T10:00:00Z"))
    }

    @Test
    fun `GET tree for unknown project returns 404`() {
        `when`(vfsService.getTree("unknown")).thenThrow(ProjectNotFoundException("unknown"))

        mockMvc.perform(get("/api/vfs/unknown/tree"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET file returns content`() {
        val content = FileContent("src/Main.kt", "Main.kt", "fun main() {}", "kotlin", 14L)
        `when`(vfsService.getFileContent("test-project", "src/Main.kt")).thenReturn(content)

        mockMvc.perform(get("/api/vfs/test-project/files").param("path", "src/Main.kt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Main.kt"))
            .andExpect(jsonPath("$.language").value("kotlin"))
    }

    @Test
    fun `GET file with path traversal returns 400`() {
        `when`(vfsService.getFileContent("test-project", "../etc/passwd"))
            .thenThrow(InvalidPathException("../etc/passwd"))

        mockMvc.perform(get("/api/vfs/test-project/files").param("path", "../etc/passwd"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET file not found returns 404`() {
        `when`(vfsService.getFileContent("test-project", "missing.kt"))
            .thenThrow(FileNotFoundException("missing.kt"))

        mockMvc.perform(get("/api/vfs/test-project/files").param("path", "missing.kt"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE project returns 204`() {
        mockMvc.perform(delete("/api/vfs/test-project"))
            .andExpect(status().isNoContent)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.VirtualFileSystemControllerTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement VirtualFileSystemController**

```kotlin
package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.vfs.model.GenerationResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vfs")
class VirtualFileSystemController(
    private val codeGenerationService: CodeGenerationService,
    private val vfsService: VirtualFileSystemService
) {

    @PostMapping("/{projectName}/generate")
    fun generate(
        @PathVariable projectName: String,
        @RequestBody graph: ProjectGraph
    ): GenerationResult {
        return codeGenerationService.generateForProject(projectName, graph)
    }

    @GetMapping("/{projectName}/tree")
    fun getTree(@PathVariable projectName: String): Map<String, Any?> {
        val tree = vfsService.getTree(projectName)
        val generatedAt = vfsService.getGeneratedAt(projectName)
        return mapOf("root" to tree, "generatedAt" to generatedAt?.toString())
    }

    @GetMapping("/{projectName}/files")
    fun getFile(
        @PathVariable projectName: String,
        @RequestParam path: String
    ) = vfsService.getFileContent(projectName, path)

    @DeleteMapping("/{projectName}")
    fun deleteProject(@PathVariable projectName: String): ResponseEntity<Void> {
        vfsService.deleteProject(projectName)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(ProjectNotFoundException::class)
    fun handleProjectNotFound(e: ProjectNotFoundException) =
        ResponseEntity.notFound().build<Void>()

    @ExceptionHandler(FileNotFoundException::class)
    fun handleFileNotFound(e: FileNotFoundException) =
        ResponseEntity.notFound().build<Void>()

    @ExceptionHandler(InvalidPathException::class)
    fun handleInvalidPath(e: InvalidPathException) =
        ResponseEntity.badRequest().build<Void>()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.agentwork.modeler.vfs.VirtualFileSystemControllerTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemController.kt \
        src/test/kotlin/com/agentwork/modeler/vfs/VirtualFileSystemControllerTest.kt
git commit -m "feat(vfs): add VirtualFileSystemController with REST endpoints and tests"
```

---

### Task 6: Install shiki frontend dependency

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install shiki**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npm install shiki`

- [ ] **Step 2: Verify installation**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && node -e "require('shiki')"`
Expected: No error

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "build: add shiki dependency for syntax highlighting"
```

---

### Task 7: Frontend types and VFS API client

**Files:**
- Create: `frontend/types/vfs.ts`
- Create: `frontend/lib/vfs-client.ts`

- [ ] **Step 1: Create VFS types**

```typescript
export enum FileType {
  FILE = "FILE",
  DIRECTORY = "DIRECTORY",
}

export interface FileTreeNode {
  name: string;
  path: string;
  type: FileType;
  children: FileTreeNode[] | null;
  size: number | null;
}

export interface FileContent {
  path: string;
  name: string;
  content: string;
  language: string;
  size: number;
}

export enum GenerationStatus {
  VALID = "VALID",
  INVALID = "INVALID",
}

export interface GenerationResult {
  status: GenerationStatus;
  fileCount: number | null;
  generatedAt: string | null;
  errors: string[] | null;
}

export interface OpenTab {
  path: string;
  name: string;
  language: string;
}
```

- [ ] **Step 2: Create VFS API client**

```typescript
import { ProjectGraph } from "@/types/graph";
import { GenerationResult, FileTreeNode, FileContent } from "@/types/vfs";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function generateCode(
  projectName: string,
  graph: ProjectGraph
): Promise<GenerationResult> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/generate`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(graph),
    }
  );
  if (!response.ok) {
    throw new Error(`Generation failed: ${response.statusText}`);
  }
  return response.json();
}

export async function getFileTree(
  projectName: string,
  signal?: AbortSignal
): Promise<{ root: FileTreeNode; generatedAt: string }> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/tree`,
    { signal }
  );
  if (!response.ok) {
    throw new Error(`Failed to get file tree: ${response.statusText}`);
  }
  return response.json();
}

export async function getFileContent(
  projectName: string,
  path: string,
  signal?: AbortSignal
): Promise<FileContent> {
  const response = await fetch(
    `${API_BASE}/api/vfs/${encodeURIComponent(projectName)}/files?path=${encodeURIComponent(path)}`,
    { signal }
  );
  if (!response.ok) {
    throw new Error(`Failed to get file: ${response.statusText}`);
  }
  return response.json();
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/types/vfs.ts frontend/lib/vfs-client.ts
git commit -m "feat(vfs): add frontend VFS types and API client"
```

---

### Task 8: codeExplorerStore

**Files:**
- Create: `frontend/store/codeExplorerStore.ts`

- [ ] **Step 1: Create the store**

```typescript
import { create } from "zustand";
import { FileTreeNode, FileContent, OpenTab } from "@/types/vfs";

type ExplorerStatus = "idle" | "generating" | "valid" | "stale" | "invalid";

interface CodeExplorerState {
  isOpen: boolean;
  tree: FileTreeNode | null;
  generatedAt: string | null;
  status: ExplorerStatus;
  openTabs: OpenTab[];
  activeTabPath: string | null;
  fileCache: Map<string, FileContent>;
  expandedFolders: Set<string>;

  togglePanel: () => void;
  setTree: (tree: FileTreeNode, generatedAt: string) => void;
  openFile: (path: string, name: string, language: string) => void;
  closeTab: (path: string) => void;
  setActiveTab: (path: string) => void;
  setFileContent: (path: string, content: FileContent) => void;
  markStale: () => void;
  setStatus: (status: ExplorerStatus) => void;
  toggleFolder: (path: string) => void;
  clearFileCache: () => void;
  reset: () => void;
}

export const useCodeExplorerStore = create<CodeExplorerState>((set, get) => ({
  isOpen: false,
  tree: null,
  generatedAt: null,
  status: "idle",
  openTabs: [],
  activeTabPath: null,
  fileCache: new Map(),
  expandedFolders: new Set(),

  togglePanel: () => set((s) => ({ isOpen: !s.isOpen })),

  setTree: (tree, generatedAt) =>
    set({ tree, generatedAt, status: "valid" }),

  openFile: (path, name, language) =>
    set((s) => {
      const exists = s.openTabs.some((t) => t.path === path);
      if (exists) {
        return { activeTabPath: path };
      }
      return {
        openTabs: [...s.openTabs, { path, name, language }],
        activeTabPath: path,
      };
    }),

  closeTab: (path) =>
    set((s) => {
      const newTabs = s.openTabs.filter((t) => t.path !== path);
      const newCache = new Map(s.fileCache);
      newCache.delete(path);
      let newActive = s.activeTabPath;
      if (s.activeTabPath === path) {
        const idx = s.openTabs.findIndex((t) => t.path === path);
        newActive = newTabs[Math.min(idx, newTabs.length - 1)]?.path ?? null;
      }
      return { openTabs: newTabs, activeTabPath: newActive, fileCache: newCache };
    }),

  setActiveTab: (path) => set({ activeTabPath: path }),

  setFileContent: (path, content) =>
    set((s) => {
      const newCache = new Map(s.fileCache);
      newCache.set(path, content);
      return { fileCache: newCache };
    }),

  markStale: () =>
    set((s) => (s.status === "generating" ? {} : { status: "stale" })),

  setStatus: (status) => set({ status }),

  toggleFolder: (path) =>
    set((s) => {
      const next = new Set(s.expandedFolders);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return { expandedFolders: next };
    }),

  clearFileCache: () => set({ fileCache: new Map() }),

  reset: () =>
    set({
      isOpen: false,
      tree: null,
      generatedAt: null,
      status: "idle",
      openTabs: [],
      activeTabPath: null,
      fileCache: new Map(),
      expandedFolders: new Set(),
    }),
}));
```

- [ ] **Step 2: Commit**

```bash
git add frontend/store/codeExplorerStore.ts
git commit -m "feat(vfs): add codeExplorerStore with tab management and stale detection"
```

---

### Task 9: useCodeGeneration hook

**Files:**
- Create: `frontend/hooks/useCodeGeneration.ts`

- [ ] **Step 1: Create the debounce hook**

```typescript
import { useEffect, useRef } from "react";
import { useEditorStore } from "@/store/editorStore";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { generateCode, getFileTree } from "@/lib/vfs-client";
import { GenerationStatus } from "@/types/vfs";

const DEBOUNCE_MS = 800;

export function useCodeGeneration() {
  const abortRef = useRef<AbortController | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const unsubscribe = useEditorStore.subscribe((state, prev) => {
      const changed =
        state.nodes !== prev.nodes ||
        state.edges !== prev.edges ||
        state.projectName !== prev.projectName;

      if (!changed) return;

      const explorerState = useCodeExplorerStore.getState();

      if (!explorerState.isOpen) {
        if (explorerState.status !== "idle") {
          explorerState.markStale();
        }
        return;
      }

      explorerState.markStale();

      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (abortRef.current) abortRef.current.abort();

      timeoutRef.current = setTimeout(() => {
        triggerGeneration(state.projectName, state.nodes, state.edges);
      }, DEBOUNCE_MS);
    });

    return () => {
      unsubscribe();
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (abortRef.current) abortRef.current.abort();
    };
  }, []);

  // Regenerate on panel open if stale
  useEffect(() => {
    const unsubscribe = useCodeExplorerStore.subscribe((state, prev) => {
      if (state.isOpen && !prev.isOpen) {
        const editorState = useEditorStore.getState();
        if (state.status === "stale" || state.status === "idle") {
          triggerGeneration(editorState.projectName, editorState.nodes, editorState.edges);
        }
      }
    });
    return unsubscribe;
  }, []);
}

async function triggerGeneration(
  projectName: string,
  nodes: any[],
  edges: any[]
) {
  const store = useCodeExplorerStore.getState();
  store.setStatus("generating");

  const graph = {
    version: 1 as const,
    name: projectName,
    nodes,
    edges,
  };

  try {
    const result = await generateCode(projectName, graph);

    if (result.status === GenerationStatus.INVALID) {
      useCodeExplorerStore.getState().setStatus("invalid");
      return;
    }

    const abort = new AbortController();
    const treeResponse = await getFileTree(projectName, abort.signal);
    const explorerStore = useCodeExplorerStore.getState();
    explorerStore.setTree(treeResponse.root, treeResponse.generatedAt);
    explorerStore.clearFileCache();
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") return;
    useCodeExplorerStore.getState().setStatus("stale");
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/hooks/useCodeGeneration.ts
git commit -m "feat(vfs): add useCodeGeneration hook with debounce and stale detection"
```

---

### Task 10: ActivityBar and CodeExplorerPanel components

**Files:**
- Create: `frontend/components/code-explorer/ActivityBar.tsx`
- Create: `frontend/components/code-explorer/CodeExplorerPanel.tsx`
- Create: `frontend/components/code-explorer/FileTree.tsx`
- Create: `frontend/components/code-explorer/FileTreeItem.tsx`

- [ ] **Step 1: Create ActivityBar**

```tsx
"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { CodeIcon } from "lucide-react";

export function ActivityBar() {
  const isOpen = useCodeExplorerStore((s) => s.isOpen);
  const togglePanel = useCodeExplorerStore((s) => s.togglePanel);

  return (
    <div className="flex flex-col items-center w-10 bg-background border-r py-2 gap-3 shrink-0">
      <button
        onClick={togglePanel}
        className={`w-7 h-7 flex items-center justify-center rounded transition-colors ${
          isOpen
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:text-foreground hover:bg-muted"
        }`}
        title="Code Explorer"
      >
        <CodeIcon className="size-4" />
      </button>
    </div>
  );
}
```

- [ ] **Step 2: Create FileTreeItem**

```tsx
"use client";

import { FileTreeNode, FileType } from "@/types/vfs";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { ChevronRightIcon, ChevronDownIcon, FileIcon, FolderIcon, FolderOpenIcon } from "lucide-react";

interface FileTreeItemProps {
  node: FileTreeNode;
  depth: number;
}

export function FileTreeItem({ node, depth }: FileTreeItemProps) {
  const expandedFolders = useCodeExplorerStore((s) => s.expandedFolders);
  const toggleFolder = useCodeExplorerStore((s) => s.toggleFolder);
  const openFile = useCodeExplorerStore((s) => s.openFile);
  const activeTabPath = useCodeExplorerStore((s) => s.activeTabPath);

  const isFolder = node.type === FileType.DIRECTORY;
  const isExpanded = expandedFolders.has(node.path);
  const isActive = !isFolder && activeTabPath === node.path;

  const handleClick = () => {
    if (isFolder) {
      toggleFolder(node.path);
    } else {
      openFile(node.path, node.name, detectLanguage(node.name));
    }
  };

  return (
    <div>
      <button
        onClick={handleClick}
        className={`flex items-center gap-1 w-full text-left py-0.5 px-1 text-xs rounded transition-colors ${
          isActive
            ? "bg-primary/15 text-primary"
            : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
        }`}
        style={{ paddingLeft: `${depth * 12 + 4}px` }}
      >
        {isFolder ? (
          <>
            {isExpanded ? (
              <ChevronDownIcon className="size-3 shrink-0" />
            ) : (
              <ChevronRightIcon className="size-3 shrink-0" />
            )}
            {isExpanded ? (
              <FolderOpenIcon className="size-3.5 shrink-0 text-yellow-500" />
            ) : (
              <FolderIcon className="size-3.5 shrink-0 text-yellow-500" />
            )}
          </>
        ) : (
          <>
            <span className="w-3 shrink-0" />
            <FileIcon className="size-3.5 shrink-0" />
          </>
        )}
        <span className="truncate">{node.name}</span>
      </button>
      {isFolder && isExpanded && node.children?.map((child) => (
        <FileTreeItem key={child.path} node={child} depth={depth + 1} />
      ))}
    </div>
  );
}

function detectLanguage(name: string): string {
  if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
  if (name === "Dockerfile") return "dockerfile";
  if (name.endsWith(".properties")) return "properties";
  if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
  return "text";
}
```

- [ ] **Step 3: Create FileTree**

```tsx
"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { FileTreeItem } from "./FileTreeItem";

export function FileTree() {
  const tree = useCodeExplorerStore((s) => s.tree);

  if (!tree || !tree.children) {
    return null;
  }

  return (
    <div className="py-1">
      {tree.children.map((node) => (
        <FileTreeItem key={node.path} node={node} depth={0} />
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Create CodeExplorerPanel**

```tsx
"use client";

import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { Badge } from "@/components/ui/badge";
import { FileTree } from "./FileTree";
import { LoaderIcon } from "lucide-react";

const statusConfig: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
  idle: { label: "idle", variant: "outline" },
  generating: { label: "generating", variant: "secondary" },
  valid: { label: "valid", variant: "default" },
  stale: { label: "stale", variant: "outline" },
  invalid: { label: "invalid", variant: "destructive" },
};

export function CodeExplorerPanel() {
  const isOpen = useCodeExplorerStore((s) => s.isOpen);
  const status = useCodeExplorerStore((s) => s.status);
  const tree = useCodeExplorerStore((s) => s.tree);

  if (!isOpen) return null;

  const config = statusConfig[status] || statusConfig.idle;

  return (
    <div className="w-60 bg-background border-r flex flex-col shrink-0 overflow-hidden">
      <div className="h-8 border-b flex items-center px-2.5 gap-2 shrink-0">
        <span className="text-[10px] font-semibold tracking-wider text-muted-foreground uppercase flex-1">
          Code Explorer
        </span>
        <Badge variant={config.variant} className="h-4 text-[9px] px-1.5 gap-1">
          {status === "generating" && <LoaderIcon className="size-2.5 animate-spin" />}
          {config.label}
        </Badge>
      </div>
      <div className="flex-1 overflow-y-auto">
        {status === "idle" && !tree ? (
          <div className="flex items-center justify-center h-full px-4">
            <p className="text-muted-foreground text-xs text-center">
              Add nodes to your graph to see generated code here
            </p>
          </div>
        ) : (
          <FileTree />
        )}
        {status === "invalid" && tree && (
          <div className="px-2 py-1.5 border-t">
            <p className="text-[10px] text-destructive">
              Graph has validation errors — showing last valid state
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add frontend/components/code-explorer/
git commit -m "feat(vfs): add ActivityBar, CodeExplorerPanel, FileTree components"
```

---

### Task 11: CodeViewerModal with Shiki

**Files:**
- Create: `frontend/components/code-explorer/CodeViewerModal.tsx`
- Create: `frontend/lib/shiki.ts`

- [ ] **Step 1: Create Shiki helper**

```typescript
import { createHighlighter, type Highlighter } from "shiki";

let highlighterPromise: Promise<Highlighter> | null = null;

export function getHighlighter(): Promise<Highlighter> {
  if (!highlighterPromise) {
    highlighterPromise = createHighlighter({
      themes: ["one-dark-pro"],
      langs: ["kotlin", "groovy", "dockerfile", "properties", "yaml"],
    });
  }
  return highlighterPromise;
}

const langMap: Record<string, string> = {
  kotlin: "kotlin",
  dockerfile: "dockerfile",
  properties: "properties",
  yaml: "yaml",
  text: "text",
};

export function mapLanguage(lang: string): string {
  return langMap[lang] ?? "text";
}
```

- [ ] **Step 2: Create CodeViewerModal**

```tsx
"use client";

import { useEffect, useState, useCallback } from "react";
import { useCodeExplorerStore } from "@/store/codeExplorerStore";
import { useEditorStore } from "@/store/editorStore";
import { getFileContent } from "@/lib/vfs-client";
import { getHighlighter, mapLanguage } from "@/lib/shiki";
import { XIcon, LoaderIcon } from "lucide-react";

export function CodeViewerModal() {
  const openTabs = useCodeExplorerStore((s) => s.openTabs);
  const activeTabPath = useCodeExplorerStore((s) => s.activeTabPath);
  const fileCache = useCodeExplorerStore((s) => s.fileCache);
  const setFileContent = useCodeExplorerStore((s) => s.setFileContent);
  const setActiveTab = useCodeExplorerStore((s) => s.setActiveTab);
  const closeTab = useCodeExplorerStore((s) => s.closeTab);
  const status = useCodeExplorerStore((s) => s.status);
  const projectName = useEditorStore((s) => s.projectName);

  const [highlightedHtml, setHighlightedHtml] = useState<string>("");
  const [loading, setLoading] = useState(false);

  const activeTab = openTabs.find((t) => t.path === activeTabPath);
  const activeContent = activeTabPath ? fileCache.get(activeTabPath) : null;

  // Load file content on tab activation
  useEffect(() => {
    if (!activeTabPath || fileCache.has(activeTabPath)) return;

    let cancelled = false;
    setLoading(true);
    getFileContent(projectName, activeTabPath)
      .then((content) => {
        if (!cancelled) {
          setFileContent(activeTabPath, content);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [activeTabPath, projectName, fileCache, setFileContent]);

  // Highlight code
  useEffect(() => {
    if (!activeContent) {
      setHighlightedHtml("");
      return;
    }

    let cancelled = false;
    getHighlighter().then((highlighter) => {
      if (cancelled) return;
      const lang = mapLanguage(activeContent.language);
      const supported = highlighter.getLoadedLanguages().includes(lang);
      const html = highlighter.codeToHtml(activeContent.content, {
        lang: supported ? lang : "text",
        theme: "one-dark-pro",
      });
      setHighlightedHtml(html);
    });

    return () => { cancelled = true; };
  }, [activeContent]);

  // Close on Escape
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === "Escape" && openTabs.length > 0) {
        // Close active tab
        if (activeTabPath) closeTab(activeTabPath);
      }
    },
    [activeTabPath, openTabs.length, closeTab]
  );

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  if (openTabs.length === 0) return null;

  const lineCount = activeContent?.content.split("\n").length ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60"
        onClick={() => { if (activeTabPath) closeTab(activeTabPath); }}
      />

      {/* Modal */}
      <div className="relative w-[720px] max-h-[80vh] bg-background border rounded-lg shadow-2xl flex flex-col overflow-hidden">
        {/* Header with tabs */}
        <div className="h-9 bg-muted/30 border-b flex items-center px-2 gap-1 shrink-0 overflow-x-auto">
          {openTabs.map((tab) => (
            <button
              key={tab.path}
              onClick={() => setActiveTab(tab.path)}
              className={`flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-t transition-colors shrink-0 ${
                tab.path === activeTabPath
                  ? "bg-background text-foreground border-b-2 border-primary"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <span className="truncate max-w-32">{tab.name}</span>
              <XIcon
                className="size-3 opacity-50 hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  closeTab(tab.path);
                }}
              />
            </button>
          ))}
        </div>

        {/* Code content */}
        <div className="flex-1 overflow-auto p-0">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <LoaderIcon className="size-5 animate-spin text-muted-foreground" />
            </div>
          ) : highlightedHtml ? (
            <div
              className="text-xs [&>pre]:!bg-transparent [&>pre]:p-4 [&>pre]:m-0 [&_code]:leading-relaxed"
              dangerouslySetInnerHTML={{ __html: highlightedHtml }}
            />
          ) : (
            <div className="flex items-center justify-center h-48 text-muted-foreground text-xs">
              No content
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="h-7 bg-muted/30 border-t flex items-center px-3 gap-3 text-[10px] text-muted-foreground shrink-0">
          <span>{activeTab?.language ?? "text"}</span>
          <span className="text-border">|</span>
          <span>{lineCount} lines</span>
          <span className="text-border">|</span>
          <span>UTF-8</span>
          <span className="flex-1" />
          {status === "valid" && (
            <span className="text-primary bg-primary/10 px-1.5 py-0.5 rounded text-[9px]">
              last valid state
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/components/code-explorer/CodeViewerModal.tsx frontend/lib/shiki.ts
git commit -m "feat(vfs): add CodeViewerModal with Shiki syntax highlighting and tab bar"
```

---

### Task 12: Integrate into page layout

**Files:**
- Modify: `frontend/app/page.tsx:92-108`

- [ ] **Step 1: Add imports and hook to page.tsx**

Add these imports at the top of `frontend/app/page.tsx`:

```typescript
import { ActivityBar } from "@/components/code-explorer/ActivityBar";
import { CodeExplorerPanel } from "@/components/code-explorer/CodeExplorerPanel";
import { CodeViewerModal } from "@/components/code-explorer/CodeViewerModal";
import { useCodeGeneration } from "@/hooks/useCodeGeneration";
```

Inside the `EditorPage` component, add the hook call (after existing hooks):

```typescript
useCodeGeneration();
```

- [ ] **Step 2: Update the layout JSX**

Change the return block (lines ~92-108) from:

```tsx
return (
  <div className="flex flex-col h-screen">
    <Toolbar onNewProject={handleNewProjectRequest} />
    <div className="flex flex-1 overflow-hidden">
      <Canvas />
      <PropertiesPanel />
    </div>
    <PreviewPanel />
    <ValidationPanel />
    ...
  </div>
);
```

to:

```tsx
return (
  <div className="flex h-screen">
    <ActivityBar />
    <CodeExplorerPanel />
    <div className="flex flex-col flex-1 overflow-hidden">
      <Toolbar onNewProject={handleNewProjectRequest} />
      <div className="flex flex-1 overflow-hidden">
        <Canvas />
        <PropertiesPanel />
      </div>
      <PreviewPanel />
      <ValidationPanel />
      ...
    </div>
    <CodeViewerModal />
  </div>
);
```

The outer flex is now `flex-row` (default). ActivityBar + ExplorerPanel sit left, the rest fills remaining space.

- [ ] **Step 3: Verify the app loads without errors**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev`
Expected: Page loads, Activity Bar visible on the left, clicking `</>` icon toggles the Explorer Panel.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/page.tsx
git commit -m "feat(vfs): integrate ActivityBar, CodeExplorerPanel, and CodeViewerModal into layout"
```

---

### Task 13: End-to-end smoke test

**Files:** none (manual verification)

- [ ] **Step 1: Start backend**

Run: `./gradlew bootRun`

- [ ] **Step 2: Start frontend**

Run: `cd /Users/czarnik/IdeaProjects/modeler/frontend && npm run dev`

- [ ] **Step 3: Manual verification checklist**

1. Open app in browser
2. Activity Bar visible on left with `</>` icon
3. Click icon → Explorer Panel slides open, shows empty state message
4. Create an agent node with name + system prompt + model provider
5. Panel status changes: `stale` → `generating` → `valid`
6. File tree appears with generated files (AgentConfig.kt, Application.kt, etc.)
7. Click on a file → Modal opens with syntax-highlighted code
8. Open second file → new tab appears in modal
9. Switch between tabs → content loads correctly
10. Close tab with × → tab removed
11. Press Escape → active tab closes
12. Close Explorer Panel → re-open → tree still visible
13. Modify agent node → status goes `stale` → auto-regenerates
14. Close panel → modify node → re-open panel → immediate regeneration

- [ ] **Step 4: Run all backend tests to ensure no regressions**

Run: `./gradlew test`
Expected: ALL PASS
