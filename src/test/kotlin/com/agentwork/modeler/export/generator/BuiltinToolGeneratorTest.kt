package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class BuiltinToolGeneratorTest {
    private val generator = BuiltinToolGenerator()

    @Test
    fun `returns null for THINK`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.THINK)))
    }

    @Test
    fun `returns null for USER_PROMPT`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.USER_PROMPT)))
    }

    @Test
    fun `returns null for FILESYSTEM`() {
        assertNull(generator.generate(KoogBuiltinTool(BuiltinToolType.FILESYSTEM)))
    }

    @Test
    fun `generates ShellToolSet with workingDir config`() {
        val tool = KoogBuiltinTool(BuiltinToolType.SHELL, mapOf("workingDir" to "/app"))
        val code = generator.generate(tool)!!
        assertContains(code, "class ShellToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "ProcessBuilder")
        assertContains(code, "workingDir")
        assertContains(code, "waitFor")
    }

    @Test
    fun `generates FetchToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.FETCH))!!
        assertContains(code, "class FetchToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "HttpClient")
    }

    @Test
    fun `generates MemoryToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.MEMORY))!!
        assertContains(code, "class MemoryToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "store")
        assertContains(code, "retrieve")
        assertContains(code, "listKeys")
    }

    @Test
    fun `generates TodoToolSet`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.TODO))!!
        assertContains(code, "class TodoToolSet")
        assertContains(code, "@Tool")
        assertContains(code, "addTask")
        assertContains(code, "completeTask")
        assertContains(code, "listTasks")
    }

    @Test
    fun `generates JiraToolSet with all jira tools`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA, mapOf("baseUrl" to "https://x.atlassian.net")))!!
        assertContains(code, "class JiraToolSet")
        listOf("fun searchIssues(", "fun getIssue(", "fun createIssue(", "fun addComment(", "fun transitionIssue(")
            .forEach { assertContains(code, it) }
        assertContains(code, "/rest/api/3/search/jql")
        assertContains(code, "/rest/api/3/issue")
    }

    @Test
    fun `jira tool set authenticates with basic auth from env and fails fast when missing`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
        assertContains(code, "\"ATLASSIAN_EMAIL\"")
        assertContains(code, "\"ATLASSIAN_API_TOKEN\"")
        assertContains(code, "is not set")
        assertContains(code, "\"Basic \"")
        assertContains(code, "Base64")
    }

    @Test
    fun `jira tool set encodes queries, builds json safely and trims base url`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
        assertContains(code, "URLEncoder.encode(")
        assertContains(code, "buildJsonObject")
        assertContains(code, "it.scheme + \"://\" + it.authority")
    }

    @Test
    fun `jira tool set wraps text in ADF and resolves transitions by name`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
        assertContains(code, "\"doc\"")
        assertContains(code, "\"paragraph\"")
        assertContains(code, "/transitions")
        assertContains(code, "ignoreCase = true")
        assertContains(code, "Available:")
    }

    @Test
    fun `generates ConfluenceToolSet with all confluence tools`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.CONFLUENCE))!!
        assertContains(code, "class ConfluenceToolSet")
        listOf("fun searchPages(", "fun getPage(", "fun createPage(", "fun updatePage(")
            .forEach { assertContains(code, it) }
        assertContains(code, "/wiki/rest/api/search?cql=")
        assertContains(code, "/wiki/api/v2/pages")
        assertContains(code, "body-format=storage")
        assertContains(code, "\"ATLASSIAN_API_TOKEN\"")
        assertContains(code, "URLEncoder.encode(")
    }

    @Test
    fun `confluence tool set resolves space id and bumps page version`() {
        val code = generator.generate(KoogBuiltinTool(BuiltinToolType.CONFLUENCE))!!
        assertContains(code, "/wiki/api/v2/spaces?keys=")
        assertContains(code, "\"representation\"")
        assertContains(code, "\"storage\"")
        assertContains(code, "+ 1")
    }

    @Test
    fun `atlassian tool sets reject ids that would change the request path`() {
        val jira = generator.generate(KoogBuiltinTool(BuiltinToolType.JIRA))!!
        assertContains(jira, "Regex(\"[A-Za-z][A-Za-z0-9_]*-\\\\d+|\\\\d+\").matches(issueKey)")
        assertContains(jira, "\"Invalid issueKey: \"")
        val confluence = generator.generate(KoogBuiltinTool(BuiltinToolType.CONFLUENCE))!!
        assertContains(confluence, "Regex(\"\\\\d+\").matches(pageId)")
        assertContains(confluence, "\"Invalid pageId: \"")
    }
}
