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
