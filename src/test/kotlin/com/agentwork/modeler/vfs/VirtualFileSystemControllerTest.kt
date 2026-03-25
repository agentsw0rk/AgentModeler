package com.agentwork.modeler.vfs

import com.agentwork.modeler.export.model.GraphNode
import com.agentwork.modeler.export.model.Position
import com.agentwork.modeler.export.model.ProjectGraph
import com.agentwork.modeler.vfs.model.FileContent
import com.agentwork.modeler.vfs.model.FileTreeNode
import com.agentwork.modeler.vfs.model.FileType
import com.agentwork.modeler.vfs.model.GenerationResult
import com.agentwork.modeler.vfs.model.GenerationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(VirtualFileSystemController::class)
class VirtualFileSystemControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @MockitoBean
    private lateinit var codeGenerationService: CodeGenerationService

    @MockitoBean
    private lateinit var vfsService: VirtualFileSystemService

    private val validGraph = ProjectGraph(
        version = 1,
        name = "test-project",
        nodes = listOf(
            GraphNode(
                "a1", "agent", Position(0.0, 0.0),
                mapOf("name" to "Agent", "systemPrompt" to "Hello", "modelProvider" to "OPENAI")
            )
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
        val tree = FileTreeNode(
            "test-project", "", FileType.DIRECTORY,
            listOf(FileTreeNode("Main.kt", "Main.kt", FileType.FILE, null, 100L)), null
        )
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
