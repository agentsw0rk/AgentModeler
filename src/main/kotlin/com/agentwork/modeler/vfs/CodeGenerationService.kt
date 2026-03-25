package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.ExportValidationException
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.vfs.model.GenerationResult
import com.agentwork.modeler.vfs.model.GenerationStatus
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CodeGenerationService(
    private val exportService: ExportService,
    private val vfsService: VirtualFileSystemService
) {
    fun generateForProject(projectName: String, graph: ProjectGraph): GenerationResult {
        val files = try {
            exportService.generateFiles(graph)
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
