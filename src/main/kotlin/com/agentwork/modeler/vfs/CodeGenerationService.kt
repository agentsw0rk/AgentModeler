package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.ExportValidationException
import com.agentwork.modeler.export.model.WorkspaceGraph
import com.agentwork.modeler.vfs.model.GenerationResult
import com.agentwork.modeler.vfs.model.GenerationStatus
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CodeGenerationService(
    private val exportService: ExportService,
    private val vfsService: VirtualFileSystemService
) {
    fun generateForProject(projectName: String, graph: WorkspaceGraph, backendId: String = "spring-boot", targets: List<String>? = null): GenerationResult {
        val files = try {
            exportService.generateFiles(graph, backendId, targets)
        } catch (e: ExportValidationException) {
            return GenerationResult(
                status = GenerationStatus.INVALID,
                errors = e.errors
            )
        }

        vfsService.writeFiles(projectName, files)

        return GenerationResult(
            status = GenerationStatus.VALID,
            fileCount = files.size,
            generatedAt = Instant.now().toString()
        )
    }
}
