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

    @Test
    fun `writeFiles throws when max projects limit is reached`() {
        for (i in 1..10) {
            service.writeFiles("project-$i", mapOf("A.kt" to "a"))
        }
        assertThrows<RuntimeException> {
            service.writeFiles("project-11", mapOf("A.kt" to "a"))
        }
    }

    @Test
    fun `cleanupExpired removes projects past TTL`() {
        service.writeFiles("my-project", mapOf("A.kt" to "a"))
        assertTrue(service.exists("my-project"))

        // Manually expire the project by setting lastAccess far in the past
        val field = VirtualFileSystemService::class.java.getDeclaredField("projects")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val projects = field.get(service) as java.util.concurrent.ConcurrentHashMap<String, ManagedProject>
        projects["my-project"]!!.lastAccess = java.time.Instant.now().minusSeconds(31 * 60)

        service.cleanupExpired()

        assertFalse(service.exists("my-project"))
    }
}
