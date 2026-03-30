package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.model.GraphEdge
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.WorkspaceGraph
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
        @RequestBody request: GenerateRequest
    ): GenerationResult {
        return codeGenerationService.generateForProject(projectName, request.resolveGraph(), request.backend)
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

data class GenerateRequest(
    val graph: WorkspaceGraph? = null,
    val backend: String = "spring-boot",
    val version: Int? = null,
    val name: String? = null,
    val nodes: List<GraphNode>? = null,
    val edges: List<GraphEdge>? = null
) {
    fun resolveGraph(): WorkspaceGraph = graph ?: WorkspaceGraph(
        version = version ?: 1,
        name = name ?: "project",
        nodes = nodes ?: emptyList(),
        edges = edges ?: emptyList()
    )
}
