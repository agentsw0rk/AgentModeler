package com.agentwork.modeler.export

import com.agentwork.modeler.export.model.WorkspaceGraph
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ExportController(private val exportService: ExportService) {

    @PostMapping("/api/export")
    fun export(@RequestBody graph: WorkspaceGraph): ResponseEntity<ByteArray> {
        val zip = exportService.export(graph)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${graph.name}.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(zip)
    }

    @ExceptionHandler(ExportValidationException::class)
    fun handleValidation(ex: ExportValidationException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            mapOf("error" to "Validation failed", "details" to ex.errors)
        )
    }
}
