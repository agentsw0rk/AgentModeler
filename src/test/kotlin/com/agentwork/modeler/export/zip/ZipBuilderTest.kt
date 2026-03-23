package com.agentwork.modeler.export.zip

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals

class ZipBuilderTest {
    private val builder = ZipBuilder()

    @Test
    fun `creates ZIP with expected entries and content`() {
        val files = mapOf(
            "src/Hello.kt" to "fun main() {}",
            "src/tools/MyTool.kt" to "class MyTool"
        )
        val zip = builder.buildZip(files)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = String(zis.readAllBytes())
                entry = zis.nextEntry
            }
        }
        assertEquals(2, entries.size)
        assertEquals("fun main() {}", entries["src/Hello.kt"])
        assertEquals("class MyTool", entries["src/tools/MyTool.kt"])
    }
}
