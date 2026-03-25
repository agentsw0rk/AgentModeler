package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.ExportService
import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.export.validation.GraphValidator
import com.agentwork.modeler.vfs.model.GenerationStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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
